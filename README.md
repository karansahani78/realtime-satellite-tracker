<div align="center">

<p align="center">
  <a href="https://github.com/karansahani78/sattrack/blob/main/docs/orbitview-animation.gif">
    <img src="https://raw.githubusercontent.com/karansahani78/sattrack/main/docs/orbitview-animation.gif" width="900"/>
  </a>
</p>

# 🛰 OrbitView — Satellite Tracking Platform

**Production-ready real-time satellite tracker powered by live TLE data, SGP4 orbit propagation, and WebSocket streaming**

[![Java](https://img.shields.io/badge/Java-17_LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18.2-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://react.dev)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)](LICENSE)

[![SGP4](https://img.shields.io/badge/Algorithm-SGP4_Propagator-00d4ff?style=flat-square)](https://celestrak.org/SOCRATES/)
[![TLE](https://img.shields.io/badge/Data-CelesTrak_Live_TLE-00ff88?style=flat-square)](https://celestrak.org)
[![WebSocket](https://img.shields.io/badge/Realtime-STOMP_WebSocket-7b61ff?style=flat-square)](https://stomp.github.io/)
[![JWT](https://img.shields.io/badge/Auth-JWT_Stateless-ff6b35?style=flat-square)](https://jwt.io/)

<br/>

| 🛰 Satellites Tracked | ⚡ Refresh Rate | 🎯 SGP4 Accuracy | 🔭 Prediction Window | 📡 TLE Sync |
|:---------------------:|:---------------:|:----------------:|:--------------------:|:-----------:|
| **14,000+** | **5 seconds** | **~2 km (LEO)** | **72 hours** | **Every 30 min** |

</div>

---

## 📋 Table of Contents

- [✨ Features](#-features)
- [🏗 Architecture](#-architecture)
- [🔩 Tech Stack](#-tech-stack)
- [🚀 Quick Start](#-quick-start)
- [📡 API Reference](#-api-reference)
- [🐘 Database Schema](#-database-schema)
- [📁 Project Structure](#-project-structure)
- [🐳 Docker & Deployment](#-docker--deployment)
- [⚙ Environment Variables](#-environment-variables)
- [🛸 How SGP4 Works](#-how-sgp4-works)
- [📜 License](#-license)

---

## ✨ Features

<table>
<tr>
<td width="50%">

**🛰 Core Tracking**
- Tracks 1,000+ satellites using live CelesTrak TLE data
- Real-time position via SGP4 orbit propagation (~2 km LEO accuracy)
- 5-second WebSocket position streaming to all clients
- Ground track visualization on interactive Leaflet world map
- Satellite sunlight / eclipse status detection
- Observer azimuth, elevation & range from any ground location

</td>
<td width="50%">

**🏗 Built for Scale**
- Stateless JWT auth → horizontal pod scaling ready
- Multi-TTL Caffeine cache (5s positions → 6h metadata)
- Async SGP4 computation thread pool
- Flyway migrations for zero-downtime schema updates
- Docker Compose one-command local setup
- `/actuator/health` liveness + readiness probes

</td>
</tr>
<tr>
<td>

**🔭 Orbit Prediction**
- Future position prediction up to 72 hours ahead
- Pass prediction over any ground observer location
- Rise/set azimuth, max elevation, pass duration
- Visual pass detection: lit satellite + night sky observer
- Orbital period, apogee, perigee, inclination stats

</td>
<td>

**🔐 Security**
- JWT access tokens (24h) + refresh tokens (7d)
- BCrypt password hashing (strength 12)
- Per-environment CORS configuration
- Bean Validation on all request bodies
- Token-bucket rate limiting per IP
- Parameterized JPA queries (no SQL injection)

</td>
</tr>
</table>

---

### 🔬 Advanced Orbital Analysis

OrbitView goes beyond basic position tracking with a full suite of orbital mechanics analysis tools designed for radio operators, conjunction monitoring, and mission planning workflows.

---

#### 📻 Doppler Shift Calculation

Computes the instantaneous Doppler frequency shift for any satellite relative to a ground observer, enabling accurate radio frequency prediction for uplink/downlink communications.

- Derives range-rate (radial velocity) from the SGP4-propagated ECI velocity vector and the observer's ECEF position
- Supports any nominal carrier frequency — useful for VHF/UHF amateur passes, L-band telemetry, and S-band downlinks
- Returns both the shifted receive frequency and the shift magnitude in Hz/kHz
- Integrates with pass prediction: pre-computes the full Doppler curve across an entire pass so radio operators can configure their SDR or transceiver in advance

| Field | Description |
|-------|-------------|
| `nominalFreqHz` | Carrier frequency of the satellite transmitter |
| `dopplerShiftHz` | Instantaneous frequency shift (negative = approaching) |
| `rangeRateKmSec` | Radial velocity component toward/away from observer |
| `receivedFreqHz` | Corrected receive frequency accounting for shift |

---

#### 🚨 Satellite Conjunction Detection

Detects close approaches between any two tracked satellites within a configurable screening distance, providing early warning of potential collision risk or orbital proximity events.

- Screens all active satellite pairs at configurable time intervals (default: every 15 minutes over a 24-hour window)
- Computes miss distance in km using ECI-frame relative position vectors
- Reports Time of Closest Approach (TCA), miss distance, and relative speed at TCA
- Flags conjunctions below a hard threshold (default: 5 km) as high-risk events
- Streams high-risk conjunction alerts in real time over the `/topic/conjunctions` WebSocket topic
- Persists conjunction events to the database for post-event auditing and trend analysis

| Risk Level | Miss Distance | Action |
|------------|---------------|--------|
| 🟢 Nominal | > 25 km | Logged only |
| 🟡 Caution | 5 – 25 km | Logged + WebSocket alert |
| 🔴 Warning | < 5 km | Logged + WebSocket alert + email notification |

---

#### 🌐 Orbital Event Detection

Automatically detects and timestamps discrete orbital events as each satellite propagates forward in time, eliminating the need for clients to poll position endpoints for threshold crossings.

- **Apogee / Perigee crossings** — detected when the radial distance derivative passes through zero; reports exact crossing time, altitude, and current orbital elements
- **Ascending / Descending node crossings** — detected when the satellite crosses the equatorial plane (latitude sign change); reports RAAN at crossing for orbit counting and repeat-groundtrack analysis
- **Eclipse entry and exit** — computed via cylindrical Earth shadow model; reports penumbra entry, umbra entry, umbra exit, and penumbra exit timestamps with fractional eclipse depth
- Events are accumulated per-satellite during propagation sweeps and stored in the `orbital_events` table for historical queries

```
Propagation sweep (T₀ → T₀+72h, 30s steps)
     │
     ├─ Apogee/Perigee detector  → orbital_events (type=APOGEE / PERIGEE)
     ├─ Node crossing detector   → orbital_events (type=ASC_NODE / DESC_NODE)
     └─ Eclipse shadow model     → orbital_events (type=ECLIPSE_ENTRY / ECLIPSE_EXIT)
```

---

#### 📡 Signal Visibility Analysis

Combines elevation geometry, solar illumination, and observer sky conditions into a single composite **visibility score** per pass, replacing the simple `isVisible` boolean with fine-grained signal quality data.

- Minimum elevation filter (configurable, default 10°) eliminates horizon-grazing passes with poor signal-to-noise ratio
- Sunlight status sourced directly from the SGP4 eclipse model — differentiates penumbra from full shadow
- Observer night condition computed from solar depression angle at the ground station
- Atmospheric signal path length estimated from elevation angle for link-budget calculations
- Composite `visibilityClass` field returned on every pass prediction:

| `visibilityClass` | Conditions |
|-------------------|------------|
| `OPTICAL_AND_RADIO` | Satellite lit + observer night + elevation ≥ 10° |
| `RADIO_ONLY` | Any elevation ≥ 10° (regardless of lighting) |
| `MARGINAL` | Elevation 5°–10° (atmospheric degradation likely) |
| `NOT_VISIBLE` | Below horizon or blocked |

---

#### 🏔 Ground Station Tracking

Provides a continuous, observer-relative tracking data stream for any registered ground station, going beyond the snapshot AZ/EL values in the `/current` endpoint.

- Streams real-time azimuth, elevation, and slant range at the WebSocket broadcast cadence (5 seconds)
- Computes antenna pointing rate (°/second) to support motorized dish controllers and rotor interfaces
- Calculates two-way light-time delay and one-way propagation delay in milliseconds for timing-sensitive applications
- Supports multiple simultaneous ground stations per user account — each station receives its own `/topic/station/{stationId}` WebSocket channel
- Ground station profiles stored in the `ground_stations` table with geodetic lat/lon/altitude (WGS-84)

---

#### 📊 Advanced Orbital Analytics

Exposes derived orbital mechanics quantities beyond the basic position/velocity output of the raw SGP4 propagator.

- **Relative velocity between satellites** — ECI-frame vector subtraction of two propagated velocity states; useful for rendezvous planning and conjunction severity assessment
- **Specific orbital energy (vis-viva)** — computed as `ε = v²/2 − μ/r`; a negative value confirms a bound orbit; magnitude indicates altitude regime
- **Semi-major axis** — derived from mean motion in the TLE via `a = (μ / n²)^(1/3)`; reported in km alongside the TLE-native mean motion value
- **Inclination comparison across a catalog subset** — batch endpoint returns inclination distribution for a filtered satellite set, supporting constellation coverage analysis
- **Mean motion drift (Ṅ)** — first derivative of mean motion from TLE Line 1 field; indicates active manoeuvring or significant drag decay

| Analytic | Endpoint | Notes |
|----------|----------|-------|
| Orbital energy | `GET /satellites/{id}/analytics` | Returns ε, a, e, T |
| Relative velocity | `GET /satellites/relative?ids=A,B` | ECI ΔV vector + scalar magnitude |
| Inclination distribution | `GET /satellites/analytics/inclinations?category=GPS` | Histogram-ready buckets |
| Mean motion drift | Included in `/satellites/{id}` detail response | From TLE Line 1 field 7 |

---

#### ⚡ Real-time Orbital Event Streaming

All orbital events and analysis results are broadcast over dedicated STOMP WebSocket topics so frontends and external consumers receive push notifications without polling.

```javascript
// Subscribe to conjunction warnings across all tracked satellites
client.subscribe('/topic/conjunctions', (msg) => {
  const { satelliteA, satelliteB, missDistanceKm, tcaUtc, riskLevel } = JSON.parse(msg.body)
  showConjunctionAlert(satelliteA, satelliteB, missDistanceKm, riskLevel)
})

// Subscribe to orbital events for a specific satellite
client.subscribe('/topic/satellite/25544/events', (msg) => {
  const { eventType, eventTimeUtc, altitudeKm } = JSON.parse(msg.body)
  // eventType: APOGEE | PERIGEE | ASC_NODE | DESC_NODE | ECLIPSE_ENTRY | ECLIPSE_EXIT
  logOrbitalEvent(eventType, eventTimeUtc, altitudeKm)
})

// Subscribe to ground station tracking stream
client.subscribe('/topic/station/my-station-id', (msg) => {
  const { azimuthDeg, elevationDeg, rangeKm, rangeRateKmSec, pointingRateDegSec } = JSON.parse(msg.body)
  updateAntennaDish(azimuthDeg, elevationDeg)
})
```

**WebSocket topic reference — advanced channels:**

| Topic | Cadence | Payload |
|-------|---------|---------|
| `/topic/conjunctions` | On detection | Conjunction summary + risk level |
| `/topic/satellite/{id}/events` | On event crossing | Event type, time, orbital state |
| `/topic/station/{stationId}` | Every 5 seconds | AZ, EL, range, range-rate, pointing rate |
| `/topic/satellites/eclipses` | On shadow boundary | Satellite ID, shadow type, entry/exit time |

---

## 🏗 Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                       CLIENT LAYER                           │
│   ⚛ React 18 + Vite  │  🗺 Leaflet Map  │  ⚡ STOMP WS      │
└───────────────────────────────┬──────────────────────────────┘
                                │  HTTPS / WSS
┌───────────────────────────────▼──────────────────────────────┐
│                     GATEWAY LAYER                            │
│             ⚙ Nginx — SSL Termination + WS Upgrade           │
└───────────────────────────────┬──────────────────────────────┘
                                │  HTTP/1.1 + WebSocket
┌───────────────────────────────▼──────────────────────────────┐
│                   APPLICATION LAYER                          │
│  ☕ Spring Boot 3  │  🔐 JWT  │  🛸 SGP4  │  📡 TLE Service  │
│                    📢 STOMP In-Memory Broker                  │
│  🔬 Doppler  │  🚨 Conjunction  │  🌐 Events  │  📊 Analytics │
└──────────┬─────────────────────────────────┬─────────────────┘
           │  JPA / JDBC                     │  WebFlux HTTP
┌──────────▼──────────┐          ┌───────────▼────────────────┐
│  🐘 PostgreSQL 16   │          │   🌍 CelesTrak API          │
│  ⚡ Caffeine Cache  │          │   (Live TLE Data)           │
└─────────────────────┘          └────────────────────────────┘
```

**Key architecture decisions:**

| Decision | Why |
|----------|-----|
| **Stateless JWT** | Every backend pod validates tokens independently — zero-config horizontal scaling |
| **SGP4 from scratch** | All public TLE data is tuned for SGP4 specifically — any other propagator gives wrong results |
| **STOMP over raw WebSocket** | Built-in pub/sub topics, SockJS fallback, first-class Spring integration |
| **Caffeine per-cache TTL** | Positions stale in 5s; satellite metadata valid 6h — one global TTL wastes resources |
| **ConcurrentHashMap TLE cache** | O(1) lookup during propagation avoids DB round-trip on every position request |
| **Nginx reverse proxy** | Single TLS certificate, WebSocket upgrade, and static file serving in one place |
| **Event detection in propagation sweep** | Apogee/perigee, node crossings, and eclipse transitions are detected in a single forward sweep rather than separate passes — avoids redundant SGP4 calls and keeps CPU cost O(n·steps) regardless of how many event types are active |
| **Conjunction screening via bounding-box pre-filter** | Before computing exact miss distances, satellites are binned into spatial cells; only pairs sharing a cell proceed to the full ECI-frame closest-approach calculation — reduces O(n²) pair comparisons by ~95% for typical catalog sizes |

> **Scaling path:** Swap Caffeine → Redis for shared cache across instances. Add a Kafka topic for WebSocket fan-out. Deploy on AWS ECS behind an ALB — no application code changes required.

---

## 🔩 Tech Stack

<details>
<summary><b>🖥 Backend — Java / Spring Boot</b></summary>

| Library | Version | Purpose |
|---------|---------|---------|
| Java | 17 LTS | Language — records, sealed classes, text blocks |
| Spring Boot | 3.2.1 | Framework, auto-configuration, embedded Tomcat |
| Spring Security | 6.x | JWT filter chain, method-level `@PreAuthorize` |
| Spring Data JPA | 3.2.1 | ORM, repository pattern, JPQL queries |
| Spring WebFlux | 3.2.1 | Reactive HTTP client for TLE fetching |
| Spring WebSocket | 3.2.1 | STOMP message broker for live streaming |
| Flyway | 10.x | Versioned SQL schema migrations |
| JJWT | 0.12.3 | JWT generation + validation (HS256) |
| Caffeine | 3.x | High-performance in-process cache |
| HikariCP | 5.x | JDBC connection pool |
| SpringDoc OpenAPI | 2.3.0 | Auto-generated Swagger UI |
| Lombok | 1.18.x | Builders, getters, `@Slf4j` boilerplate reduction |

</details>

<details>
<summary><b>⚛ Frontend — React / Vite</b></summary>

| Library | Version | Purpose |
|---------|---------|---------|
| React | 18.2 | UI framework, concurrent rendering |
| Vite | 5.x | Build tool, HMR, code splitting |
| React Leaflet | 4.x | Interactive world map with satellite overlays |
| Zustand | 4.x | Lightweight global state (auth + satellite data) |
| @stomp/stompjs | 7.x | STOMP WebSocket client |
| SockJS-client | 1.6.x | WebSocket transport fallback |
| Recharts | 2.x | Altitude / velocity time-series charts |
| Tailwind CSS | 3.4 | Utility-first dark-theme styling |
| Axios | 1.6 | HTTP client with JWT interceptors + auto-refresh |
| date-fns | 3.x | UTC-safe date formatting |
| Lucide React | 0.303 | Icon system |

</details>

<details>
<summary><b>🏗 Infrastructure</b></summary>

| Tool | Purpose |
|------|---------|
| Docker + Compose | Container orchestration, one-command local setup |
| PostgreSQL 16 | Primary database — JSONB, GIN indexes, TIMESTAMPTZ |
| Nginx | Reverse proxy, SSL termination, WebSocket upgrade |
| Flyway | Schema version control — auto-runs on startup |
| Spring Actuator | `/health`, `/metrics` for load balancer probes |
| Maven | Dependency management, multi-stage Docker build |

</details>

---

## 🚀 Quick Start

### Prerequisites

> 🐳 **Docker 24+** and **Docker Compose v2** — no local Java or Node required, everything runs in containers.

### Option A — Docker (Recommended)

```bash
# 1. Clone the repository
git clone https://github.com/yourusername/satellite-tracker.git
cd satellite-tracker

# 2. Copy environment config and set your secrets
cp .env.example .env
# Edit .env: set JWT_SECRET to $(openssl rand -base64 64)

# 3. Start the full stack
docker compose up --build -d

# 4. Watch startup logs — Flyway runs migrations automatically
docker compose logs -f backend

# 5. Open the app
open http://localhost                     # React frontend
open http://localhost/swagger-ui.html     # Swagger API explorer
```

> ⚠️ **First boot:** fetches live TLE data from CelesTrak (~30 seconds). Falls back to demo TLEs (ISS, Hubble, CSS) if CelesTrak is temporarily unavailable.

---

### Option B — Local Development

**Requirements:** Java 17+, Node 20+, PostgreSQL 14+

```bash
# 1. Create the database
createdb satellite_tracker
psql satellite_tracker -c "CREATE USER satellite_user WITH PASSWORD 'satellite_pass';"
psql satellite_tracker -c "GRANT ALL PRIVILEGES ON DATABASE satellite_tracker TO satellite_user;"

# 2. Start the backend (port 8080)
cd backend
./mvnw spring-boot:run

# 3. Start the frontend (port 3000) — new terminal
cd frontend
npm install
npm run dev

# 4. Verify a live position
curl http://localhost:8080/api/satellites/25544/current
```

---

### Option C — Railway (One-Click Cloud)

```bash
npm install -g @railway/cli
railway login
railway init
railway up
```

Set these in your Railway dashboard:

```
DATABASE_URL         = postgresql://user:pass@host/db
JWT_SECRET           = <openssl rand -base64 64>
CORS_ALLOWED_ORIGINS = https://your-frontend.up.railway.app
SERVER_PORT          = 8080
```

---

## 📡 API Reference

**Base URL:** `http://localhost:8080/api`  
**Swagger UI:** [`http://localhost:8080/swagger-ui.html`](http://localhost:8080/swagger-ui.html)

### 🛰 Satellite Catalog

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/satellites` | Paginated catalog. `?query=ISS&category=Weather&activeOnly=true&page=0&size=20` |
| `GET` | `/satellites/featured` | Notable satellites: ISS, Hubble, CSS Tianhe, NOAA, GPS |
| `GET` | `/satellites/{noradId}` | Full detail + current TLE |
| `GET` | `/categories` | All distinct satellite categories |

### 📍 Position & Tracking

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/satellites/{noradId}/current` | Real-time position. Add `?lat=51.5&lon=-0.12` for AZ/EL/range |
| `GET` | `/satellites/{noradId}/predict` | Future position. `?minutes=90` (max 4320 = 72h) |
| `GET` | `/satellites/{noradId}/track` | Ground track polyline. `?start=&end=&intervalSeconds=30` |
| `GET` | `/satellites/{noradId}/passes` | Pass predictions. `?lat=51.5&lon=-0.12&hours=24&minElevation=10` |
| `GET` | `/satellites/positions/all` | All satellite positions for map overview. `?limit=100` |

<details>
<summary><b>📦 Example: Current position response</b></summary>

```json
{
  "noradCatalogId": 25544,
  "name": "ISS (ZARYA)",
  "timestamp": "2024-01-15T14:32:07.123Z",
  "latitude": 51.6234,
  "longitude": -12.4521,
  "altitudeKm": 418.3,
  "velocityKmPerSec": 7.66,
  "azimuth": 234.1,
  "elevation": 42.7,
  "rangeKm": 612.4,
  "isDaylight": true,
  "isVisible": true
}
```

</details>

<details>
<summary><b>📦 Example: Pass prediction response</b></summary>

```json
{
  "noradCatalogId": 25544,
  "name": "ISS (ZARYA)",
  "riseTime": "2024-01-15T19:42:00Z",
  "maxElevationTime": "2024-01-15T19:45:30Z",
  "setTime": "2024-01-15T19:49:00Z",
  "maxElevationDeg": 68.4,
  "riseAzimuthDeg": 311.2,
  "setAzimuthDeg": 127.8,
  "durationSeconds": 420,
  "isVisualPass": true,
  "isDaylightPass": true,
  "isObserverNight": true
}
```

</details>

### 🔬 Advanced Orbital Analysis Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/satellites/{noradId}/doppler` | Doppler shift at current time. `?lat=&lon=&freqHz=437550000` |
| `GET` | `/satellites/{noradId}/passes/doppler` | Full Doppler curve across next pass. `?lat=&lon=&freqHz=` |
| `GET` | `/satellites/{noradId}/analytics` | Orbital energy, semi-major axis, mean motion drift |
| `GET` | `/satellites/{noradId}/events` | Historical orbital events (apogee, node crossings, eclipses). `?hours=24` |
| `GET` | `/satellites/relative` | Relative velocity between two satellites. `?ids=25544,20580` |
| `GET` | `/satellites/analytics/inclinations` | Inclination distribution for a satellite category. `?category=GPS` |
| `GET` | `/conjunctions` | Active conjunction warnings. `?minRiskLevel=CAUTION&hours=24` |
| `GET` | `/conjunctions/{id}` | Detailed conjunction report with TCA, miss distance, and relative velocity |
| `GET` | `/ground-stations` | List registered ground stations for the authenticated user |
| `POST` | `/ground-stations` | Register a new ground station. Body: `{name, lat, lon, altitudeM}` |
| `DELETE` | `/ground-stations/{stationId}` | Remove a ground station |

<details>
<summary><b>📦 Example: Doppler shift response</b></summary>

```json
{
  "noradCatalogId": 25544,
  "name": "ISS (ZARYA)",
  "timestamp": "2024-01-15T14:32:07.123Z",
  "nominalFreqHz": 437550000,
  "dopplerShiftHz": -3241,
  "receivedFreqHz": 437546759,
  "rangeRateKmSec": -2.228,
  "rangeKm": 612.4,
  "elevationDeg": 42.7
}
```

</details>

<details>
<summary><b>📦 Example: Conjunction warning response</b></summary>

```json
{
  "conjunctionId": "conj-20240115-001",
  "satelliteA": { "noradId": 25544, "name": "ISS (ZARYA)" },
  "satelliteB": { "noradId": 43205, "name": "COSMOS 2519" },
  "timeOfClosestApproach": "2024-01-15T22:17:43Z",
  "missDistanceKm": 3.82,
  "relativeVelocityKmSec": 14.3,
  "riskLevel": "WARNING",
  "detectedAt": "2024-01-15T14:00:00Z"
}
```

</details>

### 🔐 Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/auth/register` | Register. Body: `{username, email, password, displayName}` |
| `POST` | `/auth/login` | Login. Body: `{usernameOrEmail, password}` → returns JWT tokens |
| `POST` | `/auth/refresh` | Refresh tokens. Body: `{refreshToken}` |
| `GET` | `/auth/me` | Current user info (requires `Authorization: Bearer <token>`) |
| `PUT` | `/auth/preferences` | Update observer location, timezone, theme |
| `POST` | `/auth/favorites/{noradId}` | Add satellite to favorites |
| `DELETE` | `/auth/favorites/{noradId}` | Remove satellite from favorites |

### ⚡ WebSocket (STOMP)

Connect to `ws://localhost:8080/ws` with SockJS fallback:

```javascript
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const client = new Client({
  webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
  onConnect: () => {

    // All satellite positions — server broadcasts every 5 seconds
    client.subscribe('/topic/satellites/all', (msg) => {
      const { positions, satelliteCount, timestamp } = JSON.parse(msg.body)
      updateMap(positions) // positions: [{noradId, lat, lon, alt, ...}]
    })

    // Track one specific satellite
    client.subscribe('/topic/satellite/25544', handleISSUpdate)
    client.publish({
      destination: '/app/track',
      body: JSON.stringify({ noradId: 25544 })
    })
  }
})

client.activate()
```

---

## 🐘 Database Schema

```
satellites ──< tle_records
     │
     └──< tracking_logs >── users
     │                         │
     └──< orbital_events    user_roles
                             user_favorite_satellites
                             ground_stations
```

<details>
<summary><b>📄 Full schema — key tables</b></summary>

```sql
-- Satellite master catalog
CREATE TABLE satellites (
    id                     BIGSERIAL    PRIMARY KEY,
    norad_catalog_id       INTEGER      NOT NULL UNIQUE,  -- ISS=25544, Hubble=20580
    cospar_id              VARCHAR(20),                   -- e.g. "1998-067A"
    name                   VARCHAR(100) NOT NULL,
    category               VARCHAR(100),
    orbital_period_minutes DOUBLE PRECISION,
    inclination_deg        DOUBLE PRECISION,
    apogee_km              DOUBLE PRECISION,
    perigee_km             DOUBLE PRECISION,
    is_active              BOOLEAN      NOT NULL DEFAULT true,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- GIN index: fast full-text satellite name search
CREATE INDEX idx_satellite_name ON satellites
    USING gin(to_tsvector('english', name));

-- TLE history — only one is_current=true per satellite at any time
CREATE TABLE tle_records (
    id           BIGSERIAL   PRIMARY KEY,
    satellite_id BIGINT      NOT NULL REFERENCES satellites(id),
    line1        VARCHAR(70) NOT NULL,   -- NORAD TLE Line 1 (69 chars)
    line2        VARCHAR(70) NOT NULL,   -- NORAD TLE Line 2 (69 chars)
    epoch        TIMESTAMPTZ NOT NULL,
    is_current   BOOLEAN     NOT NULL DEFAULT false,
    fetched_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Partial index: instant current-TLE lookup without full scan
CREATE INDEX idx_tle_satellite_current
    ON tle_records(satellite_id, is_current)
    WHERE is_current = true;

-- Users table
CREATE TABLE users (
    id                   BIGSERIAL    PRIMARY KEY,
    username             VARCHAR(50)  NOT NULL UNIQUE,
    email                VARCHAR(100) NOT NULL UNIQUE,
    password_hash        VARCHAR(255) NOT NULL,
    observer_latitude    DOUBLE PRECISION,
    observer_longitude   DOUBLE PRECISION,
    observer_altitude_m  INTEGER DEFAULT 0,
    timezone             VARCHAR(50)  DEFAULT 'UTC',
    theme                VARCHAR(20)  DEFAULT 'dark',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_login_at        TIMESTAMPTZ
);

-- Ground stations — per-user observer profiles for tracking and Doppler
CREATE TABLE ground_stations (
    id           BIGSERIAL        PRIMARY KEY,
    user_id      BIGINT           NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name         VARCHAR(100)     NOT NULL,
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    altitude_m   INTEGER          NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

-- Orbital events — apogee/perigee crossings, node crossings, eclipse transitions
CREATE TABLE orbital_events (
    id           BIGSERIAL   PRIMARY KEY,
    satellite_id BIGINT      NOT NULL REFERENCES satellites(id),
    event_type   VARCHAR(30) NOT NULL,   -- APOGEE | PERIGEE | ASC_NODE | DESC_NODE | ECLIPSE_ENTRY | ECLIPSE_EXIT
    event_time   TIMESTAMPTZ NOT NULL,
    altitude_km  DOUBLE PRECISION,
    latitude     DOUBLE PRECISION,
    longitude    DOUBLE PRECISION,
    extra_data   JSONB,                  -- eclipse depth, RAAN at node, etc.
    detected_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orbital_events_satellite_time
    ON orbital_events(satellite_id, event_time DESC);

-- Conjunction warnings — close approach records between satellite pairs
CREATE TABLE conjunctions (
    id                    BIGSERIAL        PRIMARY KEY,
    satellite_a_id        BIGINT           NOT NULL REFERENCES satellites(id),
    satellite_b_id        BIGINT           NOT NULL REFERENCES satellites(id),
    time_of_closest_approach TIMESTAMPTZ  NOT NULL,
    miss_distance_km      DOUBLE PRECISION NOT NULL,
    relative_velocity_km_sec DOUBLE PRECISION,
    risk_level            VARCHAR(20)      NOT NULL,  -- NOMINAL | CAUTION | WARNING
    detected_at           TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    resolved_at           TIMESTAMPTZ
);

CREATE INDEX idx_conjunctions_tca ON conjunctions(time_of_closest_approach DESC);
CREATE INDEX idx_conjunctions_risk ON conjunctions(risk_level) WHERE resolved_at IS NULL;
```

</details>

**Schema design decisions:**

| Decision | Rationale |
|----------|-----------|
| NORAD ID ≠ DB PK | NORAD ID is the universal external identifier; internal `BIGSERIAL` PK stays stable |
| Orbital elements on `satellites` | Denormalized for fast catalog queries without a join on every request |
| `is_current` partial index | Tiny index vs full table scan — only one row per satellite matches |
| `TIMESTAMPTZ` everywhere | Forces UTC storage; eliminates daylight-saving bugs in pass prediction |
| GIN index on satellite name | Supports `LIKE '%query%'` searches efficiently |
| `orbital_events` JSONB `extra_data` | Stores event-type-specific fields (eclipse depth, RAAN) without schema churn |
| `conjunctions` unresolved partial index | Keeps the active-warning query fast without scanning historical resolved rows |

> Flyway migrations live in `backend/src/main/resources/db/migration/`. Add `V2__description.sql` files for future changes — they run automatically on startup.

---

## 📁 Project Structure

<details>
<summary><b>☕ Backend — Java package layout</b></summary>

```
backend/src/main/java/com/satellitetracker/
│
├── SatelliteTrackerApplication.java      ← @SpringBootApplication entry point
│
├── config/
│   ├── SecurityConfig.java               ← JWT filter chain, CORS, endpoint rules
│   ├── WebSocketConfig.java              ← STOMP broker, SockJS endpoint
│   ├── CacheConfig.java                  ← Caffeine per-cache TTL policies
│   └── AppConfig.java                    ← WebClient bean for CelesTrak requests
│
├── controller/
│   ├── SatelliteController.java          ← REST /api/satellites/**
│   ├── AuthController.java               ← REST /api/auth/**
│   ├── ConjunctionController.java        ← REST /api/conjunctions/**
│   ├── GroundStationController.java      ← REST /api/ground-stations/**
│   └── SatelliteWebSocketController.java ← Scheduled broadcast + @MessageMapping
│
├── service/
│   ├── TleService.java                   ← CelesTrak fetch, parse, cache, schedule
│   ├── OrbitPropagationService.java      ← SGP4 positions, tracks, pass prediction
│   ├── OrbitalEventService.java          ← Apogee/perigee, node, eclipse detection
│   ├── ConjunctionService.java           ← Pairwise close-approach screening + alerts
│   ├── DopplerService.java               ← Range-rate and frequency shift computation
│   ├── GroundStationService.java         ← Observer tracking, pointing rate, link delay
│   ├── OrbitalAnalyticsService.java      ← Energy, semi-major axis, relative velocity
│   ├── SatelliteService.java             ← Catalog search, featured, categories
│   └── UserService.java                  ← Register, login, JWT, preferences
│
├── model/
│   ├── entity/
│   │   ├── Satellite.java                ← @Entity: satellites table
│   │   ├── TleRecord.java                ← @Entity: tle_records table
│   │   ├── User.java                     ← @Entity: users table
│   │   ├── GroundStation.java            ← @Entity: ground_stations table
│   │   ├── OrbitalEvent.java             ← @Entity: orbital_events table
│   │   ├── Conjunction.java              ← @Entity: conjunctions table
│   │   └── TrackingLog.java              ← @Entity: tracking_logs table
│   └── dto/
│       ├── SatelliteDto.java             ← Summary, Detail, TleDto
│       ├── OrbitDto.java                 ← Position, OrbitTrack, PassPrediction
│       ├── DopplerDto.java               ← DopplerShift, DopplerCurve
│       ├── ConjunctionDto.java           ← ConjunctionSummary, ConjunctionDetail
│       ├── OrbitalAnalyticsDto.java      ← Energy, SemiMajorAxis, RelativeVelocity
│       └── AuthDto.java                  ← LoginRequest, RegisterRequest, AuthResponse
│
├── repository/
│   ├── SatelliteRepository.java          ← JPA: fulltext search, category filter
│   ├── TleRecordRepository.java          ← JPA: current TLE, markAllNotCurrent
│   ├── UserRepository.java               ← JPA: findByUsernameOrEmail
│   ├── GroundStationRepository.java      ← JPA: findByUserId
│   ├── OrbitalEventRepository.java       ← JPA: findBySatelliteAndEventTimeBetween
│   ├── ConjunctionRepository.java        ← JPA: active warnings, risk level filter
│   └── TrackingLogRepository.java
│
├── security/
│   ├── JwtUtils.java                     ← HS256 token generation + validation
│   ├── JwtAuthenticationFilter.java      ← OncePerRequestFilter: extract & validate JWT
│   ├── JwtAuthenticationEntryPoint.java  ← 401 JSON error response
│   └── UserDetailsServiceImpl.java       ← Spring Security UserDetails bridge
│
├── util/
│   ├── Sgp4Propagator.java               ← ★ Complete SGP4 algorithm (Vallado 2006, ~750 LOC)
│   ├── TleParser.java                    ← 2/3-line TLE format parser + checksum validation
│   ├── AstroUtils.java                   ← Sun position, AZ/EL/range, GMST, ECEF conversion
│   └── DopplerUtils.java                 ← Range-rate projection, relativistic correction
│
└── exception/
    ├── GlobalExceptionHandler.java       ← @RestControllerAdvice, RFC 7807 error format
    ├── ResourceNotFoundException.java    ← 404
    ├── TleParseException.java            ← 422
    └── ConflictException.java            ← 409
```

</details>

<details>
<summary><b>⚛ Frontend — React source layout</b></summary>

```
frontend/src/
│
├── main.jsx                    ← ReactDOM.createRoot, BrowserRouter
├── App.jsx                     ← Routes, WebSocket init, global data fetch
├── index.css                   ← Tailwind directives + Leaflet dark overrides
│
├── pages/
│   ├── MapPage.jsx              ← World map + live satellite overlay + sidebar
│   ├── SatellitesPage.jsx       ← Searchable paginated satellite catalog
│   ├── SatelliteDetailPage.jsx  ← Detail panel + orbit chart + pass predictions
│   ├── PassesPage.jsx           ← Observer pass predictor with location input
│   ├── ConjunctionsPage.jsx     ← Live conjunction warning dashboard
│   ├── AnalyticsPage.jsx        ← Orbital analytics: energy, inclination, Doppler
│   └── LoginPage.jsx            ← JWT login / registration forms
│
├── components/
│   ├── map/
│   │   ├── SatelliteMap.jsx     ← Leaflet MapContainer with dark tile layer
│   │   ├── SatelliteMarker.jsx  ← Animated divIcon + info popup
│   │   └── OrbitPath.jsx        ← Ground track Polyline, ±180° date-line split
│   ├── satellite/
│   │   ├── SatellitePanel.jsx   ← Sidebar: live position stats + controls
│   │   ├── SatelliteCard.jsx    ← Catalog grid card with orbital parameters
│   │   ├── PassTable.jsx        ← Upcoming passes with rise/set/max-elevation
│   │   ├── DopplerChart.jsx     ← Recharts Doppler curve across pass window
│   │   ├── OrbitalEventLog.jsx  ← Timestamped apogee/node/eclipse event feed
│   │   └── OrbitalChart.jsx     ← Recharts altitude + velocity over time
│   ├── conjunction/
│   │   ├── ConjunctionAlert.jsx ← Toast notification for WARNING-level events
│   │   └── ConjunctionTable.jsx ← Active close-approach table with risk badges
│   ├── groundstation/
│   │   ├── StationManager.jsx   ← CRUD UI for ground station profiles
│   │   └── StationTracker.jsx   ← Live AZ/EL/range display + pointing rate
│   ├── layout/
│   │   └── Layout.jsx           ← Top nav bar + live/offline status indicator
│   └── ui/
│       ├── TimeSlider.jsx       ← ±72h time scrubber for position prediction
│       ├── SearchBar.jsx        ← Debounced satellite search input
│       └── LoadingSpinner.jsx
│
├── store/
│   └── index.js                 ← Zustand: useAuthStore + useSatelliteStore + useConjunctionStore
│
├── services/
│   ├── api.js                   ← Axios instance, JWT interceptors, auto-refresh
│   └── websocket.js             ← STOMP client: connect, subscribe, reconnect
│
└── utils/
    └── formatters.js            ← Coordinate, speed, time, magnitude, frequency formatters
```

</details>

<details>
<summary><b>📁 Monorepo root</b></summary>

```
satellite-tracker/
├── satellite-animation.svg      ← Animated orbital diagram (this header)
├── docker-compose.yml           ← Full stack: postgres + backend + frontend + nginx
├── docker-compose.prod.yml      ← Production overrides (resource limits, restart policy)
├── .env.example                 ← Environment variable template
├── README.md
├── LICENSE
│
├── backend/
│   ├── Dockerfile               ← Multi-stage: Maven build → JRE 17 slim runtime
│   ├── pom.xml
│   └── src/
│       ├── main/java/           ← Application source
│       ├── main/resources/
│       │   ├── application.properties
│       │   └── db/migration/
│       │       ├── V1__initial_schema.sql
│       │       └── V2__advanced_orbital_tables.sql  ← ground_stations, orbital_events, conjunctions
│       └── test/
│
├── frontend/
│   ├── Dockerfile               ← Multi-stage: Node build → Nginx static serve
│   ├── vite.config.js
│   ├── tailwind.config.js
│   └── src/
│
├── nginx/
│   └── nginx.conf               ← Reverse proxy + WebSocket upgrade headers
│
└── database/
    └── init.sql                 ← DB user creation + initial permissions
```

</details>

---

## 🐳 Docker & Deployment

<details>
<summary><b>📄 docker-compose.yml — full stack</b></summary>

```yaml
version: '3.9'

services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: satellite_tracker
      POSTGRES_USER: ${DATABASE_USERNAME}
      POSTGRES_PASSWORD: ${DATABASE_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./database/init.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DATABASE_USERNAME}"]
      interval: 10s
      retries: 5
    networks: [satellite-net]

  backend:
    build: ./backend
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/satellite_tracker
      DATABASE_USERNAME: ${DATABASE_USERNAME}
      DATABASE_PASSWORD: ${DATABASE_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      CORS_ALLOWED_ORIGINS: http://localhost
    depends_on:
      postgres: { condition: service_healthy }
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      retries: 3
    networks: [satellite-net]

  frontend:
    build:
      context: ./frontend
      args:
        VITE_API_URL: ""    # empty = relative URLs proxied through nginx
    networks: [satellite-net]

  nginx:
    image: nginx:alpine
    ports: ["80:80", "443:443"]
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/conf.d/default.conf
    depends_on: [backend, frontend]
    networks: [satellite-net]

volumes:
  pgdata:
networks:
  satellite-net:
```

</details>

<details>
<summary><b>🐋 Backend Dockerfile — multi-stage build</b></summary>

```dockerfile
# Stage 1: Build with full JDK + Maven
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -f pom.xml clean package -DskipTests

# Stage 2: Minimal JRE runtime (~180MB vs ~420MB JDK)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --start-period=60s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

</details>

<details>
<summary><b>⚙ nginx.conf — reverse proxy + WebSocket upgrade</b></summary>

```nginx
upstream backend  { server backend:8080; }
upstream frontend { server frontend:80; }

server {
  listen 80;

  # React SPA — serve index.html for all routes
  location / {
    proxy_pass http://frontend;
    try_files $uri $uri/ /index.html;
  }

  # Spring Boot REST API
  location /api/ {
    proxy_pass http://backend;
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
  }

  # STOMP WebSocket (SockJS + raw WS)
  location /ws/ {
    proxy_pass http://backend;
    proxy_http_version 1.1;
    proxy_set_header Upgrade    $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout  86400s;
    proxy_send_timeout  86400s;
  }

  # Swagger UI
  location /swagger-ui/ {
    proxy_pass http://backend/swagger-ui/;
  }
}
```

</details>

### ☁ Cloud Deployment Steps

| Step | Action |
|------|--------|
| **1. Database** | Provision managed Postgres (Railway, Render, AWS RDS Free Tier, or Supabase) |
| **2. Secrets** | `openssl rand -base64 64` for `JWT_SECRET` — store in platform secrets vault |
| **3. Backend** | Deploy `backend/Dockerfile` — Flyway runs `V1__initial_schema.sql` on first boot |
| **4. Frontend** | Deploy `frontend/Dockerfile` with `VITE_API_URL=https://your-backend.com` as build arg |
| **5. Verify** | `GET /actuator/health` → `{"status":"UP","components":{"db":{"status":"UP"}}}` |

---

## ⚙ Environment Variables

Copy `.env.example` → `.env` and fill in your values:

```bash
# ── Database ─────────────────────────────────────────────────
DATABASE_URL=jdbc:postgresql://localhost:5432/satellite_tracker
DATABASE_USERNAME=satellite_user
DATABASE_PASSWORD=changeme_in_production
DB_POOL_SIZE=20

# ── JWT — generate before deploying! ─────────────────────────
# Run: openssl rand -base64 64
JWT_SECRET=your-256-bit-base64-encoded-secret-here
JWT_EXPIRATION_MS=86400000          # 24 hours
JWT_REFRESH_EXPIRATION_MS=604800000 # 7 days

# ── CORS ─────────────────────────────────────────────────────
CORS_ALLOWED_ORIGINS=http://localhost:3000,https://yoursite.com

# ── TLE Data ─────────────────────────────────────────────────
TLE_REFRESH_INTERVAL=30             # Minutes between CelesTrak syncs

# ── Rate Limiting ─────────────────────────────────────────────
RATE_LIMIT_RPM=60
RATE_LIMIT_BURST=10

# ── Advanced Orbital Analysis ────────────────────────────────
CONJUNCTION_SCREEN_INTERVAL_MIN=15  # Minutes between conjunction screening sweeps
CONJUNCTION_WARNING_THRESHOLD_KM=5  # Miss distance threshold for WARNING alerts
CONJUNCTION_CAUTION_THRESHOLD_KM=25 # Miss distance threshold for CAUTION alerts
EVENT_DETECTION_STEP_SECONDS=30     # Propagation step size for orbital event sweeps

# ── Server ───────────────────────────────────────────────────
SERVER_PORT=8080
SHOW_SQL=false                      # Set true for SQL query debugging
```

> 🔴 **Security checklist before going live:**
> - Change `JWT_SECRET` from the placeholder — use `openssl rand -base64 64`
> - Change `DATABASE_PASSWORD` from `changeme`
> - Remove or change the seeded admin account (default: `admin` / `Admin@123!`)
> - Restrict `CORS_ALLOWED_ORIGINS` to your exact production frontend URL
> - Enable HTTPS on your load balancer or via Let's Encrypt / Certbot

---

## 🛸 How SGP4 Works

The core of this platform is a **complete from-scratch SGP4 implementation** in Java (`Sgp4Propagator.java`, ~750 lines), based on Vallado et al. *"Revisiting Spacetrack Report #3"*, AIAA 2006-6753.

**Why SGP4 specifically?** All public TLE data is deliberately tuned to the SGP4 force model. TLE elements are *mean* elements that absorb modelling errors as corrections — using any other propagator (Cowell, Runge-Kutta, etc.) with TLE data produces incorrect positions because the data and the algorithm are mathematically coupled.

**Propagation pipeline:**

```
TLE (line1 + line2)
       │
       ▼
Parse mean orbital elements
(inclination, RAAN, eccentricity, arg of perigee, mean anomaly, mean motion, B*)
       │
       ▼
SGP4 Initialization — compute secular drift rates due to:
  • J2, J3, J4 Earth oblateness perturbations
  • Atmospheric drag (B* drag term)
  • Resonance classification: LEO vs deep-space
       │
       ▼
Propagate to target time (minutesFromEpoch):
  • Apply secular drift (mean motion, nodal regression, perigee precession)
  • Solve Kepler's equation via Newton-Raphson iteration
  • Apply short-period J2 corrections
       │
       ▼
ECI state vector: [x, y, z km] + [vx, vy, vz km/s]
       │
       ├──────────────────────────────────────────────────────────┐
       │                                                          │
       ▼                                                          ▼
Rotate ECI → ECEF via GMST                          Advanced analysis layer
Apply Bowring iterative method                        • Doppler: project ECI velocity onto
→ Geodetic lat/lon/alt (WGS-84)                        observer LOS vector → range-rate →
                                                         Δf = f₀ · (rangeRate / c)
                                                      • Eclipse: cylindrical shadow model
                                                         → penumbra / umbra depth scalar
                                                      • Conjunction: ECI-frame ΔR between
                                                         two propagated state vectors
                                                      • Orbital energy: ε = v²/2 − μ/r
```

**Usage example:**

```java
// Initialize from raw TLE strings
Sgp4Propagator sgp4 = new Sgp4Propagator(line1, line2);

// Minutes since TLE epoch (negative = past, positive = future)
double jd = AstroUtils.toJulianDate(Instant.now());
double minutesFromEpoch = (jd - sgp4.getEpochJulianDate()) * 1440.0;

// Propagate → ECI state vector
EciState eci = sgp4.propagate(minutesFromEpoch);

// Convert to geodetic coordinates (WGS-84)
GeodeticPosition geo = Sgp4Propagator.eciToGeodetic(eci.positionArray(), jd);

System.out.printf("ISS: %.4f°N  %.4f°E  %.1f km alt  %.2f km/s%n",
    geo.latitude(), geo.longitude(), geo.altitudeKm(), eci.speed());
// ISS: 51.6234°N  -12.4521°E  418.3 km alt  7.66 km/s

// Doppler shift for a 437.550 MHz uplink from a ground observer
double dopplerHz = DopplerUtils.computeShiftHz(eci, observerEcef, 437_550_000.0);
System.out.printf("Doppler shift: %+.0f Hz  →  receive on %.3f MHz%n",
    dopplerHz, (437_550_000.0 + dopplerHz) / 1e6);
// Doppler shift: -3241 Hz  →  receive on 437.547 MHz
```

**Accuracy by TLE age:**

| TLE Age | Expected Position Error |
|---------|------------------------|
| < 24 hours | ~1–3 km |
| 1–3 days | ~5–15 km |
| 7 days | ~10–30 km |
| > 30 days | ⚠️ Unreliable |

---

## 📜 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

```
MIT License — free to use, modify, and distribute with attribution.
```

---

<div align="center">

Built with ☕ Java + ⚛ React

TLE data from [CelesTrak](https://celestrak.org) · SGP4 algorithm by [Vallado et al. (2006)](https://celestrak.org/publications/AIAA/2006-6753/)

**Give ⭐ Star this repo if you found it useful**

</div>
