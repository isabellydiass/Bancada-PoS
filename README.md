# Bancada PoS

A firmware regression workbench for a simulated payment terminal. Compare two versions on the same BRL 42.90 purchase after a payment is approved and its response is lost.

| Version | What the terminal does next | Approved authorizations |
| --- | --- | ---: |
| Reference 1.4.0 | Looks up the original operation | 1 |
| Candidate 1.5.0 | Starts another operation for the same purchase | 2 |

The workbench marks the candidate as a regression and points to the first step that changed. Everything in the scenario is simulated.

[Try the live demo](https://bancada-pos.pages.dev). The first run can take longer while the hosted services start.

## How a comparison reaches the screen

1. Angular submits `POST /api/comparisons`. The Java 21/Spring Boot recorder runs the scenario and commits the result and an outbox row in PostgreSQL. It returns `202` and the comparison ID.
2. The recorder sends `comparison.recorded.v1` to Kafka and marks the outbox row as published after the broker acknowledges it. A failed send stays eligible for another attempt.
3. The Java 21/Spring Boot evidence service stores the comparison in MongoDB under its unique ID before acknowledging consumption. If Kafka delivers the same event again, the original evidence stays intact.
4. Angular polls `GET /api/comparisons/{id}` for the evidence. `POST /api/comparisons/{id}/report` saves the JSON to object storage and `GET /api/comparisons/{id}/report` downloads the saved report.

PostgreSQL is the durable record of what the simulator produced. MongoDB serves the screen a document with both step sequences. The read service can fall behind without preventing the recorder from accepting new comparisons. The extra database and broker are a deliberate trade-off for demonstrating recovery across service boundaries.

S3Mock is the default report store. The evidence service uses the AWS SDK for Java in both modes. For a private Amazon S3 bucket, configure `REPORT_BUCKET` and `AWS_REGION`, set `REPORT_S3_MOCK=false` and `REPORT_S3_ENDPOINT=`, and supply temporary credentials through the SDK's default credential provider chain. Compose forwards `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` and `AWS_SESSION_TOKEN` from the caller when set. The service only needs to put and get objects within the report prefix.

## Hosted deployment

See [Cloudflare Pages and Render deployment](docs/cloud-deployment.md) for the remote setup.

## Verify and present

Run `mvn verify` with Java 21 for the backend checks. With the Compose stack running, `python3 scripts/smoke.py` stops and restarts the evidence service, verifies the comparison, replays its Kafka event and round-trips the report. The same checks run in GitHub Actions.

Use `docker compose logs recorder evidence` to follow the comparison ID across the services. Actuator exposes the projection and duplicate counters inside the Compose network. The `comparison.projection.delay` timer measures the delay between recording and the first successful projection.

[Interactive presentation in Portuguese](docs/Bancada-PoS.pdf). Download it and open it in a PDF viewer to use the menu and page links.
