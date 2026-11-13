# Airport Shuttle

Three Ionic Angular web interfaces connected to one Spring Boot application and MySQL database. No Capacitor, iOS, Android, or native application projects are included.

## Open locally

- Passenger: http://localhost:8088/passenger/
- Dispatcher: http://localhost:8088/dispatcher/
- Driver: http://localhost:8088/driver/

Local demonstration accounts use the password `ShuttleLocal2026!`:

| Workspace | Email |
| --- | --- |
| Passenger | passenger@shuttle.local |
| Dispatcher | dispatch@shuttle.local |
| Driver, SH-101 | driver@shuttle.local |
| Driver, SH-102 | driver2@shuttle.local |

Each workspace has its own authenticated identity within the browser session, so all three can be opened in different tabs. Passengers can also register their own accounts. Driver and dispatcher accounts are provisioned on the server, never through public registration.

## Start and stop

Use Node 22.12+ or 24 LTS, Java 21+, Maven, and MySQL. This computer has a compatible Node runtime at `~/.nvm/versions/node/v22.22.2/bin`.

```sh
cd ~/airport_shittle
export PATH="$HOME/.nvm/versions/node/v22.22.2/bin:$PATH"
npm ci
# First installation only; creates application/test databases and a private .env:
node scripts/setup-local.mjs
npm run build
npm start
```

The local database setup is already complete on this computer. Do not rerun setup over an existing `.env`. The script uses the local MySQL administrator account to create a dedicated application account; the server itself does not connect as root.

`npm run start:background` runs the built system in the background. `npm run stop` stops that managed process. Logs are in `.runtime/server.log`.

All three built interfaces are served by one Java process, with a 384 MB heap limit and a five-connection pool. Total process memory is larger than the heap. Builds run sequentially with two Angular build workers to limit memory pressure. MySQL is the already-running local service.

For frontend development, run the backend and one of `npm run dev:passenger`, `npm run dev:dispatcher`, or `npm run dev:driver`. Those commands proxy `/api` to port 8088. Use the role path, e.g. `http://localhost:4200/passenger/`. Rebuild and restart the backend after backend changes.

## Deploy to the iMac

`deploy_airport_shuttle.sh` builds on the MacBook Pro and deploys the complete web system to the iMac: Spring Boot, an isolated `airport_shuttle` MySQL schema/user, and the passenger, dispatcher, and driver interfaces behind NGINX. It follows the existing TMS connection pattern but does not reuse or modify the TMS database. Airport Shuttle uses the same Java 17 runtime already used by TMS.

Before the first deployment, provide the iMac MySQL administrator password without committing it:

```sh
cd ~/airport_shittle
MYSQL_ADMIN_PASSWORD='your-imac-mysql-admin-password' ./deploy_airport_shuttle.sh
```

The default hostname is the existing `managetransportation.com` site, so this deployment intentionally replaces the current root site with Airport Shuttle. The existing `api.managetransportation.com` server and other configured sites are not changed. The Airport Shuttle database is separate from TMS.

Firebase hosting projects:

- Passenger: `https://airport-shuttle-passenger-ui.web.app/`
- Dispatcher: `https://airport-shuttle-dispatcher-ui.web.app/`
- Driver: `https://airport-shuttle-driver-ui.web.app/`

Custom-domain URLs (Firebase Hosting setup is in progress):

- Passenger: `https://passenger.managetransportation.com/`
- Dispatcher: `https://dispatcher.managetransportation.com/`
- Driver: `https://driver.managetransportation.com/`

These custom domains keep the apps and API on the same site so session cookies work in browsers that block third-party cookies. Run `./deploy_airport_shuttle_firebase.sh` to rebuild and publish all three. They use `https://managetransportation.com` as their API base, and the backend deployment allowlist includes both the Firebase and custom-domain origins.

## Workflow

1. Passenger chooses pickup, destination, flight, pickup time, passenger count, luggage, and optional notes, then requests a ride.
2. Dispatcher sees each driver's vehicle seat capacity and any trip within the 90-minute assignment window before assigning. The backend enforces both checks. Drivers can hold future assignments but only operate one trip at a time.
3. Driver accepts or declines. Declines return the request to dispatch. Acceptance enables the ordered progression below.
4. Driver starts toward pickup and explicitly enables browser location sharing. Road-based ETA is calculated when routing is available; the driver can enter an ETA override.
5. Passenger sees trip/location updates, receives an alert when the driver marks arrived, and can see the live wait timer. Browser push is opt-in.
6. Driver completes boarding, transit, and completion. Passenger history retains the trip and offers a downloadable text receipt.

```text
REQUESTED -> ASSIGNED -> ACCEPTED -> EN_ROUTE -> ARRIVING
          -> BOARDING -> IN_TRANSIT -> COMPLETED
```

The transition from ARRIVING is BOARDING. The wait time is saved when boarding begins or the trip ends. After the configurable 10-minute grace period, the driver may report a passenger no-show; this ends the trip and records the wait for dispatch review, without collecting a fee. Before boarding, the passenger or dispatcher may cancel. Dispatcher route edits and reassignment are audited. Reassignment is disallowed after boarding; a pickup cannot be changed after boarding starts. Server-side version checks reject stale writes.

## Structure

- `passenger-ui/`: booking, live tracking, alerts, history and receipt access.
- `dispatcher-ui/`: map, filtered trip queue, assignments and route overrides.
- `driver-ui/`: dated schedule, acceptance/decline, navigation, GPS and status updates.
- `shared/`: common Ionic shell, authenticated API client, Leaflet map and trip details.
- `airport-shuttle-ws/`: role/session authentication, trip domain rules, MySQL persistence, Flyway migrations, push notification delivery, and tests.
- `public/sw.js`: browser push notification and notification-click handling. It does not cache private API data.

Each UI is an independently installable Angular project with its own `package.json`, lockfile, workspace, and local copy of its shared UI modules. From any UI directory, run `npm ci`, then `npm run build` or `npm start`. All three use the same Spring Boot API; local development proxies `/api` to port 8088. The root scripts coordinate builds and deployment only. Data is stored in MySQL. Browser storage is not used as a database. Updates poll the server every three seconds and expose an offline/reconnecting state. Location uploads are throttled to roughly eight seconds when GPS changes.

## Tests

```sh
npm run test:backend
npx playwright install chromium
npm test
```

Tests use **only `airport_shuttle_test`**. The browser test command clears trip/notification fixtures in that test database and starts its own server on port 8090. It does not clear the application database. Backend integration tests roll back their trip changes. Browser reports, screenshots and failure traces are in `playwright-report/` and `test-results/`.

The tests cover the complete cross-role trip flow, role/ownership boundaries, input validation, idempotent booking, assignment/version conflicts, capacity, decline/cancel rules, GPS, five-minute alert deduplication, receipt downloads, session refresh/logout, offline recovery, same-browser role isolation, responsive layouts, encrypted push payloads, delivery retries and expired subscriptions.

## Operational boundaries

- This is a working local web implementation. Sample operations use Bamako, Mali and Aéroport International Président Modibo Keïta-Sénou (BKO), with airport zones, Bamako landmarks, and hotels. Locations live in MySQL and are updated by Flyway migrations. Pickup inputs and displayed times use the browser's local time zone.
- Fares use road distance at USD 1/km and the cached daily USD-to-XOF rate; the quote and rate are saved on the trip. The older USD 45 plus USD 8 per additional passenger wording was stale. Receipts are trip records, not proof of payment. No payment processor or live airline/flight-status feed is connected; payments and flight-driven pickup changes remain inactive until a provider and credentials are configured.
- Maps use OpenStreetMap tiles and the public OSRM routing service. Estimates do not include live traffic. External map/routing availability is not guaranteed; the UI reports route failures and retains location markers. Configure a contracted routing/tile provider before operating at scale.
- Browser GPS requires permission and a secure context (HTTPS, or localhost). The driver's page must remain active; browsers may suspend background location. Native/background tracking is intentionally deferred.
- Web Push uses generated VAPID keys persisted in MySQL, real browser subscriptions, encrypted messages, a retry queue, and a service worker. Provider delivery depends on browser/OS permissions and connectivity. Automated tests validate generation, encryption, retries, and handling with simulated provider responses; delivery to a real unattended device still requires an operator/device test. Configure the VAPID contact before deployment.
- The default bind address is loopback. For deployment, use HTTPS, set `COOKIE_SECURE=true`, set `SEED_DEMO=false`, provision real operator accounts, replace the demo credentials, and configure database backups. Sessions are persisted in MySQL with an eight-hour idle timeout and expire automatically. No public deployment has been created.

## API outline

All mutations require `X-Shuttle-Request: 1`; the browser also sends `X-Shuttle-Role`. Cross-origin API access is disabled. Authentication uses an HttpOnly, SameSite session cookie with per-role identities and BCrypt password hashes. User permissions are checked on every protected operation.

- `POST /api/auth/login`, `/register`, `/logout`; `GET /api/auth/me`
- `GET /api/locations`, `/drivers`, `/trips`; `POST /api/trips`
- `GET /api/trips/{id}`, `/{id}/events`, `/{id}/receipt`
- `GET /api/trips/{id}/assignment-options` previews driver seat and schedule conflicts; `PUT /api/trips/{id}/assignment` enforces them
- `PUT /api/trips/{id}/route`, `/{id}/status`, `/{id}/location`, `/{id}/no-show`
- `GET /api/notifications`; `PUT /api/notifications/{id}/read`
- `GET /api/push/key`; `POST /api/push/subscribe`, `/unsubscribe`
- `GET /api/health`

Technology references: [Ionic Angular](https://ionicframework.com/docs/angular/overview), [Angular compatibility](https://angular.dev/reference/versions), [Spring Boot](https://docs.spring.io/spring-boot/3.5/reference/index.html), [Leaflet](https://leafletjs.com/reference.html), [OSRM](https://project-osrm.org/docs/v5.24.0/api/).
