# TapIn — Систем за присуство во училница (Classroom Presence System)

> Студентот ја допира задната страна од професоровиот телефон преку **NFC** → присуството автоматски се запишува во базата, се синхронизира со серверот и се прикажува на веб дашборд во реално време.

Дистрибуиран систем составен од **четири компоненти** кои работат заедно: серверски backend со REST API, две Android апликации (професор и студент) и веб дашборд.


---

## Содржина

1. [Автори](#автори)
2. [Преглед на системот](#преглед-на-системот)
3. [Технологии](#технологии)
4. [Структура на проектот](#структура-на-проектот)
5. [Како работи NFC](#како-работи-nfc)
6. [Модел на податоци](#модел-на-податоци)
7. [Безбедност](#безбедност)
8. [REST API — целосна референца](#rest-api--целосна-референца)
9. [Мобилни апликации](#мобилни-апликации)
10. [Веб дашборд](#веб-дашборд)
11. [Брз старт](#брз-старт)
12. [Docker](#docker)
13. [Cloud deploy (Render)](#cloud-deploy-render)
14. [Тестирање](#тестирање)
15. [Демо акаунти](#демо-акаунти)

---

## Автори
>Даниел Ефтимов 102785
>Горан Милошоски 102706
>Ирена Ефтимова 102708

## Преглед на системот

```
┌─────────────────┐        NFC tap         ┌─────────────────┐
│   СТУДЕНТ APP    │ ─────────────────────► │  ПРОФЕСОР APP   │
│   (Android/HCE)  │   потпишан payload     │ (Android/Reader)│
└─────────────────┘                        └────────┬────────┘
                                                     │ HTTPS (REST)
                                                     ▼
                                            ┌─────────────────┐
                                            │     BACKEND     │
                                            │    (FastAPI)    │ ◄── PostgreSQL (Supabase)
                                            └────────┬────────┘
                                                     │ HTTPS (REST)
                                                     ▼
                                            ┌─────────────────┐
                                            │   ВЕБ ДАШБОРД   │
                                            │ (HTML/Tailwind) │
                                            └─────────────────┘
```

| Компонента | Улога |
|------------|-------|
| **Студент апликација** | Емулира NFC картичка (HCE) и емитува потпишан payload при тап |
| **Професор апликација** | Чита NFC тапови, складира локално (Room), синхронизира со серверот |
| **Backend** | REST API, автентикација (JWT), валидација на NFC потписи, статистики |
| **Веб дашборд** | Преглед на присуство, статистики, графикони, филтри, CSV извоз |

---

## Технологии

| Дел | Технологија |
|-----|-------------|
| База | PostgreSQL (Supabase) |
| Backend | Python 3, FastAPI, SQLAlchemy 2, Pydantic 2 |
| Автентикација | JWT (HS256), BCrypt |
| Мобилни | Kotlin, Jetpack Compose, Ktor (HTTP), DataStore, Room |
| NFC | HCE (студент емитува) + ReaderMode (професор чита) + HMAC потпис |
| Дашборд | HTML + TailwindCSS + Chart.js + vanilla JS (без build чекор) |
| Deploy | Docker / docker-compose, Render Blueprint |

---

## Како работи NFC

Системот користи **Host-based Card Emulation (HCE)** на студентската апликација и **Reader Mode** (foreground dispatch) на професорската. Не се потребни физички тагови.

```
┌──────────────┐   SELECT AID  00 A4 04 00 08 F0544150494E3031   ┌──────────────┐
│ ПРОФЕСОР APP │ ────────────────────────────────────────────►  │  СТУДЕНТ APP │
│   (reader)   │                                                 │    (HCE)     │
│              │ ◄────────────────────────────────────────────  │              │
│              │   "v2|193001|Име Презиме|<ts>|<hmac16>" + 9000  │              │
└──────┬───────┘                                                 └──────────────┘
       │
       │ POST /api/attendance { sessionId, [signedPayload] }
       ▼
┌──────────────┐   валидира HMAC + timestamp window (±60s) → resolvira student
│   BACKEND    │
└──────────────┘
```

- **AID:** `F0 54 41 50 49 4E 30 31` (`"TAPIN01"`) — custom, не колидира со платежни картички.
- **Payload (v2):** `v2|<студентски_број>|<име>|<unixSeconds>|<hmac16>`
  - `hmac16 = HMAC-SHA256(SECRET, "v2:<број>:<име>:<ts>")[:16]`
  - Payload-от содржи студентски број и име.
- **Безбедност:** потписот + timestamp прозорец (±60s) спречуваат **replay attack** — снимен таг не може да се „преиграе" подоцна.
- Студентската апликација работи **дури и кога телефонот е заклучен** (`requireDeviceUnlock=false` во `apduservice.xml`).
- Поддржан е и постар **v1** формат (`број|ts|hmac`) и **NDEF** fallback за тестирање со физички тагови.

Имплементација:
- Студент HCE: `student-app/.../nfc/TapInHceService.kt`, потпис: `nfc/SecureNfc.kt`
- Професор reader: `teacher-app/.../nfc/NfcReader.kt`
- Backend валидација: `backend/secure_nfc.py`

---

## Модел на податоци

Релациона PostgreSQL шема (`database/schema.sql`). Нормализирана, со индекси за чести filter patterns.

| Табела | Опис | Клучни полиња |
|--------|------|---------------|
| `users` | Сите корисници (role-based) | `id`, `email` (unique), `password_hash`, `full_name`, `role` (`ADMIN`/`TEACHER`/`STUDENT`), `student_number` (unique) |
| `courses` | Предмети, секој со свој професор | `id`, `code` (unique), `name`, `teacher_id` → `users` |
| `enrollments` | Кој студент е на кој предмет | PK (`student_id`, `course_id`) |
| `attendance_sessions` | Состанок/час во време | `id`, `course_id`, `teacher_id`, `started_at`, `ended_at` (NULL = активна) |
| `attendance` | Еден ред по NFC тап | `id`, `session_id`, `student_id`, `tapped_at`, **UNIQUE(`session_id`, `student_id`)** |

**Релации:**
```
users 1──* courses        (teacher_id)
users *──* courses         (enrollments)
courses 1──* attendance_sessions
attendance_sessions 1──* attendance
users 1──* attendance       (student_id)
```

**Спречување дупликати:** `UNIQUE(session_id, student_id)` гарантира дека еден студент не може двапати да се запише во иста сесија (двоен тап → `duplicates`, не нова грешка).

**Индекси:** по `role`, `tapped_at DESC`, `(teacher_id, started_at DESC)`, partial index за активни сесии (`WHERE ended_at IS NULL`), `LOWER(email)`, `student_number` итн.

---

## Безбедност

| Механизам | Каде | Опис |
|-----------|------|------|
| Hashing на лозинки | `backend/security.py` | bcrypt (10 rounds), truncate на 72 бајти |
| Token автентикација | `backend/security.py`, `deps.py` | JWT HS256, TTL 24h, claims: `sub`, `email`, `role`, `name` |
| Role-based access | `backend/deps.py` | `require_teacher`, `require_admin`; професор гледа само свои податоци, админ — сè |
| NFC потпис | `backend/secure_nfc.py` | HMAC-SHA256 + timestamp window (±60s) против replay |

Сите заштитени endpoints бараат `Authorization: Bearer <token>` хедер. Без/невалиден токен → `401`; погрешна улога → `403`.

---

## REST API — целосна референца

Base URL (dev): `http://localhost:8080` · Swagger UI: `http://localhost:8080/docs`

> Сите тела се JSON. Во колоната **Auth**: „Bearer" = бара токен; „професор/админ" и „админ" означуваат ограничување по улога; „—" = јавен endpoint.

### Health

| Метод | Path | Auth | Опис |
|-------|------|------|------|
| `GET` | `/actuator/health` | — | Health check → `{ "status": "UP" }` |

### Auth (`routers/auth.py`)

| Метод | Path | Auth | Опис |
|-------|------|------|------|
| `POST` | `/api/register` | — | Регистрација (201). Алиас: `/api/auth/register` |
| `POST` | `/api/login` | — | Најава (за професор и студент). Алиас: `/api/auth/login` |
| `GET` | `/api/auth/me` | Bearer | Тековен корисник |
| `GET` | `/api/users/by-number/{student_number}` | Bearer · професор | Lookup на студент по број (за NFC tap resolve) |

**`POST /api/register`** — body:
```json
{
  "email": "ime@ugd.edu.mk",
  "password": "string (6–72)",
  "fullName": "Име Презиме",
  "role": "TEACHER | STUDENT",
  "studentNumber": "193001"
}
```
> `studentNumber` е задолжителен за `STUDENT`, забранет за други. `ADMIN` не може да се регистрира.

**`POST /api/login`** — body: `{ "email": "...", "password": "..." }`

**Одговор (login/register)** — `AuthResponse`:
```json
{
  "token": "<JWT>",
  "user": { "id": 1, "email": "...", "fullName": "...", "role": "TEACHER", "studentNumber": null },
  "expiresAt": "2026-06-04T12:00:00Z"
}
```
Статуси: `201` (register) / `200` (login) · `400` валидација · `401` погрешни кредентијали · `409` зафатена е-пошта / студентски број.

### Courses (`routers/courses.py`) — префикс `/api/courses`

| Метод | Path | Auth | Опис |
|-------|------|------|------|
| `GET` | `/api/courses` | Bearer · професор | Список (професор → свои, админ → сите) |
| `POST` | `/api/courses` | Bearer · професор | Создавање предмет (201) |
| `POST` | `/api/courses/{id}/enroll?studentId=...` | Bearer · професор | Запишување студент (204) |

**`POST /api/courses`** — body: `{ "code": "PI1", "name": "Програмирање 1", "teacherId": null }`
**Одговор** — `CourseView`: `{ "id", "code", "name", "teacherId", "teacherName" }`
Статуси: `201` · `400` корисникот не е професор · `404` професорот не е најден · `409` шифрата постои.

### Sessions (`routers/sessions.py`) — префикс `/api/sessions`

| Метод | Path | Auth | Опис |
|-------|------|------|------|
| `POST` | `/api/sessions` | Bearer · професор | Започни сесија (201) |
| `POST` | `/api/sessions/{id}/close` | Bearer · професор | Затвори сесија (204) |
| `GET` | `/api/sessions?courseId=...` | Bearer · професор | Список сесии |

**`POST /api/sessions`** — body: `{ "courseId": 1 }`
**Одговор** — `SessionView`:
```json
{
  "id": 1, "courseId": 1, "courseName": "...",
  "teacherId": 1, "teacherName": "...",
  "startedAt": "...", "endedAt": null, "active": true, "attendanceCount": 0
}
```
Статуси: `201`/`204` · `403` не е твоја сесија · `404` не е најдена.

### Attendance (`routers/attendance.py`) — префикс `/api/attendance`

| Метод | Path | Auth | Опис |
|-------|------|------|------|
| `POST` | `/api/attendance` | Bearer · професор | Bulk sync на тапови од професорската апликација |
| `GET` | `/api/attendance` | Bearer · професор | Список со филтри + пагинација |
| `DELETE` | `/api/attendance/{id}` | Bearer · админ | Бришење запис (204) |

**`POST /api/attendance`** — body (`BulkAttendanceRequest`):
```json
{
  "sessionId": 1,
  "records": [
    { "signedPayload": "v2|193001|Име|<ts>|<hmac>", "tappedAt": "..." },
    { "studentId": 5, "tappedAt": "..." }
  ]
}
```
> Секој запис има или `signedPayload` (backend валидира HMAC и resolvira студент) или `studentId` (директно / рачно внесено).

**Одговор** — `BulkAttendanceResponse`:
```json
{ "accepted": 3, "duplicates": 1, "rejected": 0, "invalidSignatures": 0, "ids": [10, 11, 12] }
```

**`GET /api/attendance`** — query параметри:

| Параметар | Опис |
|-----------|------|
| `studentId`, `teacherId`, `courseId`, `sessionId` | филтри |
| `from`, `to` | временски опсег (ISO datetime) |
| `page` (≥0), `size` (1–200) | пагинација |

> Професор автоматски е ограничен на сопствените сесии (`teacherId` се форсира).

**Одговор** — `AttendancePage`: `{ "items": [AttendanceView], "totalElements", "page", "size" }`
Каде `AttendanceView` = `{ id, sessionId, studentId, studentName, studentNumber, courseName, teacherName, tappedAt }`.

### Statistics (`routers/statistics.py`) — префикс `/api/statistics`

| Метод | Path | Auth | Опис |
|-------|------|------|------|
| `GET` | `/api/statistics?days=30` | Bearer · професор | Агрегат: overview + perCourse + trend |
| `GET` | `/api/statistics/overview` | Bearer · професор | Само преглед |
| `GET` | `/api/statistics/per-course` | Bearer · професор | Стапка по предмет |
| `GET` | `/api/statistics/trend?days=30` | Bearer · професор | Тренд по денови |
| `GET` | `/api/statistics/per-student?courseId=...` | Bearer · професор | Стапка по студент |

> Сите се **role-scoped**: професор гледа само свои предмети/сесии, админ — сè.

**`GET /api/statistics`** одговор — `StatisticsAggregate`:
```json
{
  "overview": {
    "todayCount": 12, "weekCount": 80, "monthCount": 240,
    "activeSessions": 1, "totalStudents": 8, "totalCourses": 2
  },
  "perCourse": [{ "courseId": 1, "courseName": "...", "attended": 6, "enrolled": 8, "rate": 0.75 }],
  "trend": [{ "date": "2026-06-03", "count": 12 }]
}
```

---

## Мобилни апликации

И двете се Kotlin + Jetpack Compose, MVVM (`AppViewModel`, `SessionViewModel`…), Ktor за HTTP, DataStore за токен.

### Професор апликација (`teacher-app/`)
- **Најава + регистрација** (`ui/screens/LoginScreen.kt`, `RegisterScreen.kt`)
- **Предмети** — листа/создавање (`CoursesScreen.kt`)
- **Сесија** — авто-активација при влез, NFC reader, листа на тапови, рачно затворање (`SessionScreen.kt`)
- **Offline-first складирање** — секој тап оди прво во Room (PENDING), па upload; ако нема мрежа останува за подоцна (`data/AttendanceRepository.kt`, `data/local/`)
- **Bulk sync** со progress bar и „N на чекање" badge
- **Feedback** — вибрација + звук + визуелен banner (`util/TapFeedback.kt`)

### Студент апликација (`student-app/`)
- **Најава + регистрација**
- **HCE сервис** кој емитува потпишан payload (`nfc/TapInHceService.kt`)
- **Feedback** — full-screen „Запишано!" overlay + вибрација (`ui/screens/HomeScreen.kt`)
- **NFC lifecycle** — јасни инструкции ако NFC е исклучен/неподдржан

> **Важно:** споделениот NFC секрет `SecureNfc.SHARED_SECRET` (студент) мора да е ист како `NFC_SHARED_SECRET` (backend).
> За реален телефон, во `data/api/ApiClient.kt` смени `BASE_URL` со LAN IP или Render URL.

---

## Веб дашборд

`web-dashboard/` — без build чекор; може да се сервира статички или преку самиот backend.

- **Најава** — само `TEACHER`/`ADMIN` (`assets/login.js`)
- **Статистички карти** — денес / недела / месец / активни сесии / студенти / предмети
- **Графикони (Chart.js)** — line (тренд) + horizontal bar (стапка по предмет)
- **Табела** — филтри (предмет, датум од/до), пребарување, пагинација
- **Табела по студент** — стапка на присуство со прогрес-бар
- **CSV извоз** — до 5000 редови
- **Live polling** — авто-освежување на 15s со пулс-анимација на нов запис

`assets/api.js` автоматски детектира `BASE_URL` (same-origin за Docker/FastAPI, или `:8080` во dev).

---

## Docker

**Default — користи Supabase** (пополни `backend/.env` прво):
```bash
docker compose up --build
```
- Backend: <http://localhost:8080> (Swagger: `/docs`)
- Дашборд: <http://localhost:8000> (nginx проксира `/api/*` до backend)

**Со локална Postgres** (без Supabase):
```bash
docker compose --profile local-db up --build
```
Postgres се иницијализира автоматски со `database/schema.sql`.
Во `backend/.env`: `DATABASE_URL=postgresql+psycopg://tapin:tapin_dev@postgres:5432/tapin`.

Стоп: `docker compose down` · Чиста состојба: `docker compose down -v`.

---

## Cloud deploy (Render)

Еден Render Web Service хостира и backend (`/api/*`) и дашборд (`/`, `/dashboard`, `/register`) од ист origin → без CORS/SSL проблеми. За база — Supabase.

1. Импортирај `database/schema.sql` во Supabase.
2. Во Supabase → **Project Settings → Database → Connection string → URI (Pooler)**, смени `postgresql://` со `postgresql+psycopg://`.
3. Push на GitHub → <https://dashboard.render.com/blueprints> → **New Blueprint Instance** → избери repo → `Apply`.
4. Во сервисот → **Environment** → пополни `DATABASE_URL`.
5. **Manual Deploy** → по ~3–4 мин имаш live URL.

`render.yaml` го гради `Dockerfile` од repo root, генерира `JWT_SECRET`, healthcheck на `/actuator/health`, Frankfurt регион.

> **Free tier:** сервисот спие по 15 мин idle; првиот request бара ~30s. Отвори го URL-от 1 мин пред демо.

По deploy, во двете `ApiClient.kt` смени `BASE_URL` со Render URL и rebuild:
```kotlin
const val BASE_URL = "https://tapin-XXXX.onrender.com"
```
---

## Демо профили

| Улога | Е-пошта | Лозинка |
|-------|---------|---------|
| Професор | `irena.eftimova@ugd.edu.mk` | `irena123` |
| Професор | `goran.milososki@ugd.edu.mk` | `goran123` |
| Студенти | `daniel.102708@student.ugd.edu.mk`| `daniel123` |
