package com.histmersion.server.agents

import com.histmersion.core.{AgentsConfig, PlanQuota}
import com.histmersion.core.Agents.ModelRef
import com.histmersion.core.PlanQuota.*
import com.histmersion.db.PlanQuotaRepo
import io.circe.{Json, parser}

import java.nio.charset.StandardCharsets
import java.util.concurrent.{ConcurrentHashMap, Semaphore, TimeUnit}
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource
import scala.jdk.CollectionConverters.*

/** What a CLI client reports its readings to and asks mid-step: the plan pools ([[PlanPools]]), or a
  * test's stand-in. */
trait PoolSink:
  def record(p: PoolId, rs: List[Reading]): Unit
  /** Why `p` may not take another call now, and when it frees; None = it may. */
  def blocked(p: PoolId, model: Option[String]): Option[(String, Option[Long])]
  /** Read the ChatGPT plan from the app-server when the last read is old enough (throttled). */
  def refresh(now: Long): Unit

/** The subscription pools in effect (docs/research-plan-quotas.md §5): the readings every source
  * writes, the floors (saved over config), the hold switch, the provider's own refusals — and the
  * verdict the model pick asks before every LLM call. `codexHome` is the CODEX_HOME the runs use, so
  * the app-server read is of THAT login. */
final class PlanPools(ds: DataSource, cfg: AgentsConfig, codexHome: () => Option[java.nio.file.Path],
    readCodex: (String, java.nio.file.Path, Int) => Either[String, Json] = PlanPools.readCodex) extends PoolSink:

  private val slots = ConcurrentHashMap[PoolId, Semaphore]()
  private val lastPoll = AtomicLong(0L)

  private def poolCfg(p: PoolId) = p match
    case PoolId.ClaudePlan  => cfg.quota.claudePlan
    case PoolId.ChatgptPlan => cfg.quota.chatgptPlan

  /** At most `max-calls-in-flight` LLM calls draw on a plan at once (default 1: the three-way review
    * panel would otherwise spend the last headroom three times). */
  def slot(p: PoolId): Semaphore = slots.computeIfAbsent(p, _ => Semaphore(math.max(1, poolCfg(p).maxCallsInFlight)))

  def poolOf(m: ModelRef): Option[PoolId] = PlanQuota.poolOf(m, cfg.logins)
  def paid(m: ModelRef): Boolean = PlanQuota.paid(m, cfg.logins)

  /** The floors of a pool and where they come from: saved, else config (a config string that does not
    * parse floors nothing it could not read, and says so). */
  def floors(p: PoolId): (Floors, String, Option[String]) =
    val saved = PlanQuotaRepo.pool(ds, p).floors
    saved.map(Floors.parse) match
      case Some(Right(f)) => (f, "saved", None)
      case Some(Left(m))  => (Floors.Empty, "saved", Some(s"saved floors do not parse: $m"))
      case None => Floors.parse(poolCfg(p).floors) match
        case Right(f) => (f, "config", None)
        case Left(m)  => (Floors.Empty, "config", Some(s"agents.quota.${p.wire}.floors: $m"))

  def rules(p: PoolId): Rules =
    Rules(floors(p)._1, cfg.quota.defaultFloor, math.max(1, cfg.quota.staleMinutes).toLong * 60000L, cfg.quota.margin)

  /** The verdict for one call on `p` (and its model's own buckets). The ChatGPT pool is read from the
    * app-server first when its last reading is older than `poll-seconds`. */
  def verdict(p: PoolId, model: Option[String], now: Long = System.currentTimeMillis()): (Verdict, List[WindowState]) =
    if p == PoolId.ChatgptPlan then poll(now)
    val pool = PlanQuotaRepo.pool(ds, p)
    PlanQuota.verdict(PlanQuotaRepo.readings(ds, p), pool.held, pool.refusal.map(_ -> pool.refusalUntil), rules(p), now, model)

  def record(p: PoolId, rs: List[Reading]): Unit = PlanQuotaRepo.record(ds, p, rs)

  def blocked(p: PoolId, model: Option[String]): Option[(String, Option[Long])] =
    verdict(p, model)._1 match
      case Verdict.Blocked(why, until) => Some((why, until))
      case _                           => None

  def refresh(now: Long): Unit = { val _ = poll(now) }

  def hold(p: PoolId, on: Boolean): Unit = PlanQuotaRepo.setHold(ds, p, on)
  def setFloors(p: PoolId, f: Option[String]): Unit = PlanQuotaRepo.setFloors(ds, p, f)

  /** Read the ChatGPT plan through `codex app-server` (it generates nothing), at most once per
    * `poll-seconds`, and only with `app-server` on and a CODEX_HOME to read. */
  def poll(now: Long, force: Boolean = false): Option[String] =
    val every = math.max(1, cfg.quota.pollSeconds).toLong * 1000L
    val last  = lastPoll.get()
    if !cfg.providers.codex.appServer || (!force && now - last < every) || !lastPoll.compareAndSet(last, now) then None
    else codexHome() match
      case None => Some("no CODEX_HOME to read")
      case Some(home) =>
        readCodex(cfg.providers.codex.command, home, math.max(5, cfg.doctorTimeoutSeconds)) match
          case Left(why) =>
            scribe.info(s"codex app-server rate-limit read failed: $why")
            Some(why)
          case Right(json) =>
            val (rs, refusal, plan) = PlanPools.codexReadings(json, now)
            record(PoolId.ChatgptPlan, rs)
            PlanQuotaRepo.setRefusal(ds, PoolId.ChatgptPlan, refusal.map(_._1), refusal.flatMap(_._2), plan)
            None

  /** A mod's report: recorded, and answered with whether the run may go on. */
  def guard(report: ModReport, now: Long = System.currentTimeMillis()): GuardAnswer =
    val src = Source.parse(report.source).filter(s => s == Source.Mod || s == Source.Interactive).getOrElse(Source.Mod)
    record(PoolId.ClaudePlan, PlanPools.modReadings(report.rateLimits, src, now))
    verdict(PoolId.ClaudePlan, None, now)._1 match
      case Verdict.Usable(_)          => GuardAnswer(allow = true)
      case Verdict.Blocked(why, until) => GuardAnswer(allow = false, Some(why), until.map(java.time.Instant.ofEpochMilli(_).toString))

  /** Every plan pool, as `agent quota` shows it. */
  def statuses(covers: PoolId => List[String], now: Long = System.currentTimeMillis()): List[PoolStatus] =
    PoolId.values.toList.map { p =>
      val (v, states) = verdict(p, None, now)
      val (f, src, problem) = floors(p)
      val pool = PlanQuotaRepo.pool(ds, p)
      val iso = (ms: Long) => java.time.Instant.ofEpochMilli(ms).toString
      val ws = states.map { s =>
        WindowStatus(s.reading.limit, s.reading.kind, s.reading.usedPct, s.leftPct, s.floor, s.usable, s.stale,
          s.reading.resetsAt.map(iso), iso(s.reading.observedAt), math.max(0, (now - s.reading.observedAt) / 1000),
          s.reading.source.wire, s.why)
      }
      val (ok, why, until) = v match
        case Verdict.Usable(n)       => (true, n, None)
        case Verdict.Blocked(w, u)   => (false, Some(w), u.map(iso))
      PoolStatus(p.wire, p.title, "plan", covers(p), f.text, src, pool.held, ok, why, until,
        math.max(1, poolCfg(p).maxCallsInFlight), ws, pool.planType, problem)
    }

object PlanPools:

  private def num(j: Json, k: String): Option[Double] =
    j.hcursor.downField(k).as[Double].toOption

  /** Claude's `rate_limit_event.rate_limit_info` → readings (§3a P1): every `unifiedWindows` entry
    * (`five_hour`, `seven_day`, …; `utilization` 0–1, `resetsAt` epoch s), else the representative
    * window; `status: rejected` marks the window it names refused. */
  def claudeReadings(info: Json, source: Source, now: Long): List[Reading] =
    val c        = info.hcursor
    val status   = c.downField("status").as[String].toOption
    val repKind  = c.downField("rateLimitType").as[String].toOption
    val repReset = c.downField("resetsAt").as[Long].toOption.map(_ * 1000)
    val unified  = c.downField("unifiedWindows").focus.flatMap(_.asObject).toList.flatMap(_.toList)
    val fromUnified = unified.flatMap { (kind, w) =>
      num(w, "utilization").map { u =>
        Reading("claude", kind, u * 100, w.hcursor.downField("resetsAt").as[Long].toOption.map(_ * 1000),
          rejected = status.contains("rejected") && repKind.contains(kind), now, source)
      }
    }
    if fromUnified.nonEmpty then fromUnified
    else repKind.toList.flatMap { kind =>
      val used = num(info, "utilization").map(_ * 100)
      // without a utilization the representative window says only "allowed" or "rejected"
      if status.contains("rejected") then List(Reading("claude", kind, used.getOrElse(100.0), repReset, rejected = true, now, source))
      else used.map(u => Reading("claude", kind, u, repReset, rejected = false, now, source)).toList
    }

  /** A mod's `rateLimits` (`percentUsed`, `resetsAt` ISO) → readings. */
  def modReadings(ws: List[ModWindow], source: Source, now: Long): List[Reading] =
    ws.map { w =>
      val reset = w.resetsAt.flatMap(t => scala.util.Try(java.time.Instant.parse(t).toEpochMilli).toOption)
      Reading("claude", w.kind, w.percentUsed, reset, rejected = false, now, source)
    }

  /** `account/rateLimits/read`'s result → readings (windows keyed by LENGTH, `300m`/`10080m`: primary
    * is not always the 5-hour one), the refusal (`ordinaryUsageAllowed: false`; a reached limit marks
    * its windows refused) and the plan type. Reset credits and upsells are ignored — never acted on. */
  def codexReadings(result: Json, now: Long): (List[Reading], Option[(String, Option[Long])], Option[String]) =
    val c = result.hcursor
    val buckets: List[Json] =
      c.downField("rateLimitsByLimitId").focus.flatMap(_.asObject).map(_.values.toList)
        .filter(_.nonEmpty).getOrElse(c.downField("rateLimits").focus.toList)
    val rs = buckets.flatMap { b =>
      val bc = b.hcursor
      val limit   = bc.downField("limitId").as[String].toOption.getOrElse("codex")
      val reached = bc.downField("rateLimitReachedType").focus.exists(!_.isNull)
      val model   = bc.downField("normalModelSlug").as[String].toOption.filter(_ => limit != "codex")
      List("primary", "secondary").flatMap(k => bc.downField(k).focus.filterNot(_.isNull)).flatMap { w =>
        val wc = w.hcursor
        for
          used <- wc.downField("usedPercent").as[Double].toOption
          mins <- wc.downField("windowDurationMins").as[Long].toOption
        yield Reading(limit, s"${mins}m", used, wc.downField("resetsAt").as[Long].toOption.map(_ * 1000),
          rejected = reached, now, Source.AppServer, model)
      }
    }
    val refusal = c.downField("ordinaryUsageAllowed").as[Boolean].toOption.filter(!_).map(_ =>
      ("the ChatGPT plan does not allow ordinary usage now (ordinaryUsageAllowed: false)", Option.empty[Long]))
    val plan = buckets.flatMap(_.hcursor.downField("planType").as[String].toOption).headOption
    (rs, refusal, plan)

  /** One `account/rateLimits/read` through `codex app-server` (stdio JSON-RPC: `initialize` →
    * `initialized` → the read), with `CODEX_HOME` = the runs' home. Nothing else is ever sent — in
    * particular never `account/rateLimitResetCredit/consume`. */
  def readCodex(command: String, home: java.nio.file.Path, timeoutSeconds: Int): Either[String, Json] =
    try
      val pb = ProcessBuilder(command, "app-server").redirectError(ProcessBuilder.Redirect.DISCARD)
      val env = pb.environment()
      val _ = env.put("CODEX_HOME", home.toString)
      List("OPENAI_API_KEY", "CODEX_API_KEY").foreach(k => { val _ = env.remove(k) })
      val p = pb.start()
      try
        val w = p.outputWriter(StandardCharsets.UTF_8)
        val send = (s: String) => { w.write(s); w.write("\n"); w.flush() }
        send(ReadRequests.head); send(ReadRequests(1)); send(ReadRequests(2))
        val lines = p.inputReader(StandardCharsets.UTF_8)
        val reader = java.util.concurrent.CompletableFuture.supplyAsync { () =>
          Iterator.continually(lines.readLine()).takeWhile(_ != null)
            .flatMap(l => parser.parse(l).toOption)
            .find(j => j.hcursor.downField("id").as[Int].toOption.contains(2))
        }
        reader.get(math.max(1, timeoutSeconds).toLong, TimeUnit.SECONDS) match
          case None => Left("codex app-server ended without answering account/rateLimits/read")
          case Some(j) =>
            j.hcursor.downField("result").focus.toRight(
              s"account/rateLimits/read: ${j.hcursor.downField("error").downField("message").as[String].getOrElse(j.noSpaces.take(200))}")
      finally
        p.descendants().forEach(d => { val _ = d.destroy() })
        p.destroy()
        if !p.waitFor(3, TimeUnit.SECONDS) then { val _ = p.destroyForcibly() }
    catch
      case e: InterruptedException => throw e
      case e: java.util.concurrent.TimeoutException => Left(s"codex app-server did not answer within $timeoutSeconds s")
      case e: Exception => Left(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}".take(300))

  /** The three messages of a read — and the only ones Histmersion ever sends the app-server. */
  val ReadRequests: List[String] = List(
    """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"clientInfo":{"name":"histmersion","title":"Histmersion","version":"1"}}}""",
    """{"jsonrpc":"2.0","method":"initialized"}""",
    """{"jsonrpc":"2.0","id":2,"method":"account/rateLimits/read"}""")
