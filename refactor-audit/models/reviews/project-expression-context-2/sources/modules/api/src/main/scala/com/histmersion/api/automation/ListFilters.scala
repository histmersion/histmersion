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

  type UnescapeHexResult = UnescapeHexResult.T
  object UnescapeHexResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnescapeHexAt = UnescapeHexAt.T
  object UnescapeHexAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ValuesResult = ValuesResult.T
  object ValuesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValuesVs = ValuesVs.T
  object ValuesVs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WordsResult = WordsResult.T
  object WordsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OneResult = OneResult.T
  object OneResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OneV = OneV.T
  object OneV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KnownResultPart1 = KnownResultPart1.T
  object KnownResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KnownResultPart2 = KnownResultPart2.T
  object KnownResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KnownWhat = KnownWhat.T
  object KnownWhat extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KnownVs = KnownVs.T
  object KnownVs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KnownKnown = KnownKnown.T
  object KnownKnown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeBoundResultPart1 = TimeBoundResultPart1.T
  object TimeBoundResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeBoundS = TimeBoundS.T
  object TimeBoundS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeBoundEnd = TimeBoundEnd.T
  object TimeBoundEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeResultPart1 = RangeResultPart1.T
  object RangeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeSince = RangeSince.T
  object RangeSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RangeUntil = RangeUntil.T
  object RangeUntil extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EscapeResult = EscapeResult.T
  object EscapeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EscapeS = EscapeS.T
  object EscapeS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnescapeResult = UnescapeResult.T
  object UnescapeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnescapeS = UnescapeS.T
  object UnescapeS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EncodeFacetsResult = EncodeFacetsResult.T
  object EncodeFacetsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EncodeFacetsFsPart1 = EncodeFacetsFsPart1.T
  object EncodeFacetsFsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EncodeFacetsFsPart2Part1 = EncodeFacetsFsPart2Part1.T
  object EncodeFacetsFsPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EncodeFacetsFsPart2Part2 = EncodeFacetsFsPart2Part2.T
  object EncodeFacetsFsPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeFacetsResultPart1 = DecodeFacetsResultPart1.T
  object DecodeFacetsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeFacetsResultPart2Part1 = DecodeFacetsResultPart2Part1.T
  object DecodeFacetsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeFacetsResultPart2Part2 = DecodeFacetsResultPart2Part2.T
  object DecodeFacetsResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DecodeFacetsS = DecodeFacetsS.T
  object DecodeFacetsS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetLineResult = FacetLineResult.T
  object FacetLineResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetLineLabel = FacetLineLabel.T
  object FacetLineLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetLineCountsPart1 = FacetLineCountsPart1.T
  object FacetLineCountsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FacetLineCountsPart2 = FacetLineCountsPart2.T
  object FacetLineCountsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val FacetsHeader = "X-Facet-Counts"

  val FacetMax = 40

  def values(vs: List[com.histmersion.api.automation.ListFilters.ValuesVs]): List[com.histmersion.api.automation.ListFilters.ValuesResult] =
    (vs.flatMap((_).value.split(',')).map(_.trim).filter(_.nonEmpty).distinct).map(refactorValue => _root_.com.histmersion.api.automation.ListFilters.ValuesResult(refactorValue))

  def words(groups: List[String]*): Option[com.histmersion.api.automation.ListFilters.WordsResult] =
    (Option(groups.filter(_.nonEmpty).map(_.mkString("/")).mkString(" ")).filter(_.nonEmpty)).map(refactorValue22 => _root_.com.histmersion.api.automation.ListFilters.WordsResult(refactorValue22))

  def one(v: Option[com.histmersion.api.automation.ListFilters.OneV]): Option[com.histmersion.api.automation.ListFilters.OneResult] = (v.map((_).value.trim).filter(_.nonEmpty)).map(refactorValue2 => _root_.com.histmersion.api.automation.ListFilters.OneResult(refactorValue2))

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

    def qualifier: Option[com.histmersion.api.automation.ListFilters.ApprovalFilter.QualifierResult] =
      val n = normalized
      words(n.state.orElse(Some(Agents.ApprovalState.Open.wire)).filter(_ != "all").toList, (n.kind).map(refactorValue3 => (refactorValue3).value))

    def options: List[(com.histmersion.api.automation.ListFilters.ApprovalFilter.OptionsResultPart1, Option[com.histmersion.api.automation.ListFilters.ApprovalFilter.OptionsResultPart2])] =
      val n = normalized
      List("state" -> n.state) ++ n.kind.map(k => "kind" -> Some(k)) ++ List(
        "subject" -> n.subject,
        "goal" -> n.goal
      ) ++
        n.decidedBy.map(d => "decided-by" -> Some(d))

  object ApprovalFilter:
    type QualifierResult = QualifierResult.T
    object QualifierResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OptionsResultPart1 = OptionsResultPart1.T
    object OptionsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OptionsResultPart2 = OptionsResultPart2.T
    object OptionsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type State = State.T
    object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Subject = Subject.T
    object Subject extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DecidedBy = DecidedBy.T
    object DecidedBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
    def qualifier: Option[com.histmersion.api.automation.ListFilters.RunFilter.QualifierResult] = { val n = normalized; words((n.status).map(refactorValue5 => (refactorValue5).value), (n.pipeline).map(refactorValue27 => (refactorValue27).value)) }
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
    def options: List[(com.histmersion.api.automation.ListFilters.RunFilter.OptionsResultPart1, Option[com.histmersion.api.automation.ListFilters.RunFilter.OptionsResultPart2])] =
      val n = normalized
      n.status.map("status" -> Some(_)) ++ n.pipeline.map("pipeline" -> Some(_)) ++ n.manager.map(
        "manager" -> Some(_)
      ) ++
        List("goal" -> n.goal) ++ n.model.map("model" -> Some(_)) ++ n.provider.map("provider" -> Some(_)) ++
        n.origin.map("origin" -> Some(_)) ++ List("since" -> n.since, "until" -> n.until, "subject" -> n.subject)

  object RunFilter:
    type QualifierResult = QualifierResult.T
    object QualifierResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OptionsResultPart1 = OptionsResultPart1.T
    object OptionsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OptionsResultPart2 = OptionsResultPart2.T
    object OptionsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Pipeline = Pipeline.T
    object Pipeline extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Manager = Manager.T
    object Manager extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Provider = Provider.T
    object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Origin = Origin.T
    object Origin extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
    def qualifier: Option[com.histmersion.api.automation.ListFilters.JobFilter.QualifierResult] = { val n = normalized; words((n.status).map(refactorValue14 => (refactorValue14).value), (n.kind).map(refactorValue15 => (refactorValue15).value)) }
    def normalized: JobFilter =
      JobFilter(
        values(status).map(_.toLowerCase),
        values(kind).map(_.toLowerCase),
        one(since),
        one(until),
        one(subject)
      )
    def options: List[(com.histmersion.api.automation.ListFilters.JobFilter.OptionsResultPart1, Option[com.histmersion.api.automation.ListFilters.JobFilter.OptionsResultPart2])] =
      val n = normalized
      n.status.map("status" -> Some(_)) ++ n.kind.map("kind" -> Some(_)) ++ List(
        "since" -> n.since,
        "until" -> n.until,
        "subject" -> n.subject
      )

  object JobFilter:
    type QualifierResult = QualifierResult.T
    object QualifierResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OptionsResultPart1 = OptionsResultPart1.T
    object OptionsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OptionsResultPart2 = OptionsResultPart2.T
    object OptionsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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

  def known(what: com.histmersion.api.automation.ListFilters.KnownWhat, vs: List[com.histmersion.api.automation.ListFilters.KnownVs], known: Seq[com.histmersion.api.automation.ListFilters.KnownKnown]): Either[com.histmersion.api.automation.ListFilters.KnownResultPart1, List[com.histmersion.api.automation.ListFilters.KnownResultPart2]] =
    vs.find(v => !known.contains(v)) match
      case Some(v) => Left(_root_.com.histmersion.api.automation.ListFilters.KnownResultPart1(s"unknown $what '$v' (one of: ${known.mkString(", ")})"))
      case None    => Right(vs)

  private val Relative = """(\d{1,6})\s*([dhm])""".r
  private val DateOnly = """(\d{4}-\d{2}-\d{2})""".r

  def timeBound(s: com.histmersion.api.automation.ListFilters.TimeBoundS, end: com.histmersion.api.automation.ListFilters.TimeBoundEnd, now: Instant): Either[com.histmersion.api.automation.ListFilters.TimeBoundResultPart1, Instant] =
    (s).value.trim.toLowerCase match
      case Relative(n, unit) =>
        val secs = n.toLong * (unit match { case "d" => 86400L; case "h" => 3600L; case _ => 60L })
        Right(now.minusSeconds(secs))
      case DateOnly(d) =>
        scala.util
          .Try(LocalDate.parse(d))
          .toOption
          .map(day => (if ((end) == _root_.com.histmersion.api.automation.ListFilters.TimeBoundEnd(true)) then day.plusDays(1) else day).atStartOfDay(ZoneOffset.UTC).toInstant)
          .toRight(_root_.com.histmersion.api.automation.ListFilters.TimeBoundResultPart1(s"'$s' is not a date"))
      case _ =>
        scala.util
          .Try(Instant.parse((s).value.trim))
          .toOption
          .toRight(
            _root_.com.histmersion.api.automation.ListFilters.TimeBoundResultPart1(s"'$s' is neither a date (2026-10-01), an instant (2026-10-01T12:00:00Z) nor a time ago (7d, 12h, 30m)")
          )

  def range(
      since: Option[com.histmersion.api.automation.ListFilters.RangeSince],
      until: Option[com.histmersion.api.automation.ListFilters.RangeUntil],
      now: Instant
  ): Either[com.histmersion.api.automation.ListFilters.RangeResultPart1, (Option[Instant], Option[Instant])] =
    for
      s <- since.fold(Right(None))(timeBound(_, end = _root_.com.histmersion.api.automation.ListFilters.TimeBoundEnd(false), now).map(Some(_)).left.map(m => s"since: $m"))
      u <- until.fold(Right(None))(timeBound(_, end = _root_.com.histmersion.api.automation.ListFilters.TimeBoundEnd(true), now).map(Some(_)).left.map(m => s"until: $m"))
      _ <- Either.cond(s.zip(u).forall((a, b) => a.isBefore(b)), (), "since must lie before until")
    yield (s, u)

  /** `%`, `,`, `=` and anything outside printable ASCII become `%XX` (UTF-8), so any value survives a header.
    */
  def escape(s: com.histmersion.api.automation.ListFilters.EscapeS): com.histmersion.api.automation.ListFilters.EscapeResult =
    val b = new StringBuilder
    (s).value.getBytes("UTF-8").foreach { byte =>
      val c = (byte & 0xff).toChar
      if c > ' ' && c < 0x7f && c != '%' && c != ',' && c != '=' then b.append(c)
      else b.append(f"%%${byte & 0xff}%02X")
    }
    _root_.com.histmersion.api.automation.ListFilters.EscapeResult(b.toString)

  def unescape(s: com.histmersion.api.automation.ListFilters.UnescapeS): com.histmersion.api.automation.ListFilters.UnescapeResult =
    if !(s).value.contains('%') then s
    else
      val out = new java.io.ByteArrayOutputStream
      def hex(at: com.histmersion.api.automation.ListFilters.UnescapeHexAt): Option[com.histmersion.api.automation.ListFilters.UnescapeHexResult] =
        Option
          .when((at).value + 3 <= (s).value.length)((s).value.substring((at).value + 1, (at).value + 3))
          .filter(_.forall(c => Character.digit(c, 16) >= 0))
          .map(_root_.com.histmersion.api.automation.ListFilters.UnescapeHexResult(Integer.parseInt(_, 16)))
      var i = 0
      while i < (s).value.length do
        (if s(i) == '%' then hex(_root_.com.histmersion.api.automation.ListFilters.UnescapeHexAt(i)) else None) match
          case Some(b) => out.write((b).value); i += 3
          case None    => (s).value.substring(i, i + 1).getBytes("UTF-8").foreach(x => out.write(x.toInt)); i += 1
      _root_.com.histmersion.api.automation.ListFilters.UnescapeResult(new String(out.toByteArray, "UTF-8"))

  def encodeFacets(fs: List[(com.histmersion.api.automation.ListFilters.EncodeFacetsFsPart1, List[(com.histmersion.api.automation.ListFilters.EncodeFacetsFsPart2Part1, com.histmersion.api.automation.ListFilters.EncodeFacetsFsPart2Part2)])]): com.histmersion.api.automation.ListFilters.EncodeFacetsResult =
    fs.flatMap((f, vs) => vs.map((v, n) => s"$f=${escape(v)}=$n")).mkString(",")

  def decodeFacets(s: com.histmersion.api.automation.ListFilters.DecodeFacetsS): List[(com.histmersion.api.automation.ListFilters.DecodeFacetsResultPart1, List[(com.histmersion.api.automation.ListFilters.DecodeFacetsResultPart2Part1, com.histmersion.api.automation.ListFilters.DecodeFacetsResultPart2Part2)])] =
    val triples = (s).value.split(',').toList.map(_.trim).filter(_.nonEmpty).flatMap { t =>
      t.split('=') match
        case Array(f, v, n) if f.nonEmpty => n.trim.toIntOption.map(c => (f.trim, unescape(_root_.com.histmersion.api.automation.ListFilters.UnescapeS(v)), c))
        case _                            => None
    }
    triples.map(_._1).distinct.map(f => f -> triples.collect { case (`f`, v, n) => v -> n })

  def facetLine(label: com.histmersion.api.automation.ListFilters.FacetLineLabel, counts: List[(com.histmersion.api.automation.ListFilters.FacetLineCountsPart1, com.histmersion.api.automation.ListFilters.FacetLineCountsPart2)]): com.histmersion.api.automation.ListFilters.FacetLineResult =
    if counts.isEmpty then _root_.com.histmersion.api.automation.ListFilters.FacetLineResult("")
    else s"$label: ${counts.map((v, n) => s"$v ${Paging.grouped(n)}").mkString(" · ")}"
