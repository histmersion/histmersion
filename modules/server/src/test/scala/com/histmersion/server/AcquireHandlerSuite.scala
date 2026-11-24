package com.histmersion
package server
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.citations.CitationFixtures
import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.pluginhost.{FetchChain, OaFetcher, Registries}

import com.histmersion.api.ListPatch
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.automation.worklists.QueueRows
import com.histmersion.util.database.{Db, Migrate, WritePermit}
import com.histmersion.domain.library.ingest.{FetchedCopy, IngestJobs, IngestMeta}
import com.histmersion.domain.library.acquisition.*
import com.histmersion.api.automation.Queue
import com.histmersion.api.library.{Edge, FollowupRow, NeighborRow, RefRow, UpdateNotice, WorkRef}

import com.histmersion.api.library.{
  Acquire,
  AcquireMarkdown,
  ArchiveFile,
  ArchiveImportRequest,
  ArchiveItem,
  AssessRequest,
  CalibrePushRequest,
  FindingsPatch,
  FollowupsRequest,
  RefsRequest,
  Source
}
import com.histmersion.api.library.Finding
import com.histmersion.api.library.Ingest
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.api.library.Doi
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Citekey
import com.histmersion.domain.library.search.Search
import com.histmersion.domain.library.acquisition.DiscoveryClient
import com.histmersion.domain.library.ArxivId
import com.histmersion.plugin.SourceCollection
import com.histmersion.domain.library.collections.RefusingCollectionLibrary
import com.histmersion.domain.library.collections.CollectionsService
import com.histmersion.domain.library.collections.CalibreGapControllers
import com.histmersion.domain.library.ingest.StoredOriginals
import com.histmersion.util.storage.Location
import com.histmersion.api.library.RetractionNotice
import com.histmersion.api.library.AcquireApi
import com.histmersion.domain.Licence
import com.histmersion.config.AcquireConfig
import com.histmersion.api.Write
import com.histmersion.plugin.SourceFetcher
import com.histmersion.plugin.Access
import com.histmersion.plugin.WorkKey
import com.histmersion.plugin.Fetch
import com.histmersion.config.ConverterConfig
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.TestPermits.given
import com.histmersion.api.automation.Creation
import com.histmersion.domain.Creator
import com.histmersion.util.database.TestCreators.given

/** The acquisition handlers against a real Postgres (`HISTMERSION_TEST_DB_URL`), with every upstream replaced by a
  * fake. What is exercised here is the part fixtures cannot reach: resolving references to stored citekeys, writing
  * typed edges, applying a verdict to `status`, and the ingest sequence — including that a re-ingest does NOT wipe an
  * enriched source.
  */
class AcquireHandlerSuite extends DbSuite:

  private val ByBacklog = domain.automation.workflows.InTreeWorkflow.IngestBacklog.creator
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg =
    val base = AppConfig.load()
    base.modify(_.db).setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
  private lazy val ds =
    val d = testPool(cfg.db); Migrate.run(d); d

  // ── fakes ───────────────────────────────────────────────────────────────────────────────────

  // NB: every citekey this suite touches starts with `zzacq_`, and `wipe` REFUSES anything else.
  // The suite runs against whatever HISTMERSION_TEST_DB_URL points at — which is routinely a copy of the
  // real corpus — so a test that deleted `haak2015_massive` would quietly destroy a real source.
  private val TestPrefix = "zzacq"
  private val AbSha = Sha256("abababababababababababababababababababababababababababababababab")

  private val target = WorkRef(
    doi = (Some("10.1038/zztest001")).map(Doi.apply),
    openalexId = Some(WorkRef.OpenalexId("W1")),
    title = (Some("Massive migration test")).map(api.library.Title.apply),
    year = (Some(2015)).map(Year.apply),
    authors = (List("Wolfgang Haak")).map(api.library.AuthorName.apply),
    venue = (Some("Nature")).map(Source.Venue.apply),
    citedByCount = Some(Source.CitedByCount(900)),
    citekey = (Some("zzacq_ingested")).map(Citekey.apply)
  )

  private class FakeDiscovery(
      work: WorkRef = target,
      refs: List[RefRow] = Nil,
      notices: List[UpdateNotice] = Nil,
      hits: List[WorkRef] = Nil
  ) extends DiscoveryClient:
    def search(
        q: Search.Query,
        since: Option[com.histmersion.Year],
        sort: Option[DiscoveryClient.SearchSort],
        limit: DiscoveryClient.SearchLimit
    ) = Right(hits)
    def meta(doi: domain.library.Doi) = Right(work.modify(_.doi).setTo(Some(Doi(doi.value))))
    def references(doi: domain.library.Doi) = Right((DiscoveryClient.Backend("crossref"), refs))
    def updates(doi: domain.library.Doi) = Right(notices)

  private class FakeCitations(rows: List[FollowupRow]) extends CitationsClient:
    def citing(work: WorkRef, since: Option[com.histmersion.Year], limit: CitationsClient.CitingLimit) = Right(
      (CitationsClient.Backend("semantic-scholar"), rows)
    )

  private object NoPdf extends OaFetcher:
    def fetch(doi: Option[domain.library.Doi], arxiv: Option[ArxivId]) = Left(
      Problem.Unclassified(Legacy.Text("no legal open-access PDF found"))
    )

  private class SomePdf(
      source: FetchedFile.Source = FetchedFile.Source("unpaywall"),
      licence: Option[plugin.Licence] = None
  ) extends OaFetcher:
    def fetch(doi: Option[domain.library.Doi], arxiv: Option[ArxivId]) =
      Right(
        FetchedFile(
          source,
          Some(plugin.FetchedFile.Url("https://example.org/x.pdf")),
          FetchedFile.Bytes(("%PDF-1.4\n" + "x" * 2000).getBytes("UTF-8")),
          plugin.FetchedFile.Extension("pdf"),
          licence
        )
      )

  private object NoWiki extends WikiClient:
    def refs(url: WikiClient.ArticleUrl) = Left(Problem.Unclassified(Legacy.Text("not used here")))

  /** archive.org as the library's collection, answering `item` alone, its import run by `h`. */
  private def archiveService(archived: ArchiveItem, h: AcquisitionControllers) =
    import com.histmersion.plugin.{CollectionFile, CollectionId, CollectionItem, CollectionQuery}
    val collection = new SourceCollection:
      def title = SourceCollection.Title("archive.org")
      private val one = CollectionItem(
        CollectionId(archived.identifier.value),
        archived.title.map(t => CollectionItem.Title(t.value)),
        archived.creator.map(c => CollectionItem.Authors(c.value)),
        archived.year.map(y => plugin.Year(y.value.toInt)),
        publisher = archived.publisher.map(p => CollectionItem.Publisher(p.value)),
        files = archived.files.map(f =>
          CollectionFile(
            CollectionFile.Name(f.name.value),
            f.format.map(x => CollectionFile.Format(x.value)),
            Some(CollectionFile.Megabytes(f.megabytes.value))
          )
        ),
        restricted = CollectionItem.Restricted(archived.lendingOnly.value)
      )
      def search(query: CollectionQuery) = Right(List(one))
      def item(id: CollectionId) = Right(Some(one))
      def file(i: CollectionItem, format: Option[SourceCollection.FileFormat]) =
        i.files
          .find(_.name.value.endsWith(".epub"))
          .orElse(i.files.headOption)
          .toRight(Problem.Unclassified(Legacy.Text("no file")))
      def original(i: CollectionItem, f: CollectionFile) = Left(
        Problem.Unclassified(Legacy.Text("the chain downloads it"))
      )
    val library = new RefusingCollectionLibrary:
      override def archiveImport(i: ArchiveItem, f: ArchiveFile, req: ArchiveImportRequest)(using Creator.New) =
        h.archiveImport(i, f, req)
    CollectionsService.InMemory(
      java.util.concurrent.atomic.AtomicReference(Nil),
      library,
      () => scala.collection.immutable.ListMap(Registries.SourceCollectionsKey("archive-org") -> collection),
      CollectionsService.Limit(8)
    )

  private class FakeRw(text: RetractionWatchClient.Csv) extends RetractionWatchClient:
    def csv(): Either[Problem, RetractionWatchClient.Csv] = Right(text)

  private def handlers(
      discovery: DiscoveryClient = FakeDiscovery(),
      citations: CitationsClient = FakeCitations(Nil),
      oa: OaFetcher = NoPdf,
      rw: RetractionWatchClient = FakeRw(RetractionWatchClient.Csv("")),
      app: AppConfig = cfg,
      jobs: IngestJobs = AcquisitionTesting.skeleton(Deps(cfg, ds))
  ) = AcquisitionTesting.controllers(Deps(app, ds), discovery, citations, oa, NoWiki, rw, jobs)

  /** The Calibre gaps over `app`'s stores. */
  private def gaps(app: AppConfig = cfg) =
    val deps = Deps(app, ds)
    CalibreGapControllers(
      AcquisitionTesting.sources(deps),
      StoredOriginals(
        () => deps.storage.store(Location.IngestOriginals),
        () => deps.storage.store(Location.Spool)
      ),
      () => app,
      deps.maintenance
    )

  /** The skeleton store, recording whether each ingest handed it a file to convert (the OCR job). */
  final private class RecordingJobs extends IngestJobs:
    private val skeleton = AcquisitionTesting.skeleton(Deps(cfg, ds))
    val files = java.util.concurrent.ConcurrentLinkedQueue[Option[FetchedCopy]]()
    def enqueue(copy: Option[FetchedCopy], citekey: domain.library.Citekey, meta: IngestMeta)(using
        WritePermit
    )(using Creator.New) =
      files.add(copy); skeleton.enqueue(copy, citekey, meta)

  /** A PDF fetcher that counts its calls. */
  final private class CountingPdf extends OaFetcher:
    val calls = java.util.concurrent.atomic.AtomicInteger(0)
    def fetch(doi: Option[domain.library.Doi], arxiv: Option[ArxivId]) =
      calls.incrementAndGet(); SomePdf().fetch(doi, arxiv)

  // ── fixtures in the database ────────────────────────────────────────────────────────────────

  private def source(
      ck: String,
      doi: Option[String] = None,
      title: String = "T",
      authors: List[String] = List("A B"),
      year: Option[Int] = Some(2000)
  ) =
    Source(
      Citekey(ck),
      api.library.Title(title),
      (authors).map(api.library.AuthorName.apply),
      (doi).map(Doi.apply),
      (year).map(Year.apply),
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
    )

  private def wipe(cks: String*): Unit =
    import com.augustnagro.magnum.*
    cks
      .find(!_.startsWith(TestPrefix))
      .foreach(k => fail(s"refusing to delete '$k': this suite may only touch $TestPrefix* citekeys"))
    transact(ds) {
      cks.foreach { ck =>
        sql"DELETE FROM edges WHERE src = $ck OR dst = $ck".update.run()
        sql"DELETE FROM sources WHERE citekey = $ck".update.run()
      }
    }

  // ── refs ────────────────────────────────────────────────────────────────────────────────────

  test("refs resolves by DOI, by exact citekey and by unique citekey prefix — and --apply writes edges"):
    wipe("zzacq_target", "zzacq_bydoi", "zzacqreich2018_who")
    SourceFixtures.upsert(ds, source("zzacq_target", Some("10.1038/zztest001")), cfg.limits.chunkChars)
    SourceFixtures.upsert(ds, source("zzacq_bydoi", Some("10.1038/zzref001")), cfg.limits.chunkChars)
    // a reference whose generated citekey (`zzacqreich2018_here`) shares its `<surname><year>` stem
    // with the stored `zzacqreich2018_who` — the prefix widening `resolveEdges` does for short targets
    SourceFixtures.upsert(
      ds,
      source("zzacqreich2018_who", None, "Who We Are and How We Got Here", List("Someone Zzacqreich"), Some(2018)),
      cfg.limits.chunkChars
    )

    val rows = List(
      RefRow(
        title = (Some("Something")).map(api.library.Title.apply),
        doi = (Some("10.1038/zzref001")).map(Doi.apply)
      ),
      RefRow(
        title = (Some("Who We Are and How We Got Here")).map(api.library.Title.apply),
        authors = (List("Someone Zzacqreich")).map(api.library.AuthorName.apply),
        year = (Some(2018)).map(Year.apply)
      ),
      RefRow(
        title = (Some("Unknown work")).map(api.library.Title.apply),
        authors = (List("Nobody Here")).map(api.library.AuthorName.apply),
        year = (Some(1904)).map(Year.apply)
      )
    )

    val h = handlers(FakeDiscovery(refs = rows))
    val r = h
      .refs(RefsRequest(RefsRequest.Id("zzacq_target"), apply = RefsRequest.Apply(true)))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(r.count, 3)
    assertEquals(r.resolved, 2)
    assertEquals(r.edgesAdded, 2)
    assertEquals(r.references.flatMap(_.citekey).toSet, Set("zzacq_bydoi", "zzacqreich2018_who"))
    val out = CitationFixtures
      .neighbors(ds, domain.library.Citekey("zzacq_target"))
      .filter(_.direction == NeighborRow.Direction("out"))
    assertEquals(out.map(_.citekey).toSet, Set("zzacq_bydoi", "zzacqreich2018_who"))
    assert(out.forall(_.kind == Edge.Kind.References))

  test("refs without --apply writes nothing"):
    wipe("zzacq_target", "zzacq_bydoi")
    SourceFixtures.upsert(ds, source("zzacq_target", Some("10.1038/zztest001")), cfg.limits.chunkChars)
    SourceFixtures.upsert(ds, source("zzacq_bydoi", Some("10.1038/zzref001")), cfg.limits.chunkChars)
    val h = handlers(FakeDiscovery(refs = List(RefRow(doi = (Some("10.1038/zzref001")).map(Doi.apply)))))
    val r =
      h.refs(RefsRequest(RefsRequest.Id("zzacq_target"))).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(r.resolved, 1)
    assertEquals(r.edgesAdded, 0)
    assertEquals(
      CitationFixtures
        .neighbors(ds, domain.library.Citekey("zzacq_target"))
        .count(_.direction == NeighborRow.Direction("out")),
      0
    )

  // ── assess ──────────────────────────────────────────────────────────────────────────────────

  test("a Retraction Watch hit yields verdict=retracted and --apply writes status=retracted"):
    wipe("zzacq_retr")
    SourceFixtures.upsert(ds, source("zzacq_retr", Some("10.1177/zzretracted")), cfg.limits.chunkChars)
    AcquisitionTesting
      .assessments(ds)
      .replaceRetractionWatch(
        List(
          Acquire.RetractionRow(
            Doi("10.1177/zzretracted"),
            Some(RetractionNotice.Account("+Duplication of Image")),
            Some(RetractionNotice.Date("12/15/2021 0:00")),
            Some(RetractionNotice.Nature("Retraction"))
          )
        )
      )

    val h = handlers()
    val a = h
      .assess(AssessRequest(AssessRequest.Id("zzacq_retr"), apply = AssessRequest.Apply(true)))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(a.verdict, Acquire.Verdicts.Retracted)
    assert(a.flagged.value)
    assertEquals(a.retractionWatch.head.date, Some("2021-12-15"))
    assertEquals(a.applied, Some("retracted"))
    assertEquals(
      SourceFixtures.load(ds, domain.library.Citekey("zzacq_retr")).map(_.status),
      Some(Source.Status.Retracted)
    )
    // and the verdict is STORED, so a later lint does not need the network
    assertEquals(
      AcquisitionTesting.assessments(ds).lastFor(domain.library.Citekey("zzacq_retr")).map(_._1),
      Some("retracted")
    )

  test("a clean, well-cited paper is `current` and the mirror refresh replaces every row"):
    wipe("zzacq_ok")
    SourceFixtures.upsert(ds, source("zzacq_ok", Some("10.1038/zzclean")), cfg.limits.chunkChars)
    val csv =
      "OriginalPaperDOI,RetractionNature,Reason,RetractionDate\n" +
        "10.1000/other,Retraction,\"+Fabrication, of data\",1/2/2020 0:00\n"
    val h = handlers(rw = FakeRw(RetractionWatchClient.Csv(csv)))
    assertEquals(h.updateRetractions().map(_.rows), Right(1))
    val a = h
      .assess(AssessRequest(AssessRequest.Id("zzacq_ok")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(a.verdict, Acquire.Verdicts.Current)
    assertEquals(a.retractionWatch, Nil)
    assertEquals(a.applied, None) // no --apply
    assertEquals(
      SourceFixtures.load(ds, domain.library.Citekey("zzacq_ok")).map(_.status),
      Some(Source.Status.Current)
    )

  // #699: assess --apply overwrote contested (with a challenges edge behind it) with current
  test("assess --apply keeps a status already set, unless --force-status; a retraction always applies"):
    wipe("zzacq_kept", "zzacq_retr2")
    SourceFixtures.upsert(
      ds,
      source("zzacq_kept", Some("10.1038/zzkept"))
        .modify(_.status)
        .setTo(Source.Status.Contested),
      cfg.limits.chunkChars
    )
    val h = handlers()
    val a = h
      .assess(AssessRequest(AssessRequest.Id("zzacq_kept"), apply = AssessRequest.Apply(true)))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(a.verdict, Acquire.Verdicts.Current)
    assertEquals((a.applied, a.kept), (None, Some(Source.Status.Contested)))
    assert(AcquireMarkdown.assessment(a).contains("Kept **contested** (set by hand)"))
    assertEquals(
      SourceFixtures.load(ds, domain.library.Citekey("zzacq_kept")).map(_.status),
      Some(Source.Status.Contested)
    )

    val f = h
      .assess(
        AssessRequest(
          AssessRequest.Id("zzacq_kept"),
          apply = AssessRequest.Apply(true),
          forceStatus = AssessRequest.ForceStatus(true)
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((f.applied, f.kept), (Some("current"), None))
    assertEquals(
      SourceFixtures.load(ds, domain.library.Citekey("zzacq_kept")).map(_.status),
      Some(Source.Status.Current)
    )

    SourceFixtures.upsert(
      ds,
      source("zzacq_retr2", Some("10.1177/zzretracted2"))
        .modify(_.status)
        .setTo(Source.Status.Foundational),
      cfg.limits.chunkChars
    )
    AcquisitionTesting
      .assessments(ds)
      .replaceRetractionWatch(
        List(
          Acquire
            .RetractionRow(
              Doi("10.1177/zzretracted2"),
              Some(RetractionNotice.Account("+Duplication")),
              Some(RetractionNotice.Date("12/15/2021 0:00")),
              Some(RetractionNotice.Nature("Retraction"))
            )
        )
      )
    val r = h
      .assess(AssessRequest(AssessRequest.Id("zzacq_retr2"), apply = AssessRequest.Apply(true)))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(r.applied, Some("retracted"))
    assertEquals(
      SourceFixtures.load(ds, domain.library.Citekey("zzacq_retr2")).map(_.status),
      Some(Source.Status.Retracted)
    )

  // ── followups ───────────────────────────────────────────────────────────────────────────────

  test("followups classify citing papers and --apply adds one typed edge per stored citing source"):
    wipe("zzacq_target", "zzacq_critic")
    SourceFixtures.upsert(ds, source("zzacq_target", Some("10.1038/zztest001")), cfg.limits.chunkChars)
    SourceFixtures.upsert(ds, source("zzacq_critic", Some("10.1038/zzcritic")), cfg.limits.chunkChars)
    val rows = List(
      FollowupRow(
        title = Some(api.library.Title("A critique")),
        year = Some(Year(2021)),
        doi = Some(Doi("10.1038/zzcritic")),
        contexts = List(FollowupRow.Contexts("These data contradict the steppe model")),
        isInfluential = FollowupRow.IsInfluential(true)
      ),
      FollowupRow(
        title = Some(api.library.Title("Unknown citer")),
        year = Some(Year(2022)),
        doi = Some(Doi("10.9999/nope")),
        contexts = List(FollowupRow.Contexts("consistent with earlier work"))
      )
    )

    val h = handlers(citations = FakeCitations(rows))
    val r = h
      .followups(FollowupsRequest(FollowupsRequest.Id("zzacq_target"), apply = FollowupsRequest.Apply(true)))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(r.count, 2)
    assertEquals(r.followups.head.proposedKind, Acquire.EdgeKinds.Refutes) // influential first
    assertEquals(r.followups.map(_.citekey), List(Some("zzacq_critic"), None))
    assertEquals(r.edgesAdded, 1)
    val incoming = CitationFixtures
      .neighbors(ds, domain.library.Citekey("zzacq_target"))
      .filter(_.direction == NeighborRow.Direction("in"))
    assertEquals(incoming.map(n => (n.citekey, Edge.Kind.wireOf(n.kind))), Vector(("zzacq_critic", "refutes")))

  // ── discover ────────────────────────────────────────────────────────────────────────────────

  test("discover marks what the corpus already has, and --not-in-db removes it"):
    wipe("zzacq_known")
    SourceFixtures.upsert(ds, source("zzacq_known", Some("10.1038/zzknown")), cfg.limits.chunkChars)
    val hits = List(
      WorkRef(
        doi = (Some("10.1038/zzknown")).map(Doi.apply),
        title = (Some("Known")).map(api.library.Title.apply),
        citekey = (Some("x2000_known")).map(Citekey.apply)
      ),
      WorkRef(
        doi = (Some("10.1038/zzbrandnew")).map(Doi.apply),
        title = (Some("New")).map(api.library.Title.apply),
        citekey = (Some("y2001_new")).map(Citekey.apply)
      )
    )
    val h = handlers(FakeDiscovery(hits = hits))
    val all =
      h.discover(AcquireApi.Query("q"), None, None, None, notInDb = false)
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(all.works.map(_.inDb), List(true, false))
    assertEquals(all.works.head.citekey, Some("zzacq_known")) // the STORED key, not the generated one
    val delta =
      h.discover(AcquireApi.Query("q"), None, None, None, notInDb = true)
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(delta.works.map(_.doi), List(Some("10.1038/zzbrandnew")))

  // ── ingest ──────────────────────────────────────────────────────────────────────────────────

  test("ingest: metadata → PDF → source → reference edges → assessment"):
    // `zzacq_bydoi` is left behind by the `refs` tests above and carries the SAME DOI as the
    // `zzacq_ref` fixture below; with two sources on one DOI the reference resolves to whichever
    // Postgres returns first, which made this test fail about half the time. Wipe it too.
    wipe("zzacq_ingested", "zzacq_ref", "zzacq_bydoi")
    SourceFixtures.upsert(ds, source("zzacq_ref", Some("10.1038/zzref001")), cfg.limits.chunkChars)
    val h =
      handlers(FakeDiscovery(refs = List(RefRow(doi = (Some("10.1038/zzref001")).map(Doi.apply)))), oa = SomePdf())
    val r =
      h.ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")))
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)

    assertEquals(r.citekey, "zzacq_ingested")
    assert(r.created.value)
    assertEquals(r.pdf.map(_.source), Some("unpaywall"))
    assert(
      r.pdf.flatMap(_.spoolPath).exists(p => java.nio.file.Files.isRegularFile(java.nio.file.Path.of(p.value))),
      "the fetched PDF must be spooled for the converter"
    )
    assertEquals(r.edgesAdded, 1)
    assertEquals(r.dropIn, None)
    assertEquals(r.steps.map(_.step), List("metadata", "fetch", "references", "store", "assess"))
    assert(r.steps.forall(_.ok.value), r.steps.toString)

    val stored = SourceFixtures
      .load(ds, domain.library.Citekey("zzacq_ingested"))
      .getOrElse(fail("source was not created"))
    assertEquals(stored.title, "Massive migration test")
    assertEquals(stored.doi, Some("10.1038/zztest001"))
    assertEquals(stored.provenance, Some("unpaywall"))
    assertEquals(stored.edges.map(_.dst), List("zzacq_ref"))
    assertEquals(r.assessment.map(_.verdict), Some(Acquire.Verdicts.Current))
    // a freshly created source takes the verdict as its status
    assertEquals(stored.status, Source.Status.Current)
    wipe("zzacq_ingested", "zzacq_ref")

  test("a fetched copy's licence becomes the source's, never over one already recorded (#2560)"):
    import com.augustnagro.magnum.*
    def license = transact(ds)(
      sql"SELECT license FROM sources WHERE citekey = 'zzacq_ingested'".query[Option[String]].run().headOption.flatten
    )
    wipe("zzacq_ingested")
    val r = handlers(oa = SomePdf(FetchedFile.Source("oabooks"), Some(plugin.Licence(" CC-BY-4.0 "))))
      .ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(
      r.steps.exists(s => s.step == Ingest.Step.Value("fetch") && s.detail.value.contains("licence CC-BY-4.0")),
      r.steps.toString
    )
    assert(r.steps.exists(s => s.step == Ingest.Step.Value("licence") && s.ok.value), r.steps.toString)
    assertEquals(license, Some("CC-BY-4.0"))
    // a re-fetch with another licence keeps the one recorded; a copy without one changes nothing
    val again =
      handlers(oa = SomePdf(FetchedFile.Source("oabooks"), Some(plugin.Licence("CC-BY-NC-4.0"))))
        .ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001"), refetch = Ingest.Request.Refetch(true)))
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(!again.steps.exists(_.step == Ingest.Step.Value("licence")), again.steps.toString)
    assertEquals(license, Some("CC-BY-4.0"))
    assert(
      !AcquisitionTesting
        .sources(Deps(cfg, ds))
        .recordLicence(domain.library.Citekey("zzacq_ingested"), Licence.Id("  "), Caller.Local),
      "a blank licence is never written"
    )
    wipe("zzacq_ingested")

  test("an open-access work no script may fetch goes to the acquisition list once, as a browser download (#1581)"):
    import com.augustnagro.magnum.*
    def items = QueueRows
      .acquireNaming(ds, domain.library.Doi("10.1038/zztest001"))
      .filter(_.status == Queue.QueueItem.Status("open"))
    def dropItems(): Unit =
      transact(ds)(items.foreach(i => sql"DELETE FROM queue_items WHERE id = ${i.id}".update.run()))
    wipe("zzacq_ingested"); dropItems()
    val gold =
      FakeDiscovery(
        target
          .modify(_.isOa)
          .setTo(Source.Candidate.IsOa(true))
          .modify(_.oaStatus)
          .setTo(Some(WorkRef.OaStatus("gold")))
          .modify(_.oaUrl)
          .setTo(Some(WorkRef.OaUrl("https://press.example.org/a/1")))
      )
    val r = handlers(gold, oa = NoPdf)
      .ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(items.size, 1, items.toString)
    assertEquals(items.head.topic, Some(Acquire.BrowserDownloadTopic))
    assertEquals(
      items.head.creator,
      Some(Creation(Creation.Unattributed, surface = Some(Creation.Surface("rest")))),
      "an ingest over REST, which carries no identity yet: unattributed, never a guessed person"
    )
    assert(
      items.head.text.value.contains("download it in the browser") && items.head.text.value
        .contains("--citekey zzacq_ingested"),
      items.head.text
    )
    assert(
      r.steps.exists(s => s.step == Ingest.Step.Value("acquire") && s.detail.value.contains(s"#${items.head.id}")),
      r.steps.toString
    )
    // again: the item that names it is reported, no second one
    val _ = handlers(gold, oa = NoPdf)
      .ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(items.size, 1)
    // a closed work, or a watcher's legal-only ingest, adds none
    dropItems(); wipe("zzacq_ingested")
    val _ = handlers(oa = NoPdf).ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")))
    val _ = handlers(gold, oa = NoPdf).ingestLegal(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")), ByBacklog)
    assertEquals(items.size, 0)
    dropItems(); wipe("zzacq_ingested")

  test("a fetcher's refusal goes into the browser-download item: where to download it, under which licence"):
    import com.augustnagro.magnum.*
    import com.histmersion.plugin.{Access, Fetch, SourceFetcher, WorkKey}
    def items = QueueRows
      .acquireNaming(ds, domain.library.Doi("10.1038/zztest001"))
      .filter(_.status == Queue.QueueItem.Status("open"))
    def dropItems(): Unit =
      transact(ds)(items.foreach(i => sql"DELETE FROM queue_items WHERE id = ${i.id}".update.run()))
    wipe("zzacq_ingested"); dropItems()
    def answering(a: Fetch): SourceFetcher = new SourceFetcher:
      val access = Access.Open
      def fetch(w: WorkKey, c: Fetch.Context) = a
    val why = Problem.Unclassified(
      Legacy.Text(
        "an openly licensed copy exists but could not be downloaded: 20.500.12657/23223 " +
          "(https://library.oapen.org/handle/20.500.12657/23223), licence CC BY 4.0"
      )
    )
    val chain = FetchChain(
      List(
        FetchChain.Step.Id("unpaywall") -> answering(
          Fetch.Miss(Fetch.Absence.NotHeld, Problem.Unclassified(Legacy.Text("no open-access location")))
        ),
        FetchChain.Step.Id("oapen") -> answering(Fetch.Refused(Fetch.Refusal.BotChallenge, why))
      )
    )
    val open =
      FakeDiscovery(
        target
          .modify(_.isOa)
          .setTo(Source.Candidate.IsOa(true))
          .modify(_.oaStatus)
          .setTo(Some(WorkRef.OaStatus("gold")))
          .modify(_.oaUrl)
          .setTo(Some(WorkRef.OaUrl("https://press.example.org/a/1")))
      )
    val _ = handlers(open, oa = chain)
      .ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(items.size, 1, items.toString)
    assert(
      items.head.text.value.contains(s"download it in the browser (oapen: ${why.sentence}), then histmersion ingest"),
      items.head.text
    )
    assert(!items.head.text.value.contains("unpaywall"), "a miss says nothing about where to download it")
    dropItems(); wipe("zzacq_ingested")

  test(
    "#1581: a gold open-access work on a publisher that refuses scripts goes to the acquisition list, from a watcher's legal path too"
  ):
    import com.augustnagro.magnum.*
    def items = QueueRows.acquireNaming(ds, domain.library.Doi("10.1038/zztest001"))
    def dropItems(): Unit =
      transact(ds)(items.foreach(i => sql"DELETE FROM queue_items WHERE id = ${i.id}".update.run()))
    wipe("zzacq_ingested"); dropItems()
    val gold = target
      .modify(_.isOa)
      .setTo(Source.Candidate.IsOa(true))
      .modify(_.oaStatus)
      .setTo(Some(WorkRef.OaStatus("gold")))
      .modify(_.oaLicence)
      .setTo(Some(WorkRef.OaLicence("cc-by")))
      .modify(_.oaUrl)
      .setTo(Some(WorkRef.OaUrl("https://www.mdpi.com/zz/1/pdf")))
      .modify(_.oaLocations)
      .setTo(List(WorkRef.OaLocations("https://www.mdpi.com/zz/1")))
    // the watcher's legal-only path: no source, but the item, with the location, the DOI, the licence and the command
    val legal =
      handlers(FakeDiscovery(gold), oa = NoPdf)
        .ingestLegal(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")), ByBacklog)
    assert(legal.left.exists(e => IngestBacklog.isNoLegalCopy(e.problems.head)), legal.toString)
    assertEquals(items.map(_.topic), Vector(Some(Acquire.BrowserDownloadTopic)))
    val text = items.head.text
    assert(
      List(
        "https://doi.org/10.1038/zztest001",
        "gold open access, licence cc-by",
        "at https://www.mdpi.com/zz/1",
        "mdpi.com refuses downloads by scripts",
        "histmersion ingest <file> --citekey zzacq_ingested"
      ).forall(text.value.contains),
      text
    )
    assert(
      SourceFixtures.load(ds, domain.library.Citekey("zzacq_ingested")).isEmpty,
      "the legal path creates no metadata-only source"
    )
    // a person's ingest of the same work: the item is reported, not doubled
    val r = handlers(FakeDiscovery(gold), oa = NoPdf)
      .ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(items.size, 1)
    assert(
      r.steps.exists(s =>
        s.step == Ingest.Step.Value("acquire") && s.detail.value.contains(s"#${items.head.id} (already listed)")
      ),
      r.steps.toString
    )
    // hybrid on the same host, or gold on a host that does not refuse scripts: the watcher adds nothing
    dropItems(); wipe("zzacq_ingested")
    val _ = handlers(FakeDiscovery(gold.modify(_.oaStatus).setTo(Some(WorkRef.OaStatus("hybrid")))), oa = NoPdf)
      .ingestLegal(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")), ByBacklog)
    val _ =
      handlers(
        FakeDiscovery(
          gold
            .modify(_.oaUrl)
            .setTo(Some(WorkRef.OaUrl("https://zenodo.org/records/1")))
            .modify(_.oaLocations)
            .setTo(Nil)
        ),
        oa = NoPdf
      )
        .ingestLegal(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")), ByBacklog)
    assertEquals(items.size, 0)
    // a copy found: nothing to download by hand
    val _ = handlers(FakeDiscovery(gold), oa = SomePdf()).ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")))
    assertEquals(items.size, 0)
    dropItems(); wipe("zzacq_ingested")

  test("no copy: the ingest report lists what each fetcher of the chain answered"):
    wipe("zzacq_ingested")
    import com.histmersion.plugin.{Access, Fetch, SourceFetcher, WorkKey}
    def answering(a: Fetch): SourceFetcher = new SourceFetcher:
      val access = Access.Open
      def fetch(w: WorkKey, c: Fetch.Context) = a
    val chain = FetchChain(
      List(
        FetchChain.Step.Id("unpaywall") -> answering(
          Fetch.Miss(Fetch.Absence.NotHeld, Problem.Unclassified(Legacy.Text("no open-access location")))
        ),
        FetchChain.Step.Id("oapen") -> answering(
          Fetch.Refused(
            Fetch.Refusal.BotChallenge,
            Problem.Unclassified(Legacy.Text("20.500.12657/23223: bot challenge"))
          )
        )
      )
    )
    val r = handlers(oa = chain)
      .ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(
      r.steps.find(_.step == Ingest.Step.Value("chain")).map(_.detail),
      Some(
        "unpaywall: no copy — no open-access location; oapen: refused (scripts refused) — 20.500.12657/23223: bot challenge"
      )
    )
    assert(
      r.steps.exists(s =>
        s.step == Ingest.Step.Value("fetch") && s.detail.value.contains("20.500.12657/23223: bot challenge")
      ),
      r.steps.toString
    )
    wipe("zzacq_ingested")

  test("--legal-only: a person's ingest asks the legal chain alone, even with acquire.allow-unauthorised = true"):
    wipe("zzacq_ingested")
    import com.histmersion.plugin.{Access, Fetch, SourceFetcher, WorkKey}
    val asked = java.util.concurrent.atomic.AtomicInteger(0)
    val unauthorised = new SourceFetcher:
      val access = Access.Unauthorised
      def fetch(w: WorkKey, c: Fetch.Context) = {
        asked.incrementAndGet(); Fetch.Miss(Fetch.Absence.NotHeld, Problem.Unclassified(Legacy.Text("stub")))
      }
    val open = new SourceFetcher:
      val access = Access.Open
      def fetch(w: WorkKey, c: Fetch.Context) =
        Fetch.Miss(Fetch.Absence.NotHeld, Problem.Unclassified(Legacy.Text("no open-access location")))
    val chain = FetchChain(
      List(FetchChain.Step.Id("unpaywall") -> open, FetchChain.Step.Id("stub-unauthorised") -> unauthorised),
      allowUnauthorised = FetchChain.AllowUnauthorised(true)
    )
    val allowing =
      cfg.modify(_.acquire).setTo(cfg.acquire.modify(_.allowUnauthorised).setTo(AcquireConfig.AllowUnauthorised(true)))
    val r = handlers(oa = chain, app = allowing)
      .ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001"), legalOnly = Ingest.Request.LegalOnly(true)))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(asked.get, 0, "no fetcher the config marks unauthorised is asked")
    assert(r.steps.exists(_.step == Ingest.Step.Value("legal-only")), r.steps.toString)
    assert(r.dropIn.isDefined, "with no legal copy it is a person's ingest as usual: the drop-in is explained")
    assert(
      SourceFixtures.load(ds, domain.library.Citekey("zzacq_ingested")).isDefined,
      "the metadata-only source is created"
    )
    // a request that says nothing is legal-only too
    wipe("zzacq_ingested")
    val _ = handlers(oa = chain, app = allowing).ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")))
    assertEquals(asked.get, 0, "an ingest that does not ask for more is legal-only")
    // only a person's explicit choice asks it (the switch is on), and the watcher's path never does
    wipe("zzacq_ingested")
    val _ = handlers(oa = chain, app = allowing)
      .ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001"), legalOnly = Ingest.Request.LegalOnly(false)))
    assertEquals(asked.get, 1)
    wipe("zzacq_ingested")
    val legal = handlers(oa = chain, app = allowing)
      .ingestLegal(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")), ByBacklog)
    assertEquals(asked.get, 1)
    assert(
      legal.left.exists(_.problems.sentences.mkString("; ").contains("unpaywall: no copy — no open-access location")),
      legal.toString
    )
    wipe("zzacq_ingested")

  test("an agent's ingest is legal-only by default; asking for more is refused unless a person allowed it"):
    wipe("zzacq_ingested")
    import com.histmersion.plugin.{Access, Fetch, SourceFetcher, WorkKey}
    val asked = java.util.concurrent.atomic.AtomicInteger(0)
    val noCopy = Problem.NotFound.Copy(Problem.Raw("10.1038/zztest001"), Vector.empty)
    val unauthorised = new SourceFetcher:
      val access = Access.Unauthorised
      def fetch(w: WorkKey, c: Fetch.Context) = {
        asked.incrementAndGet(); Fetch.Miss(Fetch.Absence.NotHeld, noCopy)
      }
    val open = new SourceFetcher:
      val access = Access.Open
      def fetch(w: WorkKey, c: Fetch.Context) = Fetch.Miss(Fetch.Absence.NotHeld, noCopy)
    val chain = FetchChain(
      List(FetchChain.Step.Id("unpaywall") -> open, FetchChain.Step.Id("stub-unauthorised") -> unauthorised),
      allowUnauthorised = FetchChain.AllowUnauthorised(true)
    )
    val allowing =
      cfg.modify(_.acquire).setTo(cfg.acquire.modify(_.allowUnauthorised).setTo(AcquireConfig.AllowUnauthorised(true)))
    val doi = Ingest.Request.Id("10.1038/zztest001")
    // the default: the legal chain alone, whatever acquire.allow-unauthorised says
    val r = handlers(oa = chain, app = allowing)
      .ingestByAgent(Ingest.Request(doi))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(asked.get, 0, "an agent's ingest never asks a fetcher the config marks unauthorised")
    assert(r.steps.exists(_.step == Ingest.Step.Value("legal-only")), r.steps.toString)
    // asking for more is refused with the typed problem, before anything is fetched or stored
    wipe("zzacq_ingested")
    val refused =
      handlers(oa = chain, app = allowing).ingestByAgent(
        Ingest.Request(doi, legalOnly = Ingest.Request.LegalOnly(false))
      )
    assertEquals(
      refused.left.map(e => util.nonempty.NonEmptyVector.toVector(e.problems)),
      Left(Vector(Problem.Refused.Policy(Problem.Refused.Rule.LegalOnly)))
    )
    assertEquals(asked.get, 0)
    assert(SourceFixtures.load(ds, domain.library.Citekey("zzacq_ingested")).isEmpty, "nothing is stored")
    // a person's setting lets an agent that asks for it use them
    val personAllowed = allowing
      .modify(_.acquire)
      .setTo(
        allowing.acquire.modify(_.agentsMayUseUnauthorised).setTo(AcquireConfig.AgentsMayUseUnauthorised(true))
      )
    val _ = handlers(oa = chain, app = personAllowed)
      .ingestByAgent(Ingest.Request(doi, legalOnly = Ingest.Request.LegalOnly(false)))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(asked.get, 1)
    wipe("zzacq_ingested")

  test("no open-access copy: the source is still created and the manual drop-in is explained"):
    wipe("zzacq_ingested")
    val r = handlers(oa = NoPdf)
      .ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(r.pdf, None)
    assert(r.dropIn.exists(_.value.contains("--citekey zzacq_ingested")), r.dropIn.toString)
    assert(r.steps.exists(s => s.step == Ingest.Step.Value("fetch") && !s.ok.value))
    assert(
      SourceFixtures.load(ds, domain.library.Citekey("zzacq_ingested")).isDefined,
      "metadata-only source must still exist"
    )
    wipe("zzacq_ingested")

  test("re-ingesting an ENRICHED source updates metadata and never wipes the enrichment"):
    wipe("zzacq_ingested")
    // first ingest, then an agent enriches it
    val h = handlers(oa = SomePdf())
    val _ = h.ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")))
    val enriched = Source.Patch(
      notes = Some(Source.Notes("The compiled article.")),
      topics = Some(List(Source.Topics("indo-european"))),
      tags = Some(ListPatch(add = List(Write.ListItem("steppe")))),
      findings = Some(
        FindingsPatch(add =
          List(Finding.Input(Finding.Claim("a claim"), region = Some(Finding.Region("Pontic steppe"))))
        )
      )
    )
    SourceFixtures
      .patch(ds, domain.library.Citekey("zzacq_ingested"), enriched, cfg.limits.chunkChars)
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)

    val again =
      h.ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")))
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(!again.created.value, "an existing citekey must be updated, not recreated")
    val after =
      SourceFixtures.load(ds, domain.library.Citekey("zzacq_ingested")).getOrElse(fail("gone"))
    assertEquals(after.notes, Some("The compiled article."))
    assertEquals(after.topics, List("indo-european"))
    assertEquals(after.tags, List("steppe"))
    assertEquals(after.findings.map(_.claim), List("a claim"))
    wipe("zzacq_ingested")

  test(
    "re-ingesting a METADATA-ONLY source (no OA copy the first time) fetches and converts it, keeping its enrichment"
  ):
    wipe("zzacq_ingested")
    val _ = handlers(oa = NoPdf)
      .ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    SourceFixtures
      .patch(
        ds,
        domain.library.Citekey("zzacq_ingested"),
        Source.Patch(notes = Some(Source.Notes("Compiled."))),
        cfg.limits.chunkChars
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    val jobs = RecordingJobs(); val pdf = CountingPdf()
    val r = handlers(oa = pdf, jobs = jobs)
      .ingest(Ingest.Request(Ingest.Request.Id("10.16993/zzbay"), citekey = Some(Citekey("zzacq_ingested"))))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(pdf.calls.get, 1)
    assert(
      jobs.files.peek().exists(c => java.nio.file.Files.isRegularFile(java.nio.file.Path.of(c.where.value))),
      "the fetched file goes to the OCR job"
    )
    assert(
      r.steps.exists(s => s.step == Ingest.Step.Value("fetch") && s.ok.value && !s.detail.value.contains("skipped")),
      r.steps.toString
    )
    assertEquals(r.dropIn, None)
    assertEquals(
      SourceFixtures.load(ds, domain.library.Citekey("zzacq_ingested")).flatMap(_.notes),
      Some("Compiled.")
    )
    wipe("zzacq_ingested")

  test("re-ingesting a source that HAS its original fetches nothing and leaves the body; --refetch converts again"):
    wipe("zzacq_ingested")
    val _ = handlers(oa = NoPdf)
      .ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    SourceFixtures
      .patch(
        ds,
        domain.library.Citekey("zzacq_ingested"),
        Source.Patch(body = Some(Source.OcrMarkdown("# The converted text"))),
        cfg.limits.chunkChars
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    val jobs = RecordingJobs(); val pdf = CountingPdf()
    val r =
      handlers(oa = pdf, jobs = jobs)
        .ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001")))
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(pdf.calls.get, 0, "nothing fetched")
    assertEquals(jobs.files.toArray.toList, List(None), "no file, so no OCR job")
    val fetch = r.steps.find(_.step == Ingest.Step.Value("fetch")).get
    assert(
      fetch.ok.value && fetch.detail.value.contains("already has a converted body") && fetch.detail.value
        .contains("--refetch"),
      fetch.toString
    )
    assertEquals(r.dropIn, None, "no 'get the file by hand' advice for a source that has it")
    assert(AcquireMarkdown.ingest(r).contains("not fetched — the source already has its original"))
    assertEquals(
      SourceFixtures.load(ds, domain.library.Citekey("zzacq_ingested")).flatMap(_.ocrMarkdown),
      Some("# The converted text")
    )
    // asked for: fetched and handed to the job, which replaces the body
    val again = handlers(oa = pdf, jobs = jobs)
      .ingest(Ingest.Request(Ingest.Request.Id("10.1038/zztest001"), refetch = Ingest.Request.Refetch(true)))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(pdf.calls.get, 1)
    assert(jobs.files.toArray.toList.last.asInstanceOf[Option[FetchedCopy]].isDefined)
    assert(
      again.steps.exists(s =>
        s.step == Ingest.Step.Value("fetch") && s.detail.value.contains("replacing a converted body")
      ),
      again.steps.toString
    )
    wipe("zzacq_ingested")

  test("what original a source has: a body, else a stored file; nothing for a metadata-only or missing source"):
    assertEquals(
      AcquisitionControllers.originalOf(Some(Some(Source.OcrMarkdown("text"))), None),
      Some("a converted body")
    )
    assertEquals(
      AcquisitionControllers.originalOf(Some(Some(Source.OcrMarkdown("  "))), Some(AbSha)),
      Some("a stored original (sha256 abababababab…)")
    )
    assertEquals(AcquisitionControllers.originalOf(Some(None), None), None)
    assertEquals(AcquisitionControllers.originalOf(None, Some(AbSha)), None)

  // ── archive.org ─────────────────────────────────────────────────────────────────────────────

  test("a lending-only archive.org item is refused, and nothing is written"):
    val item = ArchiveItem(
      ArchiveItem.Identifier("blackathena"),
      title = Some(ArchiveItem.Title("Black Athena")),
      creator = List(ArchiveItem.Creator("Martin Bernal")),
      year = Some(Year(1987)),
      lendingOnly = ArchiveItem.LendingOnly(true),
      files = List(
        ArchiveFile(
          ArchiveFile.Name("blackathena.pdf"),
          Some(ArchiveFile.Format("Text PDF")),
          ArchiveFile.Megabytes(12.0)
        )
      )
    )
    val r = archiveService(item, handlers())
      .archiveImport(Caller.Local, ArchiveImportRequest(ArchiveImportRequest.Id("blackathena")))
    assert(r.isLeft)
    assert(r.left.exists(_.problems.sentences.mkString("; ").contains("lending-only")), r.toString)
    assert(SourceFixtures.load(ds, domain.library.Citekey("zzacqbernal1987_black")).isEmpty)

  test("a public archive.org item is imported with provenance archive_org"):
    wipe("zzacqcoon1939_races")
    val item = ArchiveItem(
      ArchiveItem.Identifier("racesofeurope"),
      title = Some(ArchiveItem.Title("The Races Of Europe")),
      creator = List(ArchiveItem.Creator("Carleton Zzacqcoon")),
      year = Some(Year(1939)),
      publisher = Some(ArchiveItem.Publisher("Macmillan")),
      files = List(
        ArchiveFile(
          ArchiveFile.Name("racesofeurope.pdf"),
          Some(ArchiveFile.Format("Text PDF")),
          ArchiveFile.Megabytes(40.0)
        ),
        ArchiveFile(
          ArchiveFile.Name("racesofeurope.epub"),
          Some(ArchiveFile.Format("EPUB")),
          ArchiveFile.Megabytes(3.0)
        )
      )
    )
    // the download is the acquisition chain's: here a fetcher over the same client, by the item's identifier
    val byIdentifier = new SourceFetcher:
      val access = Access.Open
      def fetch(work: WorkKey, ctx: Fetch.Context) =
        work.archive.fold(Fetch.Miss(Fetch.Absence.NotHeld, Problem.Unclassified(Legacy.Text("no identifier"))))(_ =>
          Fetch.Found(
            FetchedFile(
              plugin.FetchedFile.Source("archive_org"),
              None,
              FetchedFile.Bytes(("%PDF-1.4\n" + "y" * 20000).getBytes("UTF-8")),
              plugin.FetchedFile.Extension("pdf")
            )
          )
        )
    val chain = FetchChain(List(FetchChain.Step.Id("archive-org") -> byIdentifier))
    val r = archiveService(item, handlers(oa = chain))
      .archiveImport(Caller.Local, ArchiveImportRequest(ArchiveImportRequest.Id("racesofeurope")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(r.citekey, "zzacqcoon1939_races")
    assertEquals(
      SourceFixtures.load(ds, domain.library.Citekey("zzacqcoon1939_races")).flatMap(_.provenance),
      Some("archive_org")
    )
    wipe("zzacqcoon1939_races")

  // ── calibre gaps ────────────────────────────────────────────────────────────────────────────

  test("calibre gaps lists sources with no calibre_id and non-calibre provenance"):
    wipe("zzacq_gap", "zzacq_incalibre")
    SourceFixtures.upsert(
      ds,
      source("zzacq_gap").modify(_.provenance).setTo(Some(Source.Provenance("unpaywall"))),
      cfg.limits.chunkChars
    )
    SourceFixtures.upsert(
      ds,
      source("zzacq_incalibre")
        .modify(_.provenance)
        .setTo(Some(Source.Provenance("calibre")))
        .modify(_.calibreId)
        .setTo(Some(Source.CalibreId("1691"))),
      cfg.limits.chunkChars
    )
    val g = gaps().gaps(None)
    assert(g.gaps.exists(_.citekey == Citekey("zzacq_gap")))
    assert(!g.gaps.exists(_.citekey == Citekey("zzacq_incalibre")))
    // recording the push removes it from the gap list
    gaps()
      .pushed(CalibrePushRequest(Citekey("zzacq_gap"), Source.CalibreId("9999")))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assert(!gaps().gaps(None).gaps.exists(_.citekey == Citekey("zzacq_gap")))
    // … and the push metadata now names the entry, which is what `calibre update` rewrites
    assertEquals(
      gaps().pushMeta(domain.library.Citekey("zzacq_gap")).map(_.calibreId),
      Right(Some("9999"))
    )
    wipe("zzacq_gap", "zzacq_incalibre")

  test("calibre gaps say which originals the server stores — what `calibre sync` downloads for the push"):
    wipe("zzacq_stored", "zzacq_nofile", "zzacq_nohash")
    val spool = java.nio.file.Files.createTempDirectory("zz-spool-")
    val app =
      cfg.modify(_.converter).setTo(cfg.converter.modify(_.spoolDir).setTo(ConverterConfig.SpoolDir(spool.toString)))
    List("zzacq_stored", "zzacq_nofile", "zzacq_nohash").foreach(ck =>
      SourceFixtures.upsert(
        ds,
        source(ck).modify(_.provenance).setTo(Some(Source.Provenance("unpaywall"))),
        cfg.limits.chunkChars
      )
    )
    val (stored, gone) = ("a" * 64, "b" * 64)
    com.augustnagro.magnum.connect(ds):
      import com.augustnagro.magnum.*
      sql"UPDATE sources SET sha256 = $stored WHERE citekey = 'zzacq_stored'".update.run()
      sql"UPDATE sources SET sha256 = $gone WHERE citekey = 'zzacq_nofile'".update.run()
    java.nio.file.Files.writeString(spool.resolve(s"$stored.pdf"), "%PDF")
    val mine = Set("zzacq_stored", "zzacq_nofile", "zzacq_nohash").map(Citekey(_))
    val marks = gaps(app)
      .gaps(Some(CalibreGapControllers.Limit(1000)))
      .gaps
      .filter(g => mine(g.citekey))
      .map(g => g.citekey -> g.storedOriginal)
      .toMap
    assertEquals(marks, Map("zzacq_stored" -> Some(true), "zzacq_nofile" -> Some(false), "zzacq_nohash" -> Some(false)))
    assert(AcquireMarkdown.calibreGaps(gaps(app).gaps(Some(CalibreGapControllers.Limit(1000)))).contains("| stored |"))
    wipe("zzacq_stored", "zzacq_nofile", "zzacq_nohash")

  // ── error paths ─────────────────────────────────────────────────────────────────────────────

  test("a nonsense identifier is a readable error, not an exception"):
    val h = handlers()
    assert(
      h.assess(AssessRequest(AssessRequest.Id("this is not an id")))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("neither a DOI"))
    )
    assert(h.refs(RefsRequest(RefsRequest.Id("also not an id"))).isLeft)
    assert(h.ingest(Ingest.Request(Ingest.Request.Id(""))).isLeft)

  test("a DOI no upstream knows is refused rather than stored as an `anonnd` junk source"):
    // the metadata guard, the counterpart of the Calibre one: no title upstream and no citekey given
    val blind = new DiscoveryClient:
      def search(
          q: Search.Query,
          since: Option[com.histmersion.Year],
          sort: Option[DiscoveryClient.SearchSort],
          limit: DiscoveryClient.SearchLimit
      ) = Right(Nil)
      def meta(doi: domain.library.Doi) = Left(Problem.Unclassified(Legacy.Text(s"$doi: HTTP 404")))
      def references(doi: domain.library.Doi) = Right((DiscoveryClient.Backend("crossref"), Nil))
      def updates(doi: domain.library.Doi) = Right(Nil)
    val h = handlers(blind)
    val r = h.ingest(Ingest.Request(Ingest.Request.Id("10.1017/zzdoesnotexist")))
    assert(r.left.exists(_.problems.sentences.mkString("; ").contains("no upstream record")), r.toString)
    assert(
      SourceFixtures.load(ds, domain.library.Citekey("anonnd")).isEmpty,
      "no junk source may be created"
    )

    // …but an explicit citekey is a deliberate act, so that DOES create the source
    wipe("zzacq_manual")
    val forced = h
      .ingest(Ingest.Request(Ingest.Request.Id("10.1017/zzdoesnotexist"), citekey = Some(Citekey("zzacq_manual"))))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(forced.citekey, "zzacq_manual")
    assert(SourceFixtures.load(ds, domain.library.Citekey("zzacq_manual")).isDefined)
    wipe("zzacq_manual")
