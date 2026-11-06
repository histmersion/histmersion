package com.histmersion.api.automation
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.Source
import com.histmersion.api.given

/** A plan is spent by every client of the account, and the provider says how much is left only as a percentage of each
  * window plus its reset, so a plan is governed by floors rather than budgets.
  */
object PlanQuota:
  type PaidResult = PaidResult.T
  object PaidResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CurrentNow = CurrentNow.T
  object CurrentNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CurrentModel = CurrentModel.T
  object CurrentModel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WindowStateNow = WindowStateNow.T
  object WindowStateNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerdictHeld = VerdictHeld.T
  object VerdictHeld extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerdictRefusalPart1 = VerdictRefusalPart1.T
  object VerdictRefusalPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerdictRefusalPart2 = VerdictRefusalPart2.T
  object VerdictRefusalPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerdictNow = VerdictNow.T
  object VerdictNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VerdictModel = VerdictModel.T
  object VerdictModel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshNeedHeld = RefreshNeedHeld.T
  object RefreshNeedHeld extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshNeedRefusalPart1 = RefreshNeedRefusalPart1.T
  object RefreshNeedRefusalPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshNeedRefusalPart2 = RefreshNeedRefusalPart2.T
  object RefreshNeedRefusalPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshNeedNow = RefreshNeedNow.T
  object RefreshNeedNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshNeedModel = RefreshNeedModel.T
  object RefreshNeedModel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThrottledResult = ThrottledResult.T
  object ThrottledResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThrottledLastAttempt = ThrottledLastAttempt.T
  object ThrottledLastAttempt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThrottledMinGapMs = ThrottledMinGapMs.T
  object ThrottledMinGapMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThrottledNow = ThrottledNow.T
  object ThrottledNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshDecisionResultPart1 = RefreshDecisionResultPart1.T
  object RefreshDecisionResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshDecisionEnabled = RefreshDecisionEnabled.T
  object RefreshDecisionEnabled extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshDecisionLastAttempt = RefreshDecisionLastAttempt.T
  object RefreshDecisionLastAttempt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshDecisionMinGapMs = RefreshDecisionMinGapMs.T
  object RefreshDecisionMinGapMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshDecisionNow = RefreshDecisionNow.T
  object RefreshDecisionNow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshDecisionForce = RefreshDecisionForce.T
  object RefreshDecisionForce extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  import Agents.{ModelRef, ProviderKind}

  enum PoolId(val wire: PoolId.Wire, val title: PoolId.Title):
    case ClaudePlan extends PoolId(_root_.com.histmersion.api.automation.PlanQuota.PoolId.Wire("claude-plan"), _root_.com.histmersion.api.automation.PlanQuota.PoolId.Title("Claude plan"))
    case ChatgptPlan extends PoolId(_root_.com.histmersion.api.automation.PlanQuota.PoolId.Wire("chatgpt-plan"), _root_.com.histmersion.api.automation.PlanQuota.PoolId.Title("ChatGPT plan"))

  object PoolId:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.automation.PlanQuota.PoolId.ParseS): Option[PoolId] = values.find(_.wire == _root_.com.histmersion.api.automation.PlanQuota.PoolId.Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

  /** A CLI configured to keep an API key in its environment (`use-api-key`) bills the key, not the plan. */
  final case class Logins(
      claudeCodeApiKey: Logins.ClaudeCodeApiKey = _root_.com.histmersion.api.automation.PlanQuota.Logins.ClaudeCodeApiKey(false),
      codexApiKey: Logins.CodexApiKey = _root_.com.histmersion.api.automation.PlanQuota.Logins.CodexApiKey(false),
      opencodeChatgpt: Logins.OpencodeChatgpt = _root_.com.histmersion.api.automation.PlanQuota.Logins.OpencodeChatgpt(false)
  )

  object Logins:
    type ClaudeCodeApiKey = ClaudeCodeApiKey.T
    object ClaudeCodeApiKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CodexApiKey = CodexApiKey.T
    object CodexApiKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpencodeChatgpt = OpencodeChatgpt.T
    object OpencodeChatgpt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def poolOf(m: ModelRef, logins: Logins): Option[PoolId] = m.provider match
    case ProviderKind.ClaudeCode if !logins.claudeCodeApiKey                              => Some(PoolId.ClaudePlan)
    case ProviderKind.Codex if !logins.codexApiKey                                        => Some(PoolId.ChatgptPlan)
    case ProviderKind.Opencode if logins.opencodeChatgpt && m.model.startsWith("openai/") => Some(PoolId.ChatgptPlan)
    case _                                                                                => None

  def paid(m: ModelRef, logins: Logins): com.histmersion.api.automation.PlanQuota.PaidResult =
    _root_.com.histmersion.api.automation.PlanQuota.PaidResult(m.provider != ProviderKind.Local && poolOf(m, logins).isEmpty)

  enum Source(val wire: Source.Wire):
    case Run extends Source(_root_.com.histmersion.api.automation.PlanQuota.Source.Wire("run"))

    case Mod extends Source(_root_.com.histmersion.api.automation.PlanQuota.Source.Wire("mod"))

    case Interactive extends Source(_root_.com.histmersion.api.automation.PlanQuota.Source.Wire("interactive"))

    case AppServer extends Source(_root_.com.histmersion.api.automation.PlanQuota.Source.Wire("app-server"))

    case Limit extends Source(_root_.com.histmersion.api.automation.PlanQuota.Source.Wire("limit"))

    case Probe extends Source(_root_.com.histmersion.api.automation.PlanQuota.Source.Wire("probe"))

  object Source:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.automation.PlanQuota.Source.ParseS): Option[Source] = values.find(_.wire == _root_.com.histmersion.api.automation.PlanQuota.Source.Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

  /** Codex names its windows by length (`300m`/`10080m`): its `primary` is not always the 5-hour window. */
  final case class Reading(
      limit: Reading.Limit,
      kind: Reading.Kind,
      usedPct: Reading.UsedPct,
      resetsAt: Option[Reading.ResetsAt],
      rejected: Reading.Rejected,
      observedAt: Reading.ObservedAt,
      source: Source,
      model: Option[Reading.Model] = None
  )

  object Reading:
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
    type UsedPct = UsedPct.T
    object UsedPct extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResetsAt = ResetsAt.T
    object ResetsAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Rejected = Rejected.T
    object Rejected extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ObservedAt = ObservedAt.T
    object ObservedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.scala.Long](_.value)
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Floors(byKind: Map[Floors.ByKindKey, Floors.ByKindValue]):
    def text: com.histmersion.api.automation.PlanQuota.Floors.TextResult = _root_.com.histmersion.api.automation.PlanQuota.Floors.TextResult(byKind.toList.sortBy(_._1).map((k, v) => s"$k=$v").mkString(", "))
    def of(limit: com.histmersion.api.automation.PlanQuota.Floors.OfLimit, kind: com.histmersion.api.automation.PlanQuota.Floors.OfKind, default: com.histmersion.api.automation.PlanQuota.Floors.OfDefault): com.histmersion.api.automation.PlanQuota.Floors.OfResult =
      byKind.get(s"$limit/$kind").orElse(byKind.get(kind)).getOrElse(default)

  object Floors:
    type TextResult = TextResult.T
    object TextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfResult = OfResult.T
    object OfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfLimit = OfLimit.T
    object OfLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfKind = OfKind.T
    object OfKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfDefault = OfDefault.T
    object OfDefault extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseResultPart1 = ParseResultPart1.T
    object ParseResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ByKindKey = ByKindKey.T
    object ByKindKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.java.lang.String](_.value)
    type ByKindValue = ByKindValue.T
    object ByKindValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Empty: Floors = Floors(Map.empty)
    def parse(s: com.histmersion.api.automation.PlanQuota.Floors.ParseS): Either[com.histmersion.api.automation.PlanQuota.Floors.ParseResultPart1, Floors] =
      val items = (s).value.split(',').toList.map(_.trim).filter(_.nonEmpty)
      val parsed = items.map { i =>
        i.indexOf('=') match
          case -1 => Left(s"'$i' is not <window>=<percent>")
          case n  =>
            val (k, v) = (i.substring(0, n).trim, i.substring(n + 1).trim.stripSuffix("%").trim)
            v.toIntOption
              .filter(p => p >= 0 && p <= 100)
              .toRight(s"'$i': a floor is a percentage 0-100")
              .flatMap(p => Either.cond(k.nonEmpty && !k.exists(_.isWhitespace), k -> p, s"'$i' names no window"))
      }
      parsed.collect { case Left(m) => m } match
        case Nil  => Right(Floors(parsed.collect { case Right(kv) => kv }.toMap))
        case errs => Left(_root_.com.histmersion.api.automation.PlanQuota.Floors.ParseResultPart1(errs.mkString("; ")))

  final case class Rules(floors: Floors, defaultFloor: Rules.DefaultFloor, staleMs: Rules.StaleMs, marginPct: Rules.MarginPct)

  object Rules:
    type DefaultFloor = DefaultFloor.T
    object DefaultFloor extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StaleMs = StaleMs.T
    object StaleMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarginPct = MarginPct.T
    object MarginPct extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class WindowState(
      reading: Reading,
      floor: WindowState.Floor,
      leftPct: WindowState.LeftPct,
      stale: WindowState.Stale,
      usable: WindowState.Usable,
      why: Option[WindowState.Why]
  )

  object WindowState:
    type Floor = Floor.T
    object Floor extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeftPct = LeftPct.T
    object LeftPct extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Stale = Stale.T
    object Stale extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Usable = Usable.T
    object Usable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum Verdict:
    case Usable(note: Option[Verdict.Usable.Note])

    case Blocked(reason: Verdict.Blocked.Reason, until: Option[Verdict.Blocked.Until])

    def usable: com.histmersion.api.automation.PlanQuota.Verdict.UsableResult = _root_.com.histmersion.api.automation.PlanQuota.Verdict.UsableResult(this.isInstanceOf[Usable])

  object Verdict:
    object Usable:
      type Note = Note.T
      object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    object Blocked:
      type Reason = Reason.T
      object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
      type Until = Until.T
      object Until extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type UsableResult = UsableResult.T
    object UsableResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def current(readings: List[Reading], now: com.histmersion.api.automation.PlanQuota.CurrentNow, model: Option[com.histmersion.api.automation.PlanQuota.CurrentModel] = None): List[Reading] =
    readings
      .filter(r => r.resetsAt.forall(_ > now))
      .filter(r => r.model.isEmpty || model.isEmpty || r.model == model)
      .groupBy(r => (r.limit, r.kind))
      .values
      .map(_.maxBy(_.observedAt))
      .toList
      .sortBy(r => (r.limit, r.kind))

  def windowState(r: Reading, rules: Rules, now: com.histmersion.api.automation.PlanQuota.WindowStateNow): WindowState =
    val floor = rules.floors.of(r.limit, r.kind, rules.defaultFloor)
    val left = (100.0 - r.usedPct).max(0)
    val stale = (now).value - r.observedAt > rules.staleMs
    val pct = (d: Double) =>
      java.math.BigDecimal(d).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros.toPlainString
    val why =
      if r.rejected then Some(s"${r.kind}: the provider refuses it")
      else if !stale && left <= floor then Some(s"${r.kind}: ${pct(left)}% left, floor $floor%")
      else if stale && left < floor + rules.marginPct then
        Some(
          s"${r.kind}: ${pct(left)}% left when last read, floor $floor% (+${rules.marginPct} while the reading is stale)"
        )
      else None
    WindowState(r, floor, left, stale, _root_.com.histmersion.api.automation.PlanQuota.WindowState.Usable(why.isEmpty), (why).map(refactorValue4 => _root_.com.histmersion.api.automation.PlanQuota.WindowState.Why(refactorValue4)))

  def verdict(
      readings: List[Reading],
      held: com.histmersion.api.automation.PlanQuota.VerdictHeld,
      refusal: Option[(com.histmersion.api.automation.PlanQuota.VerdictRefusalPart1, Option[com.histmersion.api.automation.PlanQuota.VerdictRefusalPart2])],
      rules: Rules,
      now: com.histmersion.api.automation.PlanQuota.VerdictNow,
      model: Option[com.histmersion.api.automation.PlanQuota.VerdictModel] = None
  ): (Verdict, List[WindowState]) =
    val states = current(readings, now, model).map(windowState(_, rules, now))
    val blocking = states.filterNot(_.usable)
    val earliest = blocking.flatMap(_.reading.resetsAt).minOption
    val v =
      if (held).value then Verdict.Blocked(_root_.com.histmersion.api.automation.PlanQuota.Verdict.Blocked.Reason("held: a person paused all agent use of this plan"), None)
      else
        refusal.filter(_._2.forall(((_).value > now).value)) match
          case Some((why, until))             => Verdict.Blocked(why, until)
          case None if blocking.nonEmpty      => Verdict.Blocked(blocking.flatMap(_.why).mkString("; "), earliest)
          case None if states.isEmpty         => Verdict.Usable(Some(_root_.com.histmersion.api.automation.PlanQuota.Verdict.Usable.Note("no reading yet: unknown until a run reads it")))
          case None if states.exists(_.stale) => Verdict.Usable(Some(_root_.com.histmersion.api.automation.PlanQuota.Verdict.Usable.Note("the last reading is stale")))
          case None                           => Verdict.Usable(None)
    (v, states)

  enum RefreshMode(val wire: RefreshMode.Wire):
    case Auto extends RefreshMode(_root_.com.histmersion.api.automation.PlanQuota.RefreshMode.Wire("auto"))
    case Probe extends RefreshMode(_root_.com.histmersion.api.automation.PlanQuota.RefreshMode.Wire("probe"))
    case Off extends RefreshMode(_root_.com.histmersion.api.automation.PlanQuota.RefreshMode.Wire("off"))

  object RefreshMode:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.automation.PlanQuota.RefreshMode.ParseS): Option[RefreshMode] = values.find(_.wire == _root_.com.histmersion.api.automation.PlanQuota.RefreshMode.Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

  enum Login:
    case Present(detail: Login.Present.Detail)
    case Missing(why: Login.Missing.Why)
    case Unknown(why: Login.Unknown.Why)

  object Login:
    object Present:
      type Detail = Detail.T
      object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Missing:
      type Why = Why.T
      object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    object Unknown:
      type Why = Why.T
      object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum RefreshNeed(val text: RefreshNeed.Text):
    case StaleOnly extends RefreshNeed(_root_.com.histmersion.api.automation.PlanQuota.RefreshNeed.Text("the reading is stale"))

    case NoReading extends RefreshNeed(_root_.com.histmersion.api.automation.PlanQuota.RefreshNeed.Text("no reading yet"))

  object RefreshNeed:
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  enum RefreshTrigger(val wire: RefreshTrigger.Wire):
    case Pick extends RefreshTrigger(_root_.com.histmersion.api.automation.PlanQuota.RefreshTrigger.Wire("pick"))

    case Paused extends RefreshTrigger(_root_.com.histmersion.api.automation.PlanQuota.RefreshTrigger.Wire("paused-run"))

    case Command extends RefreshTrigger(_root_.com.histmersion.api.automation.PlanQuota.RefreshTrigger.Wire("command"))

    case Schedule extends RefreshTrigger(_root_.com.histmersion.api.automation.PlanQuota.RefreshTrigger.Wire("schedule"))

  object RefreshTrigger:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def refreshNeed(
      readings: List[Reading],
      held: com.histmersion.api.automation.PlanQuota.RefreshNeedHeld,
      refusal: Option[(com.histmersion.api.automation.PlanQuota.RefreshNeedRefusalPart1, Option[com.histmersion.api.automation.PlanQuota.RefreshNeedRefusalPart2])],
      rules: Rules,
      now: com.histmersion.api.automation.PlanQuota.RefreshNeedNow,
      model: Option[com.histmersion.api.automation.PlanQuota.RefreshNeedModel] = None
  ): Option[RefreshNeed] =
    if held || refusal.exists(_._2.forall(((_).value > now).value)) then None
    else
      val states = current(readings, now, model).map(windowState(_, rules, now))
      if states.isEmpty then Some(RefreshNeed.NoReading)
      else
        val blocking = states.filterNot(_.usable)
        Option.when(blocking.nonEmpty && blocking.forall(s => s.stale && !s.reading.rejected && s.leftPct > s.floor))(
          RefreshNeed.StaleOnly
        )

  def throttled(lastAttempt: Option[com.histmersion.api.automation.PlanQuota.ThrottledLastAttempt], minGapMs: com.histmersion.api.automation.PlanQuota.ThrottledMinGapMs, now: com.histmersion.api.automation.PlanQuota.ThrottledNow): com.histmersion.api.automation.PlanQuota.ThrottledResult =
    lastAttempt.exists((t => (now).value - t < minGapMs).value)

  def refreshDecision(
      need: Option[RefreshNeed],
      login: Login,
      enabled: com.histmersion.api.automation.PlanQuota.RefreshDecisionEnabled,
      lastAttempt: Option[com.histmersion.api.automation.PlanQuota.RefreshDecisionLastAttempt],
      minGapMs: com.histmersion.api.automation.PlanQuota.RefreshDecisionMinGapMs,
      now: com.histmersion.api.automation.PlanQuota.RefreshDecisionNow,
      force: com.histmersion.api.automation.PlanQuota.RefreshDecisionForce = _root_.com.histmersion.api.automation.PlanQuota.RefreshDecisionForce(false)
  ): Either[com.histmersion.api.automation.PlanQuota.RefreshDecisionResultPart1, Option[RefreshNeed]] =
    login match
      case _ if !(enabled).value                              => Left(_root_.com.histmersion.api.automation.PlanQuota.RefreshDecisionResultPart1("refresh is off for this pool"))
      case Login.Missing(why)                         => Left(_root_.com.histmersion.api.automation.PlanQuota.RefreshDecisionResultPart1(s"no login: $why"))
      case _ if (force).value                                 => Right(need)
      case _ if need.isEmpty                          => Left(_root_.com.histmersion.api.automation.PlanQuota.RefreshDecisionResultPart1("the reading decides by itself"))
      case _ if throttled(lastAttempt, minGapMs, now) =>
        Left(_root_.com.histmersion.api.automation.PlanQuota.RefreshDecisionResultPart1("a fresh reading was taken (or tried) less than stale-minutes ago"))
      case _ => Right(need)

  final case class WindowStatus(
      limit: WindowStatus.Limit,
      kind: WindowStatus.Kind,
      usedPct: WindowStatus.UsedPct,
      leftPct: WindowStatus.LeftPct,
      floor: WindowStatus.Floor,
      usable: WindowStatus.Usable,
      stale: WindowStatus.Stale,
      resetsAt: Option[WindowStatus.ResetsAt],
      observedAt: WindowStatus.ObservedAt,
      ageSeconds: WindowStatus.AgeSeconds,
      source: WindowStatus.Source,
      why: Option[WindowStatus.Why] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object WindowStatus:
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UsedPct = UsedPct.T
    object UsedPct extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LeftPct = LeftPct.T
    object LeftPct extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Floor = Floor.T
    object Floor extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Usable = Usable.T
    object Usable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Stale = Stale.T
    object Stale extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResetsAt = ResetsAt.T
    object ResetsAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ObservedAt = ObservedAt.T
    object ObservedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AgeSeconds = AgeSeconds.T
    object AgeSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Source = Source.T
    object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class PoolStatus(
      pool: PoolStatus.Pool,
      title: PoolStatus.Title,
      kind: PoolStatus.Kind,
      covers: List[PoolStatus.Covers],
      floors: PoolStatus.Floors,
      floorsSource: PoolStatus.FloorsSource,
      held: PoolStatus.Held,
      usable: PoolStatus.Usable,
      reason: Option[PoolStatus.Reason],
      until: Option[PoolStatus.Until],
      maxCallsInFlight: PoolStatus.MaxCallsInFlight,
      windows: List[WindowStatus],
      planType: Option[PoolStatus.PlanType] = None,
      note: Option[PoolStatus.Note] = None,
      available: PoolStatus.Available = _root_.com.histmersion.api.automation.PlanQuota.PoolStatus.Available(true),
      login: Option[PoolStatus.Login] = None,
      refresh: Option[PoolStatus.Refresh] = None,
      refreshesToday: PoolStatus.RefreshesToday = _root_.com.histmersion.api.automation.PlanQuota.PoolStatus.RefreshesToday(0),
      lastRefresh: Option[RefreshInfo] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object PoolStatus:
    type Pool = Pool.T
    object Pool extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Covers = Covers.T
    object Covers extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Floors = Floors.T
    object Floors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FloorsSource = FloorsSource.T
    object FloorsSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Held = Held.T
    object Held extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Usable = Usable.T
    object Usable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Until = Until.T
    object Until extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxCallsInFlight = MaxCallsInFlight.T
    object MaxCallsInFlight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlanType = PlanType.T
    object PlanType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Note = Note.T
    object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Available = Available.T
    object Available extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Login = Login.T
    object Login extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Refresh = Refresh.T
    object Refresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefreshesToday = RefreshesToday.T
    object RefreshesToday extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class RefreshInfo(at: RefreshInfo.At, mode: RefreshInfo.Mode, trigger: RefreshInfo.Trigger, ok: RefreshInfo.Ok, detail: RefreshInfo.Detail)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object RefreshInfo:
    type At = At.T
    object At extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Mode = Mode.T
    object Mode extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Trigger = Trigger.T
    object Trigger extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Ok = Ok.T
    object Ok extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Detail = Detail.T
    object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class QuotaRefresh(pool: Option[QuotaRefresh.Pool] = None) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object QuotaRefresh:
    type Pool = Pool.T
    object Pool extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class PoolRefreshed(pool: PoolRefreshed.Pool, refreshed: PoolRefreshed.Refreshed, detail: PoolRefreshed.Detail, status: PoolStatus)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object PoolRefreshed:
    type Pool = Pool.T
    object Pool extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Refreshed = Refreshed.T
    object Refreshed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Detail = Detail.T
    object Detail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class QuotaRefreshed(pools: List[PoolRefreshed], lifted: List[QuotaRefreshed.Lifted] = Nil)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object QuotaRefreshed:
    type Lifted = Lifted.T
    object Lifted extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class QuotaInfo(
      plans: List[PoolStatus],
      metered: List[ModelChains.AllowanceStatus],
      fallthrough: List[QuotaInfo.Fallthrough],
      now: QuotaInfo.Now,
      cooldowns: List[Cooldown] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object QuotaInfo:
    type Fallthrough = Fallthrough.T
    object Fallthrough extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Now = Now.T
    object Now extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Cooldown(model: Cooldown.Model, until: Cooldown.Until, reason: Cooldown.Reason)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Cooldown:
    type Model = Model.T
    object Model extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Until = Until.T
    object Until extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class PoolSet(hold: Option[PoolSet.Hold] = None, floors: Option[PoolSet.Floors] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object PoolSet:
    type Hold = Hold.T
    object Hold extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Floors = Floors.T
    object Floors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class FallthroughSet(fallthrough: FallthroughSet.Fallthrough) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

  object FallthroughSet:
    type Fallthrough = Fallthrough.T
    object Fallthrough extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  /** A Claude Code mod's report: `$.session.usage().rateLimits` (`{kind, percentUsed, resetsAt}`). */
  final case class ModWindow(kind: ModWindow.Kind, percentUsed: ModWindow.PercentUsed, resetsAt: Option[ModWindow.ResetsAt] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ModWindow:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PercentUsed = PercentUsed.T
    object PercentUsed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResetsAt = ResetsAt.T
    object ResetsAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ModReport(source: ModReport.Source, rateLimits: List[ModWindow])
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ModReport:
    type Source = Source.T
    object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class GuardAnswer(allow: GuardAnswer.Allow, reason: Option[GuardAnswer.Reason] = None, resetsAt: Option[GuardAnswer.ResetsAt] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object GuardAnswer:
    type Allow = Allow.T
    object Allow extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ResetsAt = ResetsAt.T
    object ResetsAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
