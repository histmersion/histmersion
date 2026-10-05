package com.histmersion.server.agents

import com.histmersion.core.*
import com.histmersion.core.Agents.*
import io.circe.parser

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*

/** `agent doctor`: every provider, checked WITHOUT spending anything (docs/research-embedded-agents.md
  * §15). Per provider: whether a role's chain uses it, its key (never shown), its base URL, the free
  * model listing (`GET …/models`, online only — never a generation), the chained models present
  * upstream, every chained model priced; for `claude-code`: the executable, `claude auth status` (the
  * login method and plan only — never the account), the loopback rule, and an allowance covering it.
  *
  * `listModels` and `runCommand` are seams a test fills (a stub server, a fake executable). */
final class AgentDoctor(app: AppConfig, picker: ModelPicker, resolve: ModelRef => Either[String, (LlmClient, Map[String, Price])],
    listModels: (String, List[(String, String)], Int) => Either[String, List[String]] = AgentDoctor.listModels,
    runCommand: (List[String], Map[String, Option[String]], Int) => Either[String, (Int, String)] = AgentDoctor.runCommand):

  private val cfg = app.agents

  private def ok(n: String, d: String)   = DoctorCheck(n, CheckLevel.Ok, d)
  private def warn(n: String, d: String) = DoctorCheck(n, CheckLevel.Warn, d)
  private def fail(n: String, d: String) = DoctorCheck(n, CheckLevel.Fail, d)

  def report(online: Boolean): DoctorReport =
    val chains = Role.values.toList.map(r => r -> picker.chain(r))
    val chained = chains.flatMap(_._2.toOption.toList.flatMap(_._1)).distinct
    val (allowances, allowanceProblems) = picker.allowances
    val providers = ProviderKind.values.toList.map { k =>
      val models = chained.filter(_.provider == k)
      val (checks, upstream) = k match
        case ProviderKind.ClaudeCode => (claudeCode(models, allowances), Nil)
        case _                       => http(k, models, online)
      val priced = models.map { m =>
        resolve(m) match
          case Right(_) => ok(s"model ${m.wire}", "priced, and its client can be built")
          case Left(why) => fail(s"model ${m.wire}", why)
      }
      ProviderDoctor(k.wire, models.nonEmpty, models.map(_.wire), checks ++ priced, upstream)
    }
    val roleChecks = chains.flatMap {
      case (r, Left(m)) => List(fail(s"role ${r.wire}", m))
      case (r, Right((Nil, _))) if neededBy(r) => List(warn(s"role ${r.wire}", s"no model — ${r.wire} steps are refused"))
      case _ => Nil
    }
    val global =
      (if cfg.enabled then ok("agents", "enabled") else warn("agents", "off (agents.enabled = false): nothing runs, settings can still be made")) ::
        Agents.Prices.parse(cfg.prices).fold(m => List(fail("prices", s"agents.prices: $m")), _ => Nil) ++
        allowanceProblems.map(fail("allowances", _)) ++
        allowances.collect { case e if e.allowance.isLeft => fail(s"allowance ${e.target}", e.allowance.left.toOption.getOrElse("")) } ++
        roleChecks
    DoctorReport(cfg.enabled, online, providers, global)

  /** A role some pipeline uses (the probe is optional). */
  private def neededBy(r: Role): Boolean = Pipeline.values.exists(_.roles.contains(r))

  // ── the HTTP providers ─────────────────────────────────────────────────────────────────────────

  private def http(k: ProviderKind, models: List[ModelRef], online: Boolean): (List[DoctorCheck], List[String]) =
    val p = cfg.provider(k).getOrElse(throw IllegalStateException(s"${k.wire} has no HTTP config"))
    val used = models.nonEmpty
    val keyName = k match
      case ProviderKind.Anthropic => "ANTHROPIC_API_KEY"
      case ProviderKind.OpenAI    => "OPENAI_API_KEY"
      case _                      => "HISTMERSION_AGENTS_LOCAL_API_KEY"
    val needsKey = k != ProviderKind.Local
    val key =
      if p.apiKey.nonEmpty then ok("key", s"$keyName is set")
      else if !needsKey then ok("key", "none (a local server needs none)")
      else if used then fail("key", s"$keyName is not set in the server's environment")
      else ok("key", s"$keyName is not set (no role uses ${k.wire})")
    val url = scala.util.Try(URI.create(p.baseUrl)).toOption.filter(u => u.getScheme == "http" || u.getScheme == "https")
    val urlCheck = url.fold(fail("base URL", s"'${p.baseUrl}' is not an http(s) URL"))(u => ok("base URL", u.toString))
    val skip = Option.when(!online)("offline: not asked")
      .orElse(Option.when(needsKey && p.apiKey.isEmpty)("no key: not asked"))
      .orElse(Option.when(url.isEmpty)("no URL: not asked"))
      .orElse(Option.when(!used && k == ProviderKind.Local)("no role uses it: not asked"))
    skip match
      case Some(why) => (List(key, urlCheck, ok("models listing", why)), Nil)
      case None =>
        val (endpoint, headers) = k match
          case ProviderKind.Anthropic =>
            (p.baseUrl.stripSuffix("/") + "/v1/models?limit=1000", List("x-api-key" -> p.apiKey, "anthropic-version" -> "2023-06-01"))
          case _ =>
            (p.baseUrl.stripSuffix("/") + "/models", Option.when(p.apiKey.nonEmpty)("Authorization" -> s"Bearer ${p.apiKey}").toList)
        listModels(endpoint, headers, cfg.doctorTimeoutSeconds) match
          case Left(why) =>
            (List(key, urlCheck, (if used then fail else warn)("models listing", s"GET $endpoint: $why")), Nil)
          case Right(ids) =>
            val present = models.map { m =>
              if ids.contains(m.model) then ok(s"upstream ${m.wire}", "listed by the provider")
              else warn(s"upstream ${m.wire}", s"not in the provider's list of ${ids.size} model(s) — an alias, or a typo?")
            }
            (List(key, urlCheck, ok("models listing", s"${ids.size} model(s), free call")) ++ present, ids)

  // ── claude-code ────────────────────────────────────────────────────────────────────────────────

  private def claudeCode(models: List[ModelRef], allowances: List[picker.Effective]): List[DoctorCheck] =
    val cc = app.agents.providers.claudeCode
    val env = ClaudeCodeEnv.of(app)
    val used = models.nonEmpty
    val level = if used then fail else warn
    val bind =
      if env.loopback then ok("bind", s"the server listens on loopback (${app.server.host}); only you can start a run")
      else if cc.allowNonLoopback then
        warn("bind", s"the server listens on ${app.server.host} and allow-non-loopback is on: every client of this server must be you")
      else level("bind", s"refused: the server listens on ${app.server.host}, beyond loopback — another client could spend your " +
        "Claude plan. Bind to 127.0.0.1 or set allow-non-loopback when every client is you")
    val childEnv: Map[String, Option[String]] =
      (if cc.useApiKey then Map.empty else Map("ANTHROPIC_API_KEY" -> None, "ANTHROPIC_AUTH_TOKEN" -> None)) ++
        Option(cc.configDir.trim).filter(_.nonEmpty).map(d => "CLAUDE_CONFIG_DIR" -> Some(d.replaceFirst("^~", System.getProperty("user.home")))).toMap
    val version = runCommand(List(cc.command, "--version"), childEnv, cfg.doctorTimeoutSeconds).flatMap {
      case (0, out)    => Right(out)
      case (code, out) => Left(s"exit $code: ${out.take(300)}")
    }
    val versionCheck = version.fold(
      why => level("executable", s"'${cc.command} --version' failed: $why — install Claude Code or set " +
        "agents.providers.claude-code.command (HISTMERSION_AGENTS_CLAUDE_COMMAND) to its absolute path"),
      v => ok("executable", s"${cc.command}: ${v.trim.linesIterator.nextOption().getOrElse("")}"))
    val auth = version.toOption.map { _ =>
      runCommand(List(cc.command, "auth", "status"), childEnv, cfg.doctorTimeoutSeconds) match
        case Left(why) => level("login", s"'claude auth status' failed: $why — log in with `claude` → /login")
        // exit 1 = not logged in; the JSON says so either way
        case Right((_, out)) => AgentDoctor.login(out, cc.useApiKey) match
          case Right(d) => ok("login", d)
          case Left(d)  => level("login", d)
    }.toList
    val keyInEnv = Option.when(!cc.useApiKey && (sys.env.contains("ANTHROPIC_API_KEY") || sys.env.contains("ANTHROPIC_AUTH_TOKEN")))(
      ok("API key", "the server's environment has an Anthropic key; it is removed from claude's (use-api-key = false), " +
        "so runs spend the plan, not the API")).toList
    val covered = allowances.exists(e => e.allowance.fold(_ => false, a => models.exists(a.target.covers) &&
      a.limits.nonEmpty) || (e.allowance.isLeft && e.target.startsWith(ProviderKind.ClaudeCode.wire)))
    val plan =
      if !used then Nil
      else List(warn("plan", "claude-code spends YOUR Claude plan's usage, shared with your own Claude and Claude Code use; " +
        "its real remaining usage cannot be seen from here — the allowance you set is the only cap. Personal use on " +
        "your own machine only (docs/research-model-providers.md)"),
        if covered then ok("allowance", "an allowance covers the claude-code models")
        else warn("allowance", "no allowance covers claude-code — set one, e.g. `histmersion agent allowance 'claude-code:*' " +
          "--limit '2M tokens/5h' --reserve 20`, or a run may spend the plan to its limit"))
    val mcp = ok("tools", s"steps with tools reach ${env.mcpUrl} (read-only agent tools)")
    List(bind, versionCheck) ++ auth ++ keyInEnv ++ plan ++ List(mcp)

object AgentDoctor:

  /** `claude auth status` (JSON) → the login, without the account: logged in, how, which plan. Left =
    * not usable as configured. */
  def login(json: String, useApiKey: Boolean): Either[String, String] =
    parser.parse(json.trim).toOption.map(_.hcursor) match
      case None => Left(s"'claude auth status' did not answer JSON: ${json.take(200)}")
      case Some(c) =>
        val in     = c.downField("loggedIn").as[Boolean].getOrElse(false)
        val method = c.downField("authMethod").as[String].getOrElse("unknown")
        val plan   = c.downField("subscriptionType").as[String].toOption.filter(_.nonEmpty)
        if !in then Left("not logged in — run `claude`, then /login (or `claude setup-token` for a long-lived token)")
        else if !useApiKey && (method == "api_key" || method == "api_key_helper") then
          Left(s"logged in with an API key ($method), not a Claude plan — log in with your Claude account, or set use-api-key")
        else Right(s"logged in ($method${plan.fold("")(p => s", plan $p")})")

  private lazy val client: HttpClient =
    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build()

  /** `GET <url>` with `headers`; `data[].id` of an OpenAI- or Anthropic-style model listing. */
  def listModels(url: String, headers: List[(String, String)], timeoutSeconds: Int): Either[String, List[String]] =
    try
      val req = headers.foldLeft(HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(math.max(1, timeoutSeconds).toLong))) {
        case (b, (k, v)) => b.header(k, v)
      }.build()
      val res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
      if res.statusCode() / 100 != 2 then Left(s"HTTP ${res.statusCode()}: ${res.body().take(200)}")
      else modelIds(res.body())
    catch
      case e: InterruptedException => throw e
      case e: Exception => Left(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}".take(300))

  /** `{"data":[{"id":…},…]}` → the ids. */
  def modelIds(body: String): Either[String, List[String]] =
    parser.parse(body).left.map(e => s"not JSON: ${e.getMessage}").flatMap { j =>
      j.hcursor.downField("data").as[List[io.circe.Json]].left.map(_ => "no data[] in the answer")
        .map(_.flatMap(_.hcursor.downField("id").as[String].toOption))
    }

  /** Run a local command (no network of ours) with `env` changes (None = removed): its exit code and
    * output, or why it could not be run. */
  def runCommand(argv: List[String], env: Map[String, Option[String]], timeoutSeconds: Int): Either[String, (Int, String)] =
    try
      val pb = ProcessBuilder(argv.asJava).redirectErrorStream(true)
      env.foreach {
        case (k, Some(v)) => val _ = pb.environment().put(k, v)
        case (k, None)    => val _ = pb.environment().remove(k)
      }
      val p = pb.start()
      p.getOutputStream.close()
      val out = java.util.concurrent.CompletableFuture.supplyAsync(() => String(p.getInputStream.readAllBytes(), StandardCharsets.UTF_8))
      if !p.waitFor(math.max(1, timeoutSeconds).toLong, TimeUnit.SECONDS) then
        p.destroyForcibly()
        Left(s"no answer within $timeoutSeconds s")
      else
        Right((p.exitValue(), out.get(5, TimeUnit.SECONDS)))
    catch
      case e: InterruptedException => throw e
      case e: Exception => Left(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}".take(300))
