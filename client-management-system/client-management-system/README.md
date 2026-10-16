# Client Management System (Spring Boot + MongoDB)

A web application for managing clients, their projects and the history of your communication with them. The backend is Spring Boot 3.5 on Java 25 with Spring Data MongoDB, and the frontend is plain HTML, CSS and JavaScript served by Spring Boot itself. There is no separate frontend server, no Docker and no cloud account.

This guide is written for **IntelliJ IDEA**. You never need CMD, PowerShell or a terminal.

---

## What you need

| Item | Version | Notes |
|---|---|---|
| IntelliJ IDEA | 2025.2 or newer (Community or Ultimate) | Older versions do not understand Java 25. |
| JDK | 25 | The one you already have installed. |
| MongoDB Community Server | 7.x or 8.x | Runs separately on your computer. |
| Internet connection | First time only | IntelliJ downloads the Maven dependencies. |

Maven itself does not need to be installed: IntelliJ has Maven built in.

---

## Step 1 — Start MongoDB (separately, before the app)

MongoDB is its own program. The Spring Boot application connects to it, but it does **not** start it for you.

**If MongoDB is not installed yet (Windows):** download *MongoDB Community Server* from mongodb.com/try/download/community, run the `.msi`, choose *Complete*, and keep **"Install MongoD as a Service"** ticked. It then starts automatically with Windows. You can also tick *MongoDB Compass* (a graphical database viewer).

**Check that MongoDB is running** (any one of these):

1. Open the Windows **Services** app, find **MongoDB Server (MongoDB)**, and check that its status is *Running*. If not, right-click it and choose **Start**.
2. Open **MongoDB Compass** and connect to `mongodb://localhost:27017`. If it connects, MongoDB is running.
3. Open `http://localhost:27017` in your browser. If MongoDB is running you will see a short message saying you are trying to access MongoDB over HTTP. If the page does not load at all, MongoDB is not running.

On macOS (Homebrew) start it with the `brew services` MongoDB service; on Linux start the `mongod` service.

You do not need to create the database: MongoDB creates `client_management` automatically when the first record is saved.

---

## Step 2 — Open the project in IntelliJ IDEA

1. Unzip `client-management-system.zip` somewhere simple, for example `C:\Projects\client-management-system`.
2. In IntelliJ choose **File → Open…**, browse into the unzipped folder, select **`pom.xml`**, click **Open**, and choose **Open as Project**.
3. If IntelliJ asks whether to trust the project, click **Trust Project**.

## Step 3 — Set the Java 25 SDK

1. **File → Project Structure… → Project**.
2. **SDK:** choose your JDK 25. If it is not in the list, click **Add SDK → JDK…** and select the folder where Java 25 is installed (for example `C:\Program Files\Java\jdk-25`).
3. **Language level:** *SDK default* (or 25).
4. Click **OK**.

## Step 4 — Make Maven use Java 25

1. **File → Settings… → Build, Execution, Deployment → Build Tools → Maven → Importing**.
   Set **JDK for importer** to *Use Project JDK* (or your JDK 25).
2. Still under **Maven**, open **Runner** and set **JRE** to *Use Project JDK*.
3. Click **OK**.

## Step 5 — Let the dependencies load

Open the **Maven** tool window (the "m" icon on the right edge) and click **Reload All Maven Projects** (the circular arrows). Wait until the progress bar at the bottom of IntelliJ finishes. The first time this downloads Spring Boot and the MongoDB driver, which can take a few minutes.

When it is done, the Java files no longer show red underlines.

## Step 6 — Run the application

1. In the **Project** panel open
   `src/main/java/com/example/cms/ClientManagementApplication.java`.
2. Click the **green Run arrow** next to `public static void main` (or next to the class name) and choose **Run 'ClientManagementApplication.main()'**.
3. Watch the **Run** window at the bottom. Success looks like this:

   ```
   Connected to MongoDB database 'client_management'. Indexes are ready.
   Open http://localhost:8080 in your browser.
   Started ClientManagementApplication in 3.2 seconds
   ```

   If MongoDB is not running you will instead see a block starting with **COULD NOT CONNECT TO MONGODB**, with instructions. The page will still open, but it will show a red banner and every action will report that the database is unavailable.

4. Open **http://localhost:8080** in your browser.

The project also includes a ready-made run configuration named **ClientManagementApplication** (from the `.run` folder). It appears in the drop-down at the top right of IntelliJ, so after the first run you can use the green arrow in the toolbar.

## Stopping and restarting

- **Stop:** click the red square **Stop** button in the Run window or the toolbar.
- **Restart:** click the **Rerun** button (circular arrow) in the Run window, or the green Run arrow again. If IntelliJ asks whether to stop the running instance, choose **Stop and Rerun**.
- Restart after starting MongoDB if the app was started while MongoDB was down, so the indexes are created.

---

## Configuring the MongoDB connection

The connection is set in `src/main/resources/application.properties`:

```properties
spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/client_management}
```

This means: use the `MONGODB_URI` environment variable if it is set, otherwise use `mongodb://localhost:27017/client_management`. For a normal local installation you do not need to change anything.

To use a different address (for example another port, or a username and password), you can either edit the default in `application.properties`, or set an environment variable in IntelliJ:

1. **Run → Edit Configurations…**
2. Select **ClientManagementApplication** under *Application*.
3. If you do not see an *Environment variables* field, click **Modify options → Environment variables**.
4. Enter, for example:
   `MONGODB_URI=mongodb://localhost:27017/client_management`
   (with a password: `MONGODB_URI=mongodb://user:password@localhost:27017/client_management?authSource=admin`)
5. Click **OK** and run the application again.

You can check the connection at any time by opening `http://localhost:8080/api/health`. It shows `"database": "UP"` or `"database": "DOWN"`. The sidebar of the web page shows the same status.

---

## Using the application

- **Dashboard** — live statistics (total, active, lead, inactive/closed clients; total, in-progress, completed and overdue projects; interactions), the newest clients, overdue projects and quick links.
- **Clients** — table with search (name, email, company) and status/type filters; add, edit, delete with confirmation; a detail page with the client summary, their projects and their interaction history.
- **Projects** — overdue section at the top, table with status, priority and client filters, add/edit forms with client selection, and a detail page with one-click status changes that only offers the allowed next statuses.
- **Interactions** — pick a client to see their history (newest first), then add, edit or delete entries. The same history also appears on each client's page.

### Business rules

1. A client created without a status becomes **LEAD**.
2. Creating a project for a **LEAD** client makes the client **ACTIVE**. **INACTIVE** or **CLOSED** clients are never reactivated automatically; the request is rejected with a clear message.
3. A project's due date cannot be earlier than its start date, and a project cannot be marked **COMPLETED** before its start date.
4. Status transitions: PLANNED → IN_PROGRESS or CANCELLED; IN_PROGRESS → ON_HOLD, COMPLETED or CANCELLED; ON_HOLD → IN_PROGRESS or CANCELLED. COMPLETED and CANCELLED are final. A new project starts as PLANNED or IN_PROGRESS.
5. A project is **overdue** when its due date is before today and it is neither COMPLETED nor CANCELLED.
6. A client that still has projects or interactions cannot be deleted (HTTP 409 with an explanation).
7. Each client has a summary with project and interaction counts.
8. All dashboard numbers are counted from MongoDB on every request.
9. `createdAt` is set automatically; `updatedAt` changes on every update.
10. Emails are unique regardless of letter case (stored in lower case, protected by a unique index). Invalid IDs, missing records, unknown enum values and bad dates return clear 400/404 errors.

Extra rule: a **BUSINESS** client must have a company name. Interaction dates cannot be in the future.

---

## REST API

Base path: `/api`. All bodies are JSON. Dates use `YYYY-MM-DD`.

| Method | URL | Purpose |
|---|---|---|
| POST | `/api/clients` | Create client |
| GET | `/api/clients?search=&status=&clientType=` | List / search / filter clients (newest first) |
| GET | `/api/clients/{id}` | Get client |
| PUT | `/api/clients/{id}` | Update client |
| DELETE | `/api/clients/{id}` | Delete client (409 if it has projects or interactions) |
| GET | `/api/clients/{id}/summary` | Client summary |
| GET | `/api/clients/{clientId}/projects` | Projects of a client |
| POST | `/api/projects` | Create project |
| GET | `/api/projects?status=&priority=&clientId=` | List / filter projects |
| GET | `/api/projects/overdue` | Overdue projects |
| GET | `/api/projects/{id}` | Get project |
| PUT | `/api/projects/{id}` | Update project (enforces status transitions) |
| DELETE | `/api/projects/{id}` | Delete project |
| POST | `/api/clients/{clientId}/interactions` | Add interaction |
| GET | `/api/clients/{clientId}/interactions` | Interaction history, newest first |
| GET | `/api/interactions/{id}` | Get interaction |
| PUT | `/api/interactions/{id}` | Update interaction |
| DELETE | `/api/interactions/{id}` | Delete interaction |
| GET | `/api/dashboard/summary` | Dashboard statistics |
| GET | `/api/health` | Database connectivity check |

The file `api-requests.http` in the project root contains these requests ready to run with IntelliJ's HTTP Client (green arrow next to each request). Tools such as Postman work too.

### Example: create a client

`POST http://localhost:8080/api/clients`

```json
{
  "firstName": "Alex",
  "lastName": "Smith",
  "email": "alex@example.com",
  "phone": "+19876543210",
  "companyName": "Smith Technologies",
  "address": "New York",
  "clientType": "BUSINESS",
  "notes": "Interested in website development"
}
```

Response `201 Created`:

```json
{
  "id": "6710f3a2c1d4e85b2a9f0c11",
  "firstName": "Alex",
  "lastName": "Smith",
  "fullName": "Alex Smith",
  "email": "alex@example.com",
  "phone": "+19876543210",
  "companyName": "Smith Technologies",
  "address": "New York",
  "clientType": "BUSINESS",
  "status": "LEAD",
  "notes": "Interested in website development",
  "createdAt": "2026-10-16T05:12:40.123Z",
  "updatedAt": "2026-10-16T05:12:40.123Z"
}
```

Sending the same email again (even as `ALEX@example.com`) returns `409 Conflict`:

```json
{
  "timestamp": "2026-10-16T05:13:02.456Z",
  "status": 409,
  "error": "Conflict",
  "message": "A client with email 'alex@example.com' already exists.",
  "path": "/api/clients"
}
```

### Example: create a project

`POST http://localhost:8080/api/projects` (use the real client ID returned above)

```json
{
  "name": "Company Website",
  "description": "Build a responsive company website",
  "clientId": "6710f3a2c1d4e85b2a9f0c11",
  "status": "PLANNED",
  "priority": "HIGH",
  "startDate": "2026-10-16",
  "dueDate": "2026-12-20",
  "budget": 2500.00
}
```

Response `201 Created`:

```json
{
  "id": "6710f3e9c1d4e85b2a9f0c12",
  "name": "Company Website",
  "description": "Build a responsive company website",
  "clientId": "6710f3a2c1d4e85b2a9f0c11",
  "clientName": "Alex Smith",
  "status": "PLANNED",
  "priority": "HIGH",
  "startDate": "2026-10-16",
  "dueDate": "2026-12-20",
  "budget": 2500.00,
  "overdue": false,
  "allowedNextStatuses": ["IN_PROGRESS", "CANCELLED"],
  "createdAt": "2026-10-16T05:14:01.789Z",
  "updatedAt": "2026-10-16T05:14:01.789Z"
}
```

Alex's status is now `ACTIVE`. Trying to move this project straight to `COMPLETED` returns `400`:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Cannot change status from PLANNED to COMPLETED. Allowed next statuses: IN_PROGRESS, CANCELLED.",
  "path": "/api/projects/6710f3e9c1d4e85b2a9f0c12"
}
```

### Example: add an interaction

`POST http://localhost:8080/api/clients/6710f3a2c1d4e85b2a9f0c11/interactions`

```json
{
  "type": "MEETING",
  "subject": "Initial requirements discussion",
  "description": "Discussed the client's website requirements.",
  "interactionDate": "2026-10-16"
}
```

Response `201 Created` with the saved interaction (`id`, `clientId`, `type`, `subject`, `description`, `interactionDate`, `createdAt`, `updatedAt`). It appears at the top of `GET /api/clients/{clientId}/interactions`.

### Example: deleting a client that has projects

`DELETE http://localhost:8080/api/clients/6710f3a2c1d4e85b2a9f0c11` returns `409 Conflict`:

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Cannot delete client 'Alex Smith' because it still has 1 project(s) and 1 interaction(s). Delete those records first, or change the client's status to CLOSED instead.",
  "path": "/api/clients/6710f3a2c1d4e85b2a9f0c11"
}
```

### Example: dashboard

`GET http://localhost:8080/api/dashboard/summary` returns counts such as `totalClients`, `activeClients`, `leads`, `inactiveClients`, `closedClients`, `inactiveAndClosedClients`, `totalProjects`, `projectsInProgress`, `completedProjects`, `overdueProjects`, `totalInteractions`, plus `recentClients` (5 newest) and `overdueProjectList` (up to 5).

### Error status codes

| Code | When |
|---|---|
| 400 | Validation failed, invalid ID format, unknown enum value, bad date, broken business rule |
| 404 | Record not found |
| 409 | Duplicate email, or deleting a client that still has projects/interactions |
| 503 | MongoDB is not reachable |
| 500 | Unexpected error (details only in the IntelliJ Run console, never in the response) |

---

## Running the automated tests in IntelliJ

The tests are in `src/test/java`:

| Test class | Covers |
|---|---|
| `ProjectStatusRulesTest` | Status transitions and overdue logic (pure Java, no database) |
| `ClientApiIntegrationTest` | Client CRUD, duplicate emails, search/filters, validation, deletion restrictions, client summary |
| `ProjectApiIntegrationTest` | Project CRUD, client activation, date rules, status transitions, overdue endpoint, filters |
| `InteractionApiIntegrationTest` | Interaction CRUD, newest-first history, client must exist |
| `DashboardIntegrationTest` | Dashboard statistics |

**Your real data is safe.** The integration tests connect to a separate database called **`client_management_test`** and empty only that database's collections before each test. A safety check stops the tests if they are ever pointed at any other database. Your `client_management` database is never touched.

The integration tests need MongoDB running on `localhost:27017`. If it is not running, they are reported as **skipped**, not failed. `ProjectStatusRulesTest` always runs.

To run them: right-click the `src/test/java` folder → **Run 'All Tests'**. Or, in the **Maven** tool window, open **Lifecycle** and double-click **test**. Results appear in the Run window.

---

## Troubleshooting

**Red code everywhere / "Cannot resolve symbol 'springframework'".**
The dependencies have not loaded. Open the Maven tool window and click **Reload All Maven Projects**. Check your internet connection and the progress bar at the bottom. If it still fails: **File → Invalidate Caches… → Invalidate and Restart**. If you are behind a proxy, set it in **Settings → Appearance & Behavior → System Settings → HTTP Proxy**.

**"Java 25 is not supported", "release version 25 not supported", or "invalid source release: 25".**
IntelliJ or Maven is using an older JDK. Check, in this order: **Project Structure → Project → SDK** is JDK 25; **Settings → Build Tools → Maven → Importing → JDK for importer** and **Maven → Runner → JRE** both use the project JDK; **Settings → Build, Execution, Deployment → Compiler → Java Compiler** has no older "target bytecode version" set for this module. Also make sure IntelliJ itself is 2025.2 or newer (**Help → About**); update it via **Help → Check for Updates** if needed. Then reload the Maven project.

**"COULD NOT CONNECT TO MONGODB" in the Run window, red banner on the page, or HTTP 503 errors.**
MongoDB is not running or is on a different address. Start it (Windows: **Services → MongoDB Server → Start**), confirm with MongoDB Compass or `http://localhost:27017`, then **Stop and Rerun** the application. If MongoDB uses another port or needs a password, set `MONGODB_URI` as described above.

**"Web server failed to start. Port 8080 was already in use."**
Usually the application is already running in another Run tab. Stop all running instances with the red Stop buttons and run again. If another program uses port 8080, change `server.port=8080` in `application.properties` to, for example, `8081`, or add the environment variable `SERVER_PORT=8081` in **Run → Edit Configurations**, then open `http://localhost:8081`.

**The page at http://localhost:8080 does not load.**
Check that the Run window shows "Started ClientManagementApplication" and has not stopped with an error. Use `http://`, not `https://`.

**My changes to HTML/CSS/JS do not appear.**
Restart the application (static files are copied when it builds), then reload the browser with **Ctrl+F5**.

**Duplicate email rejected although the letters differ in case.**
That is intended: `alex@example.com` and `Alex@Example.com` are treated as the same email.

---

## Project structure

```
client-management-system/
├── pom.xml
├── api-requests.http
├── .run/ClientManagementApplication.run.xml
└── src/
    ├── main/
    │   ├── java/com/example/cms/
    │   │   ├── ClientManagementApplication.java
    │   │   ├── client/        Client document, repository, service, controller, DTOs
    │   │   ├── project/       Project document, status rules, repository, service, controller, DTOs
    │   │   ├── interaction/   Interaction document, repository, service, controller, DTOs
    │   │   ├── dashboard/     Dashboard statistics service, controller, DTO
    │   │   ├── common/        MongoDB ID validation, health endpoint
    │   │   ├── exception/     Exceptions and the global JSON error handler
    │   │   └── config/        MongoDB client settings, startup check and index creation
    │   └── resources/
    │       ├── application.properties
    │       └── static/        index.html, style.css, app.js
    └── test/java/com/example/cms/   unit and integration tests
```

Request flow: **Controller → Service → Repository → MongoDB**. Controllers only translate HTTP to service calls; every business rule lives in a service class, and controllers return DTOs, never database documents.
