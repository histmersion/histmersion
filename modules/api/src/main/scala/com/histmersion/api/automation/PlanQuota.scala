package com.histmersion
package api.automation

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.library.Source
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** A plan is spent by every client of the account, and the provider says how much is left only as a percentage of each
  * window plus its reset, so a plan is governed by floors rather than budgets.
  */
object PlanQuota:
  type Now = Now.T
  object Now extends Newtype[Long, Pure]
  type PoolIdText = PoolIdText.T
  object PoolIdText extends Newtype[String, Pure]
  type WindowStateNow = Now
  val WindowStateNow: Now.type = Now
  type VerdictNow = Now
  val VerdictNow: Now.type = Now
  export ModelChains.ChainEntry.Model as VerdictModel
  type RefreshNeedNow = Now
  val RefreshNeedNow: Now.type = Now
  export ModelChains.ChainEntry.Model as RefreshNeedModel
  export RefreshInfo.LastAttempt as RefreshDecisionLastAttempt
  export Rules.MinGap as RefreshDecisionMinGapMs
  type RefreshDecisionNow = Now
  val RefreshDecisionNow: Now.type = Now

  import Agents.{ModelRef, ProviderKind}

  enum PoolId(val wire: PoolId.Wire, val title: PoolId.Title) derives CanEqual:
    case ClaudePlan extends PoolId(PoolId.Wire("claude-plan"), PoolId.Title("Claude plan"))
    case ChatgptPlan extends PoolId(PoolId.Wire("chatgpt-plan"), PoolId.Title("ChatGPT plan"))

  object PoolId:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    type Title = Title.T
    object Title extends Newtype[String, Pure]

    def parse(s: Wire): Option[PoolId] = values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

  /** A CLI configured to keep an API key in its environment (`use-api-key`) bills the key, not the plan. */
  final case class Logins(
      claudeCodeApiKey: Logins.ClaudeCodeApiKey = Logins.ClaudeCodeApiKey(false),
      codexApiKey: Logins.CodexApiKey = Logins.CodexApiKey(false),
      opencodeChatgpt: Logins.OpencodeChatgpt = Logins.OpencodeChatgpt(false)
  )

  object Logins:
    type ClaudeCodeApiKey = ClaudeCodeApiKey.T
    object ClaudeCodeApiKey extends Newtype[Boolean, Pure]
    type CodexApiKey = CodexApiKey.T
    object CodexApiKey extends Newtype[Boolean, Pure]
    type OpencodeChatgpt = OpencodeChatgpt.T
    object OpencodeChatgpt extends Newtype[Boolean, Pure]

  def poolOf(m: ModelRef, logins: Logins): Option[PoolId] = m.provider match
    case ProviderKind.ClaudeCode if !((logins.claudeCodeApiKey) == Logins.ClaudeCodeApiKey(true)) =>
      Some(PoolId.ClaudePlan)
    case ProviderKind.Codex if !((logins.codexApiKey) == Logins.CodexApiKey(true)) => Some(PoolId.ChatgptPlan)
    case ProviderKind.Opencode
        if ((logins.opencodeChatgpt) == Logins.OpencodeChatgpt(true)) && m.model.value.startsWith("openai/") =>
      Some(PoolId.ChatgptPlan)
    case _ => None

  def paid(m: ModelRef, logins: Logins): Boolean =
    (m.provider != ProviderKind.Local && poolOf(m, logins).isEmpty)

  enum Source(val wire: Source.Wire) derives CanEqual:
    case Run extends Source(Source.Wire("run"))

    case Mod extends Source(Source.Wire("mod"))

    case Interactive extends Source(Source.Wire("interactive"))

    case AppServer extends Source(Source.Wire("app-server"))

    case Limit extends Source(Source.Wire("limit"))

    case Probe extends Source(Source.Wire("probe"))

  object Source:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[Source] = values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

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
    object Limit extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type UsedPct = UsedPct.T
    object UsedPct extends Newtype[Double, Pure]
    type ResetsAt = ResetsAt.T
    object ResetsAt extends Newtype[Long, Pure]
    type Rejected = Rejected.T
    object Rejected extends Newtype[Boolean, Pure]
    type ObservedAt = ObservedAt.T
    object ObservedAt extends Newtype[Long, Pure]
    export ModelChains.ChainEntry.Model

  final case class Floors(byKind: Map[Floors.ByKindKey, Floors.ByKindValue]):
    def text: Floors.Wire = Floors.Wire(byKind.toList.sortBy(_._1).map((k, v) => s"$k=$v").mkString(", "))
    def of(limit: Floors.OfLimit, kind: Floors.OfKind, default: Rules.DefaultFloor): Floors.Floor =
      byKind.get(Floors.ByKindKey(s"$limit/$kind")).orElse(byKind.get(Floors.ByKindKey(kind.value))).getOrElse(default)

  object Floors:
    type Floor = Floor.T
    object Floor extends Newtype[Int, Pure]
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    export Reading.{Kind as OfKind, Limit as OfLimit}

    type ByKindKey = ByKindKey.T
    object ByKindKey extends Newtype[String, Pure]
    type ByKindValue = Floor
    val ByKindValue: Floor.type = Floor

    val Empty: Floors = Floors(Map.empty)
    def parse(s: Wire): Either[Problem, Floors] =
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
        case Nil  => Right(Floors(parsed.collect { case Right((k, v)) => ByKindKey(k) -> Floor(v) }.toMap))
        case errs => Left(Problem.Unclassified(Legacy.Text(errs.mkString("; "))))

  final case class Rules(
      floors: Floors,
      defaultFloor: Rules.DefaultFloor,
      staleMs: Rules.StaleMs,
      marginPct: Rules.MarginPct
  )

  object Rules:
    type MinGap = MinGap.T
    object MinGap extends Newtype[Long, Pure]
    export Floors.Floor as DefaultFloor
    type StaleMs = StaleMs.T
    object StaleMs extends Newtype[Long, Pure]
    type MarginPct = MarginPct.T
    object MarginPct extends Newtype[Int, Pure]

  final case class WindowState(
      reading: Reading,
      floor: WindowState.Floor,
      leftPct: WindowState.LeftPct,
      stale: WindowState.Stale,
      usable: WindowState.Usable,
      why: Option[Problem]
  )

  object WindowState:
    export Floors.Floor
    export WindowStatus.LeftPct
    type Stale = Stale.T
    object Stale extends Newtype[Boolean, Pure]
    type Usable = Usable.T
    object Usable extends Newtype[Boolean, Pure]

  enum Verdict derives CanEqual:
    case Usable(note: Option[Usable.Note])

    case Blocked(reason: Problem, until: Option[Blocked.Until])

    def usable: Boolean = (this.isInstanceOf[Usable])

  object Verdict:
    object Usable:
      type Note = Note.T
      object Note extends Newtype[String, Pure]

    object Blocked:
      export Reading.ResetsAt as Until

  def current(readings: List[Reading], now: Now, model: Option[ModelChains.ChainEntry.Model] = None): List[Reading] =
    readings
      .filter(r => r.resetsAt.forall(_.value > now.value))
      .filter(r => r.model.isEmpty || model.isEmpty || r.model == model)
      .groupBy(r => (r.limit, r.kind))
      .values
      .map(_.maxBy(_.observedAt))
      .toList
      .sortBy(r => (r.limit, r.kind))

  def windowState(r: Reading, rules: Rules, now: WindowStateNow): WindowState =
    val floor = rules.floors.of(r.limit, r.kind, rules.defaultFloor)
    val left = (100.0 - (r.usedPct).value).max(0)
    val stale = now.value - r.observedAt.value > rules.staleMs.value
    val pct = (d: Double) =>
      java.math.BigDecimal(d).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros.toPlainString
    val why =
      if (r.rejected) == Reading.Rejected(true) then Some(s"${r.kind}: the provider refuses it")
      else if !stale && left <= floor.value then Some(s"${r.kind}: ${pct(left)}% left, floor $floor%")
      else if stale && left < floor.value + rules.marginPct.value then
        Some(
          s"${r.kind}: ${pct(left)}% left when last read, floor $floor% (+${rules.marginPct} while the reading is stale)"
        )
      else None
    WindowState(
      r,
      floor,
      WindowStatus.LeftPct(left),
      WindowState.Stale(stale),
      WindowState.Usable(why.isEmpty),
      (why).map(refactorValue4 => Problem.Unclassified(Legacy.Text(refactorValue4)))
    )

  def verdict(
      readings: List[Reading],
      held: PoolStatus.Held,
      refusal: Option[(Problem, Option[Verdict.Blocked.Until])],
      rules: Rules,
      now: VerdictNow,
      model: Option[PlanQuota.VerdictModel] = None
  ): (Verdict, List[WindowState]) =
    val states = current(readings, now, model).map(windowState(_, rules, now))
    val blocking = states.filterNot((refactorCallback => ((refactorCallback.usable) == WindowState.Usable(true))))
    val earliest = blocking.flatMap(_.reading.resetsAt).minOption
    val v =
      if (held).value then
        Verdict.Blocked(Problem.Unclassified(Legacy.Text("held: a person paused all agent use of this plan")), None)
      else
        refusal.filter(_._2.forall(_.value > now.value)) match
          case Some((why, until))        => Verdict.Blocked(why, until)
          case None if blocking.nonEmpty =>
            Verdict.Blocked(
              Problem.Unclassified(Legacy.Text(blocking.flatMap(_.why).map(_.sentence).mkString("; "))),
              earliest
            )
          case None if states.isEmpty =>
            Verdict.Usable(Some(Verdict.Usable.Note("no reading yet: unknown until a run reads it")))
          case None if states.exists((refactorCallback => ((refactorCallback.stale) == WindowState.Stale(true)))) =>
            Verdict.Usable(Some(Verdict.Usable.Note("the last reading is stale")))
          case None => Verdict.Usable(None)
    (v, states)

  enum RefreshMode(val wire: RefreshMode.Wire) derives CanEqual:
    case Auto extends RefreshMode(RefreshMode.Wire("auto"))
    case Probe extends RefreshMode(RefreshMode.Wire("probe"))
    case Off extends RefreshMode(RefreshMode.Wire("off"))

  object RefreshMode:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[RefreshMode] =
      values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

  enum Login derives CanEqual:
    case Present(detail: Present.Detail)
    case Missing(why: Problem)
    case Unknown(why: Problem)

  object Login:
    object Present:
      type Detail = Detail.T
      object Detail extends Newtype[String, Pure]

  enum RefreshNeed(val text: RefreshNeed.Text) derives CanEqual:
    case StaleOnly extends RefreshNeed(RefreshNeed.Text("the reading is stale"))

    case NoReading extends RefreshNeed(RefreshNeed.Text("no reading yet"))

  object RefreshNeed:
    type Text = Text.T
    object Text extends Newtype[String, Pure]

  enum RefreshTrigger(val wire: RefreshTrigger.Wire) derives CanEqual:
    case Pick extends RefreshTrigger(RefreshTrigger.Wire("pick"))

    case Paused extends RefreshTrigger(RefreshTrigger.Wire("paused-run"))

    case Command extends RefreshTrigger(RefreshTrigger.Wire("command"))

    case Schedule extends RefreshTrigger(RefreshTrigger.Wire("schedule"))

  object RefreshTrigger:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

  def refreshNeed(
      readings: List[Reading],
      held: PoolStatus.Held,
      refusal: Option[(Problem, Option[Verdict.Blocked.Until])],
      rules: Rules,
      now: RefreshNeedNow,
      model: Option[PlanQuota.RefreshNeedModel] = None
  ): Option[RefreshNeed] =
    if ((held) == PoolStatus.Held(true)) || refusal.exists(_._2.forall(_.value > now.value)) then None
    else
      val states = current(readings, now, model).map(windowState(_, rules, now))
      if states.isEmpty then Some(RefreshNeed.NoReading)
      else
        val blocking = states.filterNot((refactorCallback => ((refactorCallback.usable) == WindowState.Usable(true))))
        Option.when(
          blocking.nonEmpty && blocking.forall(s =>
            ((s.stale) == WindowState.Stale(true)) && !((s.reading.rejected) == Reading.Rejected(
              true
            )) && s.leftPct.value > s.floor.value
          )
        )(
          RefreshNeed.StaleOnly
        )

  def throttled(lastAttempt: Option[RefreshInfo.LastAttempt], minGapMs: Rules.MinGap, now: Now): Boolean =
    lastAttempt.exists(t => now.value - t.value < minGapMs.value)

  def refreshDecision(
      need: Option[RefreshNeed],
      login: Login,
      enabled: Boolean,
      lastAttempt: Option[PlanQuota.RefreshDecisionLastAttempt],
      minGapMs: PlanQuota.RefreshDecisionMinGapMs,
      now: RefreshDecisionNow,
      force: Boolean = (false)
  ): Either[Problem, Option[RefreshNeed]] =
    login match
      case _ if !(enabled)    => Left(Problem.Unclassified(Legacy.Text("refresh is off for this pool")))
      case Login.Missing(why) => Left(Problem.Unclassified(Legacy.Text(s"no login: ${why.sentence}")))
      case _ if (force)       => Right(need)
      case _ if need.isEmpty  => Left(Problem.Unclassified(Legacy.Text("the reading decides by itself")))
      case _ if ((throttled(lastAttempt, minGapMs, now)) == (true)) =>
        Left(Problem.Unclassified(Legacy.Text("a fresh reading was taken (or tried) less than stale-minutes ago")))
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
      why: Option[Problem] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object WindowStatus:
    export Reading.Limit
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    export Reading.UsedPct
    type LeftPct = LeftPct.T
    object LeftPct extends Newtype[Double, Pure]
    export Floors.Floor
    type Usable = Usable.T
    object Usable extends Newtype[Boolean, Pure]
    type Stale = Stale.T
    object Stale extends Newtype[Boolean, Pure]
    type ResetsAt = ResetsAt.T
    object ResetsAt extends Newtype[String, Pure]
    type ObservedAt = ObservedAt.T
    object ObservedAt extends Newtype[String, Pure]
    type AgeSeconds = AgeSeconds.T
    object AgeSeconds extends Newtype[Long, Pure]
    type Source = Source.T
    object Source extends Newtype[String, Pure]

  final case class PoolStatus(
      pool: PoolStatus.Pool,
      title: PoolStatus.Title,
      kind: PoolStatus.Kind,
      covers: List[PoolStatus.Covers],
      floors: PoolStatus.Floors,
      floorsSource: PoolStatus.FloorsSource,
      held: PoolStatus.Held,
      usable: PoolStatus.Usable,
      reason: Option[Problem],
      until: Option[PoolStatus.Until],
      maxCallsInFlight: PoolStatus.MaxCallsInFlight,
      windows: List[WindowStatus],
      planType: Option[PoolStatus.PlanType] = None,
      note: Option[PoolStatus.Note] = None,
      available: PoolStatus.Available = PoolStatus.Available(true),
      login: Option[PoolStatus.Login] = None,
      refresh: Option[PoolStatus.Refresh] = None,
      refreshesToday: PoolStatus.RefreshesToday = PoolStatus.RefreshesToday(0),
      lastRefresh: Option[RefreshInfo] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object PoolStatus:
    export PoolId.{Title, Wire as Pool}
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Covers = Covers.T
    object Covers extends Newtype[String, Pure]
    type Floors = Floors.T
    object Floors extends Newtype[String, Pure]
    type FloorsSource = FloorsSource.T
    object FloorsSource extends Newtype[String, Pure]
    type Held = Held.T
    object Held extends Newtype[Boolean, Pure]
    type Usable = Usable.T
    object Usable extends Newtype[Boolean, Pure]
    type Until = Until.T
    object Until extends Newtype[String, Pure]
    type MaxCallsInFlight = MaxCallsInFlight.T
    object MaxCallsInFlight extends Newtype[Int, Pure]
    type PlanType = PlanType.T
    object PlanType extends Newtype[String, Pure]
    type Note = Note.T
    object Note extends Newtype[String, Pure]
    type Available = Available.T
    object Available extends Newtype[Boolean, Pure]
    type Login = Login.T
    object Login extends Newtype[String, Pure]
    type Refresh = Refresh.T
    object Refresh extends Newtype[String, Pure]
    type RefreshesToday = RefreshesToday.T
    object RefreshesToday extends Newtype[Int, Pure]

  final case class RefreshInfo(
      at: RefreshInfo.At,
      mode: RefreshInfo.Mode,
      trigger: RefreshInfo.Trigger,
      ok: RefreshInfo.Ok,
      detail: RefreshInfo.Detail
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object RefreshInfo:
    type LastAttempt = LastAttempt.T
    object LastAttempt extends Newtype[Long, Pure]
    type At = At.T
    object At extends Newtype[String, Pure]
    export RefreshMode.Wire as Mode
    export RefreshTrigger.Wire as Trigger
    type Ok = Ok.T
    object Ok extends Newtype[Boolean, Pure]
    type Detail = Detail.T
    object Detail extends Newtype[String, Pure]

  final case class QuotaRefresh(pool: Option[QuotaRefresh.Pool] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object QuotaRefresh:
    export PoolId.Wire as Pool

  final case class PoolRefreshed(
      pool: PoolRefreshed.Pool,
      refreshed: PoolRefreshed.Refreshed,
      detail: PoolRefreshed.Detail,
      status: PoolStatus
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object PoolRefreshed:
    export PoolId.Wire as Pool
    type Refreshed = Refreshed.T
    object Refreshed extends Newtype[Boolean, Pure]
    type Detail = Detail.T
    object Detail extends Newtype[String, Pure]

  final case class QuotaRefreshed(pools: List[PoolRefreshed], lifted: List[QuotaRefreshed.Lifted] = Nil)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object QuotaRefreshed:
    type Lifted = Lifted.T
    object Lifted extends Newtype[String, Pure]

  final case class QuotaInfo(
      plans: List[PoolStatus],
      metered: List[ModelChains.AllowanceStatus],
      fallthrough: List[QuotaInfo.Fallthrough],
      now: QuotaInfo.Now,
      cooldowns: List[Cooldown] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object QuotaInfo:
    type Fallthrough = Fallthrough.T
    object Fallthrough extends Newtype[String, Pure]
    type Now = Now.T
    object Now extends Newtype[String, Pure]

  final case class Cooldown(model: Cooldown.Model, until: Cooldown.Until, reason: Problem)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Cooldown:
    export ModelChains.ChainEntry.Model
    type Until = Until.T
    object Until extends Newtype[String, Pure]

  final case class PoolSet(hold: Option[PoolSet.Hold] = None, floors: Option[PoolSet.Floors] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object PoolSet:
    type Hold = Hold.T
    object Hold extends Newtype[Boolean, Pure]
    type Floors = Floors.T
    object Floors extends Newtype[String, Pure]

  final case class FallthroughSet(fallthrough: FallthroughSet.Fallthrough)
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object FallthroughSet:
    type Fallthrough = Fallthrough.T
    object Fallthrough extends Newtype[Boolean, Pure]

  /** A Claude Code mod's report: `$.session.usage().rateLimits` (`{kind, percentUsed, resetsAt}`). */
  final case class ModWindow(
      kind: ModWindow.Kind,
      percentUsed: ModWindow.PercentUsed,
      resetsAt: Option[ModWindow.ResetsAt] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object ModWindow:
    export WindowStatus.Kind
    export Reading.UsedPct as PercentUsed
    type ResetsAt = ResetsAt.T
    object ResetsAt extends Newtype[String, Pure]

  final case class ModReport(source: ModReport.Source, rateLimits: List[ModWindow])
      derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object ModReport:
    type Source = Source.T
    object Source extends Newtype[String, Pure]

  final case class GuardAnswer(
      allow: GuardAnswer.Allow,
      reason: Option[Problem] = None,
      resetsAt: Option[GuardAnswer.ResetsAt] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object GuardAnswer:
    type Allow = Allow.T
    object Allow extends Newtype[Boolean, Pure]
    type ResetsAt = ResetsAt.T
    object ResetsAt extends Newtype[String, Pure]
