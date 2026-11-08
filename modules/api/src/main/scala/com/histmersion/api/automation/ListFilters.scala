package com.histmersion
package api.automation
import com.histmersion.api.exchange.MergeIssue
import sttp.tapir.*

import java.time.{Instant, LocalDate, ZoneOffset}
import com.histmersion.api.Paging
import com.histmersion.api.jobs.JobState
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.RowCount

/** The facet counts travel in the [[FacetsHeader]] response header, so the JSON body stays the plain array older
  * clients read.
  */
object ListFilters:
  type EncodedFacets = EncodedFacets.T
  object EncodedFacets extends RefinedType[String, Pure]
  type FacetLine = FacetLine.T
  object FacetLine extends RefinedType[String, Pure]
  type FacetName = FacetName.T
  object FacetName extends RefinedType[String, Pure]
  type OptionLabel = OptionLabel.T
  object OptionLabel extends RefinedType[String, Pure]
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]
  type Qualifier = Qualifier.T
  object Qualifier extends RefinedType[String, Pure]
  type Term = Term.T
  object Term extends RefinedType[String, Pure]
  type TimeBound = TimeBound.T
  object TimeBound extends RefinedType[String, Pure]


  type KnownResultPart1 = Problem
  val KnownResultPart1: Problem.type = Problem
  type TimeBoundResultPart1 = Problem
  val TimeBoundResultPart1: Problem.type = Problem
  type RangeResultPart1 = Problem
  val RangeResultPart1: Problem.type = Problem
  type FacetLineResult = FacetLine
  val FacetLineResult: FacetLine.type = FacetLine

  val FacetsHeader = "X-Facet-Counts"

  val FacetMax = 40

  def values(vs: List[Term]): List[Term] =
    (vs.flatMap((_).value.split(',')).map(_.trim).filter(_.nonEmpty).distinct).map(refactorValue => Term(refactorValue))

  def words(groups: List[String]*): Option[Qualifier] =
    (Option(groups.filter(_.nonEmpty).map(_.mkString("/")).mkString(" ")).filter(_.nonEmpty)).map(refactorValue22 => Qualifier(refactorValue22))

  def one(v: Option[Term]): Option[Term] = (v.map((_).value.trim).filter(_.nonEmpty)).map(refactorValue2 => Term(refactorValue2))

  private def many[A](vs: List[A], lower: Boolean = false)(text: A => String, make: String => A): List[A] =
    vs.flatMap(a => text(a).split(',').toList).map(_.trim).filter(_.nonEmpty).distinct.map(s => make(if lower then s.toLowerCase else s))

  private def single[A](v: Option[A], lower: Boolean = false)(text: A => String, make: String => A): Option[A] =
    v.map(a => text(a).trim).filter(_.nonEmpty).map(s => make(if lower then s.toLowerCase else s))

  private def entry(key: String, v: Option[String]): (Term, Option[OptionLabel]) =
    Term(key) -> v.map(OptionLabel(_))

  export Approval.DecidedBy as DecidedBy

  final case class ApprovalFilter(
      state: Option[ApprovalFilter.State] = None,
      kind: List[ApprovalFilter.Kind] = Nil,
      subject: Option[ApprovalFilter.Subject] = None,
      goal: Option[Goal.Slug] = None,
      decidedBy: List[ApprovalFilter.DecidedBy] = Nil
  ):
    def normalized: ApprovalFilter =
      ApprovalFilter(
        single(state, true)(_.value, ApprovalFilter.State(_)),
        many(kind, true)(_.value, ApprovalFilter.Kind(_)),
        single(subject)(_.value, ApprovalFilter.Subject(_)),
        single(goal)(_.value, Goal.Slug(_)),
        many(decidedBy, true)(_.value, ApprovalFilter.DecidedBy(_))
      )

    def qualifier: Option[Qualifier] =
      val n = normalized
      words(n.state.orElse(Some(Agents.ApprovalState.Open.wire)).map(_.value).filter(_ != "all").toList, (n.kind).map(refactorValue3 => (refactorValue3).value))

    def options: List[(Term, Option[OptionLabel])] =
      val n = normalized
      List(entry("state", n.state.map(_.value))) ++ n.kind.map(k => entry("kind", Some(k.value))) ++ List(
        entry("subject", n.subject.map(_.value)),
        entry("goal", n.goal.map(_.value))
      ) ++
        n.decidedBy.map(d => entry("decided-by", Some(d.value)))

  object ApprovalFilter:
    export ListFilters.{OptionLabel as OptionsResultPart2}

    export Approval.State.{Wire as State}
    export Approval.Kind.{Wire as Kind}
    type Subject = Subject.T
    object Subject extends RefinedType[String, Pure]
    export Approval.DecidedBy.{Wire as DecidedBy}

  final case class RunFilter(
      status: List[RunFilter.Status] = Nil,
      pipeline: List[RunFilter.Pipeline] = Nil,
      manager: List[RunFilter.Manager] = Nil,
      goal: Option[Goal.Slug] = None,
      model: List[RunFilter.Model] = Nil,
      provider: List[RunFilter.Provider] = Nil,
      origin: List[RunFilter.Origin] = Nil,
      since: Option[RunFilter.Since] = None,
      until: Option[RunFilter.Until] = None,
      subject: Option[RunFilter.Subject] = None
  ):
    def qualifier: Option[Qualifier] = { val n = normalized; words((n.status).map(refactorValue5 => (refactorValue5).value), (n.pipeline).map(refactorValue27 => (refactorValue27).value)) }
    def normalized: RunFilter =
      RunFilter(
        many(status, true)(_.value, RunFilter.Status(_)),
        many(pipeline, true)(_.value, RunFilter.Pipeline(_)),
        many(manager)(_.value, RunFilter.Manager(_)),
        single(goal)(_.value, Goal.Slug(_)),
        many(model)(_.value, RunFilter.Model(_)),
        many(provider, true)(_.value, RunFilter.Provider(_)),
        many(origin, true)(_.value, RunFilter.Origin(_)),
        single(since)(_.value, RunFilter.Since(_)),
        single(until)(_.value, RunFilter.Until(_)),
        single(subject)(_.value, RunFilter.Subject(_))
      )
    def options: List[(Term, Option[OptionLabel])] =
      val n = normalized
      n.status.map(v => entry("status", Some(v.value))) ++ n.pipeline.map(v => entry("pipeline", Some(v.value))) ++ n.manager.map(
        v => entry("manager", Some(v.value))
      ) ++
        List(entry("goal", n.goal.map(_.value))) ++ n.model.map(v => entry("model", Some(v.value))) ++ n.provider.map(v => entry("provider", Some(v.value))) ++
        n.origin.map(v => entry("origin", Some(v.value))) ++ List(entry("since", n.since.map(_.value)), entry("until", n.until.map(_.value)), entry("subject", n.subject.map(_.value)))

  object RunFilter:
    export ListFilters.{OptionLabel as OptionsResultPart2}

    type Status = Status.T
    object Status extends RefinedType[String, Pure]
    type Pipeline = Pipeline.T
    object Pipeline extends RefinedType[String, Pure]
    type Manager = Manager.T
    object Manager extends RefinedType[String, Pure]
    type Model = Model.T
    object Model extends RefinedType[String, Pure]
    type Provider = Provider.T
    object Provider extends RefinedType[String, Pure]
    export Agents.Origin.{Wire as Origin}
    type Since = Since.T
    object Since extends RefinedType[String, Pure]
    type Until = Until.T
    object Until extends RefinedType[String, Pure]
    type Subject = Subject.T
    object Subject extends RefinedType[String, Pure]

  final case class JobFilter(
      status: List[JobFilter.Status] = Nil,
      kind: List[JobFilter.Kind] = Nil,
      since: Option[JobFilter.Since] = None,
      until: Option[JobFilter.Until] = None,
      subject: Option[JobFilter.Subject] = None
  ):
    def qualifier: Option[Qualifier] = { val n = normalized; words((n.status).map(refactorValue14 => (refactorValue14).value), (n.kind).map(refactorValue15 => (refactorValue15).value)) }
    def normalized: JobFilter =
      JobFilter(
        many(status, true)(_.value, JobFilter.Status(_)),
        many(kind, true)(_.value, JobFilter.Kind(_)),
        single(since)(_.value, JobFilter.Since(_)),
        single(until)(_.value, JobFilter.Until(_)),
        single(subject)(_.value, JobFilter.Subject(_))
      )
    def options: List[(Term, Option[OptionLabel])] =
      val n = normalized
      n.status.map(v => entry("status", Some(v.value))) ++ n.kind.map(v => entry("kind", Some(v.value))) ++ List(
        entry("since", n.since.map(_.value)),
        entry("until", n.until.map(_.value)),
        entry("subject", n.subject.map(_.value))
      )

  object JobFilter:
    export ListFilters.{OptionLabel as OptionsResultPart2}

    export JobState.{Wire as Status}
    type Kind = JobKinds.Wire
    val Kind: JobKinds.Wire.type = JobKinds.Wire
    type Since = Since.T
    object Since extends RefinedType[String, Pure]
    type Until = Until.T
    object Until extends RefinedType[String, Pure]
    type Subject = Subject.T
    object Subject extends RefinedType[String, Pure]

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
      .map { case (state, kind, subject, goal, decidedBy) => ApprovalFilter(state.map(ApprovalFilter.State(_)), kind.map(ApprovalFilter.Kind(_)), subject.map(ApprovalFilter.Subject(_)), goal.map(Goal.Slug(_)), decidedBy.map(ApprovalFilter.DecidedBy(_))) }(v => (v.state.map(_.value), v.kind.map(_.value), v.subject.map(_.value), v.goal.map(_.value), v.decidedBy.map(_.value)))

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
      .map { case (status, pipeline, manager, goal, model, provider, origin, since, until, subject) => RunFilter(status.map(RunFilter.Status(_)), pipeline.map(RunFilter.Pipeline(_)), manager.map(RunFilter.Manager(_)), goal.map(Goal.Slug(_)), model.map(RunFilter.Model(_)), provider.map(RunFilter.Provider(_)), origin.map(RunFilter.Origin(_)), since.map(RunFilter.Since(_)), until.map(RunFilter.Until(_)), subject.map(RunFilter.Subject(_))) }(v => (v.status.map(_.value), v.pipeline.map(_.value), v.manager.map(_.value), v.goal.map(_.value), v.model.map(_.value), v.provider.map(_.value), v.origin.map(_.value), v.since.map(_.value), v.until.map(_.value), v.subject.map(_.value)))

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
      .map { case (status, kind, since, until, subject) => JobFilter(status.map(JobFilter.Status(_)), kind.map(JobFilter.Kind(_)), since.map(JobFilter.Since(_)), until.map(JobFilter.Until(_)), subject.map(JobFilter.Subject(_))) }(v => (v.status.map(_.value), v.kind.map(_.value), v.since.map(_.value), v.until.map(_.value), v.subject.map(_.value)))

  def known(what: FacetName, vs: List[Term], known: Seq[Term]): Either[Problem, List[Term]] =
    vs.find(v => !known.contains(v)) match
      case Some(v) => Left(Problem(s"unknown $what '$v' (one of: ${known.mkString(", ")})"))
      case None    => Right((vs).map(refactorValue34 => Term((refactorValue34).value)))

  private val Relative = """(\d{1,6})\s*([dhm])""".r
  private val DateOnly = """(\d{4}-\d{2}-\d{2})""".r

  def timeBound(s: TimeBound, end: Boolean, now: Instant): Either[Problem, Instant] =
    (s).value.trim.toLowerCase match
      case Relative(n, unit) =>
        val secs = n.toLong * (unit match { case "d" => 86400L; case "h" => 3600L; case _ => 60L })
        Right(now.minusSeconds(secs))
      case DateOnly(d) =>
        scala.util
          .Try(LocalDate.parse(d))
          .toOption
          .map(day => (if ((end) == (true)) then day.plusDays(1) else day).atStartOfDay(ZoneOffset.UTC).toInstant)
          .toRight(Problem(s"'$s' is not a date"))
      case _ =>
        scala.util
          .Try(Instant.parse((s).value.trim))
          .toOption
          .toRight(
            Problem(s"'$s' is neither a date (2026-10-01), an instant (2026-10-01T12:00:00Z) nor a time ago (7d, 12h, 30m)")
          )

  def range(
      since: Option[TimeBound],
      until: Option[TimeBound],
      now: Instant
  ): Either[Problem, (Option[Instant], Option[Instant])] =
    for
      s <- since.fold(Right(None))(timeBound(_, end = (false), now).map(Some(_)).left.map(m => Problem(s"since: $m")))
      u <- until.fold(Right(None))(timeBound(_, end = (true), now).map(Some(_)).left.map(m => Problem(s"until: $m")))
      _ <- Either.cond(s.zip(u).forall((a, b) => a.isBefore(b)), (), Problem("since must lie before until"))
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
          .when((at) + 3 <= (s).length)((s).substring((at) + 1, (at) + 3))
          .filter(_.forall(c => Character.digit(c, 16) >= 0))
          .map((Integer.parseInt(_, 16)))
      var i = 0
      while i < (s).length do
        (if s(i) == '%' then hex((i)) else None) match
          case Some(b) => out.write((b)); i += 3
          case None    => (s).substring(i, i + 1).getBytes("UTF-8").foreach(x => out.write(x.toInt)); i += 1
      (new String(out.toByteArray, "UTF-8"))

  def encodeFacets(fs: List[(FacetName, List[(Term, RowCount)])]): EncodedFacets =
    EncodedFacets(fs.flatMap((f, vs) => vs.map((v, n) => s"$f=${escape(((v).value))}=$n")).mkString(","))

  def decodeFacets(s: EncodedFacets): List[(FacetName, List[(Term, RowCount)])] =
    val triples = (s).value.split(',').toList.map(_.trim).filter(_.nonEmpty).flatMap { t =>
      t.split('=') match
        case Array(f, v, n) if f.nonEmpty => n.trim.toIntOption.map(c => (f.trim, unescape((v)), c))
        case _                            => None
    }
    triples.map(_._1).distinct.map(f => { val refactorTuple9 = f -> triples.collect { case (`f`, v, n) => v -> n }; (FacetName(refactorTuple9._1), (refactorTuple9._2).map(refactorValue62 => { val refactorTuple10 = refactorValue62; (Term((refactorTuple10._1)), RowCount(refactorTuple10._2)) })) })

  def facetLine(label: FacetName, counts: List[(Term, RowCount)]): FacetLine =
    if counts.isEmpty then FacetLine("")
    else FacetLine(s"$label: ${counts.map((v, n) => s"$v ${Paging.grouped(Paging.GroupSize(n.value.toLong))}").mkString(" · ")}")
