package com.histmersion
package api.automation
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.Plural
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

object Stalls:
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]

  export StallReport.{At as ReportAt, Days as ReportDays}

  final case class Stall(
      slug: Stall.Slug,
      title: Stall.Title = Facts.Title(""),
      status: Stall.Status = Facts.Status(""),
      lastProgress: Option[Stall.LastProgress] = None,
      idleDays: Option[Stall.IdleDays] = None,
      code: Stall.Code = Stall.Code(""),
      why: Stall.Why = Stall.Why(""),
      action: Stall.Action = Stall.Action("")
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Stall:
    export com.histmersion.api.research.ReviewGoal.Slug
    export Facts.{IdleDays, LastProgress, Status, Title}
    type Code = Code.T
    object Code extends Newtype[String, Pure]
    type Why = Why.T
    object Why extends Newtype[String, Pure]
    type Action = Action.T
    object Action extends Newtype[String, Pure]

  final case class ReasonCount(code: ReasonCount.Code, count: ReasonCount.Count)
      derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object ReasonCount:
    export Stall.Code
    type Count = Count.T
    object Count extends Newtype[Int, Pure]

  final case class StallReport(
      days: StallReport.Days,
      at: StallReport.At,
      goals: StallReport.Goals = StallReport.Goals(0),
      stalled: List[Stall] = Nil,
      byReason: List[ReasonCount] = Nil
  ) derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object StallReport:
    type Days = Days.T
    object Days extends Newtype[Int, Pure]
    type At = At.T
    object At extends Newtype[String, Pure]
    type Goals = Goals.T
    object Goals extends Newtype[Int, Pure]

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

  final case class SchedWait(
      code: SchedWait.Code,
      why: SchedWait.Why,
      position: SchedWait.Position = SchedWait.Position(0)
  )

  object SchedWait:
    export Stall.Code
    type Position = Position.T
    object Position extends Newtype[Int, Pure]
    type Detail = Detail.T
    object Detail extends Newtype[String, Pure]

    /** What stands in the way of a wait: a spend cap, plan pools below their floors, or anything else. */
    enum Why derives CanEqual:
      case Budget(detail: Detail)
      case Starved(detail: Detail)
      case Other(detail: Detail)

    object Why:
      extension (w: Why)
        /** The sentence as the stall report tells it. */
        def render: Stall.Why = w match
          case Budget(d)  => Stall.Why(s"budget: $d")
          case Starved(d) => Stall.Why(s"starved: $d")
          case Other(d)   => Stall.Why(s"$d")

        def isBudget: Boolean = w match
          case Budget(_) => true
          case _         => false

        def isStarved: Boolean = w match
          case Starved(_) => true
          case _          => false

    extension (w: SchedWait)
      def isBudget: Boolean = w.why.isBudget
      def isStarved: Boolean = w.why.isStarved

  final case class Facts(
      slug: Facts.Slug,
      title: Facts.Title = Facts.Title(""),
      status: Facts.Status = Facts.Status(""),
      lastProgress: Option[Facts.LastProgress] = None,
      idleDays: Option[Facts.IdleDays] = None,
      approvals: List[(Facts.ApprovalId, Facts.ApprovalKind)] = Nil,
      personHeld: Facts.PersonHeld = Facts.PersonHeld(0),
      paused: List[Facts.Paused] = Nil,
      draining: Facts.Draining = Facts.Draining(false),
      waits: List[SchedWait] = Nil,
      lastStop: Option[Facts.LastStop] = None,
      sources: Facts.Sources = Facts.Sources(0),
      noCandidates: Facts.NoCandidates = Facts.NoCandidates(false),
      open: Facts.Open = Facts.Open(0),
      running: Facts.Running = Facts.Running(0)
  )

  object Facts:
    export com.histmersion.api.research.ReviewGoal.Slug
    type Title = Title.T
    object Title extends Newtype[String, Pure]
    type Status = Status.T
    object Status extends Newtype[String, Pure]
    type LastProgress = LastProgress.T
    object LastProgress extends Newtype[String, Pure]
    type IdleDays = IdleDays.T
    object IdleDays extends Newtype[Int, Pure]
    type ApprovalId = ApprovalId.T
    object ApprovalId extends Newtype[Long, Pure]
    type ApprovalKind = ApprovalKind.T
    object ApprovalKind extends Newtype[String, Pure]
    type PersonHeld = PersonHeld.T
    object PersonHeld extends Newtype[Int, Pure]
    type Paused = Paused.T
    object Paused extends Newtype[String, Pure]
    type Draining = Draining.T
    object Draining extends Newtype[Boolean, Pure]
    type LastStop = LastStop.T
    object LastStop extends Newtype[String, Pure]
    type Sources = Sources.T
    object Sources extends Newtype[Int, Pure]
    type NoCandidates = NoCandidates.T
    object NoCandidates extends Newtype[Boolean, Pure]
    type Open = Open.T
    object Open extends Newtype[Int, Pure]
    type Running = Running.T
    object Running extends Newtype[Int, Pure]

  def stalled(f: Facts, days: ReportDays): Boolean = f.idleDays.forall(_.value >= math.max(0, days.value))

  def classify(f: Facts): (Stall.Code, Stall.Why, Stall.Action) =
    val g = f.slug
    def waitOf(p: SchedWait => Boolean) = f.waits.find(p)
    if (f.draining) == Facts.Draining(true) then
      (
        Stall.Code(Code.Drain),
        Stall.Why("the agents are draining: nothing new starts"),
        Stall.Action("histmersion agent autopilot undrain")
      )
    else if f.approvals.nonEmpty then
      val kinds =
        f.approvals.groupMapReduce(_._2)(_ => 1)(_ + _).toList.sortBy(-_._2).map((k, n) => s"$k $n").mkString(", ")
      (
        Stall.Code(Code.Person),
        Stall.Why(
          s"waits for a person: ${Plural.count(Plural.CountN((f.approvals.size).toLong), Plural.CountSingular("open approval"))} ($kinds; oldest #${f.approvals.map(_._1).min})"
        ),
        Stall.Action(
          s"histmersion agent decide-all approve --subject $g (or decide them one by one: histmersion agent approvals --subject $g)"
        )
      )
    else if (f.personHeld).value > 0 then
      (
        Stall.Code(Code.Held),
        Stall.Why(
          s"${Plural.count(Plural.CountN(f.personHeld.value.toLong), Plural.CountSingular("item"))} held by a person"
        ),
        Stall.Action(s"histmersion agent manager seed:$g items --status held, then release")
      )
    else if f.paused.nonEmpty then
      (
        Stall.Code(Code.Paused),
        Stall.Why(s"its managers are paused: ${f.paused.mkString(", ")}"),
        Stall.Action(s"histmersion agent manager ${f.paused.head} resume")
      )
    else
      waitOf(w => w.code == Stall.Code("blocked") && w.isBudget)
        .orElse(waitOf((refactorCallback => (refactorCallback.code == Stall.Code("budget"))))) match
        case Some(w) =>
          (
            Stall.Code(Code.Budget),
            w.why.render,
            Stall.Action("raise agents.budgets.daily-usd (or the manager's daily-usd), or wait for the next UTC day")
          )
        case None =>
          waitOf(w => w.code == Stall.Code("blocked") && w.isStarved)
            .orElse(waitOf((refactorCallback => (refactorCallback.code == Stall.Code("pool"))))) match
            case Some(w) =>
              (
                Stall.Code(Code.Quota),
                w.why.render,
                Stall.Action(
                  "histmersion agent quota (a plan below its floor frees at its reset); histmersion agent models"
                )
              )
            case None =>
              waitOf(w => (w.code == Stall.Code("capacity"))) match
                case Some(w) =>
                  (
                    Stall.Code(Code.Queue),
                    Stall.Why(s"waits in the scheduler's queue (#${w.position}): ${w.why.render}"),
                    Stall.Action(
                      s"histmersion agent autopilot focus priority $g <n> (or pin it), or raise agents.max-concurrent-runs"
                    )
                  )
                case None =>
                  waitOf((refactorCallback => (refactorCallback.code == Stall.Code("blocked")))) match
                    case Some(w) =>
                      (
                        Stall.Code(Code.Blocked),
                        w.why.render,
                        Stall.Action(s"histmersion agent scheduler why <item> (histmersion agent scheduler queue)")
                      )
                    case None if (f.running).value > 0 =>
                      (
                        Stall.Code(Code.Running),
                        Stall.Why(
                          s"${Plural.count(Plural.CountN(f.running.value.toLong), Plural.CountSingular("run"))} in flight"
                        ),
                        Stall.Action("nothing: wait for it")
                      )
                    case None =>
                      f.lastStop match
                        case Some(o) =>
                          (
                            Stall.Code(Code.Failed),
                            Stall.Why(s"its last item stopped: ${o.value.take(300)}"),
                            Stall.Action(s"histmersion agent manager seed:$g items --status stopped; reopen it")
                          )
                        case None if ((f.noCandidates) == Facts.NoCandidates(true)) && f.sources == Facts.Sources(0) =>
                          (
                            Stall.Code(Code.NoCandidates),
                            Stall.Why("its candidate sweep found nothing new and it has no source"),
                            Stall.Action(
                              s"give it a better query: histmersion goals link $g query \"<words>\" (or add a source)"
                            )
                          )
                        case None if f.open == Facts.Open(0) =>
                          (
                            Stall.Code(Code.Idle),
                            Stall.Why("nothing open: it waits for its cron or new sources"),
                            Stall.Action(s"histmersion agent enqueue seed $g (propose its next articles)")
                          )
                        case None =>
                          (
                            Stall.Code(Code.Idle),
                            Stall.Why(
                              s"${Plural.count(Plural.CountN(f.open.value.toLong), Plural.CountSingular("item"))} open, none due yet (a cooldown or a backoff)"
                            ),
                            Stall.Action(s"histmersion agent scheduler why <item>")
                          )

  def report(facts: List[Facts], days: ReportDays, at: ReportAt): StallReport =
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
      StallReport.Goals(facts.size),
      stalls,
      stalls
        .groupMapReduce(_.code)(_ => 1)(_ + _)
        .toList
        .sortBy((c, n) => (-n, c.value))
        .map((c, n) => ReasonCount(c, ReasonCount.Count(n)))
    )
