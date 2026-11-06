package com.histmersion.api.automation
import com.histmersion.api.exchange.MergeIssue
import sttp.tapir.*

import java.time.{Instant, LocalDate, ZoneOffset}
import com.histmersion.api.Paging
import com.histmersion.api.jobs.JobState
import com.histmersion.api.given

/** The facet counts travel in the [[FacetsHeader]] response header, so the JSON body stays the plain array older
  * clients read.
  */
object ListFilters:
  type EncodedFacets = EncodedFacets.T
  object EncodedFacets extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetLine = FacetLine.T
  object FacetLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetName = FacetName.T
  object FacetName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OptionLabel = OptionLabel.T
  object OptionLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Qualifier = Qualifier.T
  object Qualifier extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Term = Term.T
  object Term extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeBound = TimeBound.T
  object TimeBound extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



  val FacetsHeader = "X-Facet-Counts"

  val FacetMax = 40

  def values(vs: List[com.histmersion.api.automation.ListFilters.Term]): List[com.histmersion.api.automation.ListFilters.Term] =
    (vs.flatMap((_).value.split(',')).map(_.trim).filter(_.nonEmpty).distinct).map(refactorValue => _root_.com.histmersion.api.automation.ListFilters.Term(refactorValue))

  def words(groups: List[String]*): Option[com.histmersion.api.automation.ListFilters.Qualifier] =
    (Option(groups.filter(_.nonEmpty).map(_.mkString("/")).mkString(" ")).filter(_.nonEmpty)).map(refactorValue22 => _root_.com.histmersion.api.automation.ListFilters.Qualifier(refactorValue22))

  def one(v: Option[com.histmersion.api.automation.ListFilters.Term]): Option[com.histmersion.api.automation.ListFilters.Term] = (v.map((_).value.trim).filter(_.nonEmpty)).map(refactorValue2 => _root_.com.histmersion.api.automation.ListFilters.Term(refactorValue2))

  export com.histmersion.api.automation.Approval.DecidedBy as DecidedBy

  final case class ApprovalFilter(
      state: Option[ApprovalFilter.State] = None,
      kind: List[ApprovalFilter.Kind] = Nil,
      subject: Option[ApprovalFilter.Subject] = None,
      goal: Option[com.histmersion.api.automation.Goal.Slug] = None,
      decidedBy: List[ApprovalFilter.DecidedBy] = Nil
  ):
    def normalized: ApprovalFilter =
      ApprovalFilter(
        one(state).map(_.toLowerCase),
        values(kind).map(_.toLowerCase),
        one(subject),
        one(goal),
        values(decidedBy).map(_.toLowerCase)
      )

    def qualifier: Option[com.histmersion.api.automation.ListFilters.Qualifier] =
      val n = normalized
      words(n.state.orElse(Some(Agents.ApprovalState.Open.wire)).filter(_ != "all").toList, (n.kind).map(refactorValue3 => (refactorValue3).value))

    def options: List[(com.histmersion.api.automation.ListFilters.Term, Option[com.histmersion.api.automation.ListFilters.OptionLabel])] =
      val n = normalized
      List("state" -> n.state) ++ n.kind.map(k => "kind" -> Some(k)) ++ List(
        "subject" -> n.subject,
        "goal" -> n.goal
      ) ++
        n.decidedBy.map(d => "decided-by" -> Some(d))

  object ApprovalFilter:

    type Subject = Subject.T
    object Subject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class RunFilter(
      status: List[RunFilter.Status] = Nil,
      pipeline: List[RunFilter.Pipeline] = Nil,
      manager: List[RunFilter.Manager] = Nil,
      goal: Option[com.histmersion.api.automation.Goal.Slug] = None,
      model: List[RunFilter.Model] = Nil,
      provider: List[RunFilter.Provider] = Nil,
      origin: List[RunFilter.Origin] = Nil,
      since: Option[RunFilter.Since] = None,
      until: Option[RunFilter.Until] = None,
      subject: Option[RunFilter.Subject] = None
  ):
    def qualifier: Option[com.histmersion.api.automation.ListFilters.Qualifier] = { val n = normalized; words((n.status).map(refactorValue5 => (refactorValue5).value), (n.pipeline).map(refactorValue27 => (refactorValue27).value)) }
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
    def options: List[(com.histmersion.api.automation.ListFilters.Term, Option[com.histmersion.api.automation.ListFilters.OptionLabel])] =
      val n = normalized
      n.status.map("status" -> Some(_)) ++ n.pipeline.map("pipeline" -> Some(_)) ++ n.manager.map(
        "manager" -> Some(_)
      ) ++
        List("goal" -> n.goal) ++ n.model.map("model" -> Some(_)) ++ n.provider.map("provider" -> Some(_)) ++
        n.origin.map("origin" -> Some(_)) ++ List("since" -> n.since, "until" -> n.until, "subject" -> n.subject)

  object RunFilter:

    type Pipeline = Pipeline.T
    object Pipeline extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Manager = Manager.T
    object Manager extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Provider = Provider.T
    object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Since = Since.T
    object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Until = Until.T
    object Until extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Subject = Subject.T
    object Subject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class JobFilter(
      status: List[JobFilter.Status] = Nil,
      kind: List[JobFilter.Kind] = Nil,
      since: Option[JobFilter.Since] = None,
      until: Option[JobFilter.Until] = None,
      subject: Option[JobFilter.Subject] = None
  ):
    def qualifier: Option[com.histmersion.api.automation.ListFilters.Qualifier] = { val n = normalized; words((n.status).map(refactorValue14 => (refactorValue14).value), (n.kind).map(refactorValue15 => (refactorValue15).value)) }
    def normalized: JobFilter =
      JobFilter(
        values(status).map(_.toLowerCase),
        values(kind).map(_.toLowerCase),
        one(since),
        one(until),
        one(subject)
      )
    def options: List[(com.histmersion.api.automation.ListFilters.Term, Option[com.histmersion.api.automation.ListFilters.OptionLabel])] =
      val n = normalized
      n.status.map("status" -> Some(_)) ++ n.kind.map("kind" -> Some(_)) ++ List(
        "since" -> n.since,
        "until" -> n.until,
        "subject" -> n.subject
      )

  object JobFilter:

    type Since = Since.T
    object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Until = Until.T
    object Until extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Subject = Subject.T
    object Subject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

  def known(what: com.histmersion.api.automation.ListFilters.FacetName, vs: List[com.histmersion.api.automation.ListFilters.Term], known: Seq[com.histmersion.api.automation.ListFilters.Term]): Either[com.histmersion.api.automation.ListFilters.Problem, List[com.histmersion.api.automation.ListFilters.Term]] =
    vs.find(v => !known.contains(v)) match
      case Some(v) => Left(_root_.com.histmersion.api.automation.ListFilters.Problem(s"unknown $what '$v' (one of: ${known.mkString(", ")})"))
      case None    => Right((vs).map(refactorValue34 => _root_.com.histmersion.api.automation.ListFilters.Term((refactorValue34).value)))

  private val Relative = """(\d{1,6})\s*([dhm])""".r
  private val DateOnly = """(\d{4}-\d{2}-\d{2})""".r

  def timeBound(s: com.histmersion.api.automation.ListFilters.TimeBound, end: Boolean, now: Instant): Either[com.histmersion.api.automation.ListFilters.Problem, Instant] =
    (s).value.trim.toLowerCase match
      case Relative(n, unit) =>
        val secs = n.toLong * (unit match { case "d" => 86400L; case "h" => 3600L; case _ => 60L })
        Right(now.minusSeconds(secs))
      case DateOnly(d) =>
        scala.util
          .Try(LocalDate.parse(d))
          .toOption
          .map(day => (if ((end) == (true)) then day.plusDays(1) else day).atStartOfDay(ZoneOffset.UTC).toInstant)
          .toRight(_root_.com.histmersion.api.automation.ListFilters.Problem(s"'$s' is not a date"))
      case _ =>
        scala.util
          .Try(Instant.parse((s).value.trim))
          .toOption
          .toRight(
            _root_.com.histmersion.api.automation.ListFilters.Problem(s"'$s' is neither a date (2026-10-01), an instant (2026-10-01T12:00:00Z) nor a time ago (7d, 12h, 30m)")
          )

  def range(
      since: Option[com.histmersion.api.automation.ListFilters.TimeBound],
      until: Option[com.histmersion.api.automation.ListFilters.TimeBound],
      now: Instant
  ): Either[com.histmersion.api.automation.ListFilters.Problem, (Option[Instant], Option[Instant])] =
    for
      s <- since.fold(Right(None))(timeBound(_root_.com.histmersion.api.automation.ListFilters.TimeBound((_).value), end = (false), now).map(Some(_)).left.map(m => s"since: $m"))
      u <- until.fold(Right(None))(timeBound(_root_.com.histmersion.api.automation.ListFilters.TimeBound((_).value), end = (true), now).map(Some(_)).left.map(m => s"until: $m"))
      _ <- Either.cond(s.zip(u).forall((a, b) => a.isBefore(b)), (), "since must lie before until")
    yield (s, u)

  /** `%`, `,`, `=` and anything outside printable ASCII become `%XX` (UTF-8), so any value survives a header.
    */
  def escape(s: String): String =
    val b = new StringBuilder
    (s).getBytes("UTF-8").foreach { byte =>
      val c = (byte & 0xff).toChar
      if c > ' ' && c < 0x7f && c != '%' && c != ',' && c != '=' then b.append(c)
      else b.append(f"%%${byte & 0xff}%02X")
    }
    (b.toString)

  def unescape(s: String): String =
    if !(s).contains('%') then ((s))
    else
      val out = new java.io.ByteArrayOutputStream
      def hex(at: Int): Option[Int] =
        Option
          .when((at) + 3 <= (s).value.length)((s).substring((at).value + 1, (at).value + 3))
          .filter(_.forall(c => Character.digit(c, 16) >= 0))
          .map((Integer.parseInt(_, 16)))
      var i = 0
      while i < (s).length do
        (if s(i) == '%' then hex((i)) else None) match
          case Some(b) => out.write((b)); i += 3
          case None    => (s).substring(i, i + 1).getBytes("UTF-8").foreach(x => out.write(x.toInt)); i += 1
      (new String(out.toByteArray, "UTF-8"))

  def encodeFacets(fs: List[(com.histmersion.api.automation.ListFilters.FacetName, List[(com.histmersion.api.automation.ListFilters.Term, com.histmersion.api.RowCount)])]): com.histmersion.api.automation.ListFilters.EncodedFacets =
    _root_.com.histmersion.api.automation.ListFilters.EncodedFacets(fs.flatMap((f, vs) => vs.map((v, n) => s"$f=${escape(((v).value))}=$n")).mkString(","))

  def decodeFacets(s: com.histmersion.api.automation.ListFilters.EncodedFacets): List[(com.histmersion.api.automation.ListFilters.FacetName, List[(com.histmersion.api.automation.ListFilters.Term, com.histmersion.api.RowCount)])] =
    val triples = (s).value.split(',').toList.map(_.trim).filter(_.nonEmpty).flatMap { t =>
      t.split('=') match
        case Array(f, v, n) if f.nonEmpty => n.trim.toIntOption.map(c => (f.trim, unescape((v)), c))
        case _                            => None
    }
    triples.map(_._1).distinct.map(f => { val refactorTuple9 = f -> triples.collect { case (`f`, v, n) => v -> n }; (_root_.com.histmersion.api.automation.ListFilters.FacetName(refactorTuple9._1), (refactorTuple9._2).map(refactorValue62 => { val refactorTuple10 = refactorValue62; (_root_.com.histmersion.api.automation.ListFilters.Term((refactorTuple10._1)), _root_.com.histmersion.api.RowCount(refactorTuple10._2)) })) })

  def facetLine(label: com.histmersion.api.automation.ListFilters.FacetName, counts: List[(com.histmersion.api.automation.ListFilters.Term, com.histmersion.api.RowCount)]): com.histmersion.api.automation.ListFilters.FacetLine =
    if counts.isEmpty then _root_.com.histmersion.api.automation.ListFilters.FacetLine("")
    else s"$label: ${counts.map((v, n) => s"$v ${Paging.grouped(n)}").mkString(" · ")}"
