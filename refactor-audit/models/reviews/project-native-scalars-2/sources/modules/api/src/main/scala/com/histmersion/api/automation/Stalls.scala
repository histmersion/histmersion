package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Plural
import com.histmersion.api.given

object Stalls:

  type ClassifyWaitOfPResult = ClassifyWaitOfPResult.T
  object ClassifyWaitOfPResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type StalledResult = StalledResult.T
  object StalledResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StalledDays = StalledDays.T
  object StalledDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyResultPart1 = ClassifyResultPart1.T
  object ClassifyResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyResultPart2 = ClassifyResultPart2.T
  object ClassifyResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClassifyResultPart3 = ClassifyResultPart3.T
  object ClassifyResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReportDays = _root_.com.histmersion.api.automation.Stalls.StallReport.Days
  val ReportDays: _root_.com.histmersion.api.automation.Stalls.StallReport.Days.type = _root_.com.histmersion.api.automation.Stalls.StallReport.Days
  type ReportAt = _root_.com.histmersion.api.automation.Stalls.StallReport.At
  val ReportAt: _root_.com.histmersion.api.automation.Stalls.StallReport.At.type = _root_.com.histmersion.api.automation.Stalls.StallReport.At

  final case class Stall(
      slug: Stall.Slug,
      title: Stall.Title = _root_.com.histmersion.api.automation.Stalls.Stall.Title(""),
      status: Stall.Status = _root_.com.histmersion.api.automation.Stalls.Stall.Status(""),
      lastProgress: Option[Stall.LastProgress] = None,
      idleDays: Option[Stall.IdleDays] = None,
      code: Stall.Code = _root_.com.histmersion.api.automation.Stalls.Stall.Code(""),
      why: Stall.Why = _root_.com.histmersion.api.automation.Stalls.Stall.Why(""),
      action: Stall.Action = _root_.com.histmersion.api.automation.Stalls.Stall.Action("")
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Stall:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastProgress = LastProgress.T
    object LastProgress extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IdleDays = IdleDays.T
    object IdleDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Code = Code.T
    object Code extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Action = Action.T
    object Action extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ReasonCount(code: ReasonCount.Code, count: ReasonCount.Count) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object ReasonCount:
    type Code = Code.T
    object Code extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Count = Count.T
    object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class StallReport(
      days: StallReport.Days,
      at: StallReport.At,
      goals: StallReport.Goals = _root_.com.histmersion.api.automation.Stalls.StallReport.Goals(0),
      stalled: List[Stall] = Nil,
      byReason: List[ReasonCount] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object StallReport:
    type Days = Days.T
    object Days extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type At = At.T
    object At extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Goals = Goals.T
    object Goals extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  object Code:
    val Drain = "drain"
    val Person = "person"
    val Held = "held"
    val Paused = "paused"
    val Budget = "budget"
    val Quota = "quota"
    val Blocked = "blocked"
    val Queue = "queue"
    val Failed = "failed"
    val NoCandidates = "no-candidates"
    val Running = "running"
    val Idle = "idle"

  final case class SchedWait(code: SchedWait.Code, why: SchedWait.Why, position: SchedWait.Position = _root_.com.histmersion.api.automation.Stalls.SchedWait.Position(0))

  object SchedWait:
    type Code = Code.T
    object Code extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Position = Position.T
    object Position extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Facts(
      slug: Facts.Slug,
      title: Facts.Title = _root_.com.histmersion.api.automation.Stalls.Facts.Title(""),
      status: Facts.Status = _root_.com.histmersion.api.automation.Stalls.Facts.Status(""),
      lastProgress: Option[Facts.LastProgress] = None,
      idleDays: Option[Facts.IdleDays] = None,
      approvals: List[(Facts.ApprovalsPart1, Facts.ApprovalsPart2)] = Nil,
      personHeld: Facts.PersonHeld = _root_.com.histmersion.api.automation.Stalls.Facts.PersonHeld(0),
      paused: List[Facts.Paused] = Nil,
      draining: Facts.Draining = _root_.com.histmersion.api.automation.Stalls.Facts.Draining(false),
      waits: List[SchedWait] = Nil,
      lastStop: Option[Facts.LastStop] = None,
      sources: Facts.Sources = _root_.com.histmersion.api.automation.Stalls.Facts.Sources(0),
      noCandidates: Facts.NoCandidates = _root_.com.histmersion.api.automation.Stalls.Facts.NoCandidates(false),
      open: Facts.Open = _root_.com.histmersion.api.automation.Stalls.Facts.Open(0),
      running: Facts.Running = _root_.com.histmersion.api.automation.Stalls.Facts.Running(0)
  )

  object Facts:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastProgress = LastProgress.T
    object LastProgress extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IdleDays = IdleDays.T
    object IdleDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalsPart1 = ApprovalsPart1.T
    object ApprovalsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.scala.Long](_.value)
    type ApprovalsPart2 = ApprovalsPart2.T
    object ApprovalsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PersonHeld = PersonHeld.T
    object PersonHeld extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Paused = Paused.T
    object Paused extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Draining = Draining.T
    object Draining extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastStop = LastStop.T
    object LastStop extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Sources = Sources.T
    object Sources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NoCandidates = NoCandidates.T
    object NoCandidates extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Open = Open.T
    object Open extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Running = Running.T
    object Running extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def stalled(f: Facts, days: com.histmersion.api.automation.Stalls.StalledDays): com.histmersion.api.automation.Stalls.StalledResult = f.idleDays.forall(_ >= math.max(0, (days).value))

  def classify(f: Facts): (com.histmersion.api.automation.Stalls.ClassifyResultPart1, com.histmersion.api.automation.Stalls.ClassifyResultPart2, com.histmersion.api.automation.Stalls.ClassifyResultPart3) =
    val g = f.slug
    def waitOf(p: SchedWait => com.histmersion.api.automation.Stalls.ClassifyWaitOfPResult) = f.waits.find({ val refactorFunction2 = p; (refactorValue2) => (refactorFunction2(refactorValue2)).value })
    if ((f.draining) == _root_.com.histmersion.api.automation.Stalls.Facts.Draining(true)) then
      (_root_.com.histmersion.api.automation.Stalls.ClassifyResultPart1(Code.Drain), _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart2("the agents are draining: nothing new starts"), _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart3("histmersion agent autopilot undrain"))
    else if f.approvals.nonEmpty then
      val kinds =
        f.approvals.groupMapReduce(_._2)(_ => 1)(_ + _).toList.sortBy(-_._2).map((k, n) => s"$k $n").mkString(", ")
      (
        _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart1(Code.Person),
        _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart2(s"waits for a person: ${Plural.count(_root_.com.histmersion.api.Plural.CountN((f.approvals.size).toLong), _root_.com.histmersion.api.Plural.CountSingular("open approval"))} ($kinds; oldest #${f.approvals.map(_._1).min})"),
        _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart3(s"histmersion agent decide-all approve --subject $g (or decide them one by one: histmersion agent approvals --subject $g)")
      )
    else if (f.personHeld).value > 0 then
      (
        _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart1(Code.Held),
        s"${Plural.count(f.personHeld, _root_.com.histmersion.api.Plural.CountSingular("item"))} held by a person",
        _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart3(s"histmersion agent manager seed:$g items --status held, then release")
      )
    else if f.paused.nonEmpty then
      (
        _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart1(Code.Paused),
        _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart2(s"its managers are paused: ${f.paused.mkString(", ")}"),
        _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart3(s"histmersion agent manager ${f.paused.head} resume")
      )
    else
      waitOf(w => w.code == _root_.com.histmersion.api.automation.Stalls.SchedWait.Code("blocked") && w.why.startsWith("budget")).orElse(waitOf((refactorCallback => _root_.com.histmersion.api.automation.Stalls.ClassifyWaitOfPResult(refactorCallback.code == _root_.com.histmersion.api.automation.Stalls.SchedWait.Code("budget"))))) match
        case Some(w) =>
          (
            _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart1(Code.Budget),
            w.why,
            _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart3("raise agents.budgets.daily-usd (or the manager's daily-usd), or wait for the next UTC day")
          )
        case None =>
          waitOf(w => w.code == _root_.com.histmersion.api.automation.Stalls.SchedWait.Code("blocked") && w.why.startsWith("starved")).orElse(waitOf((refactorCallback => _root_.com.histmersion.api.automation.Stalls.ClassifyWaitOfPResult(refactorCallback.code == _root_.com.histmersion.api.automation.Stalls.SchedWait.Code("pool"))))) match
            case Some(w) =>
              (
                _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart1(Code.Quota),
                w.why,
                _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart3("histmersion agent quota (a plan below its floor frees at its reset); histmersion agent models")
              )
            case None =>
              waitOf(w => _root_.com.histmersion.api.automation.Stalls.ClassifyWaitOfPResult(w.code == _root_.com.histmersion.api.automation.Stalls.SchedWait.Code("capacity"))) match
                case Some(w) =>
                  (
                    _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart1(Code.Queue),
                    _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart2(s"waits in the scheduler's queue (#${w.position}): ${w.why}"),
                    _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart3(s"histmersion agent autopilot focus priority $g <n> (or pin it), or raise agents.max-concurrent-runs")
                  )
                case None =>
                  waitOf((refactorCallback => _root_.com.histmersion.api.automation.Stalls.ClassifyWaitOfPResult(refactorCallback.code == _root_.com.histmersion.api.automation.Stalls.SchedWait.Code("blocked")))) match
                    case Some(w) =>
                      (
                        _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart1(Code.Blocked),
                        w.why,
                        _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart3(s"histmersion agent scheduler why <item> (histmersion agent scheduler queue)")
                      )
                    case None if (f.running).value > 0 =>
                      (_root_.com.histmersion.api.automation.Stalls.ClassifyResultPart1(Code.Running), s"${Plural.count(f.running, _root_.com.histmersion.api.Plural.CountSingular("run"))} in flight", _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart3("nothing: wait for it"))
                    case None =>
                      f.lastStop match
                        case Some(o) =>
                          (
                            _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart1(Code.Failed),
                            s"its last item stopped: ${o.take(300)}",
                            _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart3(s"histmersion agent manager seed:$g items --status stopped; reopen it")
                          )
                        case None if ((f.noCandidates) == _root_.com.histmersion.api.automation.Stalls.Facts.NoCandidates(true)) && f.sources == _root_.com.histmersion.api.automation.Stalls.Facts.Sources(0) =>
                          (
                            _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart1(Code.NoCandidates),
                            _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart2("its candidate sweep found nothing new and it has no source"),
                            _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart3(s"give it a better query: histmersion goals link $g query \"<words>\" (or add a source)")
                          )
                        case None if f.open == _root_.com.histmersion.api.automation.Stalls.Facts.Open(0) =>
                          (
                            _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart1(Code.Idle),
                            _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart2("nothing open: it waits for its cron or new sources"),
                            _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart3(s"histmersion agent enqueue seed $g (propose its next articles)")
                          )
                        case None =>
                          (
                            _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart1(Code.Idle),
                            s"${Plural.count(f.open, _root_.com.histmersion.api.Plural.CountSingular("item"))} open, none due yet (a cooldown or a backoff)",
                            _root_.com.histmersion.api.automation.Stalls.ClassifyResultPart3(s"histmersion agent scheduler why <item>")
                          )

  def report(facts: List[Facts], days: com.histmersion.api.automation.Stalls.ReportDays, at: com.histmersion.api.automation.Stalls.ReportAt): StallReport =
    val stalls = facts
      .filter(stalled(_, days))
      .map { f =>
        val (c, w, a) = classify(f)
        Stall(f.slug, f.title, f.status, f.lastProgress, f.idleDays, c, w, a)
      }
      .sortBy(s => (s.idleDays.fold(Int.MinValue)(-(_).value), s.slug))
    StallReport(
      days,
      at,
      _root_.com.histmersion.api.automation.Stalls.StallReport.Goals(facts.size),
      stalls,
      stalls.groupMapReduce(_.code)(_ => 1)(_ + _).toList.sortBy((c, n) => (-n, c)).map(ReasonCount.apply)
    )
