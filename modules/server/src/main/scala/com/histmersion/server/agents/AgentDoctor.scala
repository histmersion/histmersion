package com.histmersion.server.agents

import io.scalaland.chimney.dsl.*

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
import com.histmersion.util.process.{ProcessLaunch, ProcessResult}

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
    resolve: ModelRef => Either[AgentDoctor.Problem, (LlmClient, Billing)],
    listModels: (com.histmersion.server.HttpDownload.Url, List[(AgentDoctor.HeaderName, AgentDoctor.HeaderText)], AgentsConfig.DoctorTimeoutSeconds) => Either[AgentDoctor.Problem, List[ModelRef.Model]] = AgentDoctor.listModels,
    runCommand: (List[ProcessLaunch.Command], Map[ProcessLaunch.EnvironmentKey, Option[ProcessLaunch.EnvironmentValue]], AgentsConfig.DoctorTimeoutSeconds) => Either[AgentDoctor.Problem, (ProcessResult.ExitCode, AgentDoctor.Output)] =
      AgentDoctor.runCommand,
    /** The AI-provider registry of the pinned plugin generation (docs/research-plugins.md §22): a host provider whose
      * plugin is off says so, and each plugin provider gets its own section. None: every host provider is taken as on,
      * and there is no plugin provider (a test's doctor).
      */
    registry: Option[scala.collection.immutable.ListMap[AgentDoctor.RegistryKey, com.histmersion.plugin.AiProvider]] = None,
    aiHttp: com.histmersion.plugin.AiHttp = com.histmersion.plugin.AiHttp.Offline,
    /** The process environment the entry point read (none: a test's doctor). */
    paths: com.histmersion.config.UserPaths = com.histmersion.domain.automation.agents.AgentTrees.NoEnvironment
):

  private val cfg = app.agents

  private def check(level: CheckLevel, n: String, d: String) = DoctorCheck(DoctorCheck.Name(n), level, DoctorCheck.Detail(d))
  private def ok(n: String, d: String) = check(CheckLevel.Ok, n, d)
  private def warn(n: String, d: String) = check(CheckLevel.Warn, n, d)
  private def fail(n: String, d: String) = check(CheckLevel.Fail, n, d)
  private def cmd(text: String): ProcessLaunch.Command = ProcessLaunch.Command(text)
  private def envKey(text: String): ProcessLaunch.EnvironmentKey = ProcessLaunch.EnvironmentKey(text)

  def report(online: DoctorReport.Online): DoctorReport =
    val chains = Role.values.toList.map(r => r -> picker.chain(r))
    val chained = chains.flatMap(_._2.toOption.toList.flatMap(_._1)).distinct
    val (allowances, allowanceProblems) = picker.allowances
    val providers = ProviderKind.values.toList.map { k =>
      val models = chained.filter(_.provider == k)
      val (checks, upstream) = k match
        case ProviderKind.ClaudeCode => (claudeCode(models, allowances), Nil)
        case ProviderKind.Codex      => (codex(models), Nil)
        case ProviderKind.Opencode   => (opencode(models), Nil)
        case _                       => http(k, models, online.value)
      val priced = models.map { m =>
        resolve(m) match
          case Right((_, b)) => ok(s"model ${m.wire}", s"${b.describe}; its client can be built")
          case Left(why)     => fail(s"model ${m.wire}", why.value)
      }
      val off = registry
        .filterNot(_.contains(AgentDoctor.RegistryKey(k.wire.value)))
        .map(_ => BuiltinAi.lookup(k, scala.collection.immutable.ListMap.empty).left.toOption.map(_.value).getOrElse("switched off"))
        .map(why => (if models.nonEmpty then fail else warn) ("plugin", why))
        .toList
      ProviderDoctor(
        ProviderDoctor.Provider(k.wire.value),
        ProviderDoctor.Used(models.nonEmpty),
        models.map(m => ProviderDoctor.Models(m.wire.value)),
        off ++ checks ++ priced,
        upstream
      )
    } ++ plugins(chained, online.value)
    val roleChecks = chains.flatMap {
      case (r, Left(m))                        => List(fail(s"role ${r.wire}", m.value))
      case (r, Right((Nil, _))) if neededBy(r) =>
        List(warn(s"role ${r.wire}", s"no model — ${r.wire} steps are refused"))
      case _ => Nil
    }
    val global =
      (if cfg.enabled.value then ok("agents", "enabled")
       else warn("agents", "off (agents.enabled = false): nothing runs, settings can still be made")) ::
        Agents.Prices.parse(Agents.Prices.Wire(cfg.prices.value)).fold(m => List(fail("prices", s"agents.prices: $m")), _ => Nil) ++
        allowanceProblems.map(fail("allowances", _)) ++
        allowances.collect {
          case e if e.allowance.isLeft => fail(s"allowance ${e.target}", e.allowance.left.toOption.map(_.value).getOrElse(""))
        } ++
        roleChecks
    DoctorReport(DoctorReport.Enabled(cfg.enabled.value), online, providers, global)

  /** One section per PLUGIN provider: what it is, its own checks, its free model listing (online only), and every
    * chained model priced and buildable. Nothing it does is allowed to throw the report away.
    */
  private def plugins(chained: List[ModelRef], online: Boolean): List[ProviderDoctor] =
    registry.toList.flatMap(_.toList).collect {
      case (name, p) if !BuiltinAi.reserved(BuiltinAi.Reserved(name.value)) =>
        val kind = ProviderKind.Plugin(ProviderKind.Plugin.Name(name.value))
        val models = chained.filter(_.provider == kind)
        def safe[A](what: String, a: => A, orElse: String => A): A =
          try a
          catch case scala.util.control.NonFatal(e) => orElse(s"$what threw ${e.getClass.getSimpleName}")
        val own = safe(
          "its doctor",
          p.doctor(com.histmersion.plugin.AiProvider.DoctorOnline(online), aiHttp),
          why => List(com.histmersion.plugin.AiCheck(com.histmersion.plugin.AiCheck.Name("doctor"), com.histmersion.plugin.AiCheck.Level("fail"), com.histmersion.plugin.AiCheck.Detail(why)))
        )
          .map(c => DoctorCheck(DoctorCheck.Name(c.name.value), CheckLevel.parse(CheckLevel.Wire(c.level.value)).getOrElse(CheckLevel.Warn), DoctorCheck.Detail(c.detail.value)))
        val (listing, upstream) =
          if !online then (List(ok("models listing", "offline: not asked")), Nil)
          else
            safe[Either[String, List[String]]](
              "its model listing",
              p.models(aiHttp).left.map(_.value).map(_.map(_.value)),
              why => Left(why)
            ) match
              case Left(why)  => (List((if models.nonEmpty then fail else warn) ("models listing", why)), Nil)
              case Right(Nil) => (List(ok("models listing", "the provider has none")), Nil)
              case Right(ids) =>
                (
                  ok("models listing", s"${ids.size} model(s), free call") :: models.map { m =>
                    if ids.contains(m.model.value) then ok(s"upstream ${m.wire}", "listed by the provider")
                    else
                      warn(
                        s"upstream ${m.wire}",
                        s"not in the provider's list of ${ids.size} model(s) — an alias, or a typo?"
                      )
                  },
                  ids.map(ProviderDoctor.UpstreamModels(_))
                )
        val priced = models.map { m =>
          resolve(m) match
            case Right((_, b)) => ok(s"model ${m.wire}", s"${b.describe}; its client can be built")
            case Left(why)     => fail(s"model ${m.wire}", why.value)
        }
        ProviderDoctor(
          ProviderDoctor.Provider(name.value),
          ProviderDoctor.Used(models.nonEmpty),
          models.map(m => ProviderDoctor.Models(m.wire.value)),
          ok("plugin", safe("its title", p.title.value, identity)) :: (own ++ listing ++ priced),
          upstream
        )
    }

  /** A role some pipeline uses (the probe is optional). */
  private def neededBy(r: Role): Boolean = Pipeline.values.exists(_.roles.contains(r))

  // ── the HTTP providers ─────────────────────────────────────────────────────────────────────────

  private def http(k: ProviderKind, models: List[ModelRef], online: Boolean): (List[DoctorCheck], List[ProviderDoctor.UpstreamModels]) =
    val p = com.histmersion.domain.automation.ProviderKind.values
      .find(_.wire.value == k.wire.value)
      .flatMap(cfg.provider)
      .getOrElse(throw IllegalStateException(s"${k.wire} has no HTTP config"))
    val used = models.nonEmpty
    val keyName = k match
      case ProviderKind.Anthropic => "ANTHROPIC_API_KEY"
      case ProviderKind.OpenAI    => "OPENAI_API_KEY"
      case _                      => "HISTMERSION_AGENTS_LOCAL_API_KEY"
    val needsKey = k != ProviderKind.Local
    val key =
      if p.apiKey.value.nonEmpty then ok("key", s"$keyName is set")
      else if !needsKey then ok("key", "none (a local server needs none)")
      else if used then fail("key", s"$keyName is not set in the server's environment")
      else ok("key", s"$keyName is not set (no role uses ${k.wire})")
    val url =
      scala.util.Try(URI.create(p.baseUrl.value)).toOption.filter(u => u.getScheme == "http" || u.getScheme == "https")
    val urlCheck = url.fold(fail("base URL", s"'${p.baseUrl}' is not an http(s) URL"))(u => ok("base URL", u.toString))
    val skip = Option
      .when(!online)("offline: not asked")
      .orElse(Option.when(needsKey && p.apiKey.value.isEmpty)("no key: not asked"))
      .orElse(Option.when(url.isEmpty)("no URL: not asked"))
      .orElse(Option.when(!used && k == ProviderKind.Local)("no role uses it: not asked"))
    skip match
      case Some(why) => (List(key, urlCheck, ok("models listing", why)), Nil)
      case None      =>
        val (endpoint, headers) = k match
          case ProviderKind.Anthropic =>
            (
              p.baseUrl.value.stripSuffix("/") + "/v1/models?limit=1000",
              List(
                AgentDoctor.HeaderName("x-api-key") -> AgentDoctor.HeaderText(p.apiKey.value),
                AgentDoctor.HeaderName("anthropic-version") -> AgentDoctor.HeaderText("2023-06-01")
              )
            )
          case _ =>
            (
              p.baseUrl.value.stripSuffix("/") + "/models",
              Option
                .when(p.apiKey.value.nonEmpty)(AgentDoctor.HeaderName("Authorization") -> AgentDoctor.HeaderText(s"Bearer ${p.apiKey.value}"))
                .toList
            )
        listModels(com.histmersion.server.HttpDownload.Url(endpoint), headers, cfg.doctorTimeoutSeconds) match
          case Left(why) =>
            (List(key, urlCheck, (if used then fail else warn) ("models listing", s"GET $endpoint: ${why.value}")), Nil)
          case Right(ids) =>
            val present = models.map { m =>
              if ids.contains(m.model) then ok(s"upstream ${m.wire}", "listed by the provider")
              else
                warn(
                  s"upstream ${m.wire}",
                  s"not in the provider's list of ${ids.size} model(s) — an alias, or a typo?"
                )
            }
            (List(key, urlCheck, ok("models listing", s"${ids.size} model(s), free call")) ++ present, ids.map(i => ProviderDoctor.UpstreamModels(i.value)))

  // ── claude-code ────────────────────────────────────────────────────────────────────────────────

  private def claudeCode(models: List[ModelRef], allowances: List[picker.Effective]): List[DoctorCheck] =
    val cc = app.agents.providers.claudeCode
    val loopback = ClaudeCode.Env.isLoopback(app.server.host)
    val used = models.nonEmpty
    val level = if used then fail else warn
    val bind =
      if loopback then ok("bind", s"the server listens on loopback (${app.server.host}); only you can start a run")
      else if cc.allowNonLoopback.value then
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
    val childEnv: Map[ProcessLaunch.EnvironmentKey, Option[ProcessLaunch.EnvironmentValue]] =
      (if cc.useApiKey.value then Map.empty
       else Map(envKey("ANTHROPIC_API_KEY") -> None, envKey("ANTHROPIC_AUTH_TOKEN") -> None)) ++
        Option(cc.configDir.value.trim)
          .filter(_.nonEmpty)
          .map(d => envKey("CLAUDE_CONFIG_DIR") -> Some(ProcessLaunch.EnvironmentValue(AgentTrees.expandIn(d, paths))))
          .toMap
    val version = runCommand(List(cmd(cc.command.value), cmd("--version")), childEnv, cfg.doctorTimeoutSeconds).flatMap {
      case (code, out) if code.value == 0 => Right(out.value)
      case (code, out)                    => Left(AgentDoctor.Problem(s"exit ${code.value}: ${out.value.take(300)}"))
    }
    val versionCheck = version.fold(
      why =>
        level(
          "executable",
          s"'${cc.command} --version' failed: ${why.value} — install Claude Code or set " +
            "agents.providers.claude-code.command (HISTMERSION_AGENTS_CLAUDE_COMMAND) to its absolute path"
        ),
      v => ok("executable", s"${cc.command}: ${v.trim.linesIterator.nextOption().getOrElse("")}")
    )
    val auth = version.toOption.map { _ =>
      runCommand(List(cmd(cc.command.value), cmd("auth"), cmd("status")), childEnv, cfg.doctorTimeoutSeconds) match
        case Left(why) => level("login", s"'claude auth status' failed: ${why.value} — log in with `claude` → /login")
        // exit 1 = not logged in; the JSON says so either way
        case Right((_, out)) =>
          AgentDoctor.login(out, cc.useApiKey) match
            case Right(d) => ok("login", d.value)
            case Left(d)  => level("login", d.value)
    }.toList
    val keyInEnv = Option
      .when(
        !cc.useApiKey.value && (paths.environment
          .get(Environment.VariablesKey("ANTHROPIC_API_KEY"))
          .isDefined || paths.environment.get(Environment.VariablesKey("ANTHROPIC_AUTH_TOKEN")).isDefined)
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
      ) || (e.allowance.isLeft && e.target.value.startsWith(ProviderKind.ClaudeCode.wire.value))
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
      s"steps reach ${app.server.publicUrl.value.stripSuffix("/")}/mcp/agent with a run token bound to their role's tools"
    )

  /** Whether the generated tree a CLI runtime launches exists. */
  private def generated(runtime: String): DoctorCheck =
    AgentTrees.root(app, paths).map(_.resolve(runtime)) match
      case Some(d) if java.nio.file.Files.isDirectory(d) => ok("generated", s"$d")
      case Some(d) => warn("generated", s"$d does not exist yet — run `histmersion agent generate`")
      case None    => warn("generated", "no runtimes directory — set agents.runtimes.dir")

  /** A CLI provider without a login check of its own: the executable answers `--version`. */
  private def version(
      command: ProcessLaunch.Command,
      env: Map[ProcessLaunch.EnvironmentKey, Option[ProcessLaunch.EnvironmentValue]],
      level: (String, String) => DoctorCheck,
      hint: String
  ): DoctorCheck =
    runCommand(List(command, cmd("--version")), env, cfg.doctorTimeoutSeconds) match
      case Right((c, out)) if c.value == 0 => ok("executable", s"${command.value}: ${out.value.trim.linesIterator.nextOption().getOrElse("")}")
      case Right((c, out))                 => level("executable", s"'${command.value} --version' exit ${c.value}: ${out.value.take(200)} — $hint")
      case Left(why)                       => level("executable", s"'${command.value} --version' failed: ${why.value} — $hint")

  // ── codex, opencode ────────────────────────────────────────────────────────────────────────────

  private def codex(models: List[ModelRef]): List[DoctorCheck] =
    val cc = cfg.providers.codex
    val level = if models.nonEmpty then fail else warn
    val home = AgentTrees.codexHome(app, paths)
    val login = home.map { h =>
      if java.nio.file.Files.isRegularFile(h.resolve("auth.json")) then ok("login", s"$h has a login (never read here)")
      else level("login", s"no login in $h — run `CODEX_HOME=\"$h\" ${cc.command} login` once")
    }.toList
    List(
      version(
        cmd(cc.command.value),
        Map(envKey("OPENAI_API_KEY") -> None, envKey("CODEX_API_KEY") -> None),
        level,
        "install the Codex CLI or set agents.providers.codex.command"
      )
    ) ++ login ++
      List(
        agentTools,
        generated("codex"),
        if cc.appServer.value then
          ok("plan pool", "read with codex app-server (account/rateLimits/read); see histmersion agent quota")
        else warn("plan pool", "app-server reads are off: the ChatGPT plan's windows are not known")
      )

  private def opencode(models: List[ModelRef]): List[DoctorCheck] =
    val oc = cfg.providers.opencode
    val level = if models.nonEmpty then fail else warn
    List(
      version(cmd(oc.command.value), Map.empty, level, "install opencode or set agents.providers.opencode.command"),
      agentTools,
      generated("opencode"),
      if oc.chatgptLogin.value then ok("plan pool", "its openai/… models draw on the ChatGPT plan pool")
      else ok("plan pool", "metered: allowances and prices apply (no plan login)")
    )

object AgentDoctor:
  type HeaderName = HeaderName.T
  object HeaderName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HeaderText = HeaderText.T
  object HeaderText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  /** What a local command printed. */
  type Output = Output.T
  object Output extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RegistryKey = RegistryKey.T
  object RegistryKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** `claude auth status` (JSON) → the login, without the account: logged in, how, which plan. Left = not usable as
    * configured.
    */
  def login(json: Output, useApiKey: ClaudeCodeConfig.UseApiKey): Either[Problem, DoctorCheck.Detail] =
    parser.parse(json.value.trim).toOption.map(_.hcursor) match
      case None    => Left(Problem(s"'claude auth status' did not answer JSON: ${json.value.take(200)}"))
      case Some(c) =>
        val in = c.downField("loggedIn").as[Boolean].getOrElse(false)
        val method = c.downField("authMethod").as[String].getOrElse("unknown")
        val plan = c.downField("subscriptionType").as[String].toOption.filter(_.nonEmpty)
        if !in then Left(Problem("not logged in — run `claude`, then /login (or `claude setup-token` for a long-lived token)"))
        else if !useApiKey.value && (method == "api_key" || method == "api_key_helper") then
          Left(
            Problem(s"logged in with an API key ($method), not a Claude plan — log in with your Claude account, or set use-api-key")
          )
        else Right(DoctorCheck.Detail(s"logged in ($method${plan.fold("")(p => s", plan $p")})"))

  private lazy val client: HttpClient =
    HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build()

  /** `GET <url>` with `headers`; `data[].id` of an OpenAI- or Anthropic-style model listing. */
  def listModels(url: com.histmersion.server.HttpDownload.Url, headers: List[(HeaderName, HeaderText)], timeoutSeconds: AgentsConfig.DoctorTimeoutSeconds): Either[Problem, List[ModelRef.Model]] =
    try
      val req = headers
        .foldLeft(
          HttpRequest.newBuilder(URI.create(url.value)).GET().timeout(Duration.ofSeconds(math.max(1, timeoutSeconds.value).toLong))
        ) { case (b, (k, v)) =>
          b.header(k.value, v.value)
        }
        .build()
      val res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
      if res.statusCode() / 100 != 2 then Left(Problem(s"HTTP ${res.statusCode()}: ${res.body().take(200)}"))
      else modelIds(Output(res.body()))
    catch
      case e: InterruptedException => throw e
      case e: Exception => Left(Problem(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}".take(300)))

  /** `{"data":[{"id":…},…]}` → the ids. */
  def modelIds(body: Output): Either[Problem, List[ModelRef.Model]] =
    parser.parse(body.value).left.map(e => Problem(s"not JSON: ${e.getMessage}")).flatMap { j =>
      j.hcursor
        .downField("data")
        .as[List[io.circe.Json]]
        .left
        .map(_ => Problem("no data[] in the answer"))
        .map(_.flatMap(_.hcursor.downField("id").as[String].toOption).map(ModelRef.Model(_)))
    }

  /** Run a local command (no network of ours) with `env` changes (None = removed): its exit code and output, or why it
    * could not be run.
    */
  def runCommand(
      argv: List[ProcessLaunch.Command],
      env: Map[ProcessLaunch.EnvironmentKey, Option[ProcessLaunch.EnvironmentValue]],
      timeoutSeconds: AgentsConfig.DoctorTimeoutSeconds
  ): Either[Problem, (ProcessResult.ExitCode, Output)] =
    try
      val pb = ProcessBuilder(argv.map(_.value).asJava).redirectErrorStream(true)
      env.foreach {
        case (k, Some(v)) => val _ = pb.environment().put(k.value, v.value)
        case (k, None)    => val _ = pb.environment().remove(k.value)
      }
      val p = pb.start()
      p.getOutputStream.close()
      val out = java.util.concurrent.CompletableFuture.supplyAsync(() =>
        String(p.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
      )
      if !p.waitFor(math.max(1, timeoutSeconds.value).toLong, TimeUnit.SECONDS) then
        p.destroyForcibly()
        Left(Problem(s"no answer within ${timeoutSeconds.value} s"))
      else Right((ProcessResult.ExitCode(p.exitValue()), Output(out.get(5, TimeUnit.SECONDS))))
    catch
      case e: InterruptedException => throw e
      case e: Exception => Left(Problem(s"${e.getClass.getSimpleName}: ${Option(e.getMessage).getOrElse("")}".take(300)))
