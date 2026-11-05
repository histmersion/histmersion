package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Plural
import com.histmersion.api.given

object Stalls:

  final case class Stall(
      slug: Stall.Slug,
      title: Stall.Title = "",
      status: Stall.Status = "",
      lastProgress: Option[Stall.LastProgress] = None,
      idleDays: Option[Stall.IdleDays] = None,
      code: Stall.Code = "",
      why: Stall.Why = "",
      action: Stall.Action = ""
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Stall:
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
      goals: StallReport.Goals = 0,
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

  final case class SchedWait(code: SchedWait.Code, why: SchedWait.Why, position: SchedWait.Position = 0)

  object SchedWait:
    type Code = Code.T
    object Code extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Position = Position.T
    object Position extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Facts(
      slug: Facts.Slug,
      title: Facts.Title = "",
      status: Facts.Status = "",
      lastProgress: Option[Facts.LastProgress] = None,
      idleDays: Option[Facts.IdleDays] = None,
      approvals: List[(Facts.ApprovalsPart1, Facts.ApprovalsPart2)] = Nil,
      personHeld: Facts.PersonHeld = 0,
      paused: List[Facts.Paused] = Nil,
      draining: Facts.Draining = false,
      waits: List[SchedWait] = Nil,
      lastStop: Option[Facts.LastStop] = None,
      sources: Facts.Sources = 0,
      noCandidates: Facts.NoCandidates = false,
      open: Facts.Open = 0,
      running: Facts.Running = 0
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
    object ApprovalsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
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

  def stalled(f: Facts, days: Int): Boolean = f.idleDays.forall(_ >= math.max(0, days))

  def classify(f: Facts): (String, String, String) =
    val g = f.slug
    def waitOf(p: SchedWait => Boolean) = f.waits.find(p)
    if f.draining then
      (Code.Drain, "the agents are draining: nothing new starts", "histmersion agent autopilot undrain")
    else if f.approvals.nonEmpty then
      val kinds =
        f.approvals.groupMapReduce(_._2)(_ => 1)(_ + _).toList.sortBy(-_._2).map((k, n) => s"$k $n").mkString(", ")
      (
        Code.Person,
        s"waits for a person: ${Plural.count(f.approvals.size, "open approval")} ($kinds; oldest #${f.approvals.map(_._1).min})",
        s"histmersion agent decide-all approve --subject $g (or decide them one by one: histmersion agent approvals --subject $g)"
      )
    else if f.personHeld > 0 then
      (
        Code.Held,
        s"${Plural.count(f.personHeld, "item")} held by a person",
        s"histmersion agent manager seed:$g items --status held, then release"
      )
    else if f.paused.nonEmpty then
      (
        Code.Paused,
        s"its managers are paused: ${f.paused.mkString(", ")}",
        s"histmersion agent manager ${f.paused.head} resume"
      )
    else
      waitOf(w => w.code == "blocked" && w.why.startsWith("budget")).orElse(waitOf(_.code == "budget")) match
        case Some(w) =>
          (
            Code.Budget,
            w.why,
            "raise agents.budgets.daily-usd (or the manager's daily-usd), or wait for the next UTC day"
          )
        case None =>
          waitOf(w => w.code == "blocked" && w.why.startsWith("starved")).orElse(waitOf(_.code == "pool")) match
            case Some(w) =>
              (
                Code.Quota,
                w.why,
                "histmersion agent quota (a plan below its floor frees at its reset); histmersion agent models"
              )
            case None =>
              waitOf(w => w.code == "capacity") match
                case Some(w) =>
                  (
                    Code.Queue,
                    s"waits in the scheduler's queue (#${w.position}): ${w.why}",
                    s"histmersion agent autopilot focus priority $g <n> (or pin it), or raise agents.max-concurrent-runs"
                  )
                case None =>
                  waitOf(_.code == "blocked") match
                    case Some(w) =>
                      (
                        Code.Blocked,
                        w.why,
                        s"histmersion agent scheduler why <item> (histmersion agent scheduler queue)"
                      )
                    case None if f.running > 0 =>
                      (Code.Running, s"${Plural.count(f.running, "run")} in flight", "nothing: wait for it")
                    case None =>
                      f.lastStop match
                        case Some(o) =>
                          (
                            Code.Failed,
                            s"its last item stopped: ${o.take(300)}",
                            s"histmersion agent manager seed:$g items --status stopped; reopen it"
                          )
                        case None if f.noCandidates && f.sources == 0 =>
                          (
                            Code.NoCandidates,
                            "its candidate sweep found nothing new and it has no source",
                            s"give it a better query: histmersion goals link $g query \"<words>\" (or add a source)"
                          )
                        case None if f.open == 0 =>
                          (
                            Code.Idle,
                            "nothing open: it waits for its cron or new sources",
                            s"histmersion agent enqueue seed $g (propose its next articles)"
                          )
                        case None =>
                          (
                            Code.Idle,
                            s"${Plural.count(f.open, "item")} open, none due yet (a cooldown or a backoff)",
                            s"histmersion agent scheduler why <item>"
                          )

  def report(facts: List[Facts], days: Int, at: String): StallReport =
    val stalls = facts
      .filter(stalled(_, days))
      .map { f =>
        val (c, w, a) = classify(f)
        Stall(f.slug, f.title, f.status, f.lastProgress, f.idleDays, c, w, a)
      }
      .sortBy(s => (s.idleDays.fold(Int.MinValue)(-_), s.slug))
    StallReport(
      days,
      at,
      facts.size,
      stalls,
      stalls.groupMapReduce(_.code)(_ => 1)(_ + _).toList.sortBy((c, n) => (-n, c)).map(ReasonCount.apply)
    )
