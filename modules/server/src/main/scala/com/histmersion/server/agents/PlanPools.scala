package com.histmersion
package server.agents
import com.histmersion.util.problem.ProblemLog
import com.histmersion.util.json.RawJson
import com.histmersion.api.json.NewtypeCodecs.given
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.problem.{External, Problem}
import com.histmersion.util.problem.Text.sentence
import io.scalaland.chimney.dsl.*

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.database.Transactor

import com.histmersion.domain.automation.agents.*

import com.histmersion.config.{AgentsConfig, ClaudeCodeConfig, CodexConfig, ScheduledRefresh}
import com.histmersion.api.automation.ModelChains.ChainEntry
import com.histmersion.util.process.{ProcessLaunch, ProcessResult}
import com.histmersion.domain.{PlanReadSchedule, ScheduledRead}
import com.histmersion.api.automation.PlanQuota
import com.histmersion.api.automation.Agents.{ModelRef, ProviderKind}
import com.histmersion.api.automation.PlanQuota.*
import com.histmersion.domain.automation.agents.PlanQuotaRepo

import java.nio.charset.StandardCharsets
import java.util.concurrent.{ConcurrentHashMap, Semaphore, TimeUnit}
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import scala.jdk.CollectionConverters.*
import com.histmersion.util.system.JvmHome
import io.github.iltotore.iron.constraint.any.Pure

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
)(using JvmHome)
    extends PoolSink:

  private val slots = ConcurrentHashMap[PoolId, Semaphore]()
  private val lastPoll = AtomicLong(0L)
  private val gate = RefreshGate()
  private val logins = ConcurrentHashMap[PoolId, (Long, Login)]()

  private def now0: PlanQuota.Now = PlanQuota.Now(System.currentTimeMillis())

  private def poolCfg(p: PoolId): (Floors.Wire, Int) = p match
    case PoolId.ClaudePlan =>
      (Floors.Wire(cfg.quota.claudePlan.floors.value), cfg.quota.claudePlan.maxCallsInFlight.value)
    case PoolId.ChatgptPlan =>
      (Floors.Wire(cfg.quota.chatgptPlan.floors.value), cfg.quota.chatgptPlan.maxCallsInFlight.value)

  private def staleMs: Long = math.max(1, cfg.quota.staleMinutes.value).toLong * 60000L

  /** At most `max-calls-in-flight` LLM calls draw on a plan at once (default 1: the three-way review panel would
    * otherwise spend the last headroom three times).
    */
  def slot(p: PoolId): Semaphore = slots.computeIfAbsent(p, _ => Semaphore(math.max(1, poolCfg(p)._2)))

  def poolOf(m: ModelRef): Option[PoolId] = PlanQuota.poolOf(m, cfg.logins)
  def paid(m: ModelRef): Boolean = PlanQuota.paid(m, cfg.logins)

  /** The floors of a pool and where they come from: saved, else config (a config string that does not parse floors
    * nothing it could not read, and says so).
    */
  def floors(p: PoolId): (Floors, PoolStatus.FloorsSource, Option[Problem]) =
    val saved = Transactor(ds).read(PlanQuotaRepo.pool(p)).floors
    saved.map(f => Floors.parse(Floors.Wire(f.value))) match
      case Some(Right(f)) => (f, PoolStatus.FloorsSource("saved"), None)
      case Some(Left(m))  =>
        (
          Floors.Empty,
          PoolStatus.FloorsSource("saved"),
          Some(Problem.Config.FloorsUnread(Problem.Config.FloorsFrom.Saved, m.toNonEmpty))
        )
      case None =>
        Floors.parse(poolCfg(p)._1) match
          case Right(f) => (f, PoolStatus.FloorsSource("config"), None)
          case Left(m)  =>
            (
              Floors.Empty,
              PoolStatus.FloorsSource("config"),
              Some(
                Problem.Config.FloorsUnread(
                  Problem.Config.FloorsFrom.Setting(Problem.SettingKey(s"agents.quota.${p.wire}.floors")),
                  m.toNonEmpty
                )
              )
            )

  def rules(p: PoolId): Rules =
    Rules(
      floors(p)._1,
      Rules.DefaultFloor(cfg.quota.defaultFloor.value),
      Rules.StaleMs(staleMs),
      Rules.MarginPct(cfg.quota.margin.value)
    )

  /** The verdict for one call on `p` (and its model's own buckets), from what is stored: the readings, the hold and the
    * provider's refusal as last recorded. It reads nothing else and writes nothing — what every view asks (`agent
    * quota`, `agent models`, the scheduler's dry pass). A call that runs work asks [[current]].
    */
  def verdict(p: PoolId, model: Option[ChainEntry.Model], now: PlanQuota.Now = now0): (Verdict, List[WindowState]) =
    val pool = Transactor(ds).read(PlanQuotaRepo.pool(p))
    PlanQuota.verdict(
      Transactor(ds).read(PlanQuotaRepo.readings(p)),
      PoolStatus.Held(pool.held.value),
      pool.refusal.map(r =>
        PlanQuota.Rationale(s"${r.sentence}") -> pool.refusalUntil.map(u => Verdict.Blocked.Until(u.value))
      ),
      rules(p),
      now,
      model
    )

  /** The verdict for a call that runs work on `p` (a run's pick, a step's check, the scheduler's pass): the ChatGPT
    * pool is read from the app-server first when its last reading is older than `poll-seconds` ([[poll]], which records
    * the reading and the provider's refusal); then [[verdict]]. Takes no fresh reading of its own: [[decide]] does, for
    * a pick.
    */
  def current(p: PoolId, model: Option[ChainEntry.Model], now: PlanQuota.Now = now0): (Verdict, List[WindowState]) =
    if p == PoolId.ChatgptPlan then poll(now)
    verdict(p, model, now)

  /** Why `p`'s verdict would wait for a fresh reading now (None = it decides by itself). */
  def need(p: PoolId, model: Option[ChainEntry.Model], now: PlanQuota.Now): Option[RefreshNeed] =
    val pool = Transactor(ds).read(PlanQuotaRepo.pool(p))
    PlanQuota.refreshNeed(
      Transactor(ds).read(PlanQuotaRepo.readings(p)),
      PoolStatus.Held(pool.held.value),
      pool.refusal.map(r =>
        PlanQuota.Rationale(s"${r.sentence}") -> pool.refusalUntil.map(u => Verdict.Blocked.Until(u.value))
      ),
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
      model: Option[ChainEntry.Model],
      trigger: RefreshTrigger,
      now: PlanQuota.Now = now0
  ): (Verdict, List[WindowState], Option[Refreshed]) =
    val (v, states) = current(p, model, now)
    if need(p, model, now).isEmpty then (v, states, None)
    else
      refreshPool(p, trigger, model, force = PlanPools.Force(false), now) match
        // not taken — perhaps because the caller waited while another took one: ask again either way
        case Left(_) =>
          val (v2, s2) = current(p, model, now0)
          (v2, s2, None)
        case Right(r) =>
          val (v2, s2) = current(p, model, now0)
          (v2, s2, Some(r))

  def record(p: PoolId, rs: List[Reading]): Unit = Transactor(ds).transact(PlanQuotaRepo.record(p, rs))

  /** When `p`'s newest reading was observed (epoch ms), if it has one. */
  def lastObserved(p: PoolId): Option[Reading.ObservedAt] =
    Transactor(ds).read(PlanQuotaRepo.readings(p)).map(_.observedAt).maxOption

  def blocked(
      p: PoolId,
      model: Option[AgentsControllers.ModelName]
  ): Option[(PlanQuota.Rationale, Option[PlanQuotaRepo.EpochMillis])] =
    current(p, model.map(m => ChainEntry.Model(m.value)))._1 match
      case Verdict.Blocked(why, until) =>
        Some((why, until.map(u => PlanQuotaRepo.EpochMillis(u.value))))
      case _ => None

  def refresh(now: PlanQuotaRepo.EpochMillis): Unit = { val _ = poll(PlanQuota.Now(now.value)) }

  def hold(p: PoolId, on: PlanQuotaRepo.Pool.Held): Unit = Transactor(ds).transact(PlanQuotaRepo.setHold(p, on))

  /** Whether a person holds `p` (no run draws on it until it is released). */
  def held(p: PoolId): Boolean = Transactor(ds).read(PlanQuotaRepo.pool(p)).held.value
  def setFloors(p: PoolId, f: Option[PlanQuotaRepo.Pool.Floors]): Unit =
    Transactor(ds).transact(PlanQuotaRepo.setFloors(p, f))

  // ── logins ─────────────────────────────────────────────────────────────────────────────────────

  /** Whether the runtime `p` is read and spent through is signed in — `claude auth status` for the Claude plan, a login
    * in the runs' CODEX_HOME for the ChatGPT plan (the file is never read) — checked at most once per `poll-seconds`
    * (`force`: now).
    */
  def login(p: PoolId, now: PlanQuota.Now = now0, force: PlanPools.Force = PlanPools.Force(false)): Login =
    val every = math.max(1, cfg.quota.pollSeconds.value).toLong * 1000L
    Option(logins.get(p)).filter((at, _) => !force.value && now.value - at < every).map(_._2).getOrElse {
      val l = p match
        case PoolId.ClaudePlan  => claudeLogin()
        case PoolId.ChatgptPlan => PlanPools.codexLogin(codexHome(), cfg.providers.codex.command)
      val _ = logins.put(p, (now.value, l))
      l
    }

  private def claudeLogin(): Login =
    val cc = cfg.providers.claudeCode
    seams.runCommand(
      List(ProcessLaunch.Command(cc.command.value), ProcessLaunch.Command("auth"), ProcessLaunch.Command("status")),
      PlanPools.claudeEnv(cc),
      AgentsConfig.DoctorTimeoutSeconds(math.max(1, cfg.doctorTimeoutSeconds.value))
    ) match
      case Left(why) =>
        Login.Unknown(PlanQuota.Rationale(s"'claude auth status' failed: ${why.sentence}"))
      case Right((_, out)) => PlanPools.claudeLogin(out, cc.useApiKey)

  /** Whether `m` may be offered at all: a model whose own runtime spends the pool (claude-code on the Claude plan,
    * codex on the ChatGPT plan) needs that runtime's login. Some(why) = unavailable.
    */
  def unavailable(m: ModelRef, now: PlanQuota.Now = now0): Option[Problem] =
    poolOf(m).filter(p => PlanPools.loginGates(p, m)).flatMap { p =>
      login(p, now) match
        case Login.Missing(why) => Some(Problem.Run.NoLogin(Problem.Raw.of(why)))
        case _                  => None
    }

  // ── fresh readings ─────────────────────────────────────────────────────────────────────────────

  private def claudeMode: Either[Problem, RefreshMode] =
    RefreshMode
      .parse(RefreshMode.Wire(cfg.quota.claudePlan.refresh.value))
      .toRight(
        Problem.Invalid.Unknown(
          Problem.TypeName.named[RefreshMode],
          Problem.Raw.of(cfg.quota.claudePlan.refresh),
          RefreshMode.values.toVector.map(m => Problem.Raw.of(m.wire)),
          Vector(Problem.Field("agents.quota.claude-plan.refresh"))
        )
      )

  /** How `p` takes a fresh reading, for `agent quota`; None = it takes none. */
  def refreshMode(p: PoolId): Option[Refreshed.Mode] = p match
    case PoolId.ClaudePlan =>
      claudeMode.toOption
        .filter(_ != RefreshMode.Off)
        .map(m =>
          Refreshed.Mode(
            s"${m.wire}: one claude -p turn on ${cfg.quota.claudePlan.probeModel}, at most once per ${cfg.quota.staleMinutes} min while a run waits" +
              PlanPools.scheduleText(schedule(p)).value
          )
        )
    case PoolId.ChatgptPlan =>
      Option.when(cfg.providers.codex.appServer.value)(
        Refreshed.Mode(
          "app-server: account/rateLimits/read, spends nothing" + PlanPools.scheduleText(schedule(p)).value
        )
      )

  def schedule(p: PoolId): ScheduledRefresh = p match
    case PoolId.ClaudePlan  => cfg.quota.claudePlan.refreshEveryMinutes
    case PoolId.ChatgptPlan => cfg.quota.chatgptPlan.refreshEveryMinutes

  /** Take a fresh reading of `p` on its schedule ([[PlanReadSchedule.scheduledRead]]), inside the pool's gate: a pick
    * that took one meanwhile makes this one not due. Never without a login or a way to read. Left = not taken, and why.
    */
  def refreshOnSchedule(
      p: PoolId,
      autopilotOn: PlanReadSchedule.AutopilotOn,
      wanted: PlanReadSchedule.Wanted,
      now: java.time.Instant
  ): Either[PlanQuota.Rationale, Refreshed] =
    val at = now.toEpochMilli
    gate.exclusive(p, at) { last =>
      val newest = lastObserved(p).map(o => java.time.Instant.ofEpochMilli(o.value))
      PlanReadSchedule.scheduledRead(
        schedule(p).readSchedule,
        autopilotOn,
        wanted,
        newest,
        last.map(l => java.time.Instant.ofEpochMilli(l.value)),
        now
      ) match
        case ScheduledRead.NotDue(why) => Left(why.transformInto[PlanQuota.Rationale])
        case ScheduledRead.Due         =>
          PlanQuota
            .refreshDecision(
              None,
              login(p, PlanQuota.Now(at)),
              seams.refreshes.value && refreshMode(p).isDefined,
              last.map(l => PlanQuota.RefreshInfo.LastAttempt(l.value)),
              PlanQuota.Rules.MinGap(staleMs),
              PlanQuota.Now(at),
              force = true
            )
            .map(_ =>
              takeAndLog(
                p,
                RefreshTrigger.Schedule,
                PlanQuota.Rationale(s"${RefreshTrigger.Schedule.wire}"),
                PlanQuota.Now(at)
              )
            )
    }

  /** Take a fresh reading of `p` now — when the decision table says so ([[PlanQuota.refreshDecision]]): single flight,
    * throttled per `stale-minutes`, never without a login. `force` (a person asked) skips the need and the throttle.
    * Left = not taken, and why. Every attempt is logged (server log and the pool's refresh log).
    */
  def refreshPool(
      p: PoolId,
      trigger: RefreshTrigger,
      model: Option[ChainEntry.Model] = None,
      force: PlanPools.Force = PlanPools.Force(false),
      now: PlanQuota.Now = now0
  ): Either[PlanQuota.Rationale, Refreshed] =
    gate.exclusive(p, now.value) { last =>
      // asked again inside the gate: a caller that waited sees the reading the one before it took
      val needNow = need(p, model, now)
      PlanQuota
        .refreshDecision(
          needNow,
          login(p, now, force),
          seams.refreshes.value && refreshMode(p).isDefined,
          last.map(l => PlanQuota.RefreshInfo.LastAttempt(l.value)),
          PlanQuota.Rules.MinGap(staleMs),
          now,
          force.value
        )
        .map(n =>
          takeAndLog(
            p,
            trigger,
            PlanQuota.Rationale(n.fold(s"${trigger.wire}")(x => s"${trigger.wire}, ${x.text}")),
            now
          )
        )
    }

  /** One reading of `p`, logged (server log and the pool's refresh log) with why it was taken. */
  private def takeAndLog(p: PoolId, trigger: RefreshTrigger, why: PlanQuota.Rationale, now: PlanQuota.Now): Refreshed =
    val r = take(p, now)
    val line = s"plan refresh ${p.wire} ($why) ${r.text}"
    if r.ok.value then scribe.info(line) else scribe.warn(line)
    Transactor(ds).transact(
      PlanQuotaRepo.logRefresh(
        p,
        PlanQuotaRepo.RefreshMode(r.mode.value),
        PlanQuotaRepo.RefreshTrigger(trigger.wire.value),
        r.ok.value,
        PlanQuotaRepo.RefreshDetail(r.detail.value)
      )
    )
    r

  /** One reading of `p`, recorded: the Claude probe or the ChatGPT app-server read. */
  private def take(p: PoolId, now: PlanQuota.Now): Refreshed = p match
    case PoolId.ChatgptPlan =>
      poll(now, force = PlanPools.Force(true)) match
        case Some(why) => Refreshed(Refreshed.Mode("app-server"), ok = Refreshed.Ok(false), why)
        case None      =>
          Refreshed(
            Refreshed.Mode("app-server"),
            ok = Refreshed.Ok(true),
            PlanPools.summary(Transactor(ds).read(PlanQuotaRepo.readings(p)), now)
          )
    case PoolId.ClaudePlan =>
      val cc = cfg.providers.claudeCode
      val qc = cfg.quota.claudePlan
      val argv = ClaudeCode.probeArgv(cc, qc.probeModel)
      seams.runProbe(
        argv,
        env => ClaudeCode.probeEnvironment(cc, qc.probeMaxTokens, env),
        CliProcess.RunStdin(ClaudeCode.ProbePrompt),
        CliProcess.RunTimeoutSeconds(math.max(5, qc.probeTimeoutSeconds.value))
      ) match
        case Left(why)  => Refreshed(Refreshed.Mode("probe"), ok = Refreshed.Ok(false), Refreshed.Detail(why.sentence))
        case Right(run) =>
          PlanPools.probeReadings(run, now, cc.limitCooldownMinutes) match
            case Left(why) =>
              Refreshed(Refreshed.Mode("probe"), ok = Refreshed.Ok(false), Refreshed.Detail(why.sentence))
            case Right(rs) =>
              record(PoolId.ClaudePlan, rs)
              Refreshed(Refreshed.Mode("probe"), ok = Refreshed.Ok(true), PlanPools.summary(rs, now))

  /** Read the ChatGPT plan through `codex app-server` (it generates nothing), at most once per `poll-seconds`, and only
    * with `app-server` on and a CODEX_HOME to read.
    */
  def poll(now: PlanQuota.Now, force: PlanPools.Force = PlanPools.Force(false)): Option[Refreshed.Detail] =
    val every = math.max(1, cfg.quota.pollSeconds.value).toLong * 1000L
    val last = lastPoll.get()
    if !cfg.providers.codex.appServer.value then
      Some(Refreshed.Detail("app-server reads are off (agents.providers.codex.app-server)"))
    else if (!force.value && now.value - last < every) || !lastPoll.compareAndSet(last, now.value) then None
    else
      codexHome() match
        case None       => Some(Refreshed.Detail("no CODEX_HOME to read"))
        case Some(home) =>
          Codex.Home.using(home)(
            seams.readCodex(
              cfg.providers.codex.command,
              home,
              AgentsConfig.DoctorTimeoutSeconds(math.max(5, cfg.doctorTimeoutSeconds.value))
            )
          ) match
            case Left(why) =>
              scribe.info(s"codex app-server rate-limit read failed: ${why.sentence}")
              Some(Refreshed.Detail(why.sentence))
            case Right(json) =>
              val (rs, refusal, plan) = PlanPools.codexReadings(json, now)
              record(PoolId.ChatgptPlan, rs)
              Transactor(ds).transact(
                PlanQuotaRepo.setRefusal(
                  PoolId.ChatgptPlan,
                  refusal.map(r => r._1),
                  refusal.flatMap(_._2),
                  plan
                )
              )
              None

  /** A mod's report: recorded, and answered with whether the run may go on. */
  def guard(report: ModReport, now: PlanQuota.Now = now0): GuardAnswer =
    val src = Source
      .parse(Source.Wire(report.source.value))
      .filter(s => s == Source.Mod || s == Source.Interactive)
      .getOrElse(Source.Mod)
    record(PoolId.ClaudePlan, PlanPools.modReadings(report.rateLimits, src, now))
    verdict(PoolId.ClaudePlan, None, now)._1 match
      case Verdict.Usable(_)           => GuardAnswer(allow = GuardAnswer.Allow(true))
      case Verdict.Blocked(why, until) =>
        GuardAnswer(
          allow = GuardAnswer.Allow(false),
          Some(why),
          until.map(u => GuardAnswer.ResetsAt(java.time.Instant.ofEpochMilli(u.value).toString))
        )

  /** Every plan pool, as `agent quota` shows it, from what is stored ([[verdict]]): it takes no reading — not even the
    * ChatGPT pool's app-server read — and writes nothing (looking is free). A run, the scheduler's pass and its
    * schedule, and `agent quota refresh` take the readings it shows.
    */
  def statuses(covers: PoolId => List[PoolStatus.Covers], now: PlanQuota.Now = now0): List[PoolStatus] =
    val today = java.time.Instant
      .ofEpochMilli(now.value)
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
          WindowStatus.Kind(s.reading.kind.value),
          s.reading.usedPct,
          s.leftPct,
          s.floor,
          WindowStatus.Usable(s.usable.value),
          WindowStatus.Stale(s.stale.value),
          s.reading.resetsAt.map(r => WindowStatus.ResetsAt(iso(r.value))),
          WindowStatus.ObservedAt(iso(s.reading.observedAt.value)),
          WindowStatus.AgeSeconds(math.max(0L, (now.value - s.reading.observedAt.value) / 1000)),
          WindowStatus.Source(s.reading.source.wire.value),
          s.why
        )
      }
      val lg = login(p, now)
      val missing = lg match
        case Login.Missing(why) => Some(why)
        case _                  => None
      val (ok, why, until) = (v, missing) match
        case (_, Some(m))                  => (false, Some(s"unavailable: no login — $m"), None)
        case (Verdict.Usable(n), None)     => (true, n.map(_.value), None)
        case (Verdict.Blocked(w, u), None) => (false, Some(s"$w"), u.map(x => iso(x.value)))
      val modeProblem = Option.when(p == PoolId.ClaudePlan)(claudeMode.left.toOption).flatten
      val (n, last) = Transactor(ds).read(PlanQuotaRepo.refreshes(p, PlanQuotaRepo.EpochMillis(today)))
      PoolStatus(
        p.wire,
        p.title,
        PoolStatus.Kind("plan"),
        covers(p),
        PoolStatus.Floors(f.text.value),
        src,
        PoolStatus.Held(pool.held.value),
        PoolStatus.Usable(ok),
        why.map(text => PlanQuota.Rationale(s"$text")),
        until.map(PoolStatus.Until(_)),
        PoolStatus.MaxCallsInFlight(math.max(1, poolCfg(p)._2)),
        ws,
        pool.planType.map(t => PoolStatus.PlanType(t.value)),
        (problem.toList ++ modeProblem).map(_.sentence).reduceOption[String](_ + "; " + _).map(PoolStatus.Note(_)),
        available = PoolStatus.Available(missing.isEmpty),
        login = Some(
          PoolStatus.Login(
            lg match
              case Login.Present(d) => d.value
              case Login.Missing(w) => s"no login: $w"
              case Login.Unknown(w) => s"not known: $w"
          )
        ),
        refresh = refreshMode(p).map(m => PoolStatus.Refresh(m.value)),
        refreshesToday = PoolStatus.RefreshesToday(n.value),
        lastRefresh = last
      )
    }

object PlanPools:
  type Force = Force.T
  object Force extends Newtype[Boolean, Pure]

  /** The process a ChatGPT plan's read runs. */
  private val AppServer = Problem.Io.Tool("codex app-server")
  type ScheduleText = ScheduleText.T
  object ScheduleText extends Newtype[String, Pure]

  /** The schedule as `agent quota` names it after a pool's refresh mode. */
  def scheduleText(schedule: ScheduledRefresh): ScheduleText = schedule match
    case ScheduledRefresh.Off             => ScheduleText("")
    case ScheduledRefresh.Every(interval) =>
      ScheduleText(s", and every ${interval.value} min while the autopilot has queued work for it")

  /** How the pools are read on purpose and their logins checked: the CLIs for real, a stand-in in a test (which must
    * never start a real `claude -p`).
    */
  final case class Seams(
      readCodex: (CodexConfig.Command, java.nio.file.Path, AgentsConfig.DoctorTimeoutSeconds) => Either[Problem, Json],
      runProbe: (
          List[ClaudeCode.Argument],
          java.util.Map[String, String] => Unit,
          CliProcess.RunStdin,
          CliProcess.RunTimeoutSeconds
      ) => Either[Problem, CliProcess.Run],
      runCommand: (
          List[ProcessLaunch.Command],
          Map[ProcessLaunch.EnvironmentKey, Option[ProcessLaunch.EnvironmentValue]],
          AgentsConfig.DoctorTimeoutSeconds
      ) => Either[Problem, (ProcessResult.ExitCode, AgentDoctor.Output)],
      refreshes: Seams.Refreshes = Seams.Refreshes(true)
  )

  object Seams:
    type Refreshes = Refreshes.T
    object Refreshes extends Newtype[Boolean, Pure]

    val Real: Seams = Seams(readCodex, runProbe, AgentDoctor.runCommand)

    /** Nothing is started: no read, no fresh reading taken, logins not known (so nothing is made unavailable).
      */
    val Offline: Seams = Seams(
      (_, _, _) => Left(Problem.Config.Unavailable(Problem.Config.Capability.AppServerRead)),
      (_, _, _, _) => Left(Problem.Config.Unavailable(Problem.Config.Capability.PlanProbe)),
      (_, _, _) => Left(Problem.Config.Unavailable(Problem.Config.Capability.LoginCheck)),
      refreshes = Refreshes(false)
    )

  /** The one `claude -p` probe: in an empty scratch directory, its output read whole. */
  def runProbe(
      argv: List[ClaudeCode.Argument],
      env: java.util.Map[String, String] => Unit,
      stdin: CliProcess.RunStdin,
      timeoutSeconds: CliProcess.RunTimeoutSeconds
  ): Either[Problem, CliProcess.Run] =
    try
      CliProcess.withDir(CliProcess.WithDirPrefix("histmersion-probe-")) { dir =>
        Right(
          CliProcess.run(
            argv,
            dir,
            env,
            stdin,
            timeoutSeconds,
            CliProcess.RunHint(
              "install Claude Code or set agents.providers.claude-code.command (HISTMERSION_AGENTS_CLAUDE_COMMAND)"
            )
          )
        )
      }
    catch
      case e: InterruptedException => throw e
      case e: Exception            =>
        Left(ProblemLog.unexpected(e, Problem.Where("the plan pools' quota read")))

  /** The model whose OWN runtime spends the pool needs that runtime's login: claude-code on the Claude plan, codex on
    * the ChatGPT plan (opencode on a ChatGPT login signs in by itself).
    */
  def loginGates(p: PoolId, m: ModelRef): Boolean = (p, m.provider) match
    case (PoolId.ClaudePlan, ProviderKind.ClaudeCode) => true
    case (PoolId.ChatgptPlan, ProviderKind.Codex)     => true
    case _                                            => false

  /** The `claude` CLI's environment for `auth status`: no API key unless asked, its own config dir. */
  def claudeEnv(cc: ClaudeCodeConfig)(using
      JvmHome
  ): Map[ProcessLaunch.EnvironmentKey, Option[ProcessLaunch.EnvironmentValue]] =
    val dropped: Map[ProcessLaunch.EnvironmentKey, Option[ProcessLaunch.EnvironmentValue]] =
      if cc.useApiKey.value then Map.empty
      else
        Map(
          ProcessLaunch.EnvironmentKey("ANTHROPIC_API_KEY") -> None,
          ProcessLaunch.EnvironmentKey("ANTHROPIC_AUTH_TOKEN") -> None
        )
    dropped ++
      Option(cc.configDir.value.trim)
        .filter(_.nonEmpty)
        .map(d =>
          ProcessLaunch.EnvironmentKey("CLAUDE_CONFIG_DIR") -> Some(
            ProcessLaunch.EnvironmentValue(AgentTrees.expand(d))
          )
        )
        .toMap

  /** `claude auth status` (JSON) → the login (the doctor's reading of it, [[AgentDoctor.login]]): logged out, or on an
    * API key where the plan is wanted, is Missing; an answer that is not JSON is Unknown.
    */
  def claudeLogin(out: AgentDoctor.Output, useApiKey: ClaudeCodeConfig.UseApiKey): Login =
    if RawJson.parse(out.value.trim).isLeft then
      Login.Unknown(
        PlanQuota.Rationale(s"'claude auth status' did not answer JSON: ${out.value.take(200)}")
      )
    else
      AgentDoctor
        .login(out, useApiKey)
        .fold(
          p => Login.Missing(PlanQuota.Rationale(s"${p.sentence}")),
          d => Login.Present(Login.Present.Detail(d.value))
        )

  /** The runs' CODEX_HOME has a login when its `auth.json` exists (it is never read). */
  def codexLogin(home: Option[java.nio.file.Path], command: CodexConfig.Command): Login = home match
    case None =>
      Login.Missing(
        PlanQuota.Rationale("no CODEX_HOME — set agents.runtimes.dir or agents.providers.codex.home")
      )
    case Some(h) =>
      if java.nio.file.Files.isRegularFile(h.resolve("auth.json")) then
        Login.Present(Login.Present.Detail(s"$h has a login"))
      else Login.Missing(PlanQuota.Rationale(s"$h has none — run `CODEX_HOME=\"$h\" $command login` once"))

  /** The probe's output → the readings to record: its `rate_limit_event` windows; when it carried none but ended on the
    * plan's usage limit, one refused window until the reset it names (else the cool-down). Left = it said nothing about
    * the plan.
    */
  def probeReadings(
      run: CliProcess.Run,
      now: PlanQuota.Now,
      cooldownMinutes: ClaudeCodeConfig.LimitCooldownMinutes
  ): Either[Problem, List[Reading]] =
    val o = ClaudeCode.parse(run.lines)
    if o.readings.nonEmpty then
      Right(
        o.readings.map(_.modify(_.source).setTo(Source.Probe).modify(_.observedAt).setTo(Reading.ObservedAt(now.value)))
      )
    else
      o.result.flatMap(ClaudeCode.usageLimit) match
        case Some(l) =>
          val until =
            l.until
              .map(_.toEpochMilli)
              .filter(_ > now.value)
              .getOrElse(now.value + math.max(1, cooldownMinutes.value).toLong * 60000L)
          Right(
            List(
              Reading(
                Reading.Limit("claude"),
                Reading.Kind("usage_limit"),
                Reading.UsedPct(100),
                Some(Reading.ResetsAt(until)),
                rejected = Reading.Rejected(true),
                Reading.ObservedAt(now.value),
                Source.Probe
              )
            )
          )
        case None =>
          val said = o.result
            .map(r => (r.text.value :: r.errors.map(_.value)).filter(_.nonEmpty).mkString("; "))
            .filter(_.nonEmpty)
          Left(
            Problem.Run.ProbeUnread(
              Problem.Io.ExitCode(run.exit.value),
              said.orElse(Option(run.stderr.value).filter(_.nonEmpty)).map(External.Reported.of)
            )
          )

  /** "five_hour 12% used, seven_day 42% used" — what a refresh read. */
  def summary(rs: List[Reading], now: PlanQuota.Now): Refreshed.Detail =
    val cur = PlanQuota.current(rs, now)
    if cur.isEmpty then Refreshed.Detail("no window reported")
    else
      Refreshed.Detail(
        cur
          .map { r =>
            val name =
              if r.limit.value == "claude" || r.limit.value == "codex" then r.kind.value
              else s"${r.limit}/${r.kind}"
            val pct =
              java.math
                .BigDecimal(r.usedPct.value)
                .setScale(1, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros
                .toPlainString
            s"$name $pct% used${if r.rejected.value then " (refused)" else ""}"
          }
          .mkString(", ")
      )

  private def num(j: Json, k: String): Option[Double] =
    j.cursor.downField(k).as[Double].toOption

  /** Claude's `rate_limit_event.rate_limit_info` → readings (§3a P1): every `unifiedWindows` entry (`five_hour`,
    * `seven_day`, …; `utilization` 0–1, `resetsAt` epoch s), else the representative window; `status: rejected` marks
    * the window it names refused.
    */
  def claudeReadings(info: Json, source: Source, now: PlanQuota.Now): List[Reading] =
    val c = info.cursor
    val status = c.downField("status").as[String].toOption
    val repKind = c.downField("rateLimitType").as[String].toOption
    val repReset = c.downField("resetsAt").as[Long].toOption.map(s => Reading.ResetsAt(s * 1000))
    val unified = c.downField("unifiedWindows").focus.flatMap(_.asObject).toList.flatMap(_.toList)
    val fromUnified = unified.flatMap { (kind, w) =>
      num(w, "utilization").map { u =>
        Reading(
          Reading.Limit("claude"),
          Reading.Kind(kind),
          Reading.UsedPct(u * 100),
          w.cursor.downField("resetsAt").as[Long].toOption.map(s => Reading.ResetsAt(s * 1000)),
          rejected = Reading.Rejected(status.contains("rejected") && repKind.contains(kind)),
          Reading.ObservedAt(now.value),
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
          List(
            Reading(
              Reading.Limit("claude"),
              Reading.Kind(kind),
              Reading.UsedPct(used.getOrElse(100.0)),
              repReset,
              rejected = Reading.Rejected(true),
              Reading.ObservedAt(now.value),
              source
            )
          )
        else
          used
            .map(u =>
              Reading(
                Reading.Limit("claude"),
                Reading.Kind(kind),
                Reading.UsedPct(u),
                repReset,
                rejected = Reading.Rejected(false),
                Reading.ObservedAt(now.value),
                source
              )
            )
            .toList
      }

  /** A mod's `rateLimits` (`percentUsed`, `resetsAt` ISO) → readings. */
  def modReadings(ws: List[ModWindow], source: Source, now: PlanQuota.Now): List[Reading] =
    ws.map { w =>
      val reset = w.resetsAt
        .flatMap(t => scala.util.Try(java.time.Instant.parse(t.value).toEpochMilli).toOption)
        .map(Reading.ResetsAt(_))
      Reading(
        Reading.Limit("claude"),
        Reading.Kind(w.kind.value),
        w.percentUsed,
        reset,
        rejected = Reading.Rejected(false),
        Reading.ObservedAt(now.value),
        source
      )
    }

  /** `account/rateLimits/read`'s result → readings (windows keyed by LENGTH, `300m`/`10080m`: primary is not always the
    * 5-hour one), the refusal (`ordinaryUsageAllowed: false`; a reached limit marks its windows refused) and the plan
    * type. Reset credits and upsells are ignored — never acted on.
    */
  def codexReadings(result: Json, now: PlanQuota.Now): (
      List[Reading],
      Option[(Problem, Option[PlanQuotaRepo.Pool.RefusalUntil])],
      Option[PlanQuotaRepo.Pool.PlanType]
  ) =
    val c = result.cursor
    val buckets: List[Json] =
      c.downField("rateLimitsByLimitId")
        .focus
        .flatMap(_.asObject)
        .map(_.values.toList)
        .filter(_.nonEmpty)
        .getOrElse(c.downField("rateLimits").focus.toList)
    val rs = buckets.flatMap { b =>
      val bc = b.cursor
      val limit = bc.downField("limitId").as[String].toOption.getOrElse("codex")
      val reached = bc.downField("rateLimitReachedType").focus.exists(!_.isNull)
      val model = bc.downField("normalModelSlug").as[String].toOption.filter(_ => limit != "codex")
      List("primary", "secondary").flatMap(k => bc.downField(k).focus.filterNot(_.isNull)).flatMap { w =>
        val wc = w.cursor
        for
          used <- wc.downField("usedPercent").as[Double].toOption
          mins <- wc.downField("windowDurationMins").as[Long].toOption
        yield Reading(
          Reading.Limit(limit),
          Reading.Kind(s"${mins}m"),
          Reading.UsedPct(used),
          wc.downField("resetsAt").as[Long].toOption.map(s => Reading.ResetsAt(s * 1000)),
          rejected = Reading.Rejected(reached),
          Reading.ObservedAt(now.value),
          Source.AppServer,
          model.map(Reading.Model(_))
        )
      }
    }
    val refusal = c
      .downField("ordinaryUsageAllowed")
      .as[Boolean]
      .toOption
      .filter(!_)
      .map(_ =>
        (
          Problem.Refused.Policy(Problem.Refused.Rule.OrdinaryUsageDenied),
          Option.empty[PlanQuotaRepo.Pool.RefusalUntil]
        )
      )
    val plan = buckets
      .flatMap(_.cursor.downField("planType").as[String].toOption)
      .headOption
      .map(PlanQuotaRepo.Pool.PlanType(_))
    (rs, refusal, plan)

  /** One `account/rateLimits/read` through `codex app-server` (stdio JSON-RPC: `initialize` → `initialized` → the
    * read), with `CODEX_HOME` = the runs' home. Nothing else is ever sent — in particular never
    * `account/rateLimitResetCredit/consume`.
    */
  def readCodex(
      command: CodexConfig.Command,
      home: java.nio.file.Path,
      timeoutSeconds: AgentsConfig.DoctorTimeoutSeconds
  ): Either[Problem, Json] =
    val seconds = math.max(1, timeoutSeconds.value).toLong
    try
      val pb = ProcessBuilder(command.value, "app-server").redirectError(ProcessBuilder.Redirect.DISCARD)
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
            .flatMap(l => RawJson.parse(l).toOption)
            .find(j => j.cursor.downField("id").as[Int].toOption.contains(2))
        }
        reader.get(seconds, TimeUnit.SECONDS) match
          case None =>
            Left(Problem.Io.Process(AppServer, Problem.Io.Ending.Unanswered(Problem.Raw("account/rateLimits/read"))))
          case Some(j) =>
            j.cursor
              .downField("result")
              .focus
              .toRight(
                Problem.Upstream.Replied(
                  Problem.Upstream.Name("account/rateLimits/read"),
                  External.Reported
                    .of(j.cursor.downField("error").downField("message").as[String].getOrElse(j.noSpaces))
                )
              )
      finally
        p.descendants().forEach { d =>
          val _ = d.destroy()
        }
        p.destroy()
        if !p.waitFor(3, TimeUnit.SECONDS) then { val _ = p.destroyForcibly() }
    catch
      case e: InterruptedException                  => throw e
      case e: java.util.concurrent.TimeoutException =>
        Left(
          Problem.Io.Process(
            AppServer,
            Problem.Io.Ending.TimedOut(Problem.Millis(seconds * 1000L))
          )
        )
      case e: Exception =>
        Left(ProblemLog.unexpected(e, Problem.Where("the plan pools' quota read")))

  /** The three messages of a read — and the only ones Histmersion ever sends the app-server. */
  val ReadRequests: List[String] = List(
    """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"clientInfo":{"name":"histmersion","title":"Histmersion","version":"1"}}}""",
    """{"jsonrpc":"2.0","method":"initialized"}""",
    """{"jsonrpc":"2.0","id":2,"method":"account/rateLimits/read"}"""
  )
