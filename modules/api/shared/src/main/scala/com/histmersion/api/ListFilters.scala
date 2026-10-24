package com.histmersion.api

import sttp.tapir.*

import java.time.{Instant, LocalDate, ZoneOffset}

/** The filters of the three newest-first lists — approvals, agent runs, background jobs — and the
  * facet counts that go with them. One shape on every surface: the REST query (`kind=…&kind=…`, or
  * `kind=a,b`), the CLI's options, the MCP tools' parameters and the web windows' URL all name the
  * same fields, and the server filters (and pages, [[Paging]]) — never the client.
  *
  * A multi-valued field matches ANY of its values; different fields must ALL match. The counts of a
  * facet (`kind`, `pipeline`, …) are taken over every OTHER filter but not its own, so a person who
  * picked one kind still sees how many each other kind would add; the per-state counts are the same
  * idea ([[Paging.CountsHeader]]). They cross in the [[FacetsHeader]] response header, so the JSON
  * body stays the plain array it always was. Pure. */
object ListFilters:

  /** `kind=goal-plan=64,kind=edge=3,pipeline=review-article=211` — facet, value, count; the value is
    * escaped ([[escape]]) because a model (`codex:gpt-6.1-sol`) or a manager (`seed:x`) is not a plain word. */
  val FacetsHeader = "X-Facet-Counts"

  /** At most this many values of one facet are counted (largest first): a header, not a report. */
  val FacetMax = 40

  /** The values of a multi-valued query parameter, given repeated, comma-separated or both: trimmed,
    * empties dropped, each once, in the order given. */
  def values(vs: List[String]): List[String] =
    vs.flatMap(_.split(',')).map(_.trim).filter(_.nonEmpty).distinct

  /** The words a heading puts before its noun: each group's values joined by `/`, the groups by a space. */
  def words(groups: List[String]*): Option[String] =
    Option(groups.filter(_.nonEmpty).map(_.mkString("/")).mkString(" ")).filter(_.nonEmpty)

  /** A single-valued one: trimmed, `None` when blank. */
  def one(v: Option[String]): Option[String] = v.map(_.trim).filter(_.nonEmpty)

  // ── who decided an approval ─────────────────────────────────────────────────────────────────────

  /** Who decided an approval. `Autopilot` = the checked auto approvals (their note starts
    * [[AutopilotNote]]); `Auto` = applied the moment it was proposed, by an `agents.approvals.*` mode
    * of `auto` (its result starts [[AutoResult]]); `Person` = everything else that is decided. */
  enum DecidedBy(val wire: String, val describe: String):
    case Person    extends DecidedBy("person", "decided by a person")
    case Autopilot extends DecidedBy("autopilot", "decided by the autopilot's checks")
    case Auto      extends DecidedBy("auto", "applied when proposed (an auto approval mode)")

  object DecidedBy:
    /** The note every autopilot decision starts with (`AutopilotPolicy.note`). */
    val AutopilotNote = "autopilot:"
    /** The result of a proposal applied at once (`Approvals.propose`, an `auto` mode). */
    val AutoResult = "applied automatically"

    def parse(s: String): Option[DecidedBy] = values.find(_.wire == s.trim.toLowerCase)

    /** Who decided an approval in `state` with this `note` and `result`; `None` while it is open. The
      * SQL that filters and counts by it says the same (`AgentRepo`). */
    def of(state: String, note: Option[String], result: Option[String]): Option[DecidedBy] =
      if state == Agents.ApprovalState.Open.wire then None
      else if note.exists(_.startsWith(AutopilotNote)) then Some(Autopilot)
      else if note.isEmpty && result.exists(_.startsWith(AutoResult)) then Some(Auto)
      else Some(Person)

  // ── the filters ─────────────────────────────────────────────────────────────────────────────────

  /** `GET /api/agents/approvals`: `state` = open (the default), approved, rejected, failed or `all`;
    * `kind` = any of the approval kinds; `subject` = text found in what it is about (a goal or article
    * slug, a manager, either citekey of an edge, or the goal of the run that proposed it); `goal` = one
    * goal's (its own plan, or proposed by a run for it); `decidedBy` = person, autopilot or auto. */
  final case class ApprovalFilter(
      state: Option[String] = None,
      kind: List[String] = Nil,
      subject: Option[String] = None,
      goal: Option[String] = None,
      decidedBy: List[String] = Nil):
    def normalized: ApprovalFilter =
      ApprovalFilter(one(state).map(_.toLowerCase), values(kind).map(_.toLowerCase), one(subject), one(goal),
        values(decidedBy).map(_.toLowerCase))
    /** What a listing's heading says it shows: `open goal-plan/edge` approvals (no state = open). */
    def qualifier: Option[String] =
      val n = normalized
      words(n.state.orElse(Some(Agents.ApprovalState.Open.wire)).filter(_ != "all").toList, n.kind)
    /** The query as `--option value` pairs (a CLI's next-page line), unset ones left out. */
    def options: List[(String, Option[String])] =
      val n = normalized
      List("state" -> n.state) ++ n.kind.map(k => "kind" -> Some(k)) ++ List("subject" -> n.subject, "goal" -> n.goal) ++
        n.decidedBy.map(d => "decided-by" -> Some(d))

  /** `GET /api/agents/runs`: any of these `status`es, `pipeline`s, process `manager`s (`review`,
    * `seed:<goal>`), `origin`s (person, scheduler, run, ingest, manager), `model`s (`provider:model`, as a
    * step reported it) or `provider`s (`codex`, `claude-code`, …); one `goal`'s; started `since` /
    * `until` ([[timeBound]]); `subject` = text in its target (a citekey, an article or goal slug). */
  final case class RunFilter(
      status: List[String] = Nil,
      pipeline: List[String] = Nil,
      manager: List[String] = Nil,
      goal: Option[String] = None,
      model: List[String] = Nil,
      provider: List[String] = Nil,
      origin: List[String] = Nil,
      since: Option[String] = None,
      until: Option[String] = None,
      subject: Option[String] = None):
    /** `failed review-article` runs. */
    def qualifier: Option[String] = { val n = normalized; words(n.status, n.pipeline) }
    def normalized: RunFilter =
      RunFilter(values(status).map(_.toLowerCase), values(pipeline).map(_.toLowerCase), values(manager), one(goal),
        values(model), values(provider).map(_.toLowerCase), values(origin).map(_.toLowerCase), one(since), one(until), one(subject))
    def options: List[(String, Option[String])] =
      val n = normalized
      n.status.map("status" -> Some(_)) ++ n.pipeline.map("pipeline" -> Some(_)) ++ n.manager.map("manager" -> Some(_)) ++
        List("goal" -> n.goal) ++ n.model.map("model" -> Some(_)) ++ n.provider.map("provider" -> Some(_)) ++
        n.origin.map("origin" -> Some(_)) ++ List("since" -> n.since, "until" -> n.until, "subject" -> n.subject)

  /** `GET /api/jobs`: any of these `status`es and `kind`s (ingest, reocr, media-import, agent, …);
    * started `since` / `until` ([[timeBound]]); `subject` = text in what it is about (its citekey, an agent
    * run's target or pipeline, its stored detail) or the start of its id. */
  final case class JobFilter(
      status: List[String] = Nil,
      kind: List[String] = Nil,
      since: Option[String] = None,
      until: Option[String] = None,
      subject: Option[String] = None):
    /** `failed ingest` jobs. */
    def qualifier: Option[String] = { val n = normalized; words(n.status, n.kind) }
    def normalized: JobFilter =
      JobFilter(values(status).map(_.toLowerCase), values(kind).map(_.toLowerCase), one(since), one(until), one(subject))
    def options: List[(String, Option[String])] =
      val n = normalized
      n.status.map("status" -> Some(_)) ++ n.kind.map("kind" -> Some(_)) ++ List("since" -> n.since, "until" -> n.until, "subject" -> n.subject)

  // ── the query parameters (one definition for the server, the CLI and the web client) ─────────────

  val approvalQuery: EndpointInput[ApprovalFilter] =
    query[Option[String]]("state").description("open (default), approved, rejected, failed or all")
      .and(query[List[String]]("kind").description(s"any of: ${Agents.ApprovalKind.values.map(_.wire).mkString(", ")} (repeat, or comma-separate)"))
      .and(query[Option[String]]("subject").description("text in what it is about: a slug, a manager, a citekey, the proposing run's goal"))
      .and(query[Option[String]]("goal").description("one goal's: its plan, or proposed by a run for it"))
      .and(query[List[String]]("decidedBy").description(s"any of: ${DecidedBy.values.map(_.wire).mkString(", ")}"))
      .mapTo[ApprovalFilter]

  val runQuery: EndpointInput[RunFilter] =
    query[List[String]]("status").description(s"any of: ${Agents.RunState.values.map(_.wire).mkString(", ")}")
      .and(query[List[String]]("pipeline").description(s"any of: ${Agents.Pipeline.values.map(_.wire).mkString(", ")}"))
      .and(query[List[String]]("manager").description("the process managers that dispatched it (review, seed:<goal>, …)"))
      .and(query[Option[String]]("goal").description("one goal's runs"))
      .and(query[List[String]]("model").description("provider:model, as a step reported it"))
      .and(query[List[String]]("provider").description("codex, claude-code, …"))
      .and(query[List[String]]("origin").description(s"any of: ${Agents.Origin.values.map(_.wire).mkString(", ")}"))
      .and(query[Option[String]]("since").description("started at or after: 2026-10-01, an ISO instant, or 7d / 12h / 30m ago"))
      .and(query[Option[String]]("until").description("started before the end of: 2026-10-01, or an ISO instant"))
      .and(query[Option[String]]("subject").description("text in its target: a citekey, an article or goal slug"))
      .mapTo[RunFilter]

  val jobQuery: EndpointInput[JobFilter] =
    query[List[String]]("status").description(s"any of: ${JobState.values.map(_.wire).mkString(", ")}")
      .and(query[List[String]]("kind").description("any of the job kinds: ingest, reocr, agent, media-import, …"))
      .and(query[Option[String]]("since").description("started at or after: 2026-10-01, an ISO instant, or 7d / 12h / 30m ago"))
      .and(query[Option[String]]("until").description("started before the end of: 2026-10-01, or an ISO instant"))
      .and(query[Option[String]]("subject").description("text in what it is about (citekey, target, detail), or the start of its id"))
      .mapTo[JobFilter]

  // ── validation ──────────────────────────────────────────────────────────────────────────────────

  /** Every value of `vs` among `known`, or a message naming the first that is not. */
  def known(what: String, vs: List[String], known: Seq[String]): Either[String, List[String]] =
    vs.find(v => !known.contains(v)) match
      case Some(v) => Left(s"unknown $what '$v' (one of: ${known.mkString(", ")})")
      case None    => Right(vs)

  private val Relative = """(\d{1,6})\s*([dhm])""".r
  private val DateOnly = """(\d{4}-\d{2}-\d{2})""".r

  /** A time bound, as an instant: `2026-10-01` (that day's start in UTC — or, with `end`, the start of
    * the NEXT day, so `until` a date includes it), an ISO instant (`2026-10-01T12:00:00Z`), or a time
    * ago, `7d` / `12h` / `30m` before `now`. Left: anything else. */
  def timeBound(s: String, end: Boolean, now: Instant): Either[String, Instant] =
    s.trim.toLowerCase match
      case Relative(n, unit) =>
        val secs = n.toLong * (unit match { case "d" => 86400L; case "h" => 3600L; case _ => 60L })
        Right(now.minusSeconds(secs))
      case DateOnly(d) =>
        scala.util.Try(LocalDate.parse(d)).toOption
          .map(day => (if end then day.plusDays(1) else day).atStartOfDay(ZoneOffset.UTC).toInstant)
          .toRight(s"'$s' is not a date")
      case _ =>
        scala.util.Try(Instant.parse(s.trim)).toOption
          .toRight(s"'$s' is neither a date (2026-10-01), an instant (2026-10-01T12:00:00Z) nor a time ago (7d, 12h, 30m)")

  /** `since`/`until` resolved against `now` — Left when one cannot be read, or the range is empty. */
  def range(since: Option[String], until: Option[String], now: Instant): Either[String, (Option[Instant], Option[Instant])] =
    for
      s <- since.fold(Right(None))(timeBound(_, end = false, now).map(Some(_)).left.map(m => s"since: $m"))
      u <- until.fold(Right(None))(timeBound(_, end = true, now).map(Some(_)).left.map(m => s"until: $m"))
      _ <- Either.cond(s.zip(u).forall((a, b) => a.isBefore(b)), (), "since must lie before until")
    yield (s, u)

  // ── the facet header ─────────────────────────────────────────────────────────────────────────────

  /** `%`, `,`, `=` and anything outside printable ASCII become `%XX` (UTF-8), so any value survives a
    * header. */
  def escape(s: String): String =
    val b = new StringBuilder
    s.getBytes("UTF-8").foreach { byte =>
      val c = (byte & 0xff).toChar
      if c > ' ' && c < 0x7f && c != '%' && c != ',' && c != '=' then b.append(c)
      else b.append(f"%%${byte & 0xff}%02X")
    }
    b.toString

  def unescape(s: String): String =
    if !s.contains('%') then s
    else
      val out = new java.io.ByteArrayOutputStream
      def hex(at: Int): Option[Int] =
        Option.when(at + 3 <= s.length)(s.substring(at + 1, at + 3))
          .filter(_.forall(c => Character.digit(c, 16) >= 0)).map(Integer.parseInt(_, 16))
      var i = 0 // `var`: a hand-rolled scan over the escapes
      while i < s.length do
        (if s(i) == '%' then hex(i) else None) match
          case Some(b) => out.write(b); i += 3
          case None    => s.substring(i, i + 1).getBytes("UTF-8").foreach(x => out.write(x.toInt)); i += 1
      new String(out.toByteArray, "UTF-8")

  /** The facets as the header carries them; an empty facet is left out. */
  def encodeFacets(fs: List[(String, List[(String, Int)])]): String =
    fs.flatMap((f, vs) => vs.map((v, n) => s"$f=${escape(v)}=$n")).mkString(",")

  /** The inverse of [[encodeFacets]], in the order sent; a malformed triple is skipped, not fatal. */
  def decodeFacets(s: String): List[(String, List[(String, Int)])] =
    val triples = s.split(',').toList.map(_.trim).filter(_.nonEmpty).flatMap { t =>
      t.split('=') match
        case Array(f, v, n) if f.nonEmpty => n.trim.toIntOption.map(c => (f.trim, unescape(v), c))
        case _                            => None
    }
    triples.map(_._1).distinct.map(f => f -> triples.collect { case (`f`, v, n) => v -> n })

  /** `kind: goal-plan 64 · article-publish 30 · edge 3` — one facet for a listing; empty when it has none. */
  def facetLine(label: String, counts: List[(String, Int)]): String =
    if counts.isEmpty then ""
    else s"$label: ${counts.map((v, n) => s"$v ${Paging.grouped(n)}").mkString(" · ")}"
