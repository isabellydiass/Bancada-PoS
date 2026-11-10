# Hosted deployment

Cloudflare Pages serves the Angular app and forwards its API calls to two Render web services. Aiven hosts Apache Kafka and PostgreSQL, Atlas hosts MongoDB, and a private Backblaze B2 bucket stores JSON reports through the S3 API.

## Data services

- In Aiven, create one Free PostgreSQL service and one Free Kafka service. In Kafka, create the `comparison.recorded.v1` topic and select the SASL connection with the project CA.
- In Atlas, create an M0 cluster and a database user with read/write access to `bancada`. Allow the outbound CIDR ranges shown by the Render evidence service. Include the database name in the connection URI.
- In Backblaze, create a private bucket and a bucket-scoped application key with read and write access. Use the S3 endpoint and region shown for the bucket.

## Render

Create the two Docker web services with the Free compute plan. Use `render.yaml` for the service settings. Add the Aiven project CA as `aiven-ca.pem` under Secret Files in both services. Set the recorder's PostgreSQL credentials and use this JDBC URL format:

```text
jdbc:postgresql://HOST:PORT/DB?sslmode=verify-full&sslrootcert=/etc/secrets/aiven-ca.pem
```

In both services, set `SPRING_KAFKA_BOOTSTRAP_SERVERS` to the Aiven SASL host and port. Set `SPRING_APPLICATION_JSON` with the following Spring Kafka configuration. Replace the placeholders with the Kafka service user's credentials in Render.

```json
{
  "spring": {
    "kafka": {
      "admin": { "auto-create": false },
      "properties": {
        "security.protocol": "SASL_SSL",
        "sasl.mechanism": "SCRAM-SHA-256",
        "sasl.jaas.config": "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"USERNAME\" password=\"PASSWORD\";"
      },
      "ssl": {
        "trust-store-type": "PEM",
        "trust-store-location": "file:/etc/secrets/aiven-ca.pem"
      }
    }
  }
}
```

The evidence service uses the MongoDB URI and the Backblaze bucket endpoint, region and application key. Limit the application key to the report prefix and set `REPORT_BUCKET` and `REPORT_S3_ENDPOINT` so the SDK uses the private B2 bucket.

Generate a random gateway token and set the same value as `GATEWAY_ACCESS_TOKEN` in both Render services and in Pages. The gateway adds the token server-side. The browser calls only the Pages address.

## Cloudflare Pages

Build Angular with `npm ci` and `npm run build` inside `frontend/`. Put `deploy/cloudflare/_worker.js` and `_routes.json` in `frontend/dist/bancada-pos/browser/`, then upload the contents of that directory to Pages. The zip root must contain `index.html`.

Set these production variables and deploy with them in place:

| Variable | Value |
| --- | --- |
| `RECORDER_ORIGIN` | HTTPS origin of the Render recorder |
| `EVIDENCE_ORIGIN` | HTTPS origin of the Render evidence service |
| `GATEWAY_ACCESS_TOKEN` | The gateway token, stored as a secret |

Open the Pages address and run one comparison from a cold start. Check the two authorization counts, the difference on step 3, and the exported JSON. Render Free services can go idle. The first request checks both origins before recording a comparison.

References: [Render Blueprints](https://render.com/docs/blueprint-spec), [Cloudflare Pages direct upload](https://developers.cloudflare.com/pages/get-started/direct-upload/), [Aiven Kafka TLS](https://aiven.io/docs/products/kafka/howto/connect-with-java), [Backblaze S3 API](https://www.backblaze.com/docs/cloud-storage-s3-compatible-api).
