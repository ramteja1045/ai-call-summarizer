# AI Call Summarizer

A Spring Boot service that takes a sales-call transcript, sends it to an LLM, and stores a
**validated, structured analysis**: a summary, an overall score, per-criterion scores with quoted
evidence, coaching action items and customer sentiment. Built with **Java 21, Spring Boot 3,
PostgreSQL, Flyway and any OpenAI-compatible model**.

## Flow

```
POST /api/calls  ──►  save call (PENDING)  ──►  202 Accepted
                              │ after commit
                              ▼
            @Async worker (virtual threads)
                              │
     prompt = rubric from application.yml + transcript (treated as untrusted)
                              │
                     LLM (JSON mode, temperature 0)
                              │
     parse ─► Bean Validation ─► check every rubric criterion is present
          │ invalid                                 │ valid
          ▼                                         ▼
   one repair prompt ─► still invalid → FAILED   ANALYZED (summary, score, JSON)
                                 │
                 @Scheduled sweep retries FAILED calls (max attempts)
```

## What it demonstrates

- **LLM output is treated as untrusted input.** Responses are parsed into a Java record, checked
  with Bean Validation (score ranges, required fields, allowed sentiment values) and matched
  against the configured rubric before anything is saved.
- **Self-repair.** If the model returns malformed JSON or misses a criterion, the service sends one
  follow-up prompt with the validation error, then fails cleanly if that also fails.
- **Prompt-injection guard.** The transcript is fenced off and the system prompt tells the model to
  ignore instructions inside it.
- **Background processing.** Uploads return `202` immediately. A `@TransactionalEventListener`
  starts analysis only after the row commits, on Java 21 virtual threads. A scheduled sweep retries
  failures up to a limit.
- **Provider-agnostic.** `LlmClient` is an interface. The included client works with OpenAI, Azure
  OpenAI, Groq, Ollama or vLLM by changing `LLM_BASE_URL` and `LLM_MODEL`.
- **Configurable rubric.** Grading criteria live in `application.yml` (an insurance sales framework
  by default), so coaches can change them without a code change.
- **Reporting.** A per-agent leaderboard with average score and call count.
- **Deterministic tests.** A scripted fake LLM plus a real Postgres (Testcontainers) cover the
  success, repair and failure paths.

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/calls` | Submit `{ "agentName": "maria", "transcript": "..." }`. Returns `202` |
| `GET` | `/api/calls/{id}` | Status and full analysis |
| `GET` | `/api/calls?agentName=maria` | An agent's calls, newest first |
| `GET` | `/api/calls/leaderboard` | Average score per agent |

## Run locally

```bash
docker compose up -d
export LLM_API_KEY=sk-...            # or LLM_BASE_URL=http://localhost:11434/v1 LLM_MODEL=llama3.1 for Ollama
mvn spring-boot:run
```

## Tests

```bash
mvn verify   # needs Docker; no API key required (the LLM is faked)
```

## Next steps

- Take audio input via a meeting-bot provider (Recall.ai / LiveKit) plus a speech-to-text step
- Semantic search over past calls with pgvector
- Track cost and latency per LLM call
