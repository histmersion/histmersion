package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import io.circe.parser

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import com.histmersion.api.automation.Agents

/** `agent doctor`: every provider, checked WITHOUT spending anything (docs/research-embedded-agents.md §15). Per
  * provider: whether a role's chain uses it, its key (never shown), its base URL, the free model listing (`GET
  * …/models`, online only — never a generation), the chained models present upstream, every chained model on a plan
  * pool or priced; for `claude-code`: the executable, `claude auth status` (the login method and plan only — never the
  * account), the loopback rule, and an allowance covering it.
  *
  * `listModels` and `runCommand` are seams a test fills (a stub server, a fake executable).
  */
final class AgentDoctor(
    app: AppConfig,
    picker: ModelPicker,
    resolve: ModelRef => Either[String, (LlmClient, Billing)],
    listModels: (String, List[(String, String)], Int) => Either[String, List[String]] = AgentDoctor.listModels,
    runCommand: (List[String], Map[String, Option[String]], Int) => Either[String, (Int, String)] =
      AgentDoctor.runCommand,
    /** The AI-provider registry of the pinned plugin generation (docs/research-plugins.md §22): a host provider whose
      * plugin is off says so, and each plugin provider gets its own section. None: every host provider is taken as on,
      * and there is no plugin provider (a test's doctor).
      */
    registry: Option[scala.collection.immutable.ListMap[String, com.histmersion.plugin.AiProvider]] = None,
    aiHttp: com.histmersion.plugin.AiHttp = com.histmersion.plugin.AiHttp.Offline,
    /** The process environment the entry point read (none: a test's doctor). */
    paths: com.histmersion.config.UserPaths = com.histmersion.domain.automation.agents.AgentTrees.NoEnvironment
):

  private val cfg = app.agents

  private def ok(n: String, d: String) = DoctorCheck(n, CheckLevel.Ok, d)
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
        case ProviderKind.Codex      => (codex(models), Nil)
        case ProviderKind.Opencode   => (opencode(models), Nil)
        case _                       => http(k, models, online)
      val priced = models.map { m =>
        resolve(m) match
          case Right((_, b)) => ok(s"model ${m.wire}", s"${b.describe}; its client can be built")
          case Left(why)     => fail(s"model ${m.wire}", why)
      }
      val off = registry
        .filterNot(_.contains(k.wire))
        .map(_ => BuiltinAi.lookup(k, scala.collection.immutable.ListMap.empty).left.toOption.getOrElse("switched off"))
        .map(why => (if models.nonEmpty then fail else warn) ("plugin", why))
        .toList
      ProviderDoctor(k.wire, models.nonEmpty, models.map(_.wire), off ++ checks ++ priced, upstream)
    } ++ plugins(chained, online)
    val roleChecks = chains.flatMap {
      case (r, Left(m))                        => List(fail(s"role ${r.wire}", m))
      case (r, Right((Nil, _))) if neededBy(r) =>
        List(warn(s"role ${r.wire}", s"no model — ${r.wire} steps are refused"))
      case _ => Nil
    }
    val global =
      (if cfg.enabled then ok("agents", "enabled")
       else warn("agents", "off (agents.enabled = false): nothing runs, settings can still be made")) ::
        Agents.Prices.parse(cfg.prices).fold(m => List(fail("prices", s"agents.prices: $m")), _ => Nil) ++
        allowanceProblems.map(fail("allowances", _)) ++
        allowances.collect {
          case e if e.allowance.isLeft => fail(s"allowance ${e.target}", e.allowance.left.toOption.getOrElse(""))
        } ++
        roleChecks
    DoctorReport(cfg.enabled, online, providers, global)

  /** One section per PLUGIN provider: what it is, its own checks, its free model listing (online only), and every
    * chained model priced and buildable. Nothing it does is allowed to throw the report away.
    */
  private def plugins(chained: List[ModelRef], online: Boolean): List[ProviderDoctor] =
    registry.toList.flatMap(_.toList).collect {
      case (name, p) if !BuiltinAi.reserved(name) =>
        val kind = ProviderKind.Plugin(name)
        val models = chained.filter(_.provider == kind)
        def safe[A](what: String, a: => A, orElse: String => A): A =
          try a
          catch case scala.util.control.NonFatal(e) => orElse(s"$what threw ${e.getClass.getSimpleName}")
        val own = safe(
          "its doctor",
          p.doctor(online, aiHttp),
          why => List(com.histmersion.plugin.AiCheck("doctor", "fail", why))
        )
          .map(c => DoctorCheck(c.name, CheckLevel.parse(c.level).getOrElse(CheckLevel.Warn), c.detail))
        val (listing, upstream) =
          if !online then (List(ok("models listing", "offline: not asked")), Nil)
          else
            safe("its model listing", p.models(aiHttp), why => Left(why)) match
              case Left(why)  => (List((if models.nonEmpty then fail else warn) ("models listing", why)), Nil)
              case Right(Nil) => (List(ok("models listing", "the provider has none")), Nil)
              case Right(ids) =>
                (
                  ok("models listing", s"${ids.size} model(s), free call") :: models.map { m =>
                    if ids.contains(m.model) then ok(s"upstream ${m.wire}", "listed by the provider")
                    else
                      warn(
                        s"upstream ${m.wire}",
                        s"not in the provider's list of ${ids.size} model(s) — an alias, or a typo?"
                      )
                  },
                  ids
                )
        val priced = models.map { m =>
          resolve(m) match
            case Right((_, b)) => ok(s"model ${m.wire}", s"${b.describe}; its client can be built")
            case Left(why)     => fail(s"model ${m.wire}", why)
        }
        ProviderDoctor(
          name,
          models.nonEmpty,
          models.map(_.wire),
          ok("plugin", safe("its title", p.title, identity)) :: (own ++ listing ++ priced),
          upstream
        )
    }

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
    val url =
      scala.util.Try(URI.create(p.baseUrl)).toOption.filter(u => u.getScheme == "http" || u.getScheme == "https")
    val urlCheck = url.fold(fail("base URL", s"'${p.baseUrl}' is not an http(s) URL"))(u => ok("base URL", u.toString))
    val skip = Option
      .when(!online)("offline: not asked")
      .orElse(Option.when(needsKey && p.apiKey.isEmpty)("no key: not asked"))
      .orElse(Option.when(url.isEmpty)("no URL: not asked"))
      .orElse(Option.when(!used && k == ProviderKind.Local)("no role uses it: not asked"))
    skip match
      case Some(why) => (List(key, urlCheck, ok("models listing", why)), Nil)
      case None      =>
        val (endpoint, headers) = k match
          case ProviderKind.Anthropic =>
            (
              p.baseUrl.stripSuffix("/") + "/v1/models?limit=1000",
              List("x-api-key" -> p.apiKey, "anthropic-version" -> "2023-06-01")
            )
          case _ =>
            (
              p.baseUrl.stripSuffix("/") + "/models",
              Option.when(p.apiKey.nonEmpty)("Authorization" -> s"Bearer ${p.apiKey}").toList
            )
        listModels(endpoint, headers, cfg.doctorTimeoutSeconds) match
          case Left(why) =>
            (List(key, urlCheck, (if used then fail else warn) ("models listing", s"GET $endpoint: $why")), Nil)
          case Right(ids) =>
            val present = models.map { m =>
              if ids.contains(m.model) then ok(s"upstream ${m.wire}", "listed by the provider")
              else
                warn(
                  s"upstream ${m.wire}",
                  s"not in the provider's list of ${ids.size} model(s) — an alias, or a typo?"
                )
            }
            (List(key, urlCheck, ok("models listing", s"${ids.size} model(s), free call")) ++ present, ids)

  // ── claude-code ────────────────────────────────────────────────────────────────────────────────

  private def claudeCode(models: List[ModelRef], allowances: List[picker.Effective]): List[DoctorCheck] =
    val cc = app.agents.providers.claudeCode
    val loopback = ClaudeCode.Env.isLoopback(app.server.host)
    val used = models.nonEmpty
    val level = if used then fail else warn
    val bind =
      if loopback then ok("bind", s"the server listens on loopback (${app.server.host}); only you can start a run")
      else if cc.allowNonLoopback then
        warn(
          "bind",
          s"the server listens on ${app.server.host} and allow-non-loopback is on: every client of this server must be you"
        )
      else
        level(
          "bind",
          s"refused: the server listens on ${app.server.host}, beyond loopback — another client could spend your " +
            "Claude plan. Bind to 127.0.0.1 or set allow-non-loopback when every client is you"
        )
    val childEnv: Map[String, Option[String]] =
      (if cc.useApiKey then Map.empty else Map("ANTHROPIC_API_KEY" -> None, "ANTHROPIC_AUTH_TOKEN" -> None)) ++
        Option(cc.configDir.trim)
          .filter(_.nonEmpty)
          .map(d => "CLAUDE_CONFIG_DIR" -> Some(AgentTrees.expandIn(d, paths)))
          .toMap
    val version = runCommand(List(cc.command, "--version"), childEnv, cfg.doctorTimeoutSeconds).flatMap {
      case (0, out)    => Right(out)
      case (code, out) => Left(s"exit $code: ${out.take(300)}")
    }
    val versionCheck = version.fold(
      why =>
        level(
          "executable",
          s"'${cc.command} --version' failed: $why — install Claude Code or set " +
            "agents.providers.claude-code.command (HISTMERSION_AGENTS_CLAUDE_COMMAND) to its absolute path"
        ),
      v => ok("executable", s"${cc.command}: ${v.trim.linesIterator.nextOption().getOrElse("")}")
    )
    val auth = version.toOption.map { _ =>
      runCommand(List(cc.command, "auth", "status"), childEnv, cfg.doctorTimeoutSeconds) match
        case Left(why) => level("login", s"'claude auth status' failed: $why — log in with `claude` → /login")
        // exit 1 = not logged in; the JSON says so either way
        case Right((_, out)) =>
          AgentDoctor.login(out, cc.useApiKey) match
            case Right(d) => ok("login", d)
            case Left(d)  => level("login", d)
    }.toList
    val keyInEnv = Option
      .when(
        !cc.useApiKey && (paths.environment
          .get("ANTHROPIC_API_KEY")
          .isDefined || paths.environment.get("ANTHROPIC_AUTH_TOKEN").isDefined)
      )(
        ok(
          "API key",
          "the server's environment has an Anthropic key; it is removed from claude's (use-api-key = false), " +
            "so runs spend the plan, not the API"
        )
      )
      .toList
    val covered = allowances.exists(e =>
      e.allowance.fold(
        _ => false,
        a =>
          models.exists(a.target.covers) &&
            a.limits.nonEmpty
      ) || (e.allowance.isLeft && e.target.startsWith(ProviderKind.ClaudeCode.wire))
    )
    val plan =
      if !used then Nil
      else
        List(
          warn(
            "plan",
            "claude-code spends YOUR Claude plan's usage, shared with your own Claude and Claude Code use; " +
              "the Claude plan pool pauses runs below its floors (histmersion agent quota). Personal use on " +
              "your own machine only (docs/research-model-providers.md)"
          ),
          if covered then
            ok("allowance", "an allowance also covers the claude-code models (a token cap on top of the plan floors)")
          else ok("allowance", "no metered allowance: the plan floors are the cap")
        )
    List(bind, versionCheck) ++ auth ++ keyInEnv ++ plan ++ List(agentTools, generated("claude-code"))

  private def agentTools =
    ok(
      "tools",
      s"steps reach ${app.server.publicUrl.stripSuffix("/")}/mcp/agent with a run token bound to their role's tools"
    )

  /** Whether the generated tree a CLI runtime launches exists. */
  private def generated(runtime: String): DoctorCheck =
    AgentTrees.root(app, paths).map(_.resolve(runtime)) match
      case Some(d) if java.nio.file.Files.isDirectory(d) => ok("generated", s"$d")
      case Some(d) => warn("generated", s"$d does not exist yet — run `histmersion agent generate`")
      case None    => warn("generated", "no runtimes directory — set agents.runtimes.dir")

  /** A CLI provider without a login check of its own: the executable answers `--version`. */
  private def version(
      command: String,
      env: Map[String, Option[String]],
      level: (String, String) => DoctorCheck,
      hint: String
  ): DoctorCheck =
    runCommand(List(command, "--version"), env, cfg.doctorTimeoutSeconds) match
      case Right((0, out)) => ok("executable", s"$command: ${out.trim.linesIterator.nextOption().getOrElse("")}")
      case Right((c, out)) => level("executable", s"'$command --version' exit $c: ${out.take(200)} — $hint")
      case Left(why)       => level("executable", s"'$command --version' failed: $why — $hint")

  // ── codex, opencode ────────────────────────────────────────────────────────────────────────────

  private def codex(models: List[ModelRef]): List[DoctorCheck] =
    val cc = cfg.providers.codex
    val level = if models.nonEmpty then fail else warn
    val home = AgentTrees(app, AgentDefinitions(Prompts(cfg.promptsDir), AgentRoster.none), paths).codexHome
    val login = home.map { h =>
      if java.nio.file.Files.isRegularFile(h.resolve("auth.json")) then ok("login", s"$h has a login (never read here)")
      else level("login", s"no login in $h — run `CODEX_HOME=\"$h\" ${cc.command} login` once")
    }.toList
    List(
      version(
        cc.command,
        Map("OPENAI_API_KEY" -> None, "CODEX_API_KEY" -> None),
        level,
        "install the Codex CLI or set agents.providers.codex.command"
      )
    ) ++ login ++
      List(
        agentTools,
        generated("codex"),
        if cc.appServer then
          ok("plan pool", "read with codex app-server (account/rateLimits/read); see histmersion agent quota")
        else warn("plan pool", "app-server reads are off: the ChatGPT plan's windows are not known")
      )

  private def opencode(models: List[ModelRef]): List[DoctorCheck] =
    val oc = cfg.providers.opencode
    val level = if models.nonEmpty then fail else warn
    List(
      version(oc.command, Map.empty, level, "install opencode or set agents.providers.opencode.command"),
      agentTools,
      generated("opencode"),
      if oc.chatgptLogin then ok("plan pool", "its openai/… models draw on the ChatGPT plan pool")
      else ok("plan pool", "metered: allowances and prices apply (no plan login)")
    )

object AgentDoctor:

  /** `claude auth status` (JSON) → the login, without the account: logged in, how, which plan. Left = not usable as
    * configured.
    */
  def login(json: String, useApiKey: Boolean): Either[String, String] =
    parser.parse(json.trim).toOption.map(_.hcursor) match
      case None    => Left(s"'claude auth status' did not answer JSON: ${json.take(200)}")
      case Some(c) =>
        val in = c.downField("loggedIn").as[Boolean].getOrElse(false)
        val method = c.downField("authMethod").as[String].getOrElse("unknown")
        val plan = c.downField("subscriptionType").as[String].toOption.filter(_.nonEmpty)
        if !in then Left("not logged in — run `claude`, then /login (or `claude setup-token` for a long-lived token)")
        else if !useApiKey && (method == "api_key" || method == "api_key_helper") then
          Left(
            s"logged in with an API key ($method), not a Claude plan — log in with your Claude account, or set use-api-key"
          )
        else Right(s"logged in ($method${plan.fold("")(p => s", plan $p")})")

  private lazy val client: HttpClient =
    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build()

  /** `GET <url>` with `headers`; `data[].id` of an OpenAI- or Anthropic-style model listing. */
  def listModels(url: String, headers: List[(String, String)], timeoutSeconds: Int): Either[String, List[String]] =
    try
      val req = headers
        .foldLeft(
          HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(math.max(1, timeoutSeconds).toLong))
        ) { case (b, (k, v)) =>
          b.header(k, v)
        }
        .build()
      val res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
      if res.statusCode() / 100 != 2 then Left(s"HTTP ${res.statusCode()}: ${res.body().take(200)}")
      else modelIds(res.body())
    catch
      case e: InterruptedException => throw e
      case e: Exception => Left(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}".take(300))

  /** `{"data":[{"id":…},…]}` → the ids. */
  def modelIds(body: String): Either[String, List[String]] =
    parser.parse(body).left.map(e => s"not JSON: ${e.getMessage}").flatMap { j =>
      j.hcursor
        .downField("data")
        .as[List[io.circe.Json]]
        .left
        .map(_ => "no data[] in the answer")
        .map(_.flatMap(_.hcursor.downField("id").as[String].toOption))
    }

  /** Run a local command (no network of ours) with `env` changes (None = removed): its exit code and output, or why it
    * could not be run.
    */
  def runCommand(
      argv: List[String],
      env: Map[String, Option[String]],
      timeoutSeconds: Int
  ): Either[String, (Int, String)] =
    try
      val pb = ProcessBuilder(argv.asJava).redirectErrorStream(true)
      env.foreach {
        case (k, Some(v)) => val _ = pb.environment().put(k, v)
        case (k, None)    => val _ = pb.environment().remove(k)
      }
      val p = pb.start()
      p.getOutputStream.close()
      val out = java.util.concurrent.CompletableFuture.supplyAsync(() =>
        String(p.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
      )
      if !p.waitFor(math.max(1, timeoutSeconds).toLong, TimeUnit.SECONDS) then
        p.destroyForcibly()
        Left(s"no answer within $timeoutSeconds s")
      else Right((p.exitValue(), out.get(5, TimeUnit.SECONDS)))
    catch
      case e: InterruptedException => throw e
      case e: Exception => Left(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}".take(300))
