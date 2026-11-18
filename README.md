# Alumni LinkedIn Profile Searcher

Spring Boot REST API that searches and extracts alumni profiles, parses them, and stores normalized alumni profiles in PostgreSQL.

## Stack
- Java 17
- Spring Boot 3.4.5
- Spring Web / Spring Data JPA / Hibernate
- PostgreSQL
- PhantomBuster API v2 (LinkedIn Search Export)
- Google Custom Search (X-Ray Search)
- Swagger UI (springdoc-openapi)
- Docker & Maven

---

## Live Swagger Documentation & APIs

Once deployed or running locally:
- **Swagger UI Interactive Documentation:** `http://localhost:8080/swagger-ui.html` (or `https://<YOUR_DEPLOYED_URL>/swagger-ui.html`)
- **OpenAPI JSON Spec:** `http://localhost:8080/v3/api-docs` (or `https://<YOUR_DEPLOYED_URL>/v3/api-docs`)

### Endpoints
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/alumni/search` | Performs alumni search (via PhantomBuster or Google X-Ray), extracts real profiles, and saves them to DB. |
| `GET` | `/api/alumni/all` | Retrieves all saved alumni profiles from PostgreSQL. |
| `GET` | `/api/alumni/search-saved` | Filters already saved alumni by `university`, `designation`, and optional `passoutYear`. |

---

## Deployment Guide (Render.com - 100% Free)

This repository includes a multi-stage `Dockerfile` and a `render.yaml` Blueprint for 1-click cloud deployment.

### Step 1: Push Code to GitHub
```bash
git remote add origin https://github.com/<YOUR_USERNAME>/<YOUR_REPOSITORY_NAME>.git
git push -u origin main
```

### Step 2: Deploy on Render
1. Go to [Render Dashboard](https://dashboard.render.com/).
2. Click **New +** $\rightarrow$ **PostgreSQL**:
   - Name: `alumni-db`
   - Database: `alumni_db`
   - Plan: **Free**
   - Click **Create Database**.
   - Copy the **Internal Database URL** (or External URL).
3. Click **New +** $\rightarrow$ **Web Service**:
   - Connect your GitHub repository.
   - Environment: **Docker** (Render will automatically detect `Dockerfile`).
   - Plan: **Free**.
4. In **Environment Variables**, add:
   - `PORT`: `8080`
   - `DB_URL`: `jdbc:postgresql://<HOSTNAME>:5432/alumni_db` (from step 2)
   - `DB_USERNAME`: `postgres` (or your Render DB user)
   - `DB_PASSWORD`: `<YOUR_RENDER_DB_PASSWORD>`
   - `PHANTOMBUSTER_API_KEY`: `<YOUR_PHANTOMBUSTER_API_KEY>`
   - `PHANTOMBUSTER_AGENT_ID`: `4202218449270876`
   - `PHANTOMBUSTER_MOCK_MODE`: `false`
5. Click **Create Web Service**.

Once deployed:
- **Live Deployed Application URL:** `https://<YOUR_SERVICE_NAME>.onrender.com`
- **Live Swagger Documentation:** `https://<YOUR_SERVICE_NAME>.onrender.com/swagger-ui.html`

---

## Alternative Cloud Deployment (Railway / Koyeb)

1. **Railway:**
   - Click **New Project** $\rightarrow$ **Deploy from GitHub repo**.
   - Click **Add a Service** $\rightarrow$ **Database** $\rightarrow$ **PostgreSQL**.
   - Connect Railway environment variables (`DATABASE_URL`).
   - Live URL will be: `https://<app-name>.up.railway.app/swagger-ui.html`.

2. **Docker Run Locally:**
   ```bash
   docker build -t alumni-linkedin-searcher .
   docker run -p 8080:8080 --env-file .env alumni-linkedin-searcher
   ```
