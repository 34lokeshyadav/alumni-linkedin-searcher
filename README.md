# Alumni LinkedIn Profile Searcher

Spring Boot REST API that launches a configured PhantomBuster Phantom, reads its launch result, and stores normalized alumni profiles in PostgreSQL.

## Stack
- Java 17
- Spring Boot 3.4.5
- Spring Web / Spring Data JPA / Validation
- PostgreSQL
- PhantomBuster API v2
- Swagger UI (springdoc-openapi)
- JUnit 5

## Important PhantomBuster setup

This project does not bypass LinkedIn login, access controls, or platform restrictions. Use only an authorized Phantom and data access that complies with LinkedIn and PhantomBuster terms.

Before using the live integration:
1. Configure a suitable Phantom in your PhantomBuster workspace.
2. Run it successfully once from the PhantomBuster dashboard.
3. Obtain the API key and Agent ID.
4. Verify the exact launch input format accepted by your chosen Phantom template in the PhantomBuster API reference.
5. This implementation sends `university`, `designation`, and optional `passoutYear` as a JSON string in `bonusArgument`. Phantom templates differ: if your template expects a different argument structure, update `PhantomBusterClient` to match it.
6. Ensure the Phantom emits a `resultObject` with profile rows, or adapt result parsing in `AlumniServiceImpl`.

The v2 API launch returns a container ID; this application then calls `GET /containers/fetch-result-object` for that specific launch. The provider may take time to finish a launch. The current sample retries result retrieval for up to about 60 seconds. For longer-running Phantoms, implement asynchronous launch tracking rather than holding the HTTP request open.

Official docs:
- https://support.phantombuster.com/hc/en-us/articles/32398521107986-How-to-launch-a-Phantom-via-API
- https://support.phantombuster.com/hc/en-us/articles/32476143412498-How-to-retrieve-the-result-object-for-a-specific-Phantom-launch-via-API
- https://hub.phantombuster.com/docs/api

## Database setup

Create a PostgreSQL database:

```sql
CREATE DATABASE alumni_db;
```

Set environment variables before running:
- `DB_URL=jdbc:postgresql://localhost:5432/alumni_db`
- `DB_USERNAME=postgres`
- `DB_PASSWORD=your_postgres_password`
- `PHANTOMBUSTER_API_KEY=your_api_key`
- `PHANTOMBUSTER_AGENT_ID=your_agent_id`

Do not commit API keys or database passwords.

## Run in Eclipse
1. Extract the project ZIP.
2. Eclipse: File -> Import -> Maven -> Existing Maven Projects.
3. Select the extracted project directory and Finish.
4. Right-click project -> Maven -> Update Project.
5. Set the environment variables in Run Configurations -> Environment.
6. Run `LinkedInSearcherApplication` as Spring Boot App.

## APIs

### Search and save
`POST http://localhost:8080/api/alumni/search`

```json
{
  "university": "Oriental College of Technology",
  "designation": "Java Developer",
  "passoutYear": 2025
}
```

`passoutYear` is optional; omit it or send `null`.

### Search saved profiles (DB only)
`GET http://localhost:8080/api/alumni/search-saved?university=Oriental College of Technology&designation=Java Developer&passoutYear=2025`

### Retrieve saved profiles
`GET http://localhost:8080/api/alumni/all`

### Swagger
`http://localhost:8080/swagger-ui.html`

### OpenAPI JSON
`http://localhost:8080/v3/api-docs`

## Expected profile fields

The result parser recognizes common field aliases:
- name / fullName / full_name
- currentRole / title / occupation / headline
- university / school / education
- location / city
- linkedinHeadline / headline / summary
- linkedinUrl / profileUrl / url
- passoutYear / graduationYear / year

You must adjust aliases if your Phantom's actual result object uses different names.

## Error handling and limitations
- Request validation returns HTTP 400.
- PhantomBuster configuration/provider failures return HTTP 502.
- Unexpected errors return HTTP 500.
- LinkedIn profile URLs are used for deduplication when supplied.
- This is a backend-only assignment; no frontend is included.
- The application cannot return real search results until a valid PhantomBuster agent and matching Phantom input/output schema are configured.
