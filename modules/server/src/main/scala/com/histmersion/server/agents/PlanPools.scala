package com.histmersion.server.agents

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.database.Transactor

import com.histmersion.domain.automation.agents.*

import com.histmersion.config.{AgentsConfig, ScheduledRefresh}
import com.histmersion.domain.{PlanReadSchedule, ScheduledRead}
import com.histmersion.api.automation.PlanQuota
import com.histmersion.api.automation.Agents.{ModelRef, ProviderKind}
import com.histmersion.api.automation.PlanQuota.*
import com.histmersion.domain.automation.agents.PlanQuotaRepo
import io.circe.{parser, Json}

import java.nio.charset.StandardCharsets
import java.util.concurrent.{ConcurrentHashMap, Semaphore, TimeUnit}
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import scala.jdk.CollectionConverters.*

/** The subscription pools in effect (docs/research-plan-quotas.md §5): the readings every source writes, the floors
  * (saved over config), the hold switch, the provider's own refusals — and the verdict the model pick asks before every
  * LLM call. `codexHome` is the CODEX_HOME the runs use, so the app-server read is of THAT login.
  *
  * A pick that WANTS a pool ([[decide]]) whose verdict hangs only on an old reading, or that was never read, takes a
  * fresh reading first (§8): the ChatGPT plan through the app-server, the Claude plan through its probe — one at a time
  * per pool ([[RefreshGate]]), at most once per `stale-minutes`. A pool whose runtime has no login is unavailable,
  * never offered. `seams` are how a test stands in for the CLIs.
  */
final class PlanPools(
    ds: DataSource,
    cfg: => AgentsConfig,
    codexHome: () => Option[java.nio.file.Path],
    seams: PlanPools.Seams = PlanPools.Seams.Real
)(using com.histmersion.util.system.JvmHome) extends PoolSink:

  private val slots = ConcurrentHashMap[PoolId, Semaphore]()
  private val lastPoll = AtomicLong(0L)
  private val gate = RefreshGate()
  private val logins = ConcurrentHashMap[PoolId, (Long, Login)]()

  private def poolCfg(p: PoolId): (com.histmersion.server.agents.PlanPools.PoolName, com.histmersion.server.agents.PlanPools.Limit) = p match
    case PoolId.ClaudePlan  => (cfg.quota.claudePlan.floors, cfg.quota.claudePlan.maxCallsInFlight)
    case PoolId.ChatgptPlan => (cfg.quota.chatgptPlan.floors, cfg.quota.chatgptPlan.maxCallsInFlight)

  private def staleMs: com.histmersion.server.agents.PlanPools.StaleDuration = math.max(1, cfg.quota.staleMinutes).toLong * 60000L

  /** At most `max-calls-in-flight` LLM calls draw on a plan at once (default 1: the three-way review panel would
    * otherwise spend the last headroom three times).
    */
  def slot(p: PoolId): Semaphore = slots.computeIfAbsent(p, _ => Semaphore(math.max(1, poolCfg(p)._2)))

  def poolOf(m: ModelRef): Option[PoolId] = PlanQuota.poolOf(m, cfg.logins)
  def paid(m: ModelRef): Boolean = PlanQuota.paid(m, cfg.logins)

  /** The floors of a pool and where they come from: saved, else config (a config string that does not parse floors
    * nothing it could not read, and says so).
    */
  def floors(p: PoolId): (Floors, com.histmersion.server.agents.PlanPools.WindowName, Option[com.histmersion.server.agents.PlanPools.Problem]) =
    val saved = Transactor(ds).read(PlanQuotaRepo.pool(p)).floors
    saved.map(Floors.parse) match
      case Some(Right(f)) => (f, "saved", None)
      case Some(Left(m))  => (Floors.Empty, "saved", Some(s"saved floors do not parse: $m"))
      case None           =>
        Floors.parse(poolCfg(p)._1) match
          case Right(f) => (f, "config", None)
          case Left(m)  => (Floors.Empty, "config", Some(s"agents.quota.${p.wire}.floors: $m"))

  def rules(p: PoolId): Rules =
    Rules(floors(p)._1, cfg.quota.defaultFloor, staleMs, cfg.quota.margin)

  /** The verdict for one call on `p` (and its model's own buckets). The ChatGPT pool is read from the app-server first
    * when its last reading is older than `poll-seconds`. Takes no fresh reading of its own: [[decide]] does, for a
    * pick.
    */
  def verdict(p: PoolId, model: Option[com.histmersion.server.agents.PlanPools.ModelName], now: com.histmersion.server.agents.PlanPools.Instant = System.currentTimeMillis()): (Verdict, List[WindowState]) =
    if p == PoolId.ChatgptPlan then poll(now)
    val pool = Transactor(ds).read(PlanQuotaRepo.pool(p))
    PlanQuota.verdict(
      Transactor(ds).read(PlanQuotaRepo.readings(p)),
      pool.held,
      pool.refusal.map(_ -> pool.refusalUntil),
      rules(p),
      now,
      model
    )

  /** Why `p`'s verdict would wait for a fresh reading now (None = it decides by itself). */
  def need(p: PoolId, model: Option[com.histmersion.server.agents.PlanPools.ModelName], now: com.histmersion.server.agents.PlanPools.Instant): Option[RefreshNeed] =
    val pool = Transactor(ds).read(PlanQuotaRepo.pool(p))
    PlanQuota.refreshNeed(
      Transactor(ds).read(PlanQuotaRepo.readings(p)),
      pool.held,
      pool.refusal.map(_ -> pool.refusalUntil),
      rules(p),
      now,
      model
    )

  /** The verdict for a call that WANTS `p`: when it would hang only on a stale reading, or there is no reading at all,
    * a fresh one is taken first (throttled, single flight) and the verdict asked again. The third value says what the
    * refresh did, for the run's trace.
    */
  def decide(
      p: PoolId,
      model: Option[com.histmersion.server.agents.PlanPools.ModelName],
      trigger: RefreshTrigger,
      now: com.histmersion.server.agents.PlanPools.Instant = System.currentTimeMillis()
  ): (Verdict, List[WindowState], Option[Refreshed]) =
    val (v, states) = verdict(p, model, now)
    if need(p, model, now).isEmpty then (v, states, None)
    else
      refreshPool(p, trigger, model, force = false, now) match
        // not taken — perhaps because the caller waited while another took one: ask again either way
        case Left(_) =>
          val (v2, s2) = verdict(p, model, System.currentTimeMillis())
          (v2, s2, None)
        case Right(r) =>
          val (v2, s2) = verdict(p, model, System.currentTimeMillis())
          (v2, s2, Some(r))

  def record(p: PoolId, rs: List[Reading]): Unit = Transactor(ds).transact(PlanQuotaRepo.record(p, rs))

  /** When `p`'s newest reading was observed (epoch ms), if it has one. */
  def lastObserved(p: PoolId): Option[com.histmersion.server.agents.PlanPools.Instant] = Transactor(ds).read(PlanQuotaRepo.readings(p)).map(_.observedAt).maxOption

  def blocked(p: PoolId, model: Option[com.histmersion.server.agents.PlanPools.ModelName]): Option[(com.histmersion.server.agents.PlanPools.Reason, Option[com.histmersion.server.agents.PlanPools.Instant])] =
    verdict(p, model)._1 match
      case Verdict.Blocked(why, until) => Some((why, until))
      case _                           => None

  def refresh(now: com.histmersion.server.agents.PlanPools.Instant): Unit = { val _ = poll(now) }

  def hold(p: PoolId, on: com.histmersion.server.agents.PlanPools.Held): Unit = Transactor(ds).transact(PlanQuotaRepo.setHold(p, on))

  /** Whether a person holds `p` (no run draws on it until it is released). */
  def held(p: PoolId): Boolean = Transactor(ds).read(PlanQuotaRepo.pool(p)).held
  def setFloors(p: PoolId, f: Option[com.histmersion.server.agents.PlanPools.FloorsSpec]): Unit = Transactor(ds).transact(PlanQuotaRepo.setFloors(p, f))

  // ── logins ─────────────────────────────────────────────────────────────────────────────────────

  /** Whether the runtime `p` is read and spent through is signed in — `claude auth status` for the Claude plan, a login
    * in the runs' CODEX_HOME for the ChatGPT plan (the file is never read) — checked at most once per `poll-seconds`
    * (`force`: now).
    */
  def login(p: PoolId, now: com.histmersion.server.agents.PlanPools.Instant = System.currentTimeMillis(), force: com.histmersion.server.agents.PlanPools.Force = false): Login =
    val every = math.max(1, cfg.quota.pollSeconds).toLong * 1000L
    Option(logins.get(p)).filter((at, _) => !force && now - at < every).map(_._2).getOrElse {
      val l = p match
        case PoolId.ClaudePlan  => claudeLogin()
        case PoolId.ChatgptPlan => PlanPools.codexLogin(codexHome(), cfg.providers.codex.command)
      val _ = logins.put(p, (now, l))
      l
    }

  private def claudeLogin(): Login =
    val cc = cfg.providers.claudeCode
    seams.runCommand(
      List(cc.command, "auth", "status"),
      PlanPools.claudeEnv(cc),
      math.max(1, cfg.doctorTimeoutSeconds)
    ) match
      case Left(why)       => Login.Unknown(s"'claude auth status' failed: $why")
      case Right((_, out)) => PlanPools.claudeLogin(out, cc.useApiKey)

  /** Whether `m` may be offered at all: a model whose own runtime spends the pool (claude-code on the Claude plan,
    * codex on the ChatGPT plan) needs that runtime's login. Some(why) = unavailable.
    */
  def unavailable(m: ModelRef, now: com.histmersion.server.agents.PlanPools.Instant = System.currentTimeMillis()): Option[com.histmersion.server.agents.PlanPools.Reason] =
    poolOf(m).filter(p => PlanPools.loginGates(p, m)).flatMap { p =>
      login(p, now) match
        case Login.Missing(why) => Some(s"unavailable: no login — $why")
        case _                  => None
    }

  // ── fresh readings ─────────────────────────────────────────────────────────────────────────────

  private def claudeMode: Either[com.histmersion.server.agents.PlanPools.Problem, RefreshMode] =
    RefreshMode
      .parse(cfg.quota.claudePlan.refresh)
      .toRight(s"agents.quota.claude-plan.refresh: '${cfg.quota.claudePlan.refresh}' is not auto, probe or off")

  /** How `p` takes a fresh reading, for `agent quota`; None = it takes none. */
  def refreshMode(p: PoolId): Option[com.histmersion.server.agents.Refreshed.Mode] = p match
    case PoolId.ClaudePlan =>
      claudeMode.toOption
        .filter(_ != RefreshMode.Off)
        .map(m =>
          s"${m.wire}: one claude -p turn on ${cfg.quota.claudePlan.probeModel}, at most once per ${cfg.quota.staleMinutes} min while a run waits" +
            PlanPools.scheduleText(schedule(p))
        )
    case PoolId.ChatgptPlan =>
      Option.when(cfg.providers.codex.appServer)(
        "app-server: account/rateLimits/read, spends nothing" + PlanPools.scheduleText(schedule(p))
      )

  def schedule(p: PoolId): ScheduledRefresh = p match
    case PoolId.ClaudePlan  => cfg.quota.claudePlan.refreshEveryMinutes
    case PoolId.ChatgptPlan => cfg.quota.chatgptPlan.refreshEveryMinutes

  /** Take a fresh reading of `p` on its schedule ([[PlanReadSchedule.scheduledRead]]), inside the pool's gate: a pick
    * that took one meanwhile makes this one not due. Never without a login or a way to read. Left = not taken, and why.
    */
  def refreshOnSchedule(
      p: PoolId,
      autopilotOn: com.histmersion.server.agents.PlanPools.Autopilot,
      wanted: com.histmersion.server.agents.PlanPools.Wanted,
      now: java.time.Instant
  ): Either[com.histmersion.server.agents.PlanPools.Problem, Refreshed] =
    val at = now.toEpochMilli
    gate.exclusive(p, at) { last =>
      val newest = lastObserved(p).map(java.time.Instant.ofEpochMilli)
      PlanReadSchedule.scheduledRead(
        schedule(p).readSchedule,
        autopilotOn,
        wanted,
        newest,
        last.map(java.time.Instant.ofEpochMilli),
        now
      ) match
        case ScheduledRead.NotDue(why) => Left(why)
        case ScheduledRead.Due         =>
          PlanQuota
            .refreshDecision(
              None,
              login(p, at),
              seams.refreshes && refreshMode(p).isDefined,
              last,
              staleMs,
              at,
              force = true
            )
            .map(_ => takeAndLog(p, RefreshTrigger.Schedule, RefreshTrigger.Schedule.wire, at))
    }

  /** Take a fresh reading of `p` now — when the decision table says so ([[PlanQuota.refreshDecision]]): single flight,
    * throttled per `stale-minutes`, never without a login. `force` (a person asked) skips the need and the throttle.
    * Left = not taken, and why. Every attempt is logged (server log and the pool's refresh log).
    */
  def refreshPool(
      p: PoolId,
      trigger: RefreshTrigger,
      model: Option[com.histmersion.server.agents.PlanPools.ModelName] = None,
      force: com.histmersion.server.agents.PlanPools.Force = false,
      now: com.histmersion.server.agents.PlanPools.Instant = System.currentTimeMillis()
  ): Either[com.histmersion.server.agents.PlanPools.Problem, Refreshed] =
    gate.exclusive(p, now) { last =>
      // asked again inside the gate: a caller that waited sees the reading the one before it took
      val needNow = need(p, model, now)
      PlanQuota
        .refreshDecision(
          needNow,
          login(p, now, force),
          seams.refreshes && refreshMode(p).isDefined,
          last,
          staleMs,
          now,
          force
        )
        .map(n => takeAndLog(p, trigger, n.fold(trigger.wire)(x => s"${trigger.wire}, ${x.text}"), now))
    }

  /** One reading of `p`, logged (server log and the pool's refresh log) with why it was taken. */
  private def takeAndLog(p: PoolId, trigger: RefreshTrigger, why: com.histmersion.server.agents.PlanPools.Reason, now: com.histmersion.server.agents.PlanPools.Instant): Refreshed =
    val r = take(p, now)
    val line = s"plan refresh ${p.wire} ($why) ${r.text}"
    if r.ok then scribe.info(line) else scribe.warn(line)
    Transactor(ds).transact(PlanQuotaRepo.logRefresh(p, r.mode, trigger.wire, r.ok, r.detail))
    r

  /** One reading of `p`, recorded: the Claude probe or the ChatGPT app-server read. */
  private def take(p: PoolId, now: com.histmersion.server.agents.PlanPools.Instant): Refreshed = p match
    case PoolId.ChatgptPlan =>
      poll(now, force = true) match
        case Some(why) => Refreshed("app-server", ok = false, why)
        case None      =>
          Refreshed("app-server", ok = true, PlanPools.summary(Transactor(ds).read(PlanQuotaRepo.readings(p)), now))
    case PoolId.ClaudePlan =>
      val cc = cfg.providers.claudeCode
      val qc = cfg.quota.claudePlan
      val argv = ClaudeCode.probeArgv(cc, qc.probeModel)
      seams.runProbe(
        argv,
        env => ClaudeCode.probeEnvironment(cc, qc.probeMaxTokens, env),
        ClaudeCode.ProbePrompt,
        math.max(5, qc.probeTimeoutSeconds)
      ) match
        case Left(why)  => Refreshed("probe", ok = false, why)
        case Right(run) =>
          PlanPools.probeReadings(run, now, cc.limitCooldownMinutes) match
            case Left(why) => Refreshed("probe", ok = false, why)
            case Right(rs) =>
              record(PoolId.ClaudePlan, rs)
              Refreshed("probe", ok = true, PlanPools.summary(rs, now))

  /** Read the ChatGPT plan through `codex app-server` (it generates nothing), at most once per `poll-seconds`, and only
    * with `app-server` on and a CODEX_HOME to read.
    */
  def poll(now: com.histmersion.server.agents.PlanPools.Instant, force: com.histmersion.server.agents.PlanPools.Force = false): Option[com.histmersion.server.agents.PlanPools.Outcome] =
    val every = math.max(1, cfg.quota.pollSeconds).toLong * 1000L
    val last = lastPoll.get()
    if !cfg.providers.codex.appServer then Some("app-server reads are off (agents.providers.codex.app-server)")
    else if (!force && now - last < every) || !lastPoll.compareAndSet(last, now) then None
    else
      codexHome() match
        case None       => Some("no CODEX_HOME to read")
        case Some(home) =>
          Codex.Home.using(home)(
            seams.readCodex(cfg.providers.codex.command, home, math.max(5, cfg.doctorTimeoutSeconds))
          ) match
            case Left(why) =>
              scribe.info(s"codex app-server rate-limit read failed: $why")
              Some(why)
            case Right(json) =>
              val (rs, refusal, plan) = PlanPools.codexReadings(json, now)
              record(PoolId.ChatgptPlan, rs)
              Transactor(ds).transact(
                PlanQuotaRepo.setRefusal(PoolId.ChatgptPlan, refusal.map(_._1), refusal.flatMap(_._2), plan)
              )
              None

  /** A mod's report: recorded, and answered with whether the run may go on. */
  def guard(report: ModReport, now: com.histmersion.server.agents.PlanPools.Instant = System.currentTimeMillis()): GuardAnswer =
    val src = Source.parse(report.source).filter(s => s == Source.Mod || s == Source.Interactive).getOrElse(Source.Mod)
    record(PoolId.ClaudePlan, PlanPools.modReadings(report.rateLimits, src, now))
    verdict(PoolId.ClaudePlan, None, now)._1 match
      case Verdict.Usable(_)           => GuardAnswer(allow = true)
      case Verdict.Blocked(why, until) =>
        GuardAnswer(allow = false, Some(why), until.map(java.time.Instant.ofEpochMilli(_).toString))

  /** Every plan pool, as `agent quota` shows it. Takes no fresh reading (looking is free). */
  def statuses(covers: PoolId => List[com.histmersion.server.agents.PlanPools.ModelName], now: com.histmersion.server.agents.PlanPools.Instant = System.currentTimeMillis()): List[PoolStatus] =
    val today = java.time.Instant
      .ofEpochMilli(now)
      .atZone(java.time.ZoneOffset.UTC)
      .toLocalDate
      .atStartOfDay(java.time.ZoneOffset.UTC)
      .toInstant
      .toEpochMilli
    PoolId.values.toList.map { p =>
      val (v, states) = verdict(p, None, now)
      val (f, src, problem) = floors(p)
      val pool = Transactor(ds).read(PlanQuotaRepo.pool(p))
      val iso = (ms: Long) => java.time.Instant.ofEpochMilli(ms).toString
      val ws = states.map { s =>
        WindowStatus(
          s.reading.limit,
          s.reading.kind,
          s.reading.usedPct,
          s.leftPct,
          s.floor,
          s.usable,
          s.stale,
          s.reading.resetsAt.map(iso),
          iso(s.reading.observedAt),
          math.max(0, (now - s.reading.observedAt) / 1000),
          s.reading.source.wire,
          s.why
        )
      }
      val lg = login(p, now)
      val missing = lg match
        case Login.Missing(why) => Some(why)
        case _                  => None
      val (ok, why, until) = (v, missing) match
        case (_, Some(m))                  => (false, Some(s"unavailable: no login — $m"), None)
        case (Verdict.Usable(n), None)     => (true, n, None)
        case (Verdict.Blocked(w, u), None) => (false, Some(w), u.map(iso))
      val modeProblem = Option.when(p == PoolId.ClaudePlan)(claudeMode.left.toOption).flatten
      val (n, last) = Transactor(ds).read(PlanQuotaRepo.refreshes(p, today))
      PoolStatus(
        p.wire,
        p.title,
        "plan",
        covers(p),
        f.text,
        src,
        pool.held,
        ok,
        why,
        until,
        math.max(1, poolCfg(p)._2),
        ws,
        pool.planType,
        (problem.toList ++ modeProblem).reduceOption(_ + "; " + _),
        available = missing.isEmpty,
        login = Some(lg match
          case Login.Present(d) => d
          case Login.Missing(w) => s"no login: $w"
          case Login.Unknown(w) => s"not known: $w"),
        refresh = refreshMode(p),
        refreshesToday = n,
        lastRefresh = last
      )
    }

object PlanPools:
  type Argument = Argument.T
  object Argument extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Autopilot = Autopilot.T
  object Autopilot extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommandLine = CommandLine.T
  object CommandLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommandOutput = CommandOutput.T
  object CommandOutput extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CooldownMinutes = CooldownMinutes.T
  object CooldownMinutes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvName = EnvName.T
  object EnvName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EnvSetting = EnvSetting.T
  object EnvSetting extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExitCode = ExitCode.T
  object ExitCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FloorsSpec = FloorsSpec.T
  object FloorsSpec extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Force = Force.T
  object Force extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Held = Held.T
  object Held extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Instant = Instant.T
  object Instant extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JsonKey = JsonKey.T
  object JsonKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ModelName = ModelName.T
  object ModelName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Outcome = Outcome.T
  object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PoolName = PoolName.T
  object PoolName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QuotaNumber = QuotaNumber.T
  object QuotaNumber extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reason = Reason.T
  object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ScheduleText = ScheduleText.T
  object ScheduleText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StaleDuration = StaleDuration.T
  object StaleDuration extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StandardInput = StandardInput.T
  object StandardInput extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Summary = Summary.T
  object Summary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UseApiKey = UseApiKey.T
  object UseApiKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Wanted = Wanted.T
  object Wanted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WindowName = WindowName.T
  object WindowName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The schedule as `agent quota` names it after a pool's refresh mode. */
  type ReadRequests = ReadRequestsValue
  type ReadRequestsValue = ReadRequestsValue.T
  object ReadRequestsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def scheduleText(schedule: ScheduledRefresh): com.histmersion.server.agents.PlanPools.ScheduleText = schedule match
    case ScheduledRefresh.Off             => ""
    case ScheduledRefresh.Every(interval) =>
      s", and every ${interval.value} min while the autopilot has queued work for it"

  /** How the pools are read on purpose and their logins checked: the CLIs for real, a stand-in in a test (which must
    * never start a real `claude -p`).
    */
  final case class Seams(
      readCodex: (Seams.ReadCodexArg1, java.nio.file.Path, Seams.ReadCodexArg3) => Either[Seams.ReadCodexResultPart1, Json],
      runProbe: (List[Seams.RunProbeArg1], java.util.Map[Seams.RunProbeArg2Arg1Key, Seams.RunProbeArg2Arg1Value] => Unit, Seams.RunProbeArg3, Seams.RunProbeArg4) => Either[Seams.RunProbeResultPart1, CliProcess.Run],
      runCommand: (List[Seams.RunCommandArg1], Map[Seams.RunCommandArg2Key, Option[Seams.RunCommandArg2Value]], Seams.RunCommandArg3) => Either[Seams.RunCommandResultPart1, (Seams.RunCommandResultPart2Part1, Seams.RunCommandResultPart2Part2)],
      refreshes: Seams.Refreshes = true
  )

  object Seams:
    type ReadCodexArg1 = com.histmersion.server.agents.PlanPools.CommandLine
    val ReadCodexArg1: _root_.com.histmersion.server.agents.PlanPools.CommandLine.type = _root_.com.histmersion.server.agents.PlanPools.CommandLine
    type ReadCodexArg3 = com.histmersion.server.agents.PlanPools.TimeoutSeconds
    val ReadCodexArg3: _root_.com.histmersion.server.agents.PlanPools.TimeoutSeconds.type = _root_.com.histmersion.server.agents.PlanPools.TimeoutSeconds
    type ReadCodexResultPart1 = com.histmersion.server.agents.PlanPools.Problem
    val ReadCodexResultPart1: _root_.com.histmersion.server.agents.PlanPools.Problem.type = _root_.com.histmersion.server.agents.PlanPools.Problem
    type RunProbeArg1 = com.histmersion.server.agents.PlanPools.CommandLine
    val RunProbeArg1: _root_.com.histmersion.server.agents.PlanPools.CommandLine.type = _root_.com.histmersion.server.agents.PlanPools.CommandLine
    type RunProbeArg2Arg1Key = com.histmersion.server.agents.PlanPools.EnvName
    val RunProbeArg2Arg1Key: _root_.com.histmersion.server.agents.PlanPools.EnvName.type = _root_.com.histmersion.server.agents.PlanPools.EnvName
    type RunProbeArg2Arg1Value = com.histmersion.server.agents.PlanPools.EnvSetting
    val RunProbeArg2Arg1Value: _root_.com.histmersion.server.agents.PlanPools.EnvSetting.type = _root_.com.histmersion.server.agents.PlanPools.EnvSetting
    type RunProbeArg3 = com.histmersion.server.agents.PlanPools.StandardInput
    val RunProbeArg3: _root_.com.histmersion.server.agents.PlanPools.StandardInput.type = _root_.com.histmersion.server.agents.PlanPools.StandardInput
    type RunProbeArg4 = com.histmersion.server.agents.PlanPools.TimeoutSeconds
    val RunProbeArg4: _root_.com.histmersion.server.agents.PlanPools.TimeoutSeconds.type = _root_.com.histmersion.server.agents.PlanPools.TimeoutSeconds
    type RunProbeResultPart1 = com.histmersion.server.agents.PlanPools.Problem
    val RunProbeResultPart1: _root_.com.histmersion.server.agents.PlanPools.Problem.type = _root_.com.histmersion.server.agents.PlanPools.Problem
    type RunCommandArg1 = com.histmersion.server.agents.PlanPools.CommandLine
    val RunCommandArg1: _root_.com.histmersion.server.agents.PlanPools.CommandLine.type = _root_.com.histmersion.server.agents.PlanPools.CommandLine
    type RunCommandArg2Key = com.histmersion.server.agents.PlanPools.EnvName
    val RunCommandArg2Key: _root_.com.histmersion.server.agents.PlanPools.EnvName.type = _root_.com.histmersion.server.agents.PlanPools.EnvName
    type RunCommandArg2Value = com.histmersion.server.agents.PlanPools.EnvSetting
    val RunCommandArg2Value: _root_.com.histmersion.server.agents.PlanPools.EnvSetting.type = _root_.com.histmersion.server.agents.PlanPools.EnvSetting
    type RunCommandArg3 = com.histmersion.server.agents.PlanPools.TimeoutSeconds
    val RunCommandArg3: _root_.com.histmersion.server.agents.PlanPools.TimeoutSeconds.type = _root_.com.histmersion.server.agents.PlanPools.TimeoutSeconds
    type RunCommandResultPart1 = com.histmersion.server.agents.PlanPools.Problem
    val RunCommandResultPart1: _root_.com.histmersion.server.agents.PlanPools.Problem.type = _root_.com.histmersion.server.agents.PlanPools.Problem
    type RunCommandResultPart2Part1 = com.histmersion.server.agents.PlanPools.ExitCode
    val RunCommandResultPart2Part1: _root_.com.histmersion.server.agents.PlanPools.ExitCode.type = _root_.com.histmersion.server.agents.PlanPools.ExitCode
    type RunCommandResultPart2Part2 = com.histmersion.server.agents.PlanPools.CommandOutput
    val RunCommandResultPart2Part2: _root_.com.histmersion.server.agents.PlanPools.CommandOutput.type = _root_.com.histmersion.server.agents.PlanPools.CommandOutput

    type Refreshes = Refreshes.T
    object Refreshes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Real: Seams = Seams(PlanPools.readCodex, PlanPools.runProbe, AgentDoctor.runCommand)

    /** Nothing is started: no read, no fresh reading taken, logins not known (so nothing is made unavailable).
      */
    val Offline: Seams = Seams(
      (_, _, _) => Left("no app-server read in this context"),
      (_, _, _, _) => Left("no probe in this context"),
      (_, _, _) => Left("not checked in this context"),
      refreshes = false
    )

  /** The one `claude -p` probe: in an empty scratch directory, its output read whole. */
  def runProbe(
      argv: List[com.histmersion.server.agents.PlanPools.Argument],
      env: java.util.Map[com.histmersion.server.agents.PlanPools.EnvName, com.histmersion.server.agents.PlanPools.EnvSetting] => Unit,
      stdin: com.histmersion.server.agents.PlanPools.StandardInput,
      timeoutSeconds: com.histmersion.server.agents.PlanPools.TimeoutSeconds
  ): Either[com.histmersion.server.agents.PlanPools.Problem, CliProcess.Run] =
    try
      CliProcess.withDir("histmersion-probe-") { dir =>
        Right(
          CliProcess.run(
            argv,
            dir,
            env,
            stdin,
            timeoutSeconds,
            "install Claude Code or set agents.providers.claude-code.command (HISTMERSION_AGENTS_CLAUDE_COMMAND)"
          )
        )
      }
    catch
      case e: InterruptedException => throw e
      case e: Exception => Left(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}".take(300))

  /** The model whose OWN runtime spends the pool needs that runtime's login: claude-code on the Claude plan, codex on
    * the ChatGPT plan (opencode on a ChatGPT login signs in by itself).
    */
  def loginGates(p: PoolId, m: ModelRef): Boolean = (p, m.provider) match
    case (PoolId.ClaudePlan, ProviderKind.ClaudeCode) => true
    case (PoolId.ChatgptPlan, ProviderKind.Codex)     => true
    case _                                            => false

  /** The `claude` CLI's environment for `auth status`: no API key unless asked, its own config dir. */
  def claudeEnv(cc: com.histmersion.config.ClaudeCodeConfig)(using com.histmersion.util.system.JvmHome): Map[com.histmersion.server.agents.PlanPools.EnvName, Option[com.histmersion.server.agents.PlanPools.EnvSetting]] =
    (if cc.useApiKey then Map.empty else Map("ANTHROPIC_API_KEY" -> None, "ANTHROPIC_AUTH_TOKEN" -> None)) ++
      Option(cc.configDir.trim).filter(_.nonEmpty).map(d => "CLAUDE_CONFIG_DIR" -> Some(AgentTrees.expand(d))).toMap

  /** `claude auth status` (JSON) → the login (the doctor's reading of it, [[AgentDoctor.login]]): logged out, or on an
    * API key where the plan is wanted, is Missing; an answer that is not JSON is Unknown.
    */
  def claudeLogin(out: com.histmersion.server.agents.PlanPools.CommandOutput, useApiKey: com.histmersion.server.agents.PlanPools.UseApiKey): Login =
    if parser.parse(out.trim).isLeft then Login.Unknown(s"'claude auth status' did not answer JSON: ${out.take(200)}")
    else AgentDoctor.login(out, useApiKey).fold(Login.Missing(_), Login.Present(_))

  /** The runs' CODEX_HOME has a login when its `auth.json` exists (it is never read). */
  def codexLogin(home: Option[java.nio.file.Path], command: com.histmersion.server.agents.PlanPools.CommandLine): Login = home match
    case None    => Login.Missing("no CODEX_HOME — set agents.runtimes.dir or agents.providers.codex.home")
    case Some(h) =>
      if java.nio.file.Files.isRegularFile(h.resolve("auth.json")) then Login.Present(s"$h has a login")
      else Login.Missing(s"$h has none — run `CODEX_HOME=\"$h\" $command login` once")

  /** The probe's output → the readings to record: its `rate_limit_event` windows; when it carried none but ended on the
    * plan's usage limit, one refused window until the reset it names (else the cool-down). Left = it said nothing about
    * the plan.
    */
  def probeReadings(run: CliProcess.Run, now: com.histmersion.server.agents.PlanPools.Instant, cooldownMinutes: com.histmersion.server.agents.PlanPools.CooldownMinutes): Either[com.histmersion.server.agents.PlanPools.Problem, List[Reading]] =
    val o = ClaudeCode.parse(run.lines)
    if o.readings.nonEmpty then Right(o.readings.map(_.modify(_.source).setTo(Source.Probe).modify(_.observedAt).setTo(now)))
    else
      o.result.flatMap(ClaudeCode.usageLimit) match
        case Some(l) =>
          val until =
            l.until.map(_.toEpochMilli).filter(_ > now).getOrElse(now + math.max(1, cooldownMinutes).toLong * 60000L)
          Right(List(Reading("claude", "usage_limit", 100, Some(until), rejected = true, now, Source.Probe)))
        case None =>
          val said = o.result.map(r => (r.text :: r.errors).filter(_.nonEmpty).mkString("; ")).filter(_.nonEmpty)
          Left(
            s"the probe printed no rate_limit_event (exit ${run.exit}" +
              said.orElse(Option(run.stderr).filter(_.nonEmpty)).fold("")(m => s": ${m.take(300)}") + ")"
          )

  /** "five_hour 12% used, seven_day 42% used" — what a refresh read. */
  def summary(rs: List[Reading], now: com.histmersion.server.agents.PlanPools.Instant): com.histmersion.server.agents.PlanPools.Summary =
    val cur = PlanQuota.current(rs, now)
    if cur.isEmpty then "no window reported"
    else
      cur
        .map { r =>
          val name = if r.limit == "claude" || r.limit == "codex" then r.kind else s"${r.limit}/${r.kind}"
          val pct =
            java.math.BigDecimal(r.usedPct).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros.toPlainString
          s"$name $pct% used${if r.rejected then " (refused)" else ""}"
        }
        .mkString(", ")

  private def num(j: Json, k: com.histmersion.server.agents.PlanPools.JsonKey): Option[com.histmersion.server.agents.PlanPools.QuotaNumber] =
    j.hcursor.downField(k).as[Double].toOption

  /** Claude's `rate_limit_event.rate_limit_info` → readings (§3a P1): every `unifiedWindows` entry (`five_hour`,
    * `seven_day`, …; `utilization` 0–1, `resetsAt` epoch s), else the representative window; `status: rejected` marks
    * the window it names refused.
    */
  def claudeReadings(info: Json, source: Source, now: com.histmersion.server.agents.PlanPools.Instant): List[Reading] =
    val c = info.hcursor
    val status = c.downField("status").as[String].toOption
    val repKind = c.downField("rateLimitType").as[String].toOption
    val repReset = c.downField("resetsAt").as[Long].toOption.map(_ * 1000)
    val unified = c.downField("unifiedWindows").focus.flatMap(_.asObject).toList.flatMap(_.toList)
    val fromUnified = unified.flatMap { (kind, w) =>
      num(w, "utilization").map { u =>
        Reading(
          "claude",
          kind,
          u * 100,
          w.hcursor.downField("resetsAt").as[Long].toOption.map(_ * 1000),
          rejected = status.contains("rejected") && repKind.contains(kind),
          now,
          source
        )
      }
    }
    if fromUnified.nonEmpty then fromUnified
    else
      repKind.toList.flatMap { kind =>
        val used = num(info, "utilization").map(_ * 100)
        // without a utilization the representative window says only "allowed" or "rejected"
        if status.contains("rejected") then
          List(Reading("claude", kind, used.getOrElse(100.0), repReset, rejected = true, now, source))
        else used.map(u => Reading("claude", kind, u, repReset, rejected = false, now, source)).toList
      }

  /** A mod's `rateLimits` (`percentUsed`, `resetsAt` ISO) → readings. */
  def modReadings(ws: List[ModWindow], source: Source, now: com.histmersion.server.agents.PlanPools.Instant): List[Reading] =
    ws.map { w =>
      val reset = w.resetsAt.flatMap(t => scala.util.Try(java.time.Instant.parse(t).toEpochMilli).toOption)
      Reading("claude", w.kind, w.percentUsed, reset, rejected = false, now, source)
    }

  /** `account/rateLimits/read`'s result → readings (windows keyed by LENGTH, `300m`/`10080m`: primary is not always the
    * 5-hour one), the refusal (`ordinaryUsageAllowed: false`; a reached limit marks its windows refused) and the plan
    * type. Reset credits and upsells are ignored — never acted on.
    */
  def codexReadings(result: Json, now: com.histmersion.server.agents.PlanPools.Instant): (List[Reading], Option[(com.histmersion.server.agents.PlanPools.WindowName, Option[com.histmersion.server.agents.PlanPools.Instant])], Option[com.histmersion.server.agents.PlanPools.Problem]) =
    val c = result.hcursor
    val buckets: List[Json] =
      c.downField("rateLimitsByLimitId")
        .focus
        .flatMap(_.asObject)
        .map(_.values.toList)
        .filter(_.nonEmpty)
        .getOrElse(c.downField("rateLimits").focus.toList)
    val rs = buckets.flatMap { b =>
      val bc = b.hcursor
      val limit = bc.downField("limitId").as[String].toOption.getOrElse("codex")
      val reached = bc.downField("rateLimitReachedType").focus.exists(!_.isNull)
      val model = bc.downField("normalModelSlug").as[String].toOption.filter(_ => limit != "codex")
      List("primary", "secondary").flatMap(k => bc.downField(k).focus.filterNot(_.isNull)).flatMap { w =>
        val wc = w.hcursor
        for
          used <- wc.downField("usedPercent").as[Double].toOption
          mins <- wc.downField("windowDurationMins").as[Long].toOption
        yield Reading(
          limit,
          s"${mins}m",
          used,
          wc.downField("resetsAt").as[Long].toOption.map(_ * 1000),
          rejected = reached,
          now,
          Source.AppServer,
          model
        )
      }
    }
    val refusal = c
      .downField("ordinaryUsageAllowed")
      .as[Boolean]
      .toOption
      .filter(!_)
      .map(_ =>
        ("the ChatGPT plan does not allow ordinary usage now (ordinaryUsageAllowed: false)", Option.empty[Long])
      )
    val plan = buckets.flatMap(_.hcursor.downField("planType").as[String].toOption).headOption
    (rs, refusal, plan)

  /** One `account/rateLimits/read` through `codex app-server` (stdio JSON-RPC: `initialize` → `initialized` → the
    * read), with `CODEX_HOME` = the runs' home. Nothing else is ever sent — in particular never
    * `account/rateLimitResetCredit/consume`.
    */
  def readCodex(command: com.histmersion.server.agents.PlanPools.CommandLine, home: java.nio.file.Path, timeoutSeconds: com.histmersion.server.agents.PlanPools.TimeoutSeconds): Either[com.histmersion.server.agents.PlanPools.Problem, Json] =
    try
      val pb = ProcessBuilder(command, "app-server").redirectError(ProcessBuilder.Redirect.DISCARD)
      val env = pb.environment()
      val _ = env.put("CODEX_HOME", home.toString)
      List("OPENAI_API_KEY", "CODEX_API_KEY").foreach { k =>
        val _ = env.remove(k)
      }
      val p = pb.start()
      try
        val w = p.outputWriter(StandardCharsets.UTF_8)
        val send = (s: String) => { w.write(s); w.write("\n"); w.flush() }
        send(ReadRequests.head); send(ReadRequests(1)); send(ReadRequests(2))
        val lines = p.inputReader(StandardCharsets.UTF_8)
        val reader = java.util.concurrent.CompletableFuture.supplyAsync { () =>
          Iterator
            .continually(lines.readLine())
            .takeWhile(_ != null)
            .flatMap(l => parser.parse(l).toOption)
            .find(j => j.hcursor.downField("id").as[Int].toOption.contains(2))
        }
        reader.get(math.max(1, timeoutSeconds).toLong, TimeUnit.SECONDS) match
          case None    => Left("codex app-server ended without answering account/rateLimits/read")
          case Some(j) =>
            j.hcursor
              .downField("result")
              .focus
              .toRight(
                s"account/rateLimits/read: ${j.hcursor.downField("error").downField("message").as[String].getOrElse(j.noSpaces.take(200))}"
              )
      finally
        p.descendants().forEach { d =>
          val _ = d.destroy()
        }
        p.destroy()
        if !p.waitFor(3, TimeUnit.SECONDS) then { val _ = p.destroyForcibly() }
    catch
      case e: InterruptedException                  => throw e
      case e: java.util.concurrent.TimeoutException => Left(s"codex app-server did not answer within $timeoutSeconds s")
      case e: Exception => Left(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}".take(300))

  /** The three messages of a read — and the only ones Histmersion ever sends the app-server. */
  val ReadRequests: List[com.histmersion.server.agents.PlanPools.ReadRequests] = List(
    """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"clientInfo":{"name":"histmersion","title":"Histmersion","version":"1"}}}""",
    """{"jsonrpc":"2.0","method":"initialized"}""",
    """{"jsonrpc":"2.0","id":2,"method":"account/rateLimits/read"}"""
  )
