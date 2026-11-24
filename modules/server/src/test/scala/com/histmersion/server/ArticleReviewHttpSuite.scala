package com.histmersion
package server
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import ox.supervised
import com.histmersion.config.*
import com.histmersion.domain.ReviewArticle.{Finding, Reviewer, Severity}
import com.histmersion.domain.ReviewLedger.{Closure, Entry, Ledger, Status}
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import com.histmersion.api.automation.{Agents, Goal}
import com.histmersion.api.research.{ReviewGoal, ReviewState}
import com.histmersion.api.research.Article
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.domain.research.workflows.ReviewLedgerFixtures
import com.histmersion.config.DbConfig
import com.histmersion.config.ServerConfig
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given

/** An article's review state and history over every surface: the badge on each `GET /api/articles` row (derived from
  * the review manager's item and the ledger counted in SQL), `GET /api/articles/{slug}/review` (the history from the
  * ledger, the goals linking the article, the approvals waiting), the `/article/{slug}` page and the `article_review`
  * MCP tool. Skipped without HISTMERSION_TEST_DB_URL.
  */
class ArticleReviewHttpSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val http = HttpClient.newHttpClient()
  private def send(req: HttpRequest): (Int, String) =
    val r = http.send(req, HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def json(url: String) = HttpRequest.newBuilder(URI.create(url)).header("content-type", "application/json")
  private def get(url: String) = send(HttpRequest.newBuilder(URI.create(url)).build())
  private def put(url: String, body: String) = send(json(url).PUT(HttpRequest.BodyPublishers.ofString(body)).build())
  private def delete(url: String) = send(HttpRequest.newBuilder(URI.create(url)).DELETE().build())

  private val Running = "zzrev-running"
  private val Fresh = "zzrev-fresh"
  private val Goal = "zzrev-goal"
  private val RunA = "zzrev-run-a"
  private val RunB = "zzrev-run-b"

  private def finding(id: Finding.Id, r: Reviewer, s: Severity, problem: Finding.Problem) =
    Finding(
      id,
      r,
      s,
      Finding.Kind("undefined-term"),
      Finding.Where("Background"),
      Finding.Quote(""),
      problem,
      Finding.Fix("")
    )

  private val ledger = Ledger(
    Ledger.Slug.applyUnsafe(Running),
    List(
      Entry(
        finding(Finding.Id("R1"), Reviewer.Rules, Severity.Major, Finding.Problem("a decorative citation")),
        Entry.Run.assume(RunA),
        Entry.Round(1),
        Status.Rejected,
        closed = Some(
          Closure(
            Closure.By("fixer"),
            Closure.Round(1),
            Closure.Run.assume(RunA),
            Closure.Note("the record supports the sentence")
          )
        )
      ),
      Entry(
        finding(Finding.Id("C1"), Reviewer.ColdReader, Severity.Minor, Finding.Problem("term not defined")),
        Entry.Run.assume(RunA),
        Entry.Round(1),
        Status.Fixed,
        closed = Some(Closure(Closure.By("cold-reader"), Closure.Round(2), Closure.Run.assume(RunA)))
      ),
      Entry(
        finding(Finding.Id("S1"), Reviewer.Skeptic, Severity.Major, Finding.Problem("a critique is missing")),
        Entry.Run.assume(RunB),
        Entry.Round(1)
      )
    )
  )

  private def clean(url: String, cfg: AppConfig): Unit =
    import com.augustnagro.magnum.*
    connect(testPool(cfg.db)):
      sql"DELETE FROM manager_items WHERE target LIKE 'zzrev-%'".update.run()
      sql"DELETE FROM approvals WHERE subject LIKE 'zzrev-%'".update.run()
      sql"DELETE FROM jobs WHERE id LIKE 'zzrev-%'".update.run()
      sql"DELETE FROM review_ledgers WHERE slug LIKE 'zzrev-%'".update.run()
      sql"DELETE FROM goals WHERE slug LIKE 'zzrev-%'".update.run()
    List(Running, Fresh).foreach(s => delete(s"$url/api/articles/$s"))

  private def fixtures(cfg: AppConfig): Unit =
    import com.augustnagro.magnum.*
    val ds = testPool(cfg.db)
    ReviewLedgerFixtures.put(ds, ledger)
    transact(ds):
      sql"""INSERT INTO jobs (id, kind, status, created_at) VALUES
            ($RunA, ${Agents.JobKind: String}, 'done', '2026-10-01T10:00:00Z'::timestamptz),
            ($RunB, ${Agents.JobKind: String}, 'running', '2026-10-02T10:00:00Z'::timestamptz)""".update.run()
      sql"""INSERT INTO agent_steps (job_id, seq, attempt, name, kind, status) VALUES
            ($RunB, 1, 1, 'ledger', 'code', 'done'), ($RunB, 2, 1, 'snapshot-1', 'code', 'done'),
            ($RunB, 3, 1, 'snapshot-2', 'code', 'done')""".update.run()
      sql"INSERT INTO agent_managers (id, kind) VALUES ('review', 'review') ON CONFLICT (id) DO NOTHING".update.run()
      sql"""INSERT INTO manager_items (manager_id, kind, target, status, attempts, run_id, origin)
            VALUES ('review', 'article-review', $Running, 'active', 2, $RunB, 'person')""".update.run()
      sql"INSERT INTO goals (slug, title, status) VALUES ($Goal, 'Zz review goal', 'compiled')".update.run()
      sql"INSERT INTO goal_links (goal_slug, kind, ref) VALUES ($Goal, 'article', $Running)".update.run()
      sql"""INSERT INTO approvals (kind, subject, proposal, state) VALUES ('article-filter', $Running, '{}'::jsonb, 'open')""".update
        .run()

  test("review state on the list, the history over REST, the page and MCP; an article with none is not reviewed"):
    val base = AppConfig.load()
    val cfg = base.modify(_.db).setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        clean(url, cfg)
        List(Running, Fresh).foreach { s =>
          val (st, body) =
            put(s"$url/api/articles/$s", s"""{"title":"Zz $s","kind":"concept","body":"# Zz $s\\n\\nText."}""")
          assertEquals(st, 200, body)
        }
        fixtures(cfg)

        // ── the list: a badge per row, from the cheap query ───────────────────────────────────
        val (ls, lb) = get(s"$url/api/articles?title=Zz%20zzrev&limit=50")
        assertEquals(ls, 200, lb)
        val rows = RawJson.decode[Article.List](lb).toOption.get.articles.map(a => a.slug -> a.review).toMap
        val running = rows(Article.Slug(Running)).get
        assertEquals(
          (running.state, running.label, running.round),
          (ReviewState.InReview, "in review · round 2", Some(2))
        )
        assertEquals((running.openMajors, running.openMinors, running.findings, running.runs), (1, 0, 3, 2))
        assert(running.detail.value.contains("Waiting for a person: approval #"), running.detail)
        assertEquals(rows(Article.Slug(Fresh)).map(_.state), Some(ReviewState.Unreviewed))

        // ── one article's review: history in run order, goals, approvals ─────────────────────
        val (rs, rb) = get(s"$url/api/articles/$Running/review")
        assertEquals(rs, 200, rb)
        val r = RawJson.decode[Article.ReviewView](rb).toOption.get
        assertEquals(
          r.rounds.map(rd => (rd.run, rd.runNumber, rd.round, rd.findings.map(_.id))),
          List((RunA, 1, 1, List("R1", "C1")), (RunB, 2, 1, List("S1")))
        )
        assertEquals(r.rounds.head.startedAt, Some("2026-10-01T10:00:00Z"))
        assertEquals(r.rounds.head.findings.head.note, "the record supports the sentence")
        assertEquals(
          r.goals,
          List(ReviewGoal(ReviewGoal.Slug(Goal), ReviewGoal.Title("Zz review goal"), ReviewGoal.Status("compiled")))
        )
        assertEquals(r.approvals.map(_.kind), List("article-filter"))
        assertEquals(get(s"$url/api/articles/zzrev-nothing/review")._1, 404)
        val fresh = RawJson.decode[Article.ReviewView](get(s"$url/api/articles/$Fresh/review")._2).toOption.get
        assertEquals((fresh.badge.state, fresh.rounds, fresh.goals), (ReviewState.Unreviewed, Nil, Nil))

        // ── the page: the badge and the goal under the title, the history at the end ─────────
        val (ps, page) = get(s"$url/article/$Running?fragment=true")
        assertEquals(ps, 200, page)
        assert(page.contains("""<span class="job-state review-state state-in-review""""), page)
        assert(page.contains(s"""href="/research/goals/$Goal""""), page)
        assert(page.contains("Review history: 3 findings over 2 runs, 1 still open"), page)
        assert(page.contains("rejected by fixer in run 1, round 1: the record supports the sentence"), page)

        // ── MCP ───────────────────────────────────────────────────────────────────────────────
        val call =
          s"""{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"article_review","arguments":{"slug":"$Running"}}}"""
        val (ms, mb) = send(
          json(s"$url/mcp")
            .header("accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(call))
            .build()
        )
        assertEquals(ms, 200, mb)
        assert(
          mb.contains(s"Review of [[$Running]]") && mb.contains("in review · round 2") && mb.contains(
            "#### Run 2, round 1"
          ),
          mb
        )
      finally
        clean(url, cfg)
        b.stop()
    }
