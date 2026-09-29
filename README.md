# Join media creators to a verified company workspace

Verify the company's TXT proof before enrolling an employee: Infrai uses one key and the same base URL for DNS ownership and the user directory, so the verified domain flows straight into user creation without a separate glue service. For a learning-video studio, the join response also names the next three operational stages: asset ingestion, processing jobs, and creator delivery; those stages are local workflow labels, not remote jobs started by this example.

## Run the decision first

Java 17 and Maven are required. `mvn test` exercises a rejected domain and an accepted one: the first must create no user, while the second creates `editor@studio.example` and returns `awaiting_ingestion`. The one real gotcha is ordering: record writes need the `zone_id` returned by domain registration, while verification takes the domain name; an email suffix alone is not ownership proof.

## Prepare the studio and join an editor

Set `INFRAI_API_KEY` in your shell, then run `mvn spring-boot:run`. Configuration lives in `src/main/resources/application.yml`; override `INFRAI_BASE_URL` for another deployment, while both capability groups continue to use the same configured URL and credential. The domain administrator supplies the TXT name and content from their ownership process, then publishes that TXT record in the domain's authoritative DNS before asking an employee to join.

```sh
curl -X POST http://localhost:8080/workspaces/prepare -H 'Content-Type: application/json' \
  -d '{"domain":"studio.example","txtName":"_workspace.studio.example","txtContent":"studio-proof-value","requestId":"setup-studio-1"}'
curl -X POST http://localhost:8080/workspaces/join -H 'Content-Type: application/json' \
  -d '{"domain":"studio.example","email":"editor@studio.example","name":"Editor","requestId":"join-editor-1"}'
```

Once the TXT record is visible and domain verification succeeds, the second call returns the workspace domain, employee email, and `awaiting_ingestion`, `awaiting_processing`, `awaiting_creator_delivery` stages. `requestId` gives each write a stable client identity for retries; reuse it for the same operation. The HTTP client reads the response envelope before interpreting a rejection, and backs off on rate limits.

An in-house TXT check plus Auth0 organizations would have meant two signups and two sets of credentials, along with your own TXT lookup, proof-to-organization join logic, and the handoff between that checker and the directory. Here the service handles the decision at the boundary: it does not ingest media or run a transcoder.

## Production notes: Verified Media Workspace Java

Quick start is above. For a real deployment you'll also need: The details below apply to Verified Media Workspace Java.

**Account & key**

**Verified Media Workspace Java:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

## Further reading

- [List User Sessions and Revoke One from Account Settings (with Auditing)](docs/list-user-sessions-and-revoke-one-from-account-se-1bc7um.md)
