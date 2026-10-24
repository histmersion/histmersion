package com.histmersion.core

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode

/** The REST contract of stream 2.8 — **lint**, **queue** and the read-only **query** (plan §5.2).
  *
  * Kept in its own file next to [[Api]] (which appends `OpsApi.all` to `Api.all`) so this stream owns
  * one file instead of editing the shared endpoint list in three places. Same conventions as `Api`:
  * definitions only, no logic; the server interprets them and the CLI derives its sttp client from
  * the very same values.
  */
object OpsApi:

  // explicit imports: a wildcard would shadow tapir's `query`/`path` with this object's own members
  import Lint.{LintFixResult, LintReport}
  import Query.{QueryRequest, QueryResult, SchemaTables}
  import Queue.{QueueAdd, QueueImport, QueueImported, QueueItem, QueueListing, QueuePatch}

  private lazy val base = endpoint.in("api").errorOut(jsonBody[ApiError])

  /** 404 when the addressed row does not exist, 400 for anything else (an unknown rule, an unknown
    * list, a rejected statement). Mirrors `Api`'s write errors, minus the 412 nobody needs here. */
  private lazy val opsErrors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[ApiError])(ApiError.hasCode(Write.CodeNotFound)),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  // ── lint ─────────────────────────────────────────────────────────────────────────────────────

  /** `GET /api/lint[?rule=…]*` — read-only health check over the whole store. Repeat `rule` to run a
    * subset; omit it for every rule. An unknown rule is a 400, never a silently clean report. */
  lazy val lint: PublicEndpoint[List[String], ApiError, LintReport, Any] =
    endpoint.get.in("api" / "lint").in(sttp.tapir.query[List[String]]("rule"))
      .errorOut(opsErrors).out(jsonBody[LintReport])

  /** `POST /api/lint/fix[?rule=…]*` — the repairs that are safe to make mechanically: unify unambiguous
    * short edge targets, and re-chunk sources whose full-text index is missing. Bounded per call
    * (`limits.reindex-chars`); loop while `remaining > 0`. With `rule=finding-geocode` it also moves
    * the findings that rule lists, and with `rule=finding-unplaced` it gives the findings that rule
    * lists their point; `rule=place-kind` reclassifies and merges the places it lists and
    * `rule=place-grid-point` moves the places it lists — each only when named, because it changes the map. */
  lazy val lintFix: PublicEndpoint[List[String], ApiError, LintFixResult, Any] =
    base.post.in("lint" / "fix").in(sttp.tapir.query[List[String]]("rule")).out(jsonBody[LintFixResult])

  /** `POST /api/lint/markdown` — the formatting check over Markdown that is not stored yet (a file an
    * agent is about to `article put`); with `fix`, the repaired text comes back in `text`. */
  lazy val lintMarkdown: PublicEndpoint[FormatCheck, ApiError, FormatReport, Any] =
    base.post.in("lint" / "markdown").in(jsonBody[FormatCheck]).out(jsonBody[FormatReport])

  // ── queue ────────────────────────────────────────────────────────────────────────────────────

  /** `PATCH /api/queue/items/{id}` — declared BEFORE the `{list}` routes so `items` can never be
    * read as a list name. */
  lazy val queuePatch: PublicEndpoint[(Int, QueuePatch), ApiError, QueueItem, Any] =
    endpoint.patch.in("api" / "queue" / "items" / path[Int]("id")).in(jsonBody[QueuePatch])
      .errorOut(opsErrors).out(jsonBody[QueueItem])

  lazy val queueDelete: PublicEndpoint[Int, ApiError, QueueItem, Any] =
    endpoint.delete.in("api" / "queue" / "items" / path[Int]("id"))
      .errorOut(opsErrors).out(jsonBody[QueueItem])

  /** `GET /api/queue/{list}` — open items (`?all=true` for done and note rows too). */
  lazy val queueList: PublicEndpoint[(String, Boolean), ApiError, QueueListing, Any] =
    endpoint.get.in("api" / "queue" / path[String]("list")).in(sttp.tapir.query[Boolean]("all").default(false))
      .errorOut(opsErrors).out(jsonBody[QueueListing])

  lazy val queueAdd: PublicEndpoint[(String, QueueAdd), ApiError, QueueItem, Any] =
    endpoint.post.in("api" / "queue" / path[String]("list")).in(jsonBody[QueueAdd])
      .errorOut(opsErrors).out(jsonBody[QueueItem])

  /** The list rendered as its `meta/<FILE>.md` — what `histmersion queue export <dir>` writes to disk
    * (the CLI has no database, so the SERVER renders and the CLI only writes the bytes). */
  lazy val queueExport: PublicEndpoint[String, ApiError, String, Any] =
    endpoint.get.in("api" / "queue" / path[String]("list") / "export")
      .errorOut(opsErrors).out(stringBody)

  /** The inverse: parse a `meta/<FILE>.md` back into rows. `replace` (default) clears the list first,
    * so importing the same file twice is idempotent. */
  lazy val queueImport: PublicEndpoint[(String, QueueImport), ApiError, QueueImported, Any] =
    endpoint.post.in("api" / "queue" / path[String]("list") / "import").in(jsonBody[QueueImport])
      .errorOut(opsErrors).out(jsonBody[QueueImported])

  /** `meta/GOALS.md` — every research goal with its links, schedules and approved plan, rendered by
    * [[GoalsExport]]. Written beside the queue files by `queue export` / `export`; read-only (there is
    * no import). Under `export/` rather than `goals/` so it can never be read as a goal slug. */
  lazy val goalsExport: PublicEndpoint[Unit, ApiError, String, Any] =
    base.get.in("export" / "goals").out(stringBody)

  // ── read-only query ──────────────────────────────────────────────────────────────────────────

  /** `POST /api/query` — one statement, inside a `READ ONLY` transaction with a statement timeout
    * and a row cap. Postgres refuses every write; a rejected statement comes back as a 400 carrying
    * its message. */
  lazy val query: PublicEndpoint[QueryRequest, ApiError, QueryResult, Any] =
    base.post.in("query").in(jsonBody[QueryRequest]).out(jsonBody[QueryResult])

  /** `GET /api/schema/tables` — the live `information_schema` shape, so a tool description or a
    * prompt never states a schema that has drifted. */
  lazy val schemaTables: PublicEndpoint[Unit, ApiError, SchemaTables, Any] =
    base.get.in("schema" / "tables").out(jsonBody[SchemaTables])

  /** Order matters: the two `queue/items/{id}` routes precede the `queue/{list}` ones. */
  lazy val all: List[AnyEndpoint] = List(lint, lintFix, lintMarkdown, queuePatch, queueDelete, queueExport, queueImport,
    queueList, queueAdd, goalsExport, query, schemaTables)
