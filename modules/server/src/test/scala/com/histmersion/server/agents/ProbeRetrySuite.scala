package com.histmersion.server.agents

import com.augustnagro.magnum.*
import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.db.{Db, Migrate}
import com.histmersion.server.{Deps, Handlers}
import sttp.ai.core.agent.*

/** `agent probe` against a `*_test` database with a scripted model: an answer that leaves the title
  * empty and the year null (2026-10-08, codex:gpt-6-luna: `{"citekey":"huxley1956_catalogue",
  * "title":"","year":null}`) is not an answer — the probe asks again with the problems stated, and
  * says so if the model never fills them. Skipped without HISTMERSION_TEST_DB_URL. */
class ProbeRetrySuite extends munit.FunSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(60, "s")

  private val Ck    = "zzprobe1956_catalogue"
  private val Model = ModelRef(ProviderKind.Local, "zzProbe")
  private val Usage1 = Some(TokenUsage(Tokens(100), Tokens(10), Tokens.Zero, Tokens.Zero))

  private lazy val cfg =
    val b = AppConfig.load()
    b.copy(db = b.db.copy(url = testDb.get), server = b.server.copy(host = "127.0.0.1"),
      agents = b.agents.copy(enabled = true, prices = "zzProbe=0/0", enrich = b.agents.enrich.copy(upstream = false)))

  private lazy val ds = Db.dataSource(cfg.db)

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds)(sql"DELETE FROM sources WHERE citekey LIKE 'zzprobe%'".update.run())

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit   = clean()

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))
  private val Empty = s"""{"citekey":"$Ck","title":"","year":null}"""
  private val Right_ = s"""{"citekey":"$Ck","title":"A Catalogue of Fishes","year":1956}"""

  private def handlers(c: ScriptedClient): AgentsHandlers =
    val deps = Deps(cfg, ds)
    val h = Handlers(deps)
    assert(h.putSource(Ck, SourcePut(title = "A Catalogue of Fishes", year = Some(1956))).isRight)
    AgentsHandlers(deps, h, (_, ref) => Option.when(ref == Model)(c).toRight(s"no scripted client for ${ref.wire}"))

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
