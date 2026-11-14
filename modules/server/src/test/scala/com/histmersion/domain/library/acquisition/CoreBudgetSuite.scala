package com.histmersion
package domain.library.acquisition
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.server.ServerPlugins

import com.histmersion.config.{AppConfig, LoadedConfig}
import com.histmersion.domain.CoreQuota
import com.histmersion.plugin.{Doi, Fetch, FetchedFile, SourceFetcher, WorkKey}
import com.histmersion.pluginhost.{FetchChain, Generation, PluginHost}
import com.histmersion.server.plugins.MakerPlugin
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import org.ekrich.config.ConfigFactory

import java.net.InetSocketAddress
import java.time.{Instant, LocalDate}
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters.*
import com.histmersion.plugin.Plugin
import com.histmersion.pluginhost.PolicyHttp
import com.histmersion.pluginhost.Registries
import com.histmersion.util.plugins.Plugins
import com.histmersion.plugin.Access
import com.histmersion.domain.library.search.Search

/** CORE's two uses — the acquisition chain's `core` step and the new-source search's `core` index — over ONE key and
  * ONE budget, against a stub CORE on a loopback port (never the real one): a 429 with its retry time,
  * `X-RateLimit-Remaining: 0`, the per-minute pace, the daily budget and the searches' share, the budget kept across a
  * restart, and the key resolved from a (fake) vault reference.
  */
class CoreBudgetSuite extends munit.FunSuite:

  private val t0 = Instant.parse("2026-10-26T18:50:00Z")

  /** What the stub CORE answers to the next `search/works` request; an empty script answers 200 with no results.
    */
  final private case class Reply(
      code: Int,
      headers: Map[String, String] = Map.empty,
      body: String = """{"results":[]}"""
  )

  final private class StubCore:
    val script = ConcurrentLinkedQueue[Reply]()
    val asked = ConcurrentLinkedQueue[(String, String)]() // (q, Authorization) of each search/works request
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private def reply(ex: HttpExchange, r: Reply): Unit =
      r.headers.foreach((k, v) => ex.getResponseHeaders.add(k, v))
      val b = r.body.getBytes("UTF-8")
      ex.sendResponseHeaders(r.code, b.length.toLong)
      ex.getResponseBody.write(b)
      ex.close()
    server.createContext(
      "/v3/search/works/",
      (ex: HttpExchange) =>
        val q = ex.getRequestURI.getRawQuery
          .split("&")
          .collectFirst {
            case p if p.startsWith("q=") =>
              java.net.URLDecoder.decode(p.drop(2), "UTF-8")
          }
          .getOrElse("")
        asked.add(q -> Option(ex.getRequestHeaders.getFirst("Authorization")).getOrElse(""))
        reply(ex, Option(script.poll()).getOrElse(Reply(200)))
    )
    server.createContext(
      "/files/paper.pdf",
      (ex: HttpExchange) => reply(ex, Reply(200, body = "%PDF-1.4 a stub paper " + "x" * 2048))
    )
    server.start()
    val base: String = s"http://127.0.0.1:${server.getAddress.getPort}"
    def stop(): Unit = server.stop(0)
    def found: Reply = Reply(
      200,
      Map("X-RateLimit-Limit" -> "25", "X-RateLimit-Remaining" -> "20"),
      s"""{"results":[{"doi":"10.1/x","title":"A paper","yearPublished":2026,"downloadUrl":"$base/files/paper.pdf"}]}"""
    )

  private def withCore(body: StubCore => Unit): Unit =
    val core = StubCore()
    try body(core)
    finally core.stop()

  /** The settings with CORE at the stub, as `histmersion.conf` writes them (the plugins read the host's keys). */
  private def acquire(
      core: StubCore,
      key: String = "",
      perMinute: Int = 20,
      daily: Int = 900,
      searches: Int = 600,
      maxWait: Int = 30,
      user: String = ""
  ): LoadedConfig =
    AppConfig
      .loadWithUserText(
        ConfigFactory.empty,
        s"""histmersion.acquire.core { url = "${core.base}/v3", api-key = "$key", per-minute = $perMinute,
           |  daily-tokens = $daily, search-daily-tokens = $searches, max-wait-seconds = $maxWait,
           |  exhausted-pause-seconds = 60 }
           |$user""".stripMargin
      )
      .fold(e => fail(e.sentences.mkString("; ")), identity)

  /** A clock the test moves; `sleep` moves it too (and is recorded), so nothing waits. */
  final private class Clock:
    val now = AtomicReference(t0)
    val slept = ConcurrentLinkedQueue[CoreQuota.Grant.WaitMs]()
    def advance(seconds: Long): Unit = now.updateAndGet(_.plusSeconds(seconds))
    def budget(): CoreBudget =
      CoreBudget(() => now.get, ms => { slept.add(ms); now.updateAndGet(_.plusMillis(ms.value)) })

  private val Ctx: Fetch.Context = new Fetch.Context:
    private val m = scala.collection.mutable.Map.empty[Fetch.Context.MemoKey, Any] // one fetch's memo
    def memo[A](key: Fetch.Context.MemoKey)(compute: => A): A = m.getOrElseUpdate(key, compute).asInstanceOf[A]
    val log: Plugin.Log = new Plugin.Log {
      def info(message: Plugin.Log.Line) = (); def warn(message: Plugin.Log.Line) = ()
    }

  private def generation(
      cfg: LoadedConfig,
      budget: CoreBudget,
      extra: List[Plugin] = Nil
  ): Generation =
    val http = SttpHttp(
      cfg.config.acquire,
      tries = PolicyHttp.Tries(1),
      clock = () => java.time.Instant.now(java.time.Clock.systemUTC())
    )
    ServerPlugins.fetchers(cfg, () => HttpPlugin(http), budget, extra)

  private val work = WorkKey(doi = Some(Doi("10.1/x")))

  private def lookup(g: Generation): Fetch =
    g.registries.sourceFetchers(Registries.SourceFetchersKey("core")).fetch(work, Ctx)

  private def coreIndex(loaded: LoadedConfig, g: Generation, budget: CoreBudget): Option[ScholarlyIndex] =
    val cfg = loaded.config.acquire
    Indexes
      .of(
        cfg,
        SttpHttp(cfg, tries = PolicyHttp.Tries(1), clock = () => java.time.Instant.now(java.time.Clock.systemUTC())),
        Plugins.coreKey(g, cfg.core.key.map(k => Plugins.CoreKey(k.value))),
        budget
      )
      ._1
      .find(_.name.value == "core")

  private def missWhy(f: Fetch): String = f match
    case Fetch.Miss(why) => why.sentence
    case other           => fail(s"not a miss: $other")

  test("a 429 with X-RateLimit-Retry-After: the chain goes on without core, which is not asked again until that time"):
    withCore { core =>
      val clock = Clock()
      val budget = clock.budget()
      val cfg = acquire(core, key = "k-host")
      val g = generation(cfg, budget)
      core.script.add(
        Reply(
          429,
          Map(
            "X-RateLimit-Limit" -> "25",
            "X-RateLimit-Remaining" -> "0",
            "X-RateLimit-Retry-After" -> "2026-10-26T18:52:11+0000"
          ),
          """{"message":"Too many requests"}"""
        )
      )
      val next = new SourceFetcher:
        val access = Access.Open
        def fetch(w: WorkKey, ctx: Fetch.Context) = Fetch.Miss(Problem.Unclassified(Legacy.Text("nothing here either")))
      val chain = FetchChain(
        List(
          FetchChain.Step.Id("core") -> g.registries.sourceFetchers(Registries.SourceFetchersKey("core")),
          FetchChain.Step.Id("next") -> next
        )
      )
      val out = chain.fetchWorkExplained(work, legalOnly = FetchChain.LegalOnly(true))
      // the job's report says why core had nothing, and the next step was asked
      assertEquals(out.answers.map(_.fetcher), List("core", "next"))
      val refused = out.answers.head.why match
        case Problem.Upstream.Http(name, _, status, _, _) => s"$name" == "core" && s"$status" == "429"
        case _                                            => false
      assert(refused, out.answers.head.why)
      assertEquals(core.asked.size, 1)
      // until then neither use asks CORE
      clock.advance(60)
      assert(missWhy(lookup(g)).contains("CORE asked to wait until 2026-10-26T18:52:11Z (HTTP 429)"))
      val idx = coreIndex(cfg, g, budget).get
      assert(idx.unavailable.exists(_.sentence.contains("2026-10-26T18:52:11Z")), idx.unavailable.toString)
      assert(
        idx
          .search(Search.Query("bronze age"), LocalDate.of(2026, 1, 1), ScholarlyIndex.Limit(10))
          .left
          .exists(_.sentence.contains("CORE asked to wait until 2026-10-26T18:52:11Z"))
      )
      assertEquals(core.asked.size, 1)
      // after it, CORE is asked again: here it has the PDF
      clock.advance(90)
      core.script.add(core.found)
      lookup(g) match
        case Fetch.Found(f: FetchedFile) => assertEquals(f.source, "core")
        case other                       => fail(other.toString)
      assertEquals(core.asked.size, 2)
      assertEquals(budget.state.lookupTokens, 2)
      assertEquals(budget.state.window.flatMap(_.remaining), Some(20))
    }

  test("X-RateLimit-Remaining 0 on an answer: CORE is not asked for exhausted-pause-seconds"):
    withCore { core =>
      val clock = Clock()
      val budget = clock.budget()
      val cfg = acquire(core, key = "k-host")
      val g = generation(cfg, budget)
      core.script.add(Reply(200, Map("X-RateLimit-Limit" -> "25", "X-RateLimit-Remaining" -> "0")))
      assertEquals(missWhy(lookup(g)), "no copy '10.1/x'")
      assert(missWhy(lookup(g)).contains("X-RateLimit-Remaining 0 of 25"))
      assertEquals(core.asked.size, 1)
      clock.advance(61)
      assertEquals(missWhy(lookup(g)), "no copy '10.1/x'")
      assertEquals(core.asked.size, 2)
    }

  test("pacing: at most per-minute calls in 60 s — a call waits its slot, or is not made when that is too long"):
    withCore { core =>
      val clock = Clock()
      val budget = clock.budget()
      val g = generation(acquire(core, key = "k", perMinute = 2, maxWait = 30), budget)
      lookup(g); clock.advance(5); lookup(g); clock.advance(5)
      assert(missWhy(lookup(g)).contains("acquire.core.max-wait-seconds"))
      assertEquals(core.asked.size, 2)
      // more patience: the third call waits for the first slot to leave the minute (50 s), then goes
      val patient = generation(acquire(core, key = "k", perMinute = 2, maxWait = 120), budget)
      lookup(patient)
      assertEquals(clock.slept.asScala.toList, List(50_000L))
      assertEquals(core.asked.size, 3)
    }

  test("the daily budget: searches stop at their share, PDF lookups keep the rest; both stop at daily-tokens"):
    withCore { core =>
      val clock = Clock()
      val budget = clock.budget()
      val cfg = acquire(core, key = "k", daily = 3, searches = 1)
      val g = generation(cfg, budget)
      val idx = coreIndex(cfg, g, budget).get
      assert(idx.search(Search.Query("bronze age"), LocalDate.of(2026, 1, 1), ScholarlyIndex.Limit(10)).isRight)
      assert(idx.unavailable.exists(_.sentence.contains("search-daily-tokens")), idx.unavailable.toString)
      assert(
        idx
          .search(Search.Query("bronze age"), LocalDate.of(2026, 1, 1), ScholarlyIndex.Limit(10))
          .left
          .exists(_.sentence.contains("search-daily-tokens"))
      )
      lookup(g); lookup(g)
      assert(missWhy(lookup(g)).contains("acquire.core.daily-tokens"))
      assertEquals(core.asked.size, 3)
      assertEquals((budget.state.searchTokens, budget.state.lookupTokens), (1, 2))
      // the next UTC day starts afresh
      clock.advance(24 * 3600)
      assert(idx.unavailable.isEmpty)
      assertEquals(missWhy(lookup(g)), "no copy '10.1/x'")
    }

  test("the budget outlives a restart: what was spent and a block are read back from its store"):
    withCore { core =>
      val clock = Clock()
      val saved = AtomicReference[Option[CoreQuota.State]](None)
      val store = new CoreQuotaStore:
        def load() = saved.get
        def save(s: CoreQuota.State) = saved.set(Some(s))
      val first = clock.budget()
      first.persistTo(store)
      val cfg = acquire(core, key = "k")
      core.script.add(core.found)
      core.script.add(Reply(429, Map("X-RateLimit-Retry-After" -> "2026-10-26T19:30:00+0000")))
      lookup(generation(cfg, first))
      lookup(generation(cfg, first))
      assertEquals(
        saved.get.map(s => (s.lookupTokens, s.block.map(_.until))),
        Some((2, Some(Instant.parse("2026-10-26T19:30:00Z"))))
      )
      // a new process: a fresh budget, the same store
      val second = clock.budget()
      second.persistTo(store)
      assertEquals(second.state.lookupTokens, 2)
      assert(missWhy(lookup(generation(cfg, second))).contains("CORE asked to wait until 2026-10-26T19:30:00Z"))
      assertEquals(core.asked.size, 2)
    }

  test("ONE setting enables both uses: oa-core's api-key as a vault reference keys the lookup AND the search"):
    withCore { core =>
      val budget = Clock().budget()
      val cfg = acquire( // no acquire.core.api-key
        core,
        user = """histmersion.plugins.maker { enabled = true, vault = "fakevault" }
                 |histmersion.plugins.oa-core.api-key = "fakevault:Work/Histmersion/CORE#password"""".stripMargin
      )
      val g = generation(cfg, budget, extra = List(MakerPlugin()))
      assert(g.report.ok, g.report.toString)
      assertEquals(
        Plugins.coreKey(g, cfg.config.acquire.core.key.map(k => Plugins.CoreKey(k.value))),
        Some("v(Work/Histmersion/CORE#password)")
      )
      lookup(g)
      coreIndex(cfg, g, budget)
        .getOrElse(fail("no core index"))
        .search(Search.Query("bronze age"), LocalDate.of(2026, 1, 1), ScholarlyIndex.Limit(10))
      assertEquals(core.asked.asScala.toList.map(_._2), List.fill(2)("Bearer v(Work/Histmersion/CORE#password)"))
      assert(core.asked.asScala.toList.map(_._1).contains("""doi:"10.1/x""""))
      assert(!g.report.toString.contains("v(Work"), "the report shows the reference, never the value")
    }

  test("the plain acquire.core.api-key still enables both; with no key at all neither asks CORE"):
    withCore { core =>
      val budget = Clock().budget()
      val keyed = acquire(core, key = "k-plain")
      val g = generation(keyed, budget)
      lookup(g)
      coreIndex(keyed, g, budget)
        .getOrElse(fail("no core index"))
        .search(Search.Query("q"), LocalDate.of(2026, 1, 1), ScholarlyIndex.Limit(10))
      assertEquals(core.asked.asScala.toList.map(_._2), List("Bearer k-plain", "Bearer k-plain"))
      // the index alone (oa-core switched off): the plain key still serves the search
      val off = generation(acquire(core, key = "k-plain", user = "histmersion.plugins.oa-core.enabled = false"), budget)
      assert(coreIndex(keyed, off, budget).isDefined)
      val none = generation(acquire(core), budget)
      assert(missWhy(lookup(none)).contains("histmersion.plugins.oa-core.api-key is empty"))
      assertEquals(coreIndex(acquire(core), none, budget), None)
      assertEquals(core.asked.size, 2)
    }
