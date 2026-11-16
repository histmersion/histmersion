package com.histmersion
package domain.library.acquisition
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import hearth.kindlings.optics.syntax.*

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.library.workflows.*

import com.histmersion.domain.library.sources.SourceFixtures

import com.augustnagro.magnum.transact

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Goal.Plans.LinkKind
import com.histmersion.domain.automation.worklists.QueueRows
import com.histmersion.domain.automation.goals.{GoalLinkRepo, GoalRepo}
import com.histmersion.util.database.Migrate
import com.histmersion.domain.library.workflows.WatchManagerKind

import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue
import com.histmersion.api.automation.{Goal, Queue}
import com.histmersion.api.library.{CandidateSweep, Doi, Source, Title, WorkRef}
import com.histmersion.domain.library.search.Search
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.api.catalogue.Year
import com.histmersion.util.database.Transactor
import com.histmersion.server.Deps
import com.histmersion.server.AcquisitionTesting
import com.histmersion.domain.automation.goals.GoalService
import com.histmersion.domain.research.articles.ArticleCitedSources
import com.histmersion.domain.research.articles.ArticleService
import com.histmersion.domain.automation.worklists.QueueService
import com.histmersion.api.library.Citekey
import com.histmersion.api.library.Candidates
import com.histmersion.domain.library.workflows.AgentUpstream

/** The deterministic new-source search (#3761) over fake indexes: what a sweep stores, the watermarks it keeps,
  * deduplication across indexes and against the corpus and the acquisition list, a failed index, a dry run, marking —
  * and the supervisor's rule that nothing new starts no agent run. No network.
  */
class CandidateSearchSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = {
    val b = AppConfig.load(); b.modify(_.db).setTo(b.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
  }
  private lazy val ds = { val d = testPool(cfg.db); Migrate.run(d); d }
  private val Goal = com.histmersion.domain.automation.Goal.Slug("zzcand-goal")

  /** An index answering `hits` for every query, recording the `since` it was asked. */
  final private class FakeIndex(val name: ScholarlyIndex.Name, hits: => Either[Problem, List[WorkRef]])
      extends ScholarlyIndex:
    val asked = ConcurrentLinkedQueue[(Search.Query, LocalDate)]()
    def search(query: Search.Query, since: LocalDate, limit: ScholarlyIndex.Limit) = { asked.add((query, since)); hits }

  final private class FakeAlerts extends CitationAlerts:
    val asked = ConcurrentLinkedQueue[String]()
    def citing(doi: com.histmersion.domain.library.Doi, since: LocalDate, limit: CitationAlerts.CitingLimit) = {
      asked.add(s"citing $doi"); Right(List(work(Doi("10.5555/zzcand.citing"))))
    }
    def authors(doi: com.histmersion.domain.library.Doi) = Right(
      List(AuthorName("A1"), AuthorName("A2"), AuthorName("A3"))
    )
    def byAuthor(authorId: CitationAlerts.ByAuthorAuthorId, since: LocalDate, limit: CitationAlerts.ByAuthorLimit) = {
      asked.add(s"author $authorId"); Right(Nil)
    }

  private def work(doi: Doi, title: Title = Title("A work")) =
    WorkRef(doi = Some(doi), title = Some(title), year = Some(Year(2026)))

  private def fresh(): Unit =
    import com.augustnagro.magnum.*
    transact(ds):
      sql"DELETE FROM goals WHERE slug = ${Goal.value}".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzcand%'".update.run()
      sql"DELETE FROM queue_items WHERE text LIKE '%zzcand%'".update.run()
    assert(
      transact(ds)(
        GoalRepo.put(
          Goal,
          com.histmersion.api.automation.Goal.Put(
            com.histmersion.api.automation.Goal.Title("Bronze Age collapse"),
            status = com.histmersion.api.automation.Goal.StatusValue("seeding")
          )
        )
      ).isRight
    )
    assert(
      transact(ds)(
        GoalLinkRepo.link(
          Goal,
          LinkKind.Query,
          com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("bronze age collapse"),
          None
        )
      )
    )

  private lazy val db = Transactor(ds)
  private lazy val store = CandidateService(db)

  private def search(idx: List[ScholarlyIndex], alerts: CitationAlerts, day: LocalDate) =
    val deps = Deps(cfg, ds)
    CandidateSearch(
      store,
      AcquisitionTesting.sources(deps),
      GoalService(db),
      ArticleCitedSources(
        ArticleService(db)
      ),
      QueueService(db),
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
        Citekey("zzcand_have"),
        Title("Have"),
        Nil,
        Some(Doi("10.5555/zzcand.have")),
        Some(Year(2020)),
        None,
        Source.Status.Current,
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
    QueueRows.add(
      ds,
      Queue.Kind.Acquire,
      Queue.QueueAdd(Queue.QueueItem.Text("zzcand queued https://doi.org/10.5555/zzcand.queued"))
    )
    val oa = FakeIndex(
      ScholarlyIndex.Name("openalex"),
      Right(List(work(Doi("10.5555/zzcand.a")), work(Doi("10.5555/zzcand.have")), work(Doi("10.5555/zzcand.queued"))))
    )
    val cr = FakeIndex(
      ScholarlyIndex.Name("crossref"),
      Right(
        List(work(Doi("https://doi.org/10.5555/ZZCAND.A"), Title("Same, other record")), work(Doi("10.5555/zzcand.b")))
      )
    )
    val day = LocalDate.of(2026, 10, 24)
    val s = search(List(oa, cr), FakeAlerts(), day)
      .sweep(Goal, dryRun = CandidateSearch.SweepDryRun(false))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
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
    val list = store.list(Some(Goal), Source.Candidate.State("new"), CandidateService.ListLimit(10), Offset(0))
    assertEquals(
      list.map(c => (c.doi, c.foundBy)),
      List(
        "10.5555/zzcand.a" -> List("openalex query \"bronze age collapse\"", "crossref query \"bronze age collapse\""),
        "10.5555/zzcand.b" -> List("crossref query \"bronze age collapse\"")
      )
    )

  test("the next sweep reads from the watermark; what it finds again is not new; a failed index keeps its watermark"):
    fresh()
    val oa = FakeIndex(ScholarlyIndex.Name("openalex"), Right(List(work(Doi("10.5555/zzcand.a")))))
    // `var`: the second sweep finds Crossref down
    var crFails = false
    val cr = FakeIndex(
      ScholarlyIndex.Name("crossref"),
      if crFails then Left(Problem.Unclassified(Legacy.Text("HTTP 503"))) else Right(Nil)
    )
    val s1 = search(List(oa, cr), FakeAlerts(), LocalDate.of(2026, 10, 1))
      .sweep(Goal, dryRun = CandidateSearch.SweepDryRun(false))
      .toOption
      .get
    assertEquals(s1.added, 1)
    crFails = true
    val s2 = search(List(oa, cr), FakeAlerts(), LocalDate.of(2026, 10, 24))
      .sweep(Goal, dryRun = CandidateSearch.SweepDryRun(false))
      .toOption
      .get
    assertEquals((s2.added, s2.again, s2.unseen), (0, 1, 1))
    assertEquals(oa.asked.toArray.toList.last, ("bronze age collapse", LocalDate.of(2026, 10, 1)))
    val ws = store.watches(Goal).map(w => w.index -> (w.watermark, w.lastError)).toMap
    assertEquals(ws(Candidates.Index("openalex")), (Some("2026-10-24"), None))
    assertEquals(
      ws(Candidates.Index("crossref")) match { case (mark, error) => (mark, error.map(_.sentence)) },
      (Some("2026-10-01"), Some("HTTP 503")),
      "a failed read skips nothing next time"
    )
    // the supervisor: unseen candidates → an agent run; none → no run; the search not connected → as before
    assert(!WatchManagerKind.nothingNew(Right(s2)))
    assert(WatchManagerKind.nothingNew(Right(s2.modify(_.unseen).setTo(CandidateSweep.Unseen(0)))))
    assert(!WatchManagerKind.nothingNew(Left(Problem.Unclassified(Legacy.Text("not connected")))))

  test("a dry run reads but stores nothing; marking moves candidates out of the unseen"):
    fresh()
    val oa = FakeIndex(
      ScholarlyIndex.Name("openalex"),
      Right(List(work(Doi("10.5555/zzcand.a")), work(Doi("10.5555/zzcand.b"))))
    )
    val dry = search(List(oa), FakeAlerts(), LocalDate.of(2026, 10, 24))
      .sweep(Goal, dryRun = CandidateSearch.SweepDryRun(true))
      .toOption
      .get
    assertEquals((dry.added, dry.unseen, dry.dryRun), (2, 0, true))
    assertEquals(store.watches(Goal), Nil)
    val s = search(List(oa), FakeAlerts(), LocalDate.of(2026, 10, 24))
      .sweep(Goal, dryRun = CandidateSearch.SweepDryRun(false))
      .toOption
      .get
    assertEquals(s.unseen, 2)
    val ids = store.list(Some(Goal), Source.Candidate.State("new"), CandidateService.ListLimit(10), Offset(0)).map(_.id)
    assertEquals(store.mark(ids.take(1), Source.Candidate.State("dismissed")), 1)
    assertEquals(
      store
        .markDois(Goal, List(com.histmersion.domain.library.Doi("10.5555/zzcand.b")), Source.Candidate.State("queued")),
      1
    )
    assertEquals(
      store.counts(Some(Goal)).map(c => c.state -> c.count).toMap,
      Map("queued" -> 1, "dismissed" -> 1)
    )
    // found again later: still decided, not new
    val again = search(List(oa), FakeAlerts(), LocalDate.of(2026, 10, 25))
      .sweep(Goal, dryRun = CandidateSearch.SweepDryRun(false))
      .toOption
      .get
    assertEquals((again.added, again.again, again.unseen), (0, 2, 0))

  test("the goal's sources: the works citing them and their first and last authors' newer works"):
    fresh()
    SourceFixtures.upsert(
      ds,
      Source(
        Citekey("zzcand_src"),
        Title("Source"),
        Nil,
        Some(Doi("10.5555/zzcand.src")),
        Some(Year(2010)),
        None,
        Source.Status.Current,
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
    assert(
      transact(ds)(
        GoalLinkRepo.link(
          Goal,
          LinkKind.Source,
          com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("zzcand_src"),
          None
        )
      )
    )
    val alerts = FakeAlerts()
    val s = search(Nil, alerts, LocalDate.of(2026, 10, 24))
      .sweep(Goal, dryRun = CandidateSearch.SweepDryRun(false))
      .toOption
      .get
    assertEquals(alerts.asked.toArray.toList, List("citing 10.5555/zzcand.src", "author A1", "author A3"))
    assertEquals(
      s.watches.map(w => (w.kind, w.subject)),
      List(("citing", "10.5555/zzcand.src"), ("author", "A1"), ("author", "A3"))
    )
    assertEquals(
      store
        .list(Some(Goal), Source.Candidate.State("new"), CandidateService.ListLimit(10), Offset(0))
        .map(c => (c.doi, c.foundBy)),
      List("10.5555/zzcand.citing" -> List("openalex citing 10.5555/zzcand.src"))
    )
    assert(
      search(Nil, alerts, LocalDate.of(2026, 10, 24))
        .sweep(
          com.histmersion.domain.automation.Goal.Slug("zzcand-nowhere"),
          dryRun = CandidateSearch.SweepDryRun(false)
        )
        .isLeft
    )

  test("an index that may not be asked (CORE's budget, CORE's block) is left out of the sweep, which says so"):
    fresh()
    assert(
      transact(ds)(
        GoalLinkRepo.link(
          Goal,
          LinkKind.Query,
          com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("late bronze age"),
          None
        )
      )
    )
    assert(
      transact(ds)(
        GoalLinkRepo.link(
          Goal,
          LinkKind.Query,
          com.histmersion.api.automation.Goal.Plans.GoalLink.Ref("sea peoples"),
          None
        )
      )
    )
    val oa = FakeIndex(ScholarlyIndex.Name("openalex"), Right(List(work(Doi("10.5555/zzcand.a")))))
    // the first CORE search answers 429: from then on CORE says to wait
    final class Throttled extends ScholarlyIndex:
      val name = ScholarlyIndex.Name("core")
      val asked = ConcurrentLinkedQueue[String]()
      override def unavailable =
        Option.when(!asked.isEmpty)(
          Problem.Unclassified(Legacy.Text("CORE asked to wait until 2026-10-24T12:00:00Z (HTTP 429)"))
        )
      def search(query: Search.Query, since: LocalDate, limit: ScholarlyIndex.Limit) = {
        asked.add(query.value); Left(Problem.Unclassified(Legacy.Text("CORE not asked: CORE answered HTTP 429")))
      }
    val core = Throttled()
    val s = search(List(oa, core), FakeAlerts(), LocalDate.of(2026, 10, 24))
      .sweep(Goal, dryRun = CandidateSearch.SweepDryRun(false))
      .toOption
      .get
    assertEquals(core.asked.size, 1, "asked once, then left out")
    assertEquals(oa.asked.size, 3, "the other indexes are read as usual")
    assertEquals(
      s.notes,
      List(
        "core not asked this sweep: CORE asked to wait until 2026-10-24T12:00:00Z (HTTP 429) — " +
          "2 watches left unread, which keep their watermarks and are read next sweep"
      )
    )
    assert(s.watches.filter(_.index.value == "core").forall(_.watermark.isEmpty), s.watches.toString)
    assertEquals(s.added, 1)
    // a budget spent before the sweep: CORE is not asked at all
    val spent = new ScholarlyIndex:
      val name = ScholarlyIndex.Name("core")
      override def unavailable = Some(Problem.Unclassified(Legacy.Text("CORE's daily budget for searches is spent")))
      def search(query: Search.Query, since: LocalDate, limit: ScholarlyIndex.Limit) = fail("asked")
    val s2 = search(List(spent), FakeAlerts(), LocalDate.of(2026, 10, 24))
      .sweep(Goal, dryRun = CandidateSearch.SweepDryRun(true))
      .toOption
      .get
    assert(
      s2.notes.size == 1 && s2.notes.head.value
        .startsWith("core not asked this sweep: CORE's daily budget for searches is spent — 3 watches"),
      s2.notes.toString
    )
