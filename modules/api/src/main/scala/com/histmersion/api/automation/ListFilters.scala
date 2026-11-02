package com.histmersion.api.automation
import sttp.tapir.*

import java.time.{Instant, LocalDate, ZoneOffset}
import com.histmersion.api.Paging
import com.histmersion.api.jobs.JobState
import com.histmersion.api.given
import com.histmersion.util.data.exchange.Issue

/** The facet counts travel in the [[FacetsHeader]] response header, so the JSON body stays the plain array older
  * clients read.
  */
object ListFilters:

  val FacetsHeader = "X-Facet-Counts"

  val FacetMax = 40

  def values(vs: List[String]): List[String] =
    vs.flatMap(_.split(',')).map(_.trim).filter(_.nonEmpty).distinct

  def words(groups: List[String]*): Option[String] =
    Option(groups.filter(_.nonEmpty).map(_.mkString("/")).mkString(" ")).filter(_.nonEmpty)

  def one(v: Option[String]): Option[String] = v.map(_.trim).filter(_.nonEmpty)

  export com.histmersion.domain.automation.Approval.DecidedBy as DecidedBy

  final case class ApprovalFilter(
      state: Option[String] = None,
      kind: List[String] = Nil,
      subject: Option[String] = None,
      goal: Option[String] = None,
      decidedBy: List[String] = Nil
  ):
    def normalized: ApprovalFilter =
      ApprovalFilter(
        one(state).map(_.toLowerCase),
        values(kind).map(_.toLowerCase),
        one(subject),
        one(goal),
        values(decidedBy).map(_.toLowerCase)
      )

    def qualifier: Option[String] =
      val n = normalized
      words(n.state.orElse(Some(Agents.ApprovalState.Open.wire)).filter(_ != "all").toList, n.kind)

    def options: List[(String, Option[String])] =
      val n = normalized
      List("state" -> n.state) ++ n.kind.map(k => "kind" -> Some(k)) ++ List(
        "subject" -> n.subject,
        "goal" -> n.goal
      ) ++
        n.decidedBy.map(d => "decided-by" -> Some(d))

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
      subject: Option[String] = None
  ):
    def qualifier: Option[String] = { val n = normalized; words(n.status, n.pipeline) }
    def normalized: RunFilter =
      RunFilter(
        values(status).map(_.toLowerCase),
        values(pipeline).map(_.toLowerCase),
        values(manager),
        one(goal),
        values(model),
        values(provider).map(_.toLowerCase),
        values(origin).map(_.toLowerCase),
        one(since),
        one(until),
        one(subject)
      )
    def options: List[(String, Option[String])] =
      val n = normalized
      n.status.map("status" -> Some(_)) ++ n.pipeline.map("pipeline" -> Some(_)) ++ n.manager.map(
        "manager" -> Some(_)
      ) ++
        List("goal" -> n.goal) ++ n.model.map("model" -> Some(_)) ++ n.provider.map("provider" -> Some(_)) ++
        n.origin.map("origin" -> Some(_)) ++ List("since" -> n.since, "until" -> n.until, "subject" -> n.subject)

  final case class JobFilter(
      status: List[String] = Nil,
      kind: List[String] = Nil,
      since: Option[String] = None,
      until: Option[String] = None,
      subject: Option[String] = None
  ):
    def qualifier: Option[String] = { val n = normalized; words(n.status, n.kind) }
    def normalized: JobFilter =
      JobFilter(
        values(status).map(_.toLowerCase),
        values(kind).map(_.toLowerCase),
        one(since),
        one(until),
        one(subject)
      )
    def options: List[(String, Option[String])] =
      val n = normalized
      n.status.map("status" -> Some(_)) ++ n.kind.map("kind" -> Some(_)) ++ List(
        "since" -> n.since,
        "until" -> n.until,
        "subject" -> n.subject
      )

  val approvalQuery: EndpointInput[ApprovalFilter] =
    query[Option[String]]("state")
      .description("open (default), approved, rejected, failed or all")
      .and(
        query[List[String]]("kind").description(
          s"any of: ${Agents.ApprovalKind.values.map(_.wire).mkString(", ")} (repeat, or comma-separate)"
        )
      )
      .and(
        query[Option[String]]("subject")
          .description("text in what it is about: a slug, a manager, a citekey, the proposing run's goal")
      )
      .and(query[Option[String]]("goal").description("one goal's: its plan, or proposed by a run for it"))
      .and(query[List[String]]("decidedBy").description(s"any of: ${DecidedBy.values.map(_.wire).mkString(", ")}"))
      .mapTo[ApprovalFilter]

  val runQuery: EndpointInput[RunFilter] =
    query[List[String]]("status")
      .description(s"any of: ${Agents.RunState.values.map(_.wire).mkString(", ")}")
      .and(query[List[String]]("pipeline").description(s"any of: ${Agents.Pipeline.values.map(_.wire).mkString(", ")}"))
      .and(
        query[List[String]]("manager").description("the process managers that dispatched it (review, seed:<goal>, …)")
      )
      .and(query[Option[String]]("goal").description("one goal's runs"))
      .and(query[List[String]]("model").description("provider:model, as a step reported it"))
      .and(query[List[String]]("provider").description("codex, claude-code, …"))
      .and(query[List[String]]("origin").description(s"any of: ${Agents.Origin.values.map(_.wire).mkString(", ")}"))
      .and(
        query[Option[String]]("since")
          .description("started at or after: 2026-10-01, an ISO instant, or 7d / 12h / 30m ago")
      )
      .and(query[Option[String]]("until").description("started before the end of: 2026-10-01, or an ISO instant"))
      .and(query[Option[String]]("subject").description("text in its target: a citekey, an article or goal slug"))
      .mapTo[RunFilter]

  val jobQuery: EndpointInput[JobFilter] =
    query[List[String]]("status")
      .description(s"any of: ${JobState.values.map(_.wire).mkString(", ")}")
      .and(query[List[String]]("kind").description("any of the job kinds: ingest, reocr, agent, media-import, …"))
      .and(
        query[Option[String]]("since")
          .description("started at or after: 2026-10-01, an ISO instant, or 7d / 12h / 30m ago")
      )
      .and(query[Option[String]]("until").description("started before the end of: 2026-10-01, or an ISO instant"))
      .and(
        query[Option[String]]("subject")
          .description("text in what it is about (citekey, target, detail), or the start of its id")
      )
      .mapTo[JobFilter]

  def known(what: String, vs: List[String], known: Seq[String]): Either[String, List[String]] =
    vs.find(v => !known.contains(v)) match
      case Some(v) => Left(s"unknown $what '$v' (one of: ${known.mkString(", ")})")
      case None    => Right(vs)

  private val Relative = """(\d{1,6})\s*([dhm])""".r
  private val DateOnly = """(\d{4}-\d{2}-\d{2})""".r

  def timeBound(s: String, end: Boolean, now: Instant): Either[String, Instant] =
    s.trim.toLowerCase match
      case Relative(n, unit) =>
        val secs = n.toLong * (unit match { case "d" => 86400L; case "h" => 3600L; case _ => 60L })
        Right(now.minusSeconds(secs))
      case DateOnly(d) =>
        scala.util
          .Try(LocalDate.parse(d))
          .toOption
          .map(day => (if end then day.plusDays(1) else day).atStartOfDay(ZoneOffset.UTC).toInstant)
          .toRight(s"'$s' is not a date")
      case _ =>
        scala.util
          .Try(Instant.parse(s.trim))
          .toOption
          .toRight(
            s"'$s' is neither a date (2026-10-01), an instant (2026-10-01T12:00:00Z) nor a time ago (7d, 12h, 30m)"
          )

  def range(
      since: Option[String],
      until: Option[String],
      now: Instant
  ): Either[String, (Option[Instant], Option[Instant])] =
    for
      s <- since.fold(Right(None))(timeBound(_, end = false, now).map(Some(_)).left.map(m => s"since: $m"))
      u <- until.fold(Right(None))(timeBound(_, end = true, now).map(Some(_)).left.map(m => s"until: $m"))
      _ <- Either.cond(s.zip(u).forall((a, b) => a.isBefore(b)), (), "since must lie before until")
    yield (s, u)

  /** `%`, `,`, `=` and anything outside printable ASCII become `%XX` (UTF-8), so any value survives a header.
    */
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
        Option
          .when(at + 3 <= s.length)(s.substring(at + 1, at + 3))
          .filter(_.forall(c => Character.digit(c, 16) >= 0))
          .map(Integer.parseInt(_, 16))
      var i = 0
      while i < s.length do
        (if s(i) == '%' then hex(i) else None) match
          case Some(b) => out.write(b); i += 3
          case None    => s.substring(i, i + 1).getBytes("UTF-8").foreach(x => out.write(x.toInt)); i += 1
      new String(out.toByteArray, "UTF-8")

  def encodeFacets(fs: List[(String, List[(String, Int)])]): String =
    fs.flatMap((f, vs) => vs.map((v, n) => s"$f=${escape(v)}=$n")).mkString(",")

  def decodeFacets(s: String): List[(String, List[(String, Int)])] =
    val triples = s.split(',').toList.map(_.trim).filter(_.nonEmpty).flatMap { t =>
      t.split('=') match
        case Array(f, v, n) if f.nonEmpty => n.trim.toIntOption.map(c => (f.trim, unescape(v), c))
        case _                            => None
    }
    triples.map(_._1).distinct.map(f => f -> triples.collect { case (`f`, v, n) => v -> n })

  def facetLine(label: String, counts: List[(String, Int)]): String =
    if counts.isEmpty then ""
    else s"$label: ${counts.map((v, n) => s"$v ${Paging.grouped(n)}").mkString(" · ")}"
