package com.histmersion.server.agents

import com.histmersion.server.ServerPlugins
import com.histmersion.util.plugins.Plugins

import com.histmersion.pluginhost.{FirstPartySettings, Generation, PluginHost}

import com.augustnagro.magnum.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.*
import com.histmersion.db.{AgentRepo, Migrate}
import com.histmersion.server.{Deps, Handlers}
import org.ekrich.config.ConfigFactory

import java.net.URLClassLoader
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path

/** A THIRD-PARTY AI provider end to end (docs/research-plugins.md §23): the hello-ai test plugin's own jar
  * (modules/plugin-fixtures/hello-ai — compiled against plugin-api alone, never on the server's class path) is
  * discovered from that jar, registered through the plugin host with a secret it is given, and then runs the
  * `enrich-source` pipeline through the SERVER'S OWN factory: the model of the role's chain is resolved through the
  * AI-provider registry of the generation, the host's tool loop calls the host's `facets` tool for it, the answers are
  * validated and the source is written. A plan limit moves the chain to its next model. The doctor, `agent models` and
  * `agent models set` see it — and forget it once the plugin is gone. No network: the provider is scripted. Skipped
  * without HISTMERSION_TEST_DB_URL.
  */
class PluginAiRunSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val Ck = "zzplugai2019_tin"

  private val Body =
    """# Introduction
      |
      |Tin was essential for bronze. The Uluburun ship carried ten tons of copper and one ton of tin.
      |""".stripMargin

  private val classifyJson =
    """{"summary":"Tin reached the Aegean by sea.","topics":["trade"],"regions":["Anatolia"],"tags":["Tin Trade"],
      |"period":null,"status":"current","statusEvidence":"widely cited"}""".stripMargin.replace("\n", "")
  private val finding =
    """{"claim":"The Uluburun ship carried copper and tin.","quote":"The Uluburun ship carried ten tons of copper and one ton of tin.",
      |"place":null,"start":null,"end":null,"periodName":null,"tags":[],"confidence":"high"}""".stripMargin.replace(
      "\n",
      ""
    )
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

  private def quote(s: String) = io.circe.Json.fromString(s).noSpaces

  private val Secret = "s3cr3t-value-zz"

  /** The hello-ai jar on a class loader of its own, as the plugin host would find a user's plugin. */
  private lazy val loader: URLClassLoader =
    val in = getClass.getClassLoader.getResourceAsStream("plugin-fixtures.txt")
    val jar =
      String(in.readAllBytes(), UTF_8).linesIterator.find(_.startsWith("hello-ai=")).get.stripPrefix("hello-ai=")
    URLClassLoader(Array(Path.of(jar).toUri.toURL), getClass.getClassLoader)

  private def generation(user: String = pluginConf): Generation =
    val (found, errors) = PluginHost.discover(loader)
    assert(errors.isEmpty, errors)
    val ours = found.filter(_.info.id == "hello-ai")
    assertEquals(ours.size, 1, "the plugin comes from its own jar")
    val env = ConfigFactory.parseString(s"ZZ_HELLO_AI_TOKEN = ${quote(Secret)}")
    // the six built-ins as the server has them: the HTTP APIs' first-party plugins, supplied the host's settings
    val builtin = PluginHost.discover(getClass.getClassLoader)._1.filter(p => BuiltinAi.pluginIds(p.info.id))
    val all = builtin ++ ServerPlugins.providers ++ ours
    val u = ConfigFactory.parseString(user)
    val host = FirstPartySettings.hostView(AppConfig.loadWithUserFile(env, None).fold(fail(_), _.merged), u, env, all)
    PluginHost.build(
      all,
      u,
      env,
      Plugins.services(
        () => new com.histmersion.plugin.PluginHttp { def get(u: String, h: Map[String, String]) = Left("offline") },
        env
      ),
      fallback = FirstPartySettings.fallback(_, host),
      firstParty = Plugins.firstParty(None)
    )

  private lazy val baseCfg =
    val b = AppConfig.load()
    b.copy(
      db = b.db.copy(url = testDb.get),
      server = b.server.copy(host = "127.0.0.1"),
      agents = b.agents.copy(
        enabled = true,
        prices = "echo=1/2; limited=1/2",
        enrich = b.agents.enrich.copy(upstream = false),
        roles = b.agents.roles.copy(extractor = "hello-ai:echo")
      )
    )

  private lazy val ds = testPool(baseCfg.db)

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

  /** The server's own wiring (`AgentsHandlers(deps, Handlers(deps))`: no scripted factory) over `g`. */
  private def handlers(g: Generation, cfg: AppConfig = baseCfg): (AgentsHandlers, Handlers) =
    val deps = Deps(cfg, ds, ds, plugins = g)
    val h = Handlers(deps)
    (h.agents, h)

  private def startAndRun(a: AgentsHandlers, h: Handlers): RunInfo =
    assert(h.putSource(Ck, SourcePut(title = "Bronze Age tin", year = Some(2019), body = Some(Body))).isRight)
    val run = a.startRun(RunRequest("enrich-source", Ck)).fold(e => fail(e.message), identity)
    a.engine.execute(run.id)
    AgentRepo.get(ds, run.id).get

  test("the plugin from its own jar is registered with its secret resolved, and reported by reference only"):
    val g = generation()
    val st = g.report.plugins.find(_.id == "hello-ai").get
    assertEquals((st.state, st.provides), ("registered", List("ai-provider:hello-ai")))
    assert(st.settings.exists(s => s.key == "token" && s.value == "env:ZZ_HELLO_AI_TOKEN"), st.settings)
    assert(!g.report.toString.contains(Secret), "the token's value is never in the report")
    assertEquals(BuiltinAi.known(g.registries.aiProviders), Set("hello-ai"))
    // switched off, it is gone and the six built-ins are untouched
    val off = generation(pluginConf.replace("enabled = true", "enabled = false"))
    assert(!off.registries.aiProviders.contains("hello-ai"))
    assertEquals(off.registries.aiProviders.keySet, ProviderKind.values.map(_.wire).toSet)

  test("enrich-source runs on the third-party provider through the server's own factory and the host's tool loop"):
    val (a, h) = handlers(generation())
    val run = startAndRun(a, h)
    assertEquals(run.status, "done", run.error.toString)
    val llm = AgentRepo.steps(ds, run.id, 200).filter(_.kind == "llm")
    assertEquals(llm.map(_.name), List("classify", "findings-1", "notes"))
    assert(llm.forall(s => s.status == "done" && s.model.contains("hello-ai:echo")), llm.toString)
    assertEquals(llm.flatMap(_.actualModel).distinct, List("echo-scripted"), "the model the provider said served")
    // classify: the facets call ran in the host, then the answer — two turns, each counted
    assertEquals(llm.head.usage, Usage(200, 40))
    assert(AgentRepo.messages(ds, run.id).exists(m => m.toolName.contains("facets") && m.role == "tool"))
    val got = h.getSource(Ck, body = false).toOption.get
    assertEquals(got.source.summary, Some("Tin reached the Aegean by sea."))
    assert(got.findings.exists(_.claim.contains("Uluburun")), got.findings.toString)
    assert(got.source.notes.exists(_.contains("tin by sea")), got.source.notes.toString)

  test("a chain passes over a plugin model whose plan reports its limit, to the chain's next model"):
    val cfg = baseCfg.copy(agents =
      baseCfg.agents.copy(roles = baseCfg.agents.roles.copy(extractor = "hello-ai:limited > hello-ai:echo"))
    )
    val (a, h) = handlers(generation(), cfg)
    val run = startAndRun(a, h)
    assertEquals(run.status, "done", run.error.toString)
    val done = AgentRepo.steps(ds, run.id, 200).filter(s => s.kind == "llm" && s.status == "done")
    assert(done.nonEmpty && done.forall(_.model.contains("hello-ai:echo")), done.toString)
    assert(a.picker.cooldowns().exists(_._1 == "hello-ai:limited"), a.picker.cooldowns().toString)

  test(
    "agent models, the doctor and models set see the provider while it is registered, and refuse it once it is gone"
  ):
    val (a, _) = handlers(generation())
    val chain = a.picker.roleChains(System.currentTimeMillis()).find(_.role == "extractor").get
    assertEquals(chain.active, Some("hello-ai:echo"))
    val d = a.doctor(offline = true)
    val sec = d.providers.find(_.provider == "hello-ai").get
    assert(sec.used, sec.toString)
    assert(sec.checks.exists(c => c.name == "plugin" && c.detail.contains("Hello AI")), sec.checks)
    assert(sec.checks.exists(c => c.name == "token" && c.detail.contains("never shown")), sec.checks)
    assert(sec.checks.exists(c => c.name == "model hello-ai:echo" && c.level == CheckLevel.Ok), sec.checks)
    assert(!AgentsMarkdown.doctor(d).contains(Secret))
    assert(a.setChain("extractor", ModelChains.ChainSet(List("hello-ai:echo", "local:x"))).isRight)
    // the same database, the plugin switched off: the saved chain no longer reads, a new one is refused
    val (gone, _) = handlers(generation(pluginConf.replace("enabled = true", "enabled = false")))
    assert(
      gone
        .setChain("extractor", ModelChains.ChainSet(List("hello-ai:echo")))
        .left
        .exists(_.message.contains("unknown provider 'hello-ai'"))
    )
    assert(gone.picker.chain(Role.Extractor).left.exists(_.contains("unknown provider 'hello-ai'")))
    assert(!gone.doctor(offline = true).providers.exists(_.provider == "hello-ai"))
