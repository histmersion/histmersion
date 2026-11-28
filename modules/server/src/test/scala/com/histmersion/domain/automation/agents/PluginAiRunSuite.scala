package com.histmersion
package domain.automation.agents
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.plugin.Header

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.server.agents.*

import com.histmersion.domain.automation.agents.*

import com.histmersion.server.ServerPlugins
import com.histmersion.util.plugins.Plugins

import com.histmersion.pluginhost.{FirstPartySettings, Generation, PluginHost}

import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.util.database.Migrate
import com.histmersion.server.{Deps, ServerWiring}
import org.ekrich.config.ConfigFactory

import java.net.URLClassLoader
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import com.histmersion.db.*
import com.histmersion.api.automation.{AgentsMarkdown, ModelChains}
import com.histmersion.api.library.Source
import com.histmersion.domain.automation.Run
import com.histmersion.domain.library.Citekey
import com.histmersion.util.jobs.Job
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.plugin.Plugin
import com.histmersion.config.DbConfig
import com.histmersion.config.ServerConfig
import com.histmersion.config.AgentsConfig
import com.histmersion.util.nonempty.NonEmptyVector
import com.histmersion.config.AgentRolesConfig
import com.histmersion.config.AgentEnrichConfig
import com.histmersion.api.catalogue.Year
import com.histmersion.api.automation.Agents
import com.histmersion.pluginhost.Registries
import com.histmersion.domain.library.sources.SourcesControllers
import com.histmersion.api.Api
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** A THIRD-PARTY AI provider end to end (docs/research-plugins.md §23): the hello-ai test plugin's own jar
  * (modules/plugin-fixtures/hello-ai — compiled against plugin-api alone, never on the server's class path) is
  * discovered from that jar, registered through the plugin host with a secret it is given, and then runs the
  * `enrich-source` pipeline through the SERVER'S OWN factory: the model of the role's chain is resolved through the
  * AI-provider registry of the generation, the host's tool loop calls the host's `facets` tool for it, the answers are
  * validated and the source is written. A plan limit moves the chain to its next model. The doctor, `agent models` and
  * `agent models set` see it — and forget it once the plugin is gone. No network: the provider is scripted. Skipped
  * without HISTMERSION_TEST_DB_URL.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.SilentCompare, "pending G53 (no comparison of unrelated types)")
class PluginAiRunSuite extends DbSuite with RunTranscripts:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val Ck = Citekey("zzplugai2019_tin")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val ApiTarget = RunRequest.Target(Ck.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def runId(id: RunInfo.Id): Run.Id = Run.Id.assume(id.value)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def jobId(id: RunInfo.Id): Job.Id = Job.Id.assume(id.value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val Body = Source.OcrMarkdown(
    """# Introduction
      |
      |Tin was essential for bronze. The Uluburun ship carried ten tons of copper and one ton of tin.
      |""".stripMargin
  )

  private val classifyJson =
    """{"summary":"Tin reached the Aegean by sea.","topics":["trade"],"regions":["Anatolia"],"tags":["Tin Trade"],
      |"period":null,"status":"current","statusEvidence":"widely cited"}""".stripMargin.replace("\n", "")
  private val finding =
    """{"claim":"The Uluburun ship carried copper and tin.","quote":"The Uluburun ship carried ten tons of copper and one ton of tin.",
      |"place":null,"start":null,"end":null,"periodName":null,"tags":[],"confidence":"high"}""".stripMargin.replace(
      "\n",
      ""
    )
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private val notesJson = s"""{"notes":"The source traces tin by sea [[$Ck]]."}"""

  /** The plugin's block: three scripted replies — classify (a `facets` call first), the findings, the notes. */
  private val pluginConf =
    s"""histmersion.plugins.hello-ai {
       |  enabled = true
       |  token = "env:ZZ_HELLO_AI_TOKEN"
       |  replies = [
       |    { when = "statusEvidence", text = ${quote(classifyJson)}, tool = facets, input = ${quote(
        """{"field":"tags"}"""
      )} }
       |    { when = "quote", text = ${quote(s"""{"findings":[$finding]}""")} }
       |    { when = "notes", text = ${quote(notesJson)} }
       |  ]
       |}""".stripMargin

  private def quote(s: String) = Json.fromString(s).noSpaces

  private val Secret = "s3cr3t-value-zz"

  /** The hello-ai jar on a class loader of its own, as the plugin host would find a user's plugin. */
  private lazy val loader: URLClassLoader =
    val in = getClass.getClassLoader.getResourceAsStream("plugin-fixtures.txt")
    val jar =
      String(in.readAllBytes(), UTF_8).linesIterator.find(_.startsWith("hello-ai=")).get.stripPrefix("hello-ai=")
    URLClassLoader(Array(Path.of(jar).toUri.toURL), getClass.getClassLoader)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def generation(user: String = pluginConf): Generation =
    val (found, errors) = PluginHost.discover(loader)
    assert(errors.isEmpty, errors)
    val ours = found.filter(_.info.id == Plugin.Info.Id("hello-ai"))
    assertEquals(ours.size, 1, "the plugin comes from its own jar")
    val env = ConfigFactory.parseString(s"ZZ_HELLO_AI_TOKEN = ${quote(Secret)}")
    // the six built-ins as the server has them: the HTTP APIs' first-party plugins, supplied the host's settings
    val builtin = PluginHost
      .discover(getClass.getClassLoader)
      ._1
      .filter(p => BuiltinAi.pluginIds(util.plugins.Plugin.Id.assume(p.info.id.value)))
    val all = builtin ++ ServerPlugins.providers ++ ours
    val u = ConfigFactory.parseString(user)
    val host =
      FirstPartySettings.hostView(
        AppConfig.loadWithUserFile(env, None).fold(p => fail(p.sentences.mkString("; ")), _.merged),
        u,
        env,
        all
      )
    PluginHost.build(
      all,
      u,
      env,
      Plugins.services(
        () =>
          new Plugin.Http {
            @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
            def get(u: Plugin.Http.Url, h: Map[Header.Name, Header.Value]) =
              Left(Problem.Unclassified(Legacy.Text("offline")))
          },
        env
      ),
      fallback = FirstPartySettings.fallback(_, host),
      firstParty = Plugins.firstParty(None)
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val baseCfg =
    val b = AppConfig.load()
    b.modify(_.db)
      .setTo(b.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.server)
      .setTo(b.server.modify(_.host).setTo(NonEmptyVector.of(ServerConfig.Host("127.0.0.1"))))
      .modify(_.agents)
      .setTo(
        b.agents
          .modify(_.enabled)
          .setTo(AgentsConfig.Enabled(true))
          .modify(_.prices)
          .setTo(AgentsConfig.Prices("echo=1/2; limited=1/2"))
          .modify(_.roles)
          .setTo(b.agents.roles.modify(_.extractor).setTo(AgentRolesConfig.Extractor("hello-ai:echo")))
      )
      .modify(_.libraryAgents)
      .setTo(
        b.libraryAgents
          .modify(_.enrich)
          .setTo(b.libraryAgents.enrich.modify(_.upstream).setTo(AgentEnrichConfig.Upstream(false)))
      )

  private lazy val ds = testPool(baseCfg.db)
  protected def transcriptDs: javax.sql.DataSource = ds

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzplugai%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzplugai%'".update.run()
      sql"DELETE FROM queue_items WHERE text LIKE '%zzplugai%'".update.run()
      sql"DELETE FROM agent_role_chains".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit = clean()
  override def afterSuite(): Unit = loader.close()

  /** The server's own wiring (`AgentsControllers(ServerEngineSite(deps, ServerWiring(deps)))`: no scripted factory)
    * over `g`.
    */
  private def handlers(g: Generation, cfg: AppConfig = baseCfg): (AgentsControllers, ServerWiring) =
    val deps = Deps(cfg, ds, ds, plugins = g)
    val h = ServerWiring(deps)
    (h.agents, h)

  private def startAndRun(a: AgentsControllers, h: ServerWiring): RunInfo =
    assert(
      h.library.sources
        .put(
          Ck,
          Source.Put(
            title = api.library.Title("Bronze Age tin"),
            year = (Some(2019)).map(Year.apply),
            body = Some(Body)
          )
        )
        .isRight
    )
    val run = a
      .startRun(RunRequest(Agents.RunRequest.Pipeline("enrich-source"), ApiTarget))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    a.engine.execute(runId(run.id))
    connect(ds)(AgentRepo.get(run.id)).get

  test("the plugin from its own jar is registered with its secret resolved, and reported by reference only"):
    val g = generation()
    val st = g.report.plugins.find(_.id.value == "hello-ai").get
    assertEquals((st.state, st.provides), ("registered", List("ai-provider:hello-ai")))
    assert(st.settings.exists(s => s.key.value == "token" && s.value.value == "env:ZZ_HELLO_AI_TOKEN"), st.settings)
    assert(!g.report.toString.contains(Secret), "the token's value is never in the report")
    assertEquals(
      BuiltinAi.known(
        g.registries.aiProviders.map((k, p) => util.plugins.Plugin.Id.assume(k.value) -> p)
      ),
      Set("hello-ai")
    )
    // switched off, it is gone and the six built-ins are untouched
    val off = generation(pluginConf.replace("enabled = true", "enabled = false"))
    assert(!off.registries.aiProviders.contains(Registries.AiProvidersKey("hello-ai")))
    assertEquals(off.registries.aiProviders.keySet, ProviderKind.values.map(_.wire).toSet)

  test("enrich-source runs on the third-party provider through the server's own factory and the host's tool loop"):
    val (a, h) = handlers(generation())
    val run = startAndRun(a, h)
    assertEquals(run.status, "done", run.error.toString)
    val llm = connect(ds)(AgentRepo.steps(jobId(run.id), AgentRepo.OutputChars(200))).filter(_.kind.value == "llm")
    assertEquals(llm.map(_.name), List("classify", "findings-1", "notes"))
    assert(llm.forall(s => s.status.value == "done" && s.model.contains("hello-ai:echo")), llm.toString)
    assertEquals(llm.flatMap(_.actualModel).distinct, List("echo-scripted"), "the model the provider said served")
    // classify: the facets call ran in the host, then the answer — two turns, each counted
    assertEquals(llm.head.usage, Usage(Agents.Usage.Input(200), Agents.Usage.Output(40)))
    assert(
      connect(ds)(AgentRepo.messages(jobId(run.id))).exists(m =>
        m.toolName.contains("facets") && m.role.value == "tool"
      )
    )
    val got = h.library.sources.get(Ck, body = SourcesControllers.GetBody(false)).left.map(Api.Error(_)).toOption.get
    assertEquals(got.source.summary, Some("Tin reached the Aegean by sea."))
    assert(got.findings.exists(_.claim.value.contains("Uluburun")), got.findings.toString)
    assert(got.source.notes.exists(_.value.contains("tin by sea")), got.source.notes.toString)

  test("a chain passes over a plugin model whose plan reports its limit, to the chain's next model"):
    val cfg = baseCfg
      .modify(_.agents)
      .setTo(
        baseCfg.agents
          .modify(_.roles)
          .setTo(
            baseCfg.agents.roles
              .modify(_.extractor)
              .setTo(AgentRolesConfig.Extractor("hello-ai:limited > hello-ai:echo"))
          )
      )
    val (a, h) = handlers(generation(), cfg)
    val run = startAndRun(a, h)
    assertEquals(run.status, "done", run.error.toString)
    val done = connect(ds)(AgentRepo.steps(jobId(run.id), AgentRepo.OutputChars(200))).filter(s =>
      s.kind.value == "llm" && s.status.value == "done"
    )
    assert(done.nonEmpty && done.forall(_.model.contains("hello-ai:echo")), done.toString)
    assert(a.picker.cooldowns().exists(_._1.value == "hello-ai:limited"), a.picker.cooldowns().toString)

  test(
    "agent models, the doctor and models set see the provider while it is registered, and refuse it once it is gone"
  ):
    val (a, _) = handlers(generation())
    val chain =
      a.picker.roleChains(PlanQuotaRepo.EpochMillis(System.currentTimeMillis())).find(_.role.value == "extractor").get
    assertEquals(chain.active, Some("hello-ai:echo"))
    val d = a.doctor(offline = AgentsControllers.Offline(true))
    val sec = d.providers.find(_.provider.value == "hello-ai").get
    assert(sec.used.used, sec.toString)
    assert(sec.checks.exists(c => c.name.value == "plugin" && c.detail.value.contains("Hello AI")), sec.checks)
    assert(sec.checks.exists(c => c.name.value == "token" && c.detail.value.contains("never shown")), sec.checks)
    assert(sec.checks.exists(c => c.name.value == "model hello-ai:echo" && c.level == CheckLevel.Ok), sec.checks)
    assert(!AgentsMarkdown.doctor(d).contains(Secret))
    assert(
      a.setChain(
        Agents.Role.Wire("extractor"),
        ModelChains.ChainSet(
          List(ModelChains.ChainEntry.Model("hello-ai:echo"), ModelChains.ChainEntry.Model("local:x"))
        )
      ).left
        .map(Api.Error(_))
        .isRight
    )
    // the same database, the plugin switched off: the saved chain no longer reads, a new one is refused
    val (gone, _) = handlers(generation(pluginConf.replace("enabled = true", "enabled = false")))
    assert(
      gone
        .setChain(
          Agents.Role.Wire("extractor"),
          ModelChains.ChainSet(List(ModelChains.ChainEntry.Model("hello-ai:echo")))
        )
        .left
        .map(Api.Error(_))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("unknown provider kind 'hello-ai'"))
    )
    assert(
      gone.picker
        .chain(Role.Extractor)
        .left
        .exists(_.sentences.mkString("; ").contains("unknown provider kind 'hello-ai'"))
    )
    assert(!gone.doctor(offline = AgentsControllers.Offline(true)).providers.exists(_.provider.value == "hello-ai"))
