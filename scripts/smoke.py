#!/usr/bin/env python3
"""Exercise comparison, recovery, replay and report storage in Docker Compose."""

import json
import os
import subprocess
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from uuid import UUID

BASE_URL = f"http://127.0.0.1:{os.environ.get('APP_PORT', '8080')}"
TOPIC = "comparison.recorded.v1"


def compose(*arguments, stdin=None):
    result = subprocess.run(
        ["docker", "compose", *arguments],
        input=stdin, text=True, stdout=subprocess.PIPE, check=True, timeout=60,
    )
    return result.stdout.strip()


def request(path, method="GET"):
    try:
        with urlopen(Request(BASE_URL + path, method=method), timeout=4) as response:
            return response.status, response.read(), response.headers
    except HTTPError as error:
        return error.code, error.read(), error.headers
    except (URLError, TimeoutError):
        return 0, b"", {}


def eventually(read, accepts, description, timeout=90):
    deadline = time.monotonic() + timeout
    while True:
        result = read()
        if accepts(result):
            return result
        if time.monotonic() >= deadline:
            raise AssertionError(f"Timed out waiting for {description}. Last result: {result!r}")
        time.sleep(0.5)


def create_comparison():
    status, body, _ = request("/api/comparisons", "POST")
    assert status == 202, (status, body)
    return str(UUID(json.loads(body)["id"]))


def comparison(comparison_id):
    _, body, _ = eventually(
        lambda: request(f"/api/comparisons/{comparison_id}"),
        lambda response: response[0] == 200,
        "comparison evidence",
    )
    return json.loads(body)


def replay(comparison_id):
    message = compose(
        "exec", "-T", "postgres", "psql", "-U", "bancada", "-d", "bancada",
        "-Atc", f"SELECT id::text || '|' || payload::text "
        f"FROM recorded_comparisons WHERE id = '{comparison_id}'",
    )
    assert message.startswith(comparison_id + "|"), "The recorded event is missing"
    compose(
        "exec", "-T", "kafka", "/opt/kafka/bin/kafka-console-producer.sh",
        "--bootstrap-server", "kafka:9092", "--topic", TOPIC,
        "--property", "parse.key=true", "--property", "key.separator=|",
        stdin=message + "\n",
    )


def duplicates():
    raw = compose(
        "exec", "-T", "web", "sh", "-c",
        "wget -qO- http://evidence:8080/actuator/metrics/comparison.duplicate || true",
    )
    if not raw:
        return 0
    return next(item["value"] for item in json.loads(raw)["measurements"]
                if item["statistic"] == "COUNT")


def main():
    eventually(
        lambda: compose("exec", "-T", "web", "sh", "-c",
                        "wget -qO- http://recorder:8080/actuator/health || true"),
        lambda body: '"status":"UP"' in body,
        "recorder startup",
        timeout=120,
    )
    assert request("/")[0] == 200, "The Angular entry point is unavailable"

    compose("stop", "evidence")
    try:
        comparison_id = create_comparison()
        assert request(f"/api/comparisons/{comparison_id}")[0] in (0, 404, 502, 503, 504)
        eventually(
            lambda: compose(
                "exec", "-T", "postgres", "psql", "-U", "bancada", "-d", "bancada",
                "-Atc", f"SELECT published_at IS NOT NULL FROM comparison_outbox "
                f"WHERE id = '{comparison_id}'",
            ),
            lambda value: value == "t",
            "outbox publication while the consumer is offline",
        )
        print(f"Recorded and published {comparison_id} while the consumer was offline.")
    finally:
        compose("start", "evidence")

    evidence = comparison(comparison_id)
    assert evidence["id"] == comparison_id
    assert evidence["amountCents"] == 4290
    assert evidence["firstDifference"] == 3
    assert evidence["baseline"]["verdict"] == "PASS"
    assert len(evidence["baseline"]["authorizationIds"]) == 1
    assert evidence["candidate"]["verdict"] == "REGRESSION"
    assert len(set(evidence["candidate"]["authorizationIds"])) == 2
    print("Recovered the comparison with one reference and two candidate authorizations.")

    before_replay = duplicates()
    replay(comparison_id)
    eventually(duplicates, lambda count: count >= before_replay + 1, "duplicate event handling")
    assert comparison(comparison_id) == evidence
    documents = compose(
        "exec", "-T", "mongo", "mongosh", "bancada", "--quiet", "--eval",
        f"db.comparisons.countDocuments({{_id: '{comparison_id}'}})",
    )
    assert documents == "1", documents
    print("Replayed the event and verified that the evidence stayed unchanged.")

    report_path = f"/api/comparisons/{comparison_id}/report"
    status, body, _ = request(report_path, "POST")
    assert status == 204, (status, body)
    status, body, headers = request(report_path)
    assert status == 200, (status, body)
    assert "attachment" in headers["Content-Disposition"]
    assert json.loads(body) == evidence
    print("Saved and downloaded the same report.")


if __name__ == "__main__":
    main()
