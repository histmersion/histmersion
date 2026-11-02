package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.{AgentRuntime, AgentsHandlers}
import com.histmersion.domain.automation.managers.Zero

import com.augustnagro.magnum.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.*
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.server.{Deps, ServerWiring}
import sttp.ai.core.agent.*

import java.nio.file.Files
import com.histmersion.db.*

/** `agent probe` against a `*_test` database with a scripted model: an answer that leaves the title empty and the year
  * null (2026-10-08, codex:gpt-6-luna: `{"citekey":"huxley1956_catalogue", "title":"","year":null}`) is not an answer —
  * the probe asks again with the problems stated, and says so if the model never fills them. An answer in prose (#915,
  * claude-code:haiku) is asked again the same way — through the real `claude-code` client on a fake `claude`. Skipped
  * without HISTMERSION_TEST_DB_URL.
  */
class ProbeRetrySuite extends com.histmersion.db.DbSuite with RunTranscripts:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(60, "s")

  private val Ck = "zzprobe1956_catalogue"
  private val Model = ModelRef(ProviderKind.Local, "zzProbe")
  private val Usage1 = Some(TokenUsage(Tokens(100), Tokens(10), Tokens.Zero, Tokens.Zero))

  private lazy val cfg =
    val b = AppConfig.load()
    b.copy(
      db = b.db.copy(url = testDb.get),
      server = b.server.copy(host = "127.0.0.1"),
      agents = b.agents.copy(enabled = true, prices = "zzProbe=0/0"),
      libraryAgents = b.libraryAgents.copy(enrich = b.libraryAgents.enrich.copy(upstream = false))
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

  private def handlers(c: ScriptedClient): AgentsHandlers =
    val deps = Deps(cfg, ds)
    val h = ServerWiring(deps)
    assert(h.library.sources.put(Ck, SourcePut(title = "A Catalogue of Fishes", year = Some(1956))).isRight)
    AgentsHandlers(
      ServerEngineSite(deps, h),
      (_, ref) => Option.when(ref == Model)(c).toRight(s"no scripted client for ${ref.wire}")
    )

  test("an empty title and a null year are sent back with the problems; the second answer matches"):
    val c = ScriptedClient(Model)
    c.scripts ++= List(Seq(text(Empty)), Seq(text(Right_)))
    val r = handlers(c).probe(ProbeRequest(Ck, Some(Model.wire))).fold(e => fail(e.message), identity)
    assertEquals((r.attempts, r.problems, r.matchesRecord), (2, Nil, true), r)
    assertEquals(r.usage.input, 200L, "both attempts are counted")
    val retry = c.steps(1).head.history.entries.collectFirst { case ConversationEntry.UserPrompt(p) => p }.get
    assert(retry.contains("the title is empty") && retry.contains("the year is missing"), retry.take(2000))

  test("a model that never fills them: the probe reports the answer incomplete, never as matching"):
    val c = ScriptedClient(Model)
    c.scripts ++= List.fill(1 + math.max(0, cfg.agents.step.maxRetries))(Seq(text(Empty)))
    val r = handlers(c).probe(ProbeRequest(Ck, Some(Model.wire))).fold(e => fail(e.message), identity)
    assertEquals(r.attempts, 1 + math.max(0, cfg.agents.step.maxRetries))
    assert(!r.matchesRecord)
    assertEquals(r.problems.size, 2, r.problems)
    assert(AgentsMarkdown.probe(r).contains("answer is **incomplete**: the title is empty"), AgentsMarkdown.probe(r))

  // ── #915: claude-code answering in prose ─────────────────────────────────────────────────────────

  private val Haiku = ModelRef(ProviderKind.ClaudeCode, "haiku")
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
  private def claudeHandlers(fake: FakeCli.Fake): AgentsHandlers =
    val rt = CliRuntime(FakeCli.app(Files.createTempDirectory("hm-agents-")), EngineContributions.roster)
    assert(rt.trees.generate(Generated.known(rt.definitions), List(AgentRuntime.ClaudeCode)).isRight)
    val client = ClaudeCodeClient(
      Haiku,
      cfg.agents.providers.claudeCode.copy(command = fake.script.toString, timeoutSeconds = 30),
      rt
    )
    val deps = Deps(cfg, ds)
    val h = ServerWiring(deps)
    assert(h.library.sources.put(Ck, SourcePut(title = "A Catalogue of Fishes", year = Some(1956))).isRight)
    AgentsHandlers(
      ServerEngineSite(deps, h),
      (_, ref) => Option.when(ref == Model)(client).toRight(s"no client for ${ref.wire}")
    )

  test("claude-code: a prose answer is asked again with why; the typed second answer matches (#915)"):
    val fake = FakeCli.sequence("claude", List(List(ClaudeStreams.Init, Prose), List(ClaudeStreams.Init, Typed)))
    val r = claudeHandlers(fake).probe(ProbeRequest(Ck, Some(Model.wire))).fold(e => fail(e.message), identity)
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
    val r = claudeHandlers(fake).probe(ProbeRequest(Ck, Some(Model.wire))).fold(e => fail(e.message), identity)
    assertEquals(r.attempts, 1 + math.max(0, cfg.agents.step.maxRetries))
    assertEquals(FakeCli.invocations(fake), r.attempts)
    assert(r.answer.isEmpty && !r.matchesRecord)
    val md = AgentsMarkdown.probe(r)
    assert(
      md.contains(
        "**no typed answer**: the answer is not valid JSON: there is no JSON object in the answer (it is prose)"
      ),
      md
    )
