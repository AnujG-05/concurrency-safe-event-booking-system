# Concurrency-Safe Event Booking System with LLM Dispute Resolver

A Spring Boot backend that demonstrates a real race condition in seat
booking, fixes it with optimistic locking, and adds an LLM-powered
explanation layer for failed bookings — one that never makes booking
decisions itself.

> **Note on verification:** every file in this project was written and
> reviewed carefully, but it has **not yet been compiled or run** in the
> environment that generated it (no internet access to Maven Central
> there). **Before you put this on a resume, run it yourself**: follow
> the Quick Start below, run the test suite, and confirm the concurrency
> test prints the numbers described in this README. That step isn't
> optional — it's the difference between a project you can defend in an
> interview and one you can't.

---

## The story this project tells

1. **The bug:** seat booking has a classic "check-then-act" race
   condition. Under concurrent load, multiple requests can read the same
   stale seat count before any of them commits a write — causing
   overbooking.
2. **The fix:** JPA optimistic locking (`@Version`) plus a bounded retry
   loop. A concurrency test proves the fix works with real numbers.
3. **The LLM layer:** when a booking fails, an LLM generates a
   plain-English explanation for the user — but it never decides
   anything. It only narrates outcomes the deterministic service layer
   already produced, with strict output validation and a safe fallback
   if the LLM is unavailable or misbehaves.

---

## Architecture

```
                      ┌─────────────────────┐
                      │   Controllers        │
                      │ Event / Booking /     │
                      │ Admin / Health        │
                      └──────────┬───────────┘
                                 │
                  ┌──────────────┴───────────────┐
                  │                                │
          ┌───────▼────────┐            ┌─────────▼──────────┐
          │  EventService   │            │   BookingService    │
          │  (simple CRUD)  │            │ (optimistic lock +  │
          │                 │            │  retry logic — the  │
          │                 │            │  core of the repo)  │
          └───────┬────────┘            └─────────┬──────────┘
                  │                                │
          ┌───────▼────────┐            ┌─────────▼──────────┐
          │ EventRepository │            │  BookingRepository   │
          │                 │            │  BookingAttemptLog    │
          │                 │            │  Repository           │
          └───────┬────────┘            └─────────┬──────────┘
                  │                                │
                  └───────────────┬────────────────┘
                                  │
                          ┌───────▼────────┐
                          │  MySQL / H2     │
                          └─────────────────┘

   Separately, wired into BookingController:

          ┌─────────────────────────────┐
          │  BookingExplanationService   │  <- caching + admin verify
          └──────────────┬───────────────┘
                         │
          ┌──────────────▼───────────────┐
          │   LlmExplanationService       │  <- strict JSON parsing,
          │                                │     fallback logic
          └──────────────┬───────────────┘
                         │
          ┌──────────────▼───────────────┐
          │       LlmClient                │  <- raw HTTP call,
          │  (5s timeout, provider-        │     5s timeout
          │   agnostic shape)               │
          └─────────────────────────────┘
```

The LLM path is architecturally **separate** from the booking path —
notice `BookingService` has zero dependency on anything in the `llm`
package. That's deliberate: the booking decision must remain fully
deterministic and must never depend on, or be slowed down by, an
external LLM call.

---

## Why Optimistic Locking?

`Event.java` has a field:

```java
@Version
private int version;
```

This single annotation changes how Hibernate generates UPDATE
statements. Instead of:

```sql
UPDATE events SET available_seats = ? WHERE id = ?
```

Hibernate generates:

```sql
UPDATE events SET available_seats = ?, version = version + 1
WHERE id = ? AND version = ?
```

The `AND version = ?` is the entire mechanism. If another transaction
already updated this row (and bumped its version) between our read and
our write, this UPDATE matches **zero rows**. Hibernate detects that and
throws `ObjectOptimisticLockingFailureException`. `BookingService`
catches this and retries — re-reading the now-current state — up to 3
times before giving up cleanly.

**Why optimistic over pessimistic (`SELECT ... FOR UPDATE`) here:**
seat booking is read-heavy and, most of the time, low-conflict — most
events aren't being hit by simultaneous bookings every millisecond.
Pessimistic locking would hold a real database lock for the duration of
every transaction, serializing ALL booking attempts for an event even
when most of them wouldn't have actually conflicted — hurting
throughput unnecessarily. If this were a domain with extremely frequent
contention (e.g. a single global counter hit thousands of times a
second), pessimistic locking, or an entirely different approach like a
single-threaded queue or an atomic database counter, would likely win
instead. The right choice depends on the conflict rate you expect — this
project picks optimistic locking because it fits a ticket-booking
access pattern, not because it's universally superior.

See the large comment block at the top of `BookingService.java` for a
side-by-side of what the naive, broken version of this method would
have looked like, and exactly how two threads would race each other to
produce an overbooked event.

---

## Why the LLM Never Makes the Booking Decision

Three reasons, all enforced by the architecture (not just by convention):

1. **Determinism.** The booking outcome must be reproducible purely from
   our own business rules (seat counts, locking). If an LLM call
   influenced whether a seat was granted, identical input could produce
   different outcomes depending on model behavior — unacceptable for a
   system handling real reservations.
2. **Latency.** LLM calls take hundreds of milliseconds to multiple
   seconds. The booking path needs to stay fast and short-lived to
   minimize contention windows. `LlmClient` enforces a 5-second timeout
   specifically so a slow LLM provider can never hang a request — and
   it's never even called from the booking path in the first place.
3. **Auditability.** If a booking is ever disputed, we need to point to
   exact, deterministic logic ("seats were 0 at the time of this
   UPDATE"), not "the model decided this was fine." The LLM here only
   narrates a decision that **already happened**.

This is enforced end-to-end: `LlmExplanationService.explainFailedBooking`
is only ever called from `GET /api/bookings/{id}/explain`, and only for
bookings that already have `status = FAILED`. If the LLM API call fails,
times out, returns malformed JSON, or returns valid JSON missing a
required field, the system falls back to a safe, deterministic message
— see `LlmExplanationService.fallback()`. The client never sees a raw or
broken LLM response, and the booking system itself is completely
unaffected by LLM outages.

---

## Quick Start (H2, zero setup)

Requires Java 17+ and Maven.

```bash
cd booking-system
mvn spring-boot:run
```

The app starts on `http://localhost:8080` using an in-memory H2
database — no configuration needed. Visit:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- H2 console: `http://localhost:8080/h2-console`
  (JDBC URL: `jdbc:h2:mem:bookingdb`, user: `sa`, no password)
- Health check: `http://localhost:8080/api/health`

## Running with MySQL

```bash
export DB_URL="jdbc:mysql://localhost:3306/booking_system?createDatabaseIfNotExist=true"
export DB_USERNAME="root"
export DB_PASSWORD="your_password"

mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

## Configuring the LLM API

The `/api/bookings/{id}/explain` endpoint needs an LLM API key to
generate real explanations (it works without one too — it just always
returns the fallback message, which is itself a valid demo of the
fallback behavior).

```bash
export LLM_API_URL="https://api.openai.com/v1/chat/completions"
export LLM_API_KEY="sk-..."
export LLM_API_MODEL="gpt-4o-mini"
```

`LlmClient.java` is written against an OpenAI-compatible chat-completions
response shape. If you use a different provider, you only need to adjust
`extractMessageContent()` in that one file.

---

## Running the Tests

```bash
mvn test
```

This runs two test classes:

### `BookingConcurrencyTest` — the centerpiece

Creates an event with **5 seats**, fires **20 truly concurrent** booking
requests using an `ExecutorService` + `CountDownLatch` (so all 20 threads
are released at the same instant rather than queued one after another),
and asserts:

| Metric                  | Expected value |
|--------------------------|-----------------|
| Confirmed bookings       | exactly 5       |
| Failed bookings          | exactly 15      |
| Final `availableSeats`   | exactly 0 (never negative) |

The test prints a clear before/after summary to the console explaining
what would have happened without optimistic locking (overbooking — more
than 5 confirmed bookings, or a negative seat count).

### `LlmExplanationFallbackTest`

Uses Mockito to simulate: an LLM API failure, a malformed JSON response,
a JSON response missing required fields, and a valid response — proving
the fallback path activates correctly in the first three cases and the
real explanation is used only in the fourth. No real API key or network
call is needed to run this test.

---

## API Reference

| Method | Path | Description |
|--------|------|--------------|
| POST | `/api/events` | Create an event with a fixed seat count |
| GET | `/api/events/{id}` | Fetch an event's current state |
| POST | `/api/bookings` | Attempt to book seats (optimistic-lock protected) |
| GET | `/api/bookings/event/{eventId}` | List all bookings for an event |
| GET | `/api/bookings/{id}` | Fetch a single booking |
| GET | `/api/bookings/{id}/explain` | LLM-generated explanation for a FAILED booking |
| PATCH | `/api/bookings/{id}/verify-explanation` | Admin: mark an explanation accurate/inaccurate |
| GET | `/api/admin/llm-accuracy` | Aggregate accuracy rate of LLM explanations |
| GET | `/api/health` | Health check |

### Example: triggering and explaining a failed booking

```bash
# Create an event with 1 seat
curl -X POST localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{"name": "Sold Out Show", "totalSeats": 1}'
# => {"id": 1, "name": "Sold Out Show", "totalSeats": 1, "availableSeats": 1}

# Book the only seat
curl -X POST localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -d '{"eventId": 1, "userId": 1, "seatsBooked": 1}'
# => CONFIRMED

# Try to book again — fails, no seats left
curl -X POST localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -d '{"eventId": 1, "userId": 2, "seatsBooked": 1}'
# => 409 Conflict

# Ask the LLM why (assuming the failed booking has id=2)
curl localhost:8080/api/bookings/2/explain
```

---

## Project Structure

```
src/main/java/com/bookingsystem
├── BookingSystemApplication.java
├── config/
│   └── RestTemplateConfig.java
├── controller/
│   ├── EventController.java
│   ├── BookingController.java
│   ├── AdminController.java
│   └── HealthController.java
├── dto/
│   ├── CreateEventRequest.java / EventResponse.java
│   ├── CreateBookingRequest.java / BookingResponse.java
│   ├── ExplanationResponse.java
│   ├── VerifyExplanationRequest.java
│   └── LlmAccuracyResponse.java
├── exception/
│   ├── EventNotFoundException.java
│   ├── BookingNotFoundException.java
│   ├── InsufficientSeatsException.java
│   ├── BookingConflictException.java
│   └── GlobalExceptionHandler.java
├── model/
│   ├── Event.java          <- the @Version field lives here
│   ├── Booking.java
│   ├── BookingStatus.java
│   └── BookingAttemptLog.java
├── repository/
│   ├── EventRepository.java
│   ├── BookingRepository.java
│   └── BookingAttemptLogRepository.java
├── service/
│   ├── BookingService.java        <- the core of the whole project
│   ├── EventService.java
│   └── BookingExplanationService.java
└── llm/
    ├── LlmClient.java              <- raw HTTP call, 5s timeout
    ├── LlmRawResponse.java         <- strict response contract
    ├── LlmCallFailedException.java
    └── LlmExplanationService.java <- parsing + fallback logic
```

---

## What I'd Improve Next

- **Benchmark pessimistic locking against this** under varying
  contention levels, to have real numbers backing the "optimistic is
  better here" claim rather than just the theoretical argument.
- **Distributed locking (e.g. via Redis)** if this were ever scaled
  across multiple application instances — JPA's `@Version` only protects
  against conflicts within a single database; a multi-instance,
  multi-database-replica setup would need a different mechanism.
- **Rate limiting the `/explain` endpoint** specifically, since it's the
  only one that costs real money per call (LLM API usage) — currently
  nothing stops a user from spamming it, though the per-booking caching
  mitigates this somewhat.
- **A queue between booking attempts and the LLM call** so explanation
  generation can happen asynchronously in bulk rather than synchronously
  on first request, reducing perceived latency for the end user.

---

## Author

**Anuj Gupta**
