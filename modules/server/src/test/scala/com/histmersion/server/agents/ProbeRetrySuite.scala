package com.histmersion.server.agents

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.ScriptedClient

import com.histmersion.domain.automation.agents.{AgentRuntime, AgentsControllers, PickFailure}
import com.histmersion.domain.automation.managers.Zero

import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.server.{Deps, ServerWiring}
import sttp.ai.core.agent.*

import java.nio.file.Files
import com.histmersion.db.*
import com.histmersion.api.automation.AgentsMarkdown
import com.histmersion.api.library.Source

/** `agent probe` against a `*_test` database with a scripted model: an answer that leaves the title empty and the year
  * null (2026-10-08, codex:gpt-6-luna: `{"citekey":"huxley1956_catalogue", "title":"","year":null}`) is not an answer —
  * the probe asks again with the problems stated, and says so if the model never fills them. An answer in prose (#915,
  * claude-code:haiku) is asked again the same way — through the real `claude-code` client on a fake `claude`. Skipped
  * without HISTMERSION_TEST_DB_URL.
  */
class ProbeRetrySuite extends com.histmersion.db.DbSuite with RunTranscripts:
  private given com.histmersion.util.storage.Stores = com.histmersion.util.storage.Stores.disk
  private given com.histmersion.util.storage.Resources = com.histmersion.util.storage.Resources.classpath(getClass.getClassLoader)
  private given java.time.Clock = java.time.Clock.systemUTC()
  private given com.histmersion.util.system.JvmHome = com.histmersion.util.system.JvmHome(java.nio.file.Path.of("target/test-home").toAbsolutePath)
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(60, "s")

  private val Ck = com.histmersion.domain.library.Citekey("zzprobe1956_catalogue")
  private val ApiCk = com.histmersion.api.library.Citekey("zzprobe1956_catalogue")
  private val Model = ModelRef(ProviderKind.Local, com.histmersion.api.automation.Agents.ModelRef.Model("zzProbe"))
  private val ProbeModel = ProbeRequest.Model(Model.wire.value)
  private val Usage1 = Some(TokenUsage(Tokens(100), Tokens(10), Tokens.Zero, Tokens.Zero))

  private lazy val cfg =
    val b = AppConfig.load()
    b.modify(
      _.db).setTo(b.db.modify(_.url).setTo(com.histmersion.config.DbConfig.Url(testDb.get))).modify(_.server).setTo(b.server.modify(_.host).setTo(com.histmersion.config.ServerConfig.Host("127.0.0.1"))).modify(_.agents).setTo(b.agents.modify(_.enabled).setTo(com.histmersion.config.AgentsConfig.Enabled(true)).modify(_.prices).setTo(com.histmersion.config.AgentsConfig.Prices("zzProbe=0/0"))).modify(_.libraryAgents).setTo(b.libraryAgents.modify(_.enrich).setTo(b.libraryAgents.enrich.modify(_.upstream).setTo(com.histmersion.config.AgentEnrichConfig.Upstream(false)))
    )

  private lazy val ds = testPool(cfg.db)
  protected def transcriptDs: javax.sql.DataSource = ds

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds)(sql"DELETE FROM sources WHERE citekey LIKE 'zzprobe%'".update.run())

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit = clean()

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))
  private val Empty = s"""{"citekey":"$Ck","title":"","year":null}"""
  private val Right_ = s"""{"citekey":"$Ck","title":"A Catalogue of Fishes","year":1956}"""

  private def handlers(c: ScriptedClient): AgentsControllers =
    val deps = Deps(cfg, ds)
    val h = ServerWiring(deps)
    assert(h.library.sources.put(Ck, Source.Put(title = com.histmersion.api.library.Title("A Catalogue of Fishes"), year = (Some(1956)).map(com.histmersion.api.catalogue.Year.apply))).isRight)
    AgentsControllers(
      ServerEngineSite(deps, h),
      (_, ref) => Option.when(ref == Model)(c).toRight(PickFailure.Problem(s"no scripted client for ${ref.wire}"))
    )

  test("an empty title and a null year are sent back with the problems; the second answer matches"):
    val c = ScriptedClient(Model)
    c.scripts ++= List(Seq(text(Empty)), Seq(text(Right_)))
    val r = handlers(c).probe(ProbeRequest(ApiCk, Some(ProbeModel))).fold(e => fail(e.message.value), identity)
    assertEquals((r.attempts, r.problems, r.matchesRecord), (2, Nil, true), r)
    assertEquals(r.usage.input, 200L, "both attempts are counted")
    val retry = c.steps(1).head.history.entries.collectFirst { case ConversationEntry.UserPrompt(p) => p }.get
    assert(retry.contains("the title is empty") && retry.contains("the year is missing"), retry.take(2000))

  test("a model that never fills them: the probe reports the answer incomplete, never as matching"):
    val c = ScriptedClient(Model)
    c.scripts ++= List.fill(1 + math.max(0, cfg.agents.step.maxRetries.value))(Seq(text(Empty)))
    val r = handlers(c).probe(ProbeRequest(ApiCk, Some(ProbeModel))).fold(e => fail(e.message.value), identity)
    assertEquals(r.attempts, 1 + math.max(0, cfg.agents.step.maxRetries.value))
    assert(!r.matchesRecord.value)
    assertEquals(r.problems.size, 2, r.problems)
    assert(AgentsMarkdown.probe(r).contains("answer is **incomplete**: the title is empty"), AgentsMarkdown.probe(r))

  // ── #915: claude-code answering in prose ─────────────────────────────────────────────────────────

  private val Haiku = ModelRef(ProviderKind.ClaudeCode, com.histmersion.api.automation.Agents.ModelRef.Model("haiku"))
  private def result(text: String, structured: String) =
    s"""{"type":"result","subtype":"success","is_error":false,"num_turns":2,"result":${io.circe.Json
        .fromString(text)
        .noSpaces},""" +
      s""""structured_output":$structured,"usage":{"input_tokens":100,"output_tokens":10},"permission_denials":[]}"""
  private val Prose = result("The source is A Catalogue of Fishes, published in 1956.", "null")
  private val Typed = result("Done.", Right_)

  /** The real `claude-code` client on the generated tree, running `fake` (the picker sees the scripted local model, so
    * no plan pool is read).
    */
  private def claudeHandlers(fake: FakeCli.Fake): AgentsControllers =
    val rt = CliRuntime(FakeCli.app(Files.createTempDirectory("hm-agents-")), EngineContributions.roster)
    assert(rt.trees.generate(Generated.known(rt.definitions), List(AgentRuntime.ClaudeCode)).isRight)
    val client = ClaudeCodeClient(
      Haiku,
      cfg.agents.providers.claudeCode.modify(_.command).setTo(com.histmersion.config.ClaudeCodeConfig.Command(fake.script.toString)).modify(_.timeoutSeconds).setTo(com.histmersion.config.ClaudeCodeConfig.TimeoutSeconds(30)),
      rt
    )
    val deps = Deps(cfg, ds)
    val h = ServerWiring(deps)
    assert(h.library.sources.put(Ck, Source.Put(title = com.histmersion.api.library.Title("A Catalogue of Fishes"), year = (Some(1956)).map(com.histmersion.api.catalogue.Year.apply))).isRight)
    AgentsControllers(
      ServerEngineSite(deps, h),
      (_, ref) => Option.when(ref == Model)(client).toRight(PickFailure.Problem(s"no client for ${ref.wire}"))
    )

  test("claude-code: a prose answer is asked again with why; the typed second answer matches (#915)"):
    val fake = FakeCli.sequence("claude", List(List(ClaudeStreams.Init, Prose), List(ClaudeStreams.Init, Typed)))
    val r = claudeHandlers(fake).probe(ProbeRequest(ApiCk, Some(ProbeModel))).fold(e => fail(e.message.value), identity)
    assertEquals((r.model, r.attempts, r.problems, r.matchesRecord), ("claude-code:haiku", 2, Nil, true), r)
    assertEquals(FakeCli.invocations(fake), 2)
    val retry = FakeCli.stdinOf(fake, 2)
    assert(
      retry.contains("rejected by validation") && retry.contains(
        "there is no JSON object in the answer (it is prose)"
      ) &&
        retry.contains("A Catalogue of Fishes, published in 1956"),
      retry
    )
    assert(!FakeCli.stdinOf(fake, 1).contains("rejected"), "the first ask is the plain one")
    // the probe goes through the same typed-answer path as a panel step (0aaea92): the schema and the answer tool
    assert(fake.args.contains("--json-schema"), fake.args)
    assert(fake.after("--allowedTools").split(',').toList.contains("StructuredOutput"), fake.after("--allowedTools"))
    assertEquals(fake.after("--agent"), "histmersion-agents:probe")

  test("claude-code: prose every time — no typed answer, and the probe says why"):
    val fake = FakeCli.sequence("claude", List(List(ClaudeStreams.Init, Prose)))
    val r = claudeHandlers(fake).probe(ProbeRequest(ApiCk, Some(ProbeModel))).fold(e => fail(e.message.value), identity)
    assertEquals(r.attempts, 1 + math.max(0, cfg.agents.step.maxRetries.value))
    assertEquals(FakeCli.invocations(fake), r.attempts)
    assert(r.answer.isEmpty && !r.matchesRecord.value)
    val md = AgentsMarkdown.probe(r)
    assert(
      md.contains(
        "**no typed answer**: the answer is not valid JSON: there is no JSON object in the answer (it is prose)"
      ),
      md
    )
