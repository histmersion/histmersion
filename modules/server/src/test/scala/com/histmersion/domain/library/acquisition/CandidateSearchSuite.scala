package com.histmersion.domain.library.acquisition

import com.histmersion.domain.library.workflows.*

import com.histmersion.domain.library.sources.SourceFixtures

import com.augustnagro.magnum.transact

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Goal.Plans.LinkKind
import com.histmersion.db.QueueRows
import com.histmersion.domain.automation.goals.{GoalLinkRepo, GoalRepo}
import com.histmersion.util.database.Migrate
import com.histmersion.domain.library.workflows.WatchManagerKind

import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue
import com.histmersion.api.automation.{Goal, Queue}
import com.histmersion.api.library.{Source, WorkRef}

/** The deterministic new-source search (#3761) over fake indexes: what a sweep stores, the watermarks it keeps,
  * deduplication across indexes and against the corpus and the acquisition list, a failed index, a dry run, marking —
  * and the supervisor's rule that nothing new starts no agent run. No network.
  */
class CandidateSearchSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = { val b = AppConfig.load(); b.copy(db = b.db.copy(url = testDb.get)) }
  private lazy val ds = { val d = testPool(cfg.db); Migrate.run(d); d }
  private val Goal = "zzcand-goal"

  /** An index answering `hits` for every query, recording the `since` it was asked. */
  final private class FakeIndex(val name: String, hits: => Either[String, List[WorkRef]]) extends ScholarlyIndex:
    val asked = ConcurrentLinkedQueue[(String, LocalDate)]()
    def search(query: String, since: LocalDate, limit: Int) = { asked.add((query, since)); hits }

  final private class FakeAlerts extends CitationAlerts:
    val asked = ConcurrentLinkedQueue[String]()
    def citing(doi: String, since: LocalDate, limit: Int) = {
      asked.add(s"citing $doi"); Right(List(work("10.5555/zzcand.citing")))
    }
    def authors(doi: String) = Right(List("A1", "A2", "A3"))
    def byAuthor(authorId: String, since: LocalDate, limit: Int) = { asked.add(s"author $authorId"); Right(Nil) }

  private def work(doi: String, title: String = "A work") =
    WorkRef(doi = Some(doi), title = Some(title), year = Some(2026))

  private def fresh(): Unit =
    import com.augustnagro.magnum.*
    transact(ds):
      sql"DELETE FROM goals WHERE slug = $Goal".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzcand%'".update.run()
      sql"DELETE FROM queue_items WHERE text LIKE '%zzcand%'".update.run()
    assert(
      transact(ds)(
        GoalRepo.put(Goal, com.histmersion.api.automation.Goal.Put("Bronze Age collapse", status = "seeding"))
      ).isRight
    )
    assert(transact(ds)(GoalLinkRepo.link(Goal, LinkKind.Query, "bronze age collapse", None)))

  private lazy val db = com.histmersion.util.database.Transactor(ds)
  private lazy val store = CandidateService(db)

  private def search(idx: List[ScholarlyIndex], alerts: CitationAlerts, day: LocalDate) =
    val deps = com.histmersion.server.Deps(cfg, ds)
    CandidateSearch(
      store,
      com.histmersion.server.AcquisitionTesting.sources(deps),
      com.histmersion.domain.automation.goals.GoalService(db),
      com.histmersion.domain.research.articles.ArticleCitedSources(
        com.histmersion.domain.research.articles.ArticleService(db)
      ),
      com.histmersion.domain.automation.worklists.QueueService(db),
      cfg.acquire.candidates,
      idx,
      alerts,
      () => day
    )

  test("a sweep stores what is new, deduplicated across indexes and against the corpus and the acquisition list"):
    fresh()
    SourceFixtures.upsert(
      ds,
      Source(
        "zzcand_have",
        "Have",
        Nil,
        Some("10.5555/zzcand.have"),
        Some(2020),
        None,
        com.histmersion.api.library.Source.Status.Current,
        Nil,
        Nil,
        Nil,
        None,
        None,
        None,
        None,
        None,
        None
      ),
      cfg.limits.chunkChars
    )
    QueueRows.add(ds, Queue.Kind.Acquire, Queue.QueueAdd("zzcand queued https://doi.org/10.5555/zzcand.queued"))
    val oa = FakeIndex(
      "openalex",
      Right(List(work("10.5555/zzcand.a"), work("10.5555/zzcand.have"), work("10.5555/zzcand.queued")))
    )
    val cr = FakeIndex(
      "crossref",
      Right(List(work("https://doi.org/10.5555/ZZCAND.A", "Same, other record"), work("10.5555/zzcand.b")))
    )
    val day = LocalDate.of(2026, 10, 24)
    val s = search(List(oa, cr), FakeAlerts(), day).sweep(Goal, dryRun = false).fold(e => fail(e.message), identity)
    assertEquals((s.found, s.added, s.again, s.unseen), (5, 2, 0, 2))
    assertEquals(s.skipped, Map("in the corpus or queued" -> 2))
    // the first read looks back acquire.candidates.lookback-days; the watermark is today's after it
    assertEquals(
      oa.asked.peek(),
      ("bronze age collapse", day.minusDays(cfg.acquire.candidates.lookbackDays.value.toLong))
    )
    assertEquals(
      store.watches(Goal).map(w => (w.index, w.watermark, w.lastFound)),
      List(("crossref", Some("2026-10-24"), 2), ("openalex", Some("2026-10-24"), 3))
    )
    val list = store.list(Some(Goal), "new", 10, 0)
    assertEquals(
      list.map(c => (c.doi, c.foundBy)),
      List(
        "10.5555/zzcand.a" -> List("openalex query \"bronze age collapse\"", "crossref query \"bronze age collapse\""),
        "10.5555/zzcand.b" -> List("crossref query \"bronze age collapse\"")
      )
    )

  test("the next sweep reads from the watermark; what it finds again is not new; a failed index keeps its watermark"):
    fresh()
    val oa = FakeIndex("openalex", Right(List(work("10.5555/zzcand.a"))))
    // `var`: the second sweep finds Crossref down
    var crFails = false
    val cr = FakeIndex("crossref", if crFails then Left("HTTP 503") else Right(Nil))
    val s1 = search(List(oa, cr), FakeAlerts(), LocalDate.of(2026, 10, 1)).sweep(Goal, dryRun = false).toOption.get
    assertEquals(s1.added, 1)
    crFails = true
    val s2 = search(List(oa, cr), FakeAlerts(), LocalDate.of(2026, 10, 24)).sweep(Goal, dryRun = false).toOption.get
    assertEquals((s2.added, s2.again, s2.unseen), (0, 1, 1))
    assertEquals(oa.asked.toArray.toList.last, ("bronze age collapse", LocalDate.of(2026, 10, 1)))
    val ws = store.watches(Goal).map(w => w.index -> (w.watermark, w.lastError)).toMap
    assertEquals(ws("openalex"), (Some("2026-10-24"), None))
    assertEquals(ws("crossref"), (Some("2026-10-01"), Some("HTTP 503")), "a failed read skips nothing next time")
    // the supervisor: unseen candidates → an agent run; none → no run; the search not connected → as before
    assert(!WatchManagerKind.nothingNew(Right(s2)))
    assert(WatchManagerKind.nothingNew(Right(s2.copy(unseen = 0))))
    assert(!WatchManagerKind.nothingNew(Left("not connected")))

  test("a dry run reads but stores nothing; marking moves candidates out of the unseen"):
    fresh()
    val oa = FakeIndex("openalex", Right(List(work("10.5555/zzcand.a"), work("10.5555/zzcand.b"))))
    val dry = search(List(oa), FakeAlerts(), LocalDate.of(2026, 10, 24)).sweep(Goal, dryRun = true).toOption.get
    assertEquals((dry.added, dry.unseen, dry.dryRun), (2, 0, true))
    assertEquals(store.watches(Goal), Nil)
    val s = search(List(oa), FakeAlerts(), LocalDate.of(2026, 10, 24)).sweep(Goal, dryRun = false).toOption.get
    assertEquals(s.unseen, 2)
    val ids = store.list(Some(Goal), "new", 10, 0).map(_.id)
    assertEquals(store.mark(ids.take(1), "dismissed"), 1)
    assertEquals(store.markDois(Goal, List("10.5555/zzcand.b"), "queued"), 1)
    assertEquals(
      store.counts(Some(Goal)).map(c => c.state -> c.count).toMap,
      Map("queued" -> 1, "dismissed" -> 1)
    )
    // found again later: still decided, not new
    val again = search(List(oa), FakeAlerts(), LocalDate.of(2026, 10, 25)).sweep(Goal, dryRun = false).toOption.get
    assertEquals((again.added, again.again, again.unseen), (0, 2, 0))

  test("the goal's sources: the works citing them and their first and last authors' newer works"):
    fresh()
    SourceFixtures.upsert(
      ds,
      Source(
        "zzcand_src",
        "Source",
        Nil,
        Some("10.5555/zzcand.src"),
        Some(2010),
        None,
        com.histmersion.api.library.Source.Status.Current,
        Nil,
        Nil,
        Nil,
        None,
        None,
        None,
        None,
        None,
        None
      ),
      cfg.limits.chunkChars
    )
    assert(transact(ds)(GoalLinkRepo.link(Goal, LinkKind.Source, "zzcand_src", None)))
    val alerts = FakeAlerts()
    val s = search(Nil, alerts, LocalDate.of(2026, 10, 24)).sweep(Goal, dryRun = false).toOption.get
    assertEquals(alerts.asked.toArray.toList, List("citing 10.5555/zzcand.src", "author A1", "author A3"))
    assertEquals(
      s.watches.map(w => (w.kind, w.subject)),
      List(("citing", "10.5555/zzcand.src"), ("author", "A1"), ("author", "A3"))
    )
    assertEquals(
      store.list(Some(Goal), "new", 10, 0).map(c => (c.doi, c.foundBy)),
      List("10.5555/zzcand.citing" -> List("openalex citing 10.5555/zzcand.src"))
    )
    assert(search(Nil, alerts, LocalDate.of(2026, 10, 24)).sweep("zzcand-nowhere", dryRun = false).isLeft)

  test("an index that may not be asked (CORE's budget, CORE's block) is left out of the sweep, which says so"):
    fresh()
    assert(transact(ds)(GoalLinkRepo.link(Goal, LinkKind.Query, "late bronze age", None)))
    assert(transact(ds)(GoalLinkRepo.link(Goal, LinkKind.Query, "sea peoples", None)))
    val oa = FakeIndex("openalex", Right(List(work("10.5555/zzcand.a"))))
    // the first CORE search answers 429: from then on CORE says to wait
    final class Throttled extends ScholarlyIndex:
      val name = "core"
      val asked = ConcurrentLinkedQueue[String]()
      override def unavailable = Option.when(!asked.isEmpty)("CORE asked to wait until 2026-10-24T12:00:00Z (HTTP 429)")
      def search(query: String, since: LocalDate, limit: Int) = {
        asked.add(query); Left("CORE not asked: CORE answered HTTP 429")
      }
    val core = Throttled()
    val s = search(List(oa, core), FakeAlerts(), LocalDate.of(2026, 10, 24)).sweep(Goal, dryRun = false).toOption.get
    assertEquals(core.asked.size, 1, "asked once, then left out")
    assertEquals(oa.asked.size, 3, "the other indexes are read as usual")
    assertEquals(
      s.notes,
      List(
        "core not asked this sweep: CORE asked to wait until 2026-10-24T12:00:00Z (HTTP 429) — " +
          "2 watches left unread, which keep their watermarks and are read next sweep"
      )
    )
    assert(s.watches.filter(_.index == "core").forall(_.watermark.isEmpty), s.watches.toString)
    assertEquals(s.added, 1)
    // a budget spent before the sweep: CORE is not asked at all
    val spent = new ScholarlyIndex:
      val name = "core"
      override def unavailable = Some("CORE's daily budget for searches is spent")
      def search(query: String, since: LocalDate, limit: Int) = fail("asked")
    val s2 = search(List(spent), FakeAlerts(), LocalDate.of(2026, 10, 24)).sweep(Goal, dryRun = true).toOption.get
    assert(
      s2.notes.size == 1 && s2.notes.head
        .startsWith("core not asked this sweep: CORE's daily budget for searches is spent — 3 watches"),
      s2.notes.toString
    )
