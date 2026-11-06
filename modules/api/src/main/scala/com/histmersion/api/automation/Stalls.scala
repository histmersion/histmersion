package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Plural
import com.histmersion.api.given

object Stalls:
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  type ReportDays = _root_.com.histmersion.api.automation.Stalls.StallReport.Days
  val ReportDays: _root_.com.histmersion.api.automation.Stalls.StallReport.Days.type = _root_.com.histmersion.api.automation.Stalls.StallReport.Days
  type ReportAt = _root_.com.histmersion.api.automation.Stalls.StallReport.At
  val ReportAt: _root_.com.histmersion.api.automation.Stalls.StallReport.At.type = _root_.com.histmersion.api.automation.Stalls.StallReport.At

  final case class Stall(
      slug: Stall.Slug,
      title: Stall.Title = _root_.com.histmersion.api.automation.Stalls.Facts.Title(""),
      status: Stall.Status = _root_.com.histmersion.api.automation.Stalls.Facts.Status(""),
      lastProgress: Option[Stall.LastProgress] = None,
      idleDays: Option[Stall.IdleDays] = None,
      code: Stall.Code = _root_.com.histmersion.api.automation.Stalls.Stall.Code(""),
      why: Stall.Why = _root_.com.histmersion.api.automation.Stalls.Stall.Why(""),
      action: Stall.Action = _root_.com.histmersion.api.automation.Stalls.Stall.Action("")
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Stall:
    type Slug = com.histmersion.api.automation.Stalls.Facts.Slug
    val Slug: _root_.com.histmersion.api.automation.Stalls.Facts.Slug.type = _root_.com.histmersion.api.automation.Stalls.Facts.Slug
    type Title = com.histmersion.api.automation.Stalls.Facts.Title
    val Title: _root_.com.histmersion.api.automation.Stalls.Facts.Title.type = _root_.com.histmersion.api.automation.Stalls.Facts.Title
    type Status = com.histmersion.api.automation.Stalls.Facts.Status
    val Status: _root_.com.histmersion.api.automation.Stalls.Facts.Status.type = _root_.com.histmersion.api.automation.Stalls.Facts.Status
    type LastProgress = com.histmersion.api.automation.Stalls.Facts.LastProgress
    val LastProgress: _root_.com.histmersion.api.automation.Stalls.Facts.LastProgress.type = _root_.com.histmersion.api.automation.Stalls.Facts.LastProgress
    type IdleDays = com.histmersion.api.automation.Stalls.Facts.IdleDays
    val IdleDays: _root_.com.histmersion.api.automation.Stalls.Facts.IdleDays.type = _root_.com.histmersion.api.automation.Stalls.Facts.IdleDays
    type Code = Code.T
    object Code extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Action = Action.T
    object Action extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ReasonCount(code: ReasonCount.Code, count: ReasonCount.Count) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object ReasonCount:
    type Code = com.histmersion.api.automation.Stalls.Stall.Code
    val Code: _root_.com.histmersion.api.automation.Stalls.Stall.Code.type = _root_.com.histmersion.api.automation.Stalls.Stall.Code
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
    type Code = com.histmersion.api.automation.Stalls.Stall.Code
    val Code: _root_.com.histmersion.api.automation.Stalls.Stall.Code.type = _root_.com.histmersion.api.automation.Stalls.Stall.Code
    type Why = com.histmersion.api.automation.Stalls.Stall.Why
    val Why: _root_.com.histmersion.api.automation.Stalls.Stall.Why.type = _root_.com.histmersion.api.automation.Stalls.Stall.Why
    type Position = Position.T
    object Position extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Facts(
      slug: Facts.Slug,
      title: Facts.Title = _root_.com.histmersion.api.automation.Stalls.Facts.Title(""),
      status: Facts.Status = _root_.com.histmersion.api.automation.Stalls.Facts.Status(""),
      lastProgress: Option[Facts.LastProgress] = None,
      idleDays: Option[Facts.IdleDays] = None,
      approvals: List[(Facts.ApprovalId, Facts.ApprovalKind)] = Nil,
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
    type Slug = com.histmersion.api.research.ReviewGoal.Slug
    val Slug: _root_.com.histmersion.api.research.ReviewGoal.Slug.type = _root_.com.histmersion.api.research.ReviewGoal.Slug
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastProgress = LastProgress.T
    object LastProgress extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IdleDays = IdleDays.T
    object IdleDays extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalId = ApprovalId.T
    object ApprovalId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.scala.Long](_.value)
    type ApprovalKind = ApprovalKind.T
    object ApprovalKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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

  def stalled(f: Facts, days: com.histmersion.api.automation.Stalls.ReportDays): Boolean = f.idleDays.forall(_.value >= math.max(0, days.value))

  def classify(f: Facts): (com.histmersion.api.automation.Stalls.Stall.Code, com.histmersion.api.automation.Stalls.Stall.Why, com.histmersion.api.automation.Stalls.Stall.Action) =
    val g = f.slug
    def waitOf(p: SchedWait => Boolean) = f.waits.find(p)
    if ((f.draining) == _root_.com.histmersion.api.automation.Stalls.Facts.Draining(true)) then
      (_root_.com.histmersion.api.automation.Stalls.Stall.Code(Code.Drain), _root_.com.histmersion.api.automation.Stalls.Stall.Why("the agents are draining: nothing new starts"), _root_.com.histmersion.api.automation.Stalls.Stall.Action("histmersion agent autopilot undrain"))
    else if f.approvals.nonEmpty then
      val kinds =
        f.approvals.groupMapReduce(_._2)(_ => 1)(_ + _).toList.sortBy(-_._2).map((k, n) => s"$k $n").mkString(", ")
      (
        _root_.com.histmersion.api.automation.Stalls.Stall.Code(Code.Person),
        _root_.com.histmersion.api.automation.Stalls.Stall.Why(s"waits for a person: ${Plural.count(_root_.com.histmersion.api.Plural.CountN((f.approvals.size).toLong), _root_.com.histmersion.api.Plural.CountSingular("open approval"))} ($kinds; oldest #${f.approvals.map(_._1).min})"),
        _root_.com.histmersion.api.automation.Stalls.Stall.Action(s"histmersion agent decide-all approve --subject $g (or decide them one by one: histmersion agent approvals --subject $g)")
      )
    else if (f.personHeld).value > 0 then
      (
        _root_.com.histmersion.api.automation.Stalls.Stall.Code(Code.Held),
        _root_.com.histmersion.api.automation.Stalls.Stall.Why(s"${Plural.count(_root_.com.histmersion.api.Plural.CountN(f.personHeld.value.toLong), _root_.com.histmersion.api.Plural.CountSingular("item"))} held by a person"),
        _root_.com.histmersion.api.automation.Stalls.Stall.Action(s"histmersion agent manager seed:$g items --status held, then release")
      )
    else if f.paused.nonEmpty then
      (
        _root_.com.histmersion.api.automation.Stalls.Stall.Code(Code.Paused),
        _root_.com.histmersion.api.automation.Stalls.Stall.Why(s"its managers are paused: ${f.paused.mkString(", ")}"),
        _root_.com.histmersion.api.automation.Stalls.Stall.Action(s"histmersion agent manager ${f.paused.head} resume")
      )
    else
      waitOf(w => w.code == _root_.com.histmersion.api.automation.Stalls.Stall.Code("blocked") && w.why.value.startsWith("budget")).orElse(waitOf((refactorCallback => (refactorCallback.code == _root_.com.histmersion.api.automation.Stalls.Stall.Code("budget"))))) match
        case Some(w) =>
          (
            _root_.com.histmersion.api.automation.Stalls.Stall.Code(Code.Budget),
            w.why,
            _root_.com.histmersion.api.automation.Stalls.Stall.Action("raise agents.budgets.daily-usd (or the manager's daily-usd), or wait for the next UTC day")
          )
        case None =>
          waitOf(w => w.code == _root_.com.histmersion.api.automation.Stalls.Stall.Code("blocked") && w.why.value.startsWith("starved")).orElse(waitOf((refactorCallback => (refactorCallback.code == _root_.com.histmersion.api.automation.Stalls.Stall.Code("pool"))))) match
            case Some(w) =>
              (
                _root_.com.histmersion.api.automation.Stalls.Stall.Code(Code.Quota),
                w.why,
                _root_.com.histmersion.api.automation.Stalls.Stall.Action("histmersion agent quota (a plan below its floor frees at its reset); histmersion agent models")
              )
            case None =>
              waitOf(w => (w.code == _root_.com.histmersion.api.automation.Stalls.Stall.Code("capacity"))) match
                case Some(w) =>
                  (
                    _root_.com.histmersion.api.automation.Stalls.Stall.Code(Code.Queue),
                    _root_.com.histmersion.api.automation.Stalls.Stall.Why(s"waits in the scheduler's queue (#${w.position}): ${w.why}"),
                    _root_.com.histmersion.api.automation.Stalls.Stall.Action(s"histmersion agent autopilot focus priority $g <n> (or pin it), or raise agents.max-concurrent-runs")
                  )
                case None =>
                  waitOf((refactorCallback => (refactorCallback.code == _root_.com.histmersion.api.automation.Stalls.Stall.Code("blocked")))) match
                    case Some(w) =>
                      (
                        _root_.com.histmersion.api.automation.Stalls.Stall.Code(Code.Blocked),
                        w.why,
                        _root_.com.histmersion.api.automation.Stalls.Stall.Action(s"histmersion agent scheduler why <item> (histmersion agent scheduler queue)")
                      )
                    case None if (f.running).value > 0 =>
                      (_root_.com.histmersion.api.automation.Stalls.Stall.Code(Code.Running), _root_.com.histmersion.api.automation.Stalls.Stall.Why(s"${Plural.count(_root_.com.histmersion.api.Plural.CountN(f.running.value.toLong), _root_.com.histmersion.api.Plural.CountSingular("run"))} in flight"), _root_.com.histmersion.api.automation.Stalls.Stall.Action("nothing: wait for it"))
                    case None =>
                      f.lastStop match
                        case Some(o) =>
                          (
                            _root_.com.histmersion.api.automation.Stalls.Stall.Code(Code.Failed),
                            _root_.com.histmersion.api.automation.Stalls.Stall.Why(s"its last item stopped: ${o.value.take(300)}"),
                            _root_.com.histmersion.api.automation.Stalls.Stall.Action(s"histmersion agent manager seed:$g items --status stopped; reopen it")
                          )
                        case None if ((f.noCandidates) == _root_.com.histmersion.api.automation.Stalls.Facts.NoCandidates(true)) && f.sources == _root_.com.histmersion.api.automation.Stalls.Facts.Sources(0) =>
                          (
                            _root_.com.histmersion.api.automation.Stalls.Stall.Code(Code.NoCandidates),
                            _root_.com.histmersion.api.automation.Stalls.Stall.Why("its candidate sweep found nothing new and it has no source"),
                            _root_.com.histmersion.api.automation.Stalls.Stall.Action(s"give it a better query: histmersion goals link $g query \"<words>\" (or add a source)")
                          )
                        case None if f.open == _root_.com.histmersion.api.automation.Stalls.Facts.Open(0) =>
                          (
                            _root_.com.histmersion.api.automation.Stalls.Stall.Code(Code.Idle),
                            _root_.com.histmersion.api.automation.Stalls.Stall.Why("nothing open: it waits for its cron or new sources"),
                            _root_.com.histmersion.api.automation.Stalls.Stall.Action(s"histmersion agent enqueue seed $g (propose its next articles)")
                          )
                        case None =>
                          (
                            _root_.com.histmersion.api.automation.Stalls.Stall.Code(Code.Idle),
                            _root_.com.histmersion.api.automation.Stalls.Stall.Why(s"${Plural.count(_root_.com.histmersion.api.Plural.CountN(f.open.value.toLong), _root_.com.histmersion.api.Plural.CountSingular("item"))} open, none due yet (a cooldown or a backoff)"),
                            _root_.com.histmersion.api.automation.Stalls.Stall.Action(s"histmersion agent scheduler why <item>")
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
      stalls.groupMapReduce(_.code)(_ => 1)(_ + _).toList.sortBy((c, n) => (-n, c.value)).map((c, n) => ReasonCount(c, ReasonCount.Count(n)))
    )
