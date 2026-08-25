package histdb.server

import histdb.core.*
import histdb.core.Lint.*
import histdb.core.Query.*
import histdb.core.Queue.*
import histdb.db.{LintRepo, QueryRepo, QueueRepo, SourceRepo}

/** The ONE implementation of the stream-2.8 operations — lint, the bookkeeping queues and the
  * read-only query (plan §5.2). Reached by REST through [[OpsRoutes]] and by MCP through
  * [[OpsTools]]; the CLI reaches them over HTTP, like every other command.
  *
  * Held as `Handlers.ops` so the composition root still builds exactly one object graph.
  */
final class OpsHandlers(deps: Deps):
  private val ds  = deps.ds
  private val cfg = deps.cfg

  // ── lint ─────────────────────────────────────────────────────────────────────────────────────

  /** `GET /api/lint` — read-only. An unknown rule name is a 400: a typo must never read as a clean
    * corpus. */
  def lint(rules: List[String]): Either[ApiError, LintReport] =
    Lint.select(rules)
      .left.map(m => ApiError(m))
      .map(rs => LintRepo.run(ds, rs, cfg.lint.maxItems, cfg.lint.suggestions))

  /** `POST /api/lint/fix` — the two repairs that are safe to make without a human: unify unambiguous
    * short edge targets, and re-chunk sources whose full-text index is missing. Both are bounded per
    * call; `remaining > 0` means "call me again". */
  def lintFix(): LintFixResult =
    val resolved = SourceRepo.resolveEdges(ds)
    val r        = LintRepo.reindexStale(ds, cfg.limits.chunkChars, cfg.limits.reindexChars)
    val left     = if r.remaining > 0 then s", ${r.remaining} still stale" else ""
    LintFixResult(
      applied = List(
        s"resolve-edges: $resolved edge target(s) rewritten",
        s"reindex: ${r.sources} source(s) → ${r.chunks} chunk(s)$left"),
      edgesResolved = resolved,
      sourcesReindexed = r.sources,
      chunksWritten = r.chunks,
      remaining = r.remaining)

  /** Lint, optionally after applying the safe fixes — what `histdb lint --fix` and the MCP tool do,
    * so the report the caller sees is the state AFTER the repair. */
  def lintWithFix(rules: List[String], fix: Boolean): Either[ApiError, LintReport] =
    if !fix then lint(rules)
    else
      val f = lintFix()
      lint(rules).map(_.copy(fixed = f.applied))

  // ── queue ────────────────────────────────────────────────────────────────────────────────────

  private def kindOf(list: String): Either[ApiError, Kind] =
    Kind.parse(list).toRight(ApiError(s"unknown queue list '$list' — one of: ${Kind.all.mkString(", ")}"))

  def queueList(list: String, all: Boolean): Either[ApiError, QueueListing] =
    kindOf(list).map(k => QueueListing(k.label, QueueRepo.list(ds, k, all).toList))

  /** A LOG item with no topic joins today's entry (`## 2026-08-25`), which is how an appended
    * worklog line behaves; ACQUIRE and TASKS items simply have no topic until one is given. */
  def queueAdd(list: String, in: QueueAdd): Either[ApiError, QueueItem] =
    kindOf(list).flatMap { k =>
      if in.text.trim.isEmpty then Left(ApiError("queue item text is empty"))
      else
        val topic = in.topic.map(_.trim).filter(_.nonEmpty) match
          case some @ Some(_)          => some
          case None if k == Kind.Log   => Some(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString)
          case None                    => None
        Right(QueueRepo.add(ds, k, in.copy(topic = topic)))
    }

  def queuePatch(id: Int, p: QueuePatch): Either[ApiError, QueueItem] = QueueRepo.patch(ds, id, p)

  def queueDelete(id: Int): Either[ApiError, QueueItem] = QueueRepo.delete(ds, id)

  /** The list rendered as its `meta/<FILE>.md`. Notes are included — they ARE the file's prose. */
  def queueExport(list: String): Either[ApiError, String] =
    kindOf(list).map(k => Queue.render(k, QueueRepo.list(ds, k, all = true).toList))

  def queueImport(list: String, req: QueueImport): Either[ApiError, QueueImported] =
    kindOf(list).map { k =>
      val rows            = Queue.parse(k, req.text)
      val (added, gone)   = QueueRepo.replaceAll(ds, k, rows, req.replace)
      val notes           = rows.count(_.status.contains(Queue.Note))
      QueueImported(k.label, added - notes, notes, gone)
    }

  /** Every list at once — what `histdb export <dir>` writes alongside the source notes. */
  def queueFiles(): List[(String, String)] =
    Kind.values.toList.map(k => k.file -> Queue.render(k, QueueRepo.list(ds, k, all = true).toList))

  // ── read-only query ──────────────────────────────────────────────────────────────────────────

  /** The ONLY caller that runs on `deps.queryDs` — the dedicated read-only role. Everything else in
    * this file uses the application `ds`, and must: the escape hatch is the one place arbitrary SQL
    * arrives. */
  def query(req: QueryRequest): Either[ApiError, QueryResult] =
    QueryRepo.run(deps.queryDs, req.sql, cfg.query.timeoutMs, cfg.query.maxRows, cfg.query.maxCellChars)
      .left.map(m => ApiError(m))

  def schemaTables: SchemaTables = SchemaTables(QueryRepo.tables(ds))

  /** The `query` tool's description, generated from the live schema. Falls back to a schema-less
    * description if the database cannot be read while the tool list is being built — a server that
    * cannot answer `information_schema` has bigger problems than a thin tool description. */
  def queryToolDescription: String =
    Query.toolDescription(scala.util.Try(schemaTables).getOrElse(SchemaTables(Nil)))
