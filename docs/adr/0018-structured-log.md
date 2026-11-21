# 0018. The structured log: one writer, a table and rolling files under the same ids, traced on the thread

- Status: accepted
- Date: 2026-11-21

## Context

The server's text log says what went wrong but cannot answer "what happened to this request, this job, this source, or
around this problem". The steps worth recording sit deep in shared code (a connection taken for a write, a provider's
transport, the problem log) that every operation reaches, and a job or agent run continues on other threads, after a
pause or after a restart. The log must stay readable when the database is down or held by a restore, must never slow
or fail the step it describes, and must not become a second copy of research data or of secrets.

## Decision

**Events.**

- An event (`api.log.LogEvent`) is one typed case (`RequestAnswered`, `RequestsCounted`, `ProviderCalled`, `Written`,
  `ProblemRaised`, `Lost`, `ToolCalled`, `WritesCounted`): no free text, no request or answer body, no secret. `trace`
  links the steps of one request or job across threads, jobs, provider calls and agent steps; `label` is the caller's own
  name for its calls (`Histmersion-Trace-Label`), kept beside the trace and never used as one; `subjects`
  (`<kind>:<key>`: job, run, source, problem, request) are what the step concerned, so "everything about job X" is found
  across traces.
- Ids and traces are UUIDv7 (RFC 9562: 48 bits of Unix milliseconds, the version, 74 random bits) written in lower case,
  so they sort by time as text and as the table's `uuid`. The caller supplies the clock reading and the randomness, so the
  minting is pure. A cursor accepts only an id of that shape (one the server minted).
- Times are ISO-8601 in UTC to the microsecond, the precision Postgres keeps, so the table and the files compare the same;
  people are shown the millisecond.
- A request is named by its route template (`GET /api/sources/{citekey}`), never the raw path or the query: a path
  parameter is named, never its value. A provider call records the provider, the host and the status only, never the URL
  (a key may ride in its query). A client's label is cut at 200 characters, a search's text at 500. An MCP call is one
  `POST /mcp` whatever tool it calls, so each tool call is a `ToolCalled` event in the request's trace; the body is never
  read for the log. A problem is its `<group>.<case>` and, for an unexpected one, the id its caller was shown, never its
  text.

**Tracing.**

- The trace scope (`TraceScope`) lives on the calling thread, as the generation pin does: the HTTP layer opens one per
  request (the netty-sync interpreter runs a request's whole logic on one thread, which is what lets the scope and the
  matched route ride on it), and every answer names its trace in `Histmersion-Trace`. Work handed to another thread
  carries the scope along; the server's pinning does it for every job and run, and gives work handed over from no scope
  (a scheduler tick, a start-up) a scope of its own with a new trace.
- A job or run keeps the trace it was created under on its row (`jobs.trace_id`) and takes it back whenever it runs:
  after a restart, a pause or a hand-off its steps join that trace, not the resumer's. A queued agent run executes on a
  pool thread under the permit of the unit it runs in there (never the submitter's, whose block has ended) and under the
  run's own trace.
- The database records every write statement with its trace: each connection is told the calling thread's trace and
  label (`histmersion.trace`, `histmersion.trace_label`) before it is handed out, and only when they differ from what
  that physical connection was told last, so a reused connection costs no round trip. The writer's own connections are
  told no trace and the log's own tables record no writes. Some tables are not recorded (the log's own, the bulk text
  chunks, the caches, PostGIS's and Flyway's); some are counted per minute instead (the agent lease's renewals, the
  quota updates), and the jobs' heartbeats are counted by the statement (`histmersion.log_count`, set for the
  statement's transaction). Both lists are pinned by `StructuredLogDbSuite`: moving a table in or out is a decision.
- Map tiles, static files and the health check (`log.counted`, GETs only) are counted per minute rather than logged one
  by one.

**Storage and the writer.**

- Every event goes to the table `log_events` and, under the same id and in the same JSON, to a rolling JSON-lines file:
  one per UTC day and retention class (`events-<day>.jsonl`, `problems-<day>.jsonl`). The file is written first. The log
  is read from the table; when the database cannot answer within 10 seconds, or the query asks for it (`from=file`), from
  the files, with one set of query rules for both stores (`LogQuery`). A file line that does not read (one being written)
  is skipped. The time filters (`since`, `until`) are read as the lists of activity and jobs read them (`TimeBound`): a
  date, an instant or a time ago (`7d`); a date starts its day in UTC, and as an `until` bound it includes that day.
- One writer is the only way an event reaches either store. Recording never blocks and never fails the step it
  describes: an event goes into a bounded queue (20 000 by default; when full, the loss is counted and one `Lost` event
  says how many), and the writer's own thread takes batches (500 at most, every 200 ms). Write statements the triggers
  staged in `log_writes` are taken in the same transaction as the batch, at least every second, so a failure leaves them
  staged; an event retried from the backlog is not inserted twice. While the database cannot take a batch it waits in a
  bounded backlog (100 000 by default; the file has it already), and what overflows is recorded as `Lost`
  (`DatabaseBacklog`).
- `LogRepo` is the only code that writes the log's tables. Without a log directory nothing is written to or read from
  files, and a file that cannot be read reads as empty. The composition stops the writer, after it has written what it
  holds, before the connection pools close.
- A restore holds the writer at rest: it keeps appending to the files and leaves the database alone; the waiting events
  reach the restored database when it resumes.

**Retention and privacy.**

- The same retention applies to the table and the files: ordinary events are kept `keep-days` (30), problems
  `keep-problem-days` (90), and each store at most `max-bytes` (1 GB), the oldest going first. A 5xx answer, an unreachable
  or 5xx provider, a problem and a loss count as problems. Today's file is never deleted. The table's size cap counts the
  live share of its size at the average row size, because the space a delete frees is reused rather than returned to the
  disk, and deletes at most 1 000 000 rows per run (the next run goes on). Retention runs on the writer's thread every
  hour, and what it deleted goes to the server's text log.
- The log is a debugging aid, not research: a backup's dump leaves its rows out (the tables' definitions go, so a restored
  database has them empty), no bundle carries it, and the read-only role of the SQL escape hatch has no access to its
  tables, since they hold client addresses and search text. That revocation is part of the role's provisioning, which
  the server runs at every start and again after a restore (a restore runs `--no-privileges`).
- The server's text log keeps a problem's line as before: `<group>.<case> <field>=<value> …: <sentence>`, keyed by the
  case's own field names, outside text in full, an internal problem as an error and any other as a warning. Only the
  structured event leaves the text out.

## Consequences

- "What happened" is one query by trace, job, run, source, label or problem, from the API, the `log` tool and
  `histmersion log`; a CLI failure names the trace it was answered under and the command that shows it.
- Each write statement costs a trigger row; the counted tables and statements keep the hot paths at one row per minute.
- Events lost to a full queue or an overlong outage are counted, never silent.

## Code

`modules/api/src/main/scala/com/histmersion/api/log/` (`LogEvent`, `LogPage`, `LogApi`), `api/time/TimeBound.scala`,
`modules/core/src/main/scala/com/histmersion/util/log/` (`LogQuery`, `LogSink`, `TraceScope`),
`modules/config/src/main/scala/com/histmersion/util/log/LogConfig.scala`,
`modules/server/src/main/scala/com/histmersion/util/log/` (`LogWriter`, `LogFiles`, `LogRepo`, `LogRetention`,
`LogService`, `LogData`, `RequestLog`, `TracedDataSource`, `TracedPinning`, `McpToolTracing`),
`util/storage/LineFiles.scala`, `util/problem/ProblemLog.scala`, `util/jobs/JobRuns.scala`, `JobRepo.traceOf`,
`Engine.submit`, `QueryAccess.provision`, `BackupService.rowsLeftOut`, `V111__structured_log.sql`; the CLI's `LogCmd` and
`Client.traceOf`; tests `StructuredLogDbSuite`, `StructuredLogHttpSuite`, `LogFilesSuite`, `LogQuerySuite`,
`LogEventSuite`.
