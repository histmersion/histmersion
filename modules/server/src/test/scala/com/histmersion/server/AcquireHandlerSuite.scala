package com.histmersion.server

import com.histmersion.core.*
import com.histmersion.db.{AcquireRepo, Db, Migrate, QueueRepo, SourceRepo}
import com.histmersion.server.acquire.*

import java.nio.file.Path

/** The acquisition handlers against a real Postgres (`HISTMERSION_TEST_DB_URL`), with every upstream
  * replaced by a fake. What is exercised here is the part fixtures cannot reach: resolving references
  * to stored citekeys, writing typed edges, applying a verdict to `status`, and the ingest sequence —
  * including that a re-ingest does NOT wipe an enriched source. */
class AcquireHandlerSuite extends com.histmersion.db.DbSuite:

  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg =
    val base = AppConfig.load()
    base.copy(db = base.db.copy(url = testDb.get))
  private lazy val ds =
    val d = testPool(cfg.db); Migrate.run(d); d

  // ── fakes ───────────────────────────────────────────────────────────────────────────────────

  // NB: every citekey this suite touches starts with `zzacq_`, and `wipe` REFUSES anything else.
  // The suite runs against whatever HISTMERSION_TEST_DB_URL points at — which is routinely a copy of the
  // real corpus — so a test that deleted `haak2015_massive` would quietly destroy a real source.
  private val TestPrefix = "zzacq"

  private val target = WorkRef(
    doi = Some("10.1038/zztest001"), openalexId = Some("W1"), title = Some("Massive migration test"),
    year = Some(2015), authors = List("Wolfgang Haak"), venue = Some("Nature"),
    citedByCount = Some(900), citekey = Some("zzacq_ingested"))

  private class FakeDiscovery(
      work: WorkRef = target,
      refs: List[RefRow] = Nil,
      notices: List[UpdateNotice] = Nil,
      hits: List[WorkRef] = Nil
  ) extends DiscoveryClient:
    def search(q: String, since: Option[Int], sort: Option[String], limit: Int) = Right(hits)
    def meta(doi: String) = Right(work.copy(doi = Some(doi)))
    def references(doi: String) = Right(("crossref", refs))
    def updates(doi: String) = Right(notices)

  private class FakeCitations(rows: List[FollowupRow]) extends CitationsClient:
    def citing(work: WorkRef, since: Option[Int], limit: Int) = Right(("semantic-scholar", rows))

  private object NoPdf extends OaFetcher:
    def fetch(doi: Option[String], arxiv: Option[String]) = Left("no legal open-access PDF found")

  private class SomePdf(source: String = "unpaywall", licence: Option[String] = None) extends OaFetcher:
    def fetch(doi: Option[String], arxiv: Option[String]) =
      Right(FetchedFile(source, Some("https://example.org/x.pdf"),
        ("%PDF-1.4\n" + "x" * 2000).getBytes("UTF-8"), "pdf", licence))

  private object NoWiki extends WikiClient:
    def refs(url: String) = Left("not used here")

  private class FakeArchive(item: ArchiveItem) extends ArchiveClient:
    def search(q: String, rows: Int) = Right(List(item))
    def info(id: String) = Right(item)
    def download(id: String, file: String) =
      Right(FetchedFile("archive_org", None, ("%PDF-1.4\n" + "y" * 20000).getBytes("UTF-8"), "pdf"))

  private class FakeRw(text: String) extends RetractionWatchClient:
    def csv(): Either[String, String] = Right(text)

  private def handlers(
      discovery: DiscoveryClient = FakeDiscovery(),
      citations: CitationsClient = FakeCitations(Nil),
      oa: OaFetcher = NoPdf,
      archive: ArchiveClient = FakeArchive(ArchiveItem("zz")),
      rw: RetractionWatchClient = FakeRw(""),
      app: AppConfig = cfg,
      jobs: IngestJobs = SkeletonIngest(ds, cfg.limits.chunkChars)
  ) = AcquireHandlers(Deps(app, ds), discovery, citations, oa, NoWiki, archive, rw, jobs)

  /** The skeleton store, recording whether each ingest handed it a file to convert (the OCR job). */
  private final class RecordingJobs extends IngestJobs:
    private val skeleton = SkeletonIngest(ds, cfg.limits.chunkChars)
    val files = java.util.concurrent.ConcurrentLinkedQueue[Option[Path]]()
    def enqueue(pdfPath: Option[Path], citekey: String, meta: IngestMeta) =
      files.add(pdfPath); skeleton.enqueue(pdfPath, citekey, meta)

  /** A PDF fetcher that counts its calls. */
  private final class CountingPdf extends OaFetcher:
    val calls = java.util.concurrent.atomic.AtomicInteger(0)
    def fetch(doi: Option[String], arxiv: Option[String]) =
      calls.incrementAndGet(); SomePdf().fetch(doi, arxiv)

  // ── fixtures in the database ────────────────────────────────────────────────────────────────

  private def source(ck: String, doi: Option[String] = None, title: String = "T",
                     authors: List[String] = List("A B"), year: Option[Int] = Some(2000)) =
    Source(ck, title, authors, doi, year, None, SourceStatus.Current, Nil, Nil, Nil, None, None,
      None, None, None, None)

  private def wipe(cks: String*): Unit =
    import com.augustnagro.magnum.*
    cks.find(!_.startsWith(TestPrefix)).foreach(k =>
      fail(s"refusing to delete '$k': this suite may only touch ${TestPrefix}* citekeys"))
    transact(ds) {
      cks.foreach { ck =>
        sql"DELETE FROM edges WHERE src = $ck OR dst = $ck".update.run()
        sql"DELETE FROM sources WHERE citekey = $ck".update.run()
      }
    }

  // ── refs ────────────────────────────────────────────────────────────────────────────────────

  test("refs resolves by DOI, by exact citekey and by unique citekey prefix — and --apply writes edges"):
    wipe("zzacq_target", "zzacq_bydoi", "zzacqreich2018_who")
    SourceRepo.upsert(ds, source("zzacq_target", Some("10.1038/zztest001")), cfg.limits.chunkChars)
    SourceRepo.upsert(ds, source("zzacq_bydoi", Some("10.1038/zzref001")), cfg.limits.chunkChars)
    // a reference whose generated citekey (`zzacqreich2018_here`) shares its `<surname><year>` stem
    // with the stored `zzacqreich2018_who` — the prefix widening `resolveEdges` does for short targets
    SourceRepo.upsert(ds, source("zzacqreich2018_who", None, "Who We Are and How We Got Here",
      List("Someone Zzacqreich"), Some(2018)), cfg.limits.chunkChars)

    val rows = List(
      RefRow(title = Some("Something"), doi = Some("10.1038/zzref001")),
      RefRow(title = Some("Who We Are and How We Got Here"), authors = List("Someone Zzacqreich"), year = Some(2018)),
      RefRow(title = Some("Unknown work"), authors = List("Nobody Here"), year = Some(1904)))

    val h = handlers(FakeDiscovery(refs = rows))
    val r = h.refs(RefsRequest("zzacq_target", apply = true)).fold(e => fail(e.message), identity)
    assertEquals(r.count, 3)
    assertEquals(r.resolved, 2)
    assertEquals(r.edgesAdded, 2)
    assertEquals(r.references.flatMap(_.citekey).toSet, Set("zzacq_bydoi", "zzacqreich2018_who"))
    val out = SourceRepo.neighbors(ds, "zzacq_target").filter(_.direction == "out")
    assertEquals(out.map(_.citekey).toSet, Set("zzacq_bydoi", "zzacqreich2018_who"))
    assert(out.forall(_.kind == "references"))

  test("refs without --apply writes nothing"):
    wipe("zzacq_target", "zzacq_bydoi")
    SourceRepo.upsert(ds, source("zzacq_target", Some("10.1038/zztest001")), cfg.limits.chunkChars)
    SourceRepo.upsert(ds, source("zzacq_bydoi", Some("10.1038/zzref001")), cfg.limits.chunkChars)
    val h = handlers(FakeDiscovery(refs = List(RefRow(doi = Some("10.1038/zzref001")))))
    val r = h.refs(RefsRequest("zzacq_target")).fold(e => fail(e.message), identity)
    assertEquals(r.resolved, 1)
    assertEquals(r.edgesAdded, 0)
    assertEquals(SourceRepo.neighbors(ds, "zzacq_target").count(_.direction == "out"), 0)

  // ── assess ──────────────────────────────────────────────────────────────────────────────────

  test("a Retraction Watch hit yields verdict=retracted and --apply writes status=retracted"):
    wipe("zzacq_retr")
    SourceRepo.upsert(ds, source("zzacq_retr", Some("10.1177/zzretracted")), cfg.limits.chunkChars)
    AcquireRepo.replaceRetractionWatch(ds, List(
      Acquire.RetractionRow("10.1177/zzretracted", Some("+Duplication of Image"), Some("12/15/2021 0:00"),
        Some("Retraction"))))

    val h = handlers()
    val a = h.assess(AssessRequest("zzacq_retr", apply = true)).fold(e => fail(e.message), identity)
    assertEquals(a.verdict, Acquire.Verdicts.Retracted)
    assert(a.flagged)
    assertEquals(a.retractionWatch.head.date, Some("2021-12-15"))
    assertEquals(a.applied, Some("retracted"))
    assertEquals(SourceRepo.load(ds, "zzacq_retr").map(_.status), Some(SourceStatus.Retracted))
    // and the verdict is STORED, so a later lint does not need the network
    assertEquals(AcquireRepo.assessmentFor(ds, "zzacq_retr").map(_._1), Some("retracted"))

  test("a clean, well-cited paper is `current` and the mirror refresh replaces every row"):
    wipe("zzacq_ok")
    SourceRepo.upsert(ds, source("zzacq_ok", Some("10.1038/zzclean")), cfg.limits.chunkChars)
    val csv =
      "OriginalPaperDOI,RetractionNature,Reason,RetractionDate\n" +
        "10.1000/other,Retraction,\"+Fabrication, of data\",1/2/2020 0:00\n"
    val h = handlers(rw = FakeRw(csv))
    assertEquals(h.updateRetractions().map(_.rows), Right(1))
    val a = h.assess(AssessRequest("zzacq_ok")).fold(e => fail(e.message), identity)
    assertEquals(a.verdict, Acquire.Verdicts.Current)
    assertEquals(a.retractionWatch, Nil)
    assertEquals(a.applied, None) // no --apply
    assertEquals(SourceRepo.load(ds, "zzacq_ok").map(_.status), Some(SourceStatus.Current))

  // #699: assess --apply overwrote contested (with a challenges edge behind it) with current
  test("assess --apply keeps a status already set, unless --force-status; a retraction always applies"):
    wipe("zzacq_kept", "zzacq_retr2")
    SourceRepo.upsert(ds, source("zzacq_kept", Some("10.1038/zzkept")).copy(status = SourceStatus.Contested),
      cfg.limits.chunkChars)
    val h = handlers()
    val a = h.assess(AssessRequest("zzacq_kept", apply = true)).fold(e => fail(e.message), identity)
    assertEquals(a.verdict, Acquire.Verdicts.Current)
    assertEquals((a.applied, a.kept), (None, Some(SourceStatus.Contested)))
    assert(AcquireMarkdown.assessment(a).contains("Kept **contested** (set by hand)"))
    assertEquals(SourceRepo.load(ds, "zzacq_kept").map(_.status), Some(SourceStatus.Contested))

    val f = h.assess(AssessRequest("zzacq_kept", apply = true, forceStatus = true)).fold(e => fail(e.message), identity)
    assertEquals((f.applied, f.kept), (Some("current"), None))
    assertEquals(SourceRepo.load(ds, "zzacq_kept").map(_.status), Some(SourceStatus.Current))

    SourceRepo.upsert(ds, source("zzacq_retr2", Some("10.1177/zzretracted2")).copy(status = SourceStatus.Foundational),
      cfg.limits.chunkChars)
    AcquireRepo.replaceRetractionWatch(ds, List(
      Acquire.RetractionRow("10.1177/zzretracted2", Some("+Duplication"), Some("12/15/2021 0:00"), Some("Retraction"))))
    val r = h.assess(AssessRequest("zzacq_retr2", apply = true)).fold(e => fail(e.message), identity)
    assertEquals(r.applied, Some("retracted"))
    assertEquals(SourceRepo.load(ds, "zzacq_retr2").map(_.status), Some(SourceStatus.Retracted))

  // ── followups ───────────────────────────────────────────────────────────────────────────────

  test("followups classify citing papers and --apply adds one typed edge per stored citing source"):
    wipe("zzacq_target", "zzacq_critic")
    SourceRepo.upsert(ds, source("zzacq_target", Some("10.1038/zztest001")), cfg.limits.chunkChars)
    SourceRepo.upsert(ds, source("zzacq_critic", Some("10.1038/zzcritic")), cfg.limits.chunkChars)
    val rows = List(
      FollowupRow(title = Some("A critique"), year = Some(2021), doi = Some("10.1038/zzcritic"),
        contexts = List("These data contradict the steppe model"), isInfluential = true),
      FollowupRow(title = Some("Unknown citer"), year = Some(2022), doi = Some("10.9999/nope"),
        contexts = List("consistent with earlier work")))

    val h = handlers(citations = FakeCitations(rows))
    val r = h.followups(FollowupsRequest("zzacq_target", apply = true)).fold(e => fail(e.message), identity)
    assertEquals(r.count, 2)
    assertEquals(r.followups.head.proposedKind, Acquire.EdgeKinds.Refutes) // influential first
    assertEquals(r.followups.map(_.citekey), List(Some("zzacq_critic"), None))
    assertEquals(r.edgesAdded, 1)
    val incoming = SourceRepo.neighbors(ds, "zzacq_target").filter(_.direction == "in")
    assertEquals(incoming.map(n => (n.citekey, n.kind)), Vector(("zzacq_critic", "refutes")))

  // ── discover ────────────────────────────────────────────────────────────────────────────────

  test("discover marks what the corpus already has, and --not-in-db removes it"):
    wipe("zzacq_known")
    SourceRepo.upsert(ds, source("zzacq_known", Some("10.1038/zzknown")), cfg.limits.chunkChars)
    val hits = List(
      WorkRef(doi = Some("10.1038/zzknown"), title = Some("Known"), citekey = Some("x2000_known")),
      WorkRef(doi = Some("10.1038/zzbrandnew"), title = Some("New"), citekey = Some("y2001_new")))
    val h = handlers(FakeDiscovery(hits = hits))
    val all = h.discover("q", None, None, None, notInDb = false).fold(e => fail(e.message), identity)
    assertEquals(all.works.map(_.inDb), List(true, false))
    assertEquals(all.works.head.citekey, Some("zzacq_known")) // the STORED key, not the generated one
    val delta = h.discover("q", None, None, None, notInDb = true).fold(e => fail(e.message), identity)
    assertEquals(delta.works.map(_.doi), List(Some("10.1038/zzbrandnew")))

  // ── ingest ──────────────────────────────────────────────────────────────────────────────────

  test("ingest: metadata → PDF → source → reference edges → assessment"):
    // `zzacq_bydoi` is left behind by the `refs` tests above and carries the SAME DOI as the
    // `zzacq_ref` fixture below; with two sources on one DOI the reference resolves to whichever
    // Postgres returns first, which made this test fail about half the time. Wipe it too.
    wipe("zzacq_ingested", "zzacq_ref", "zzacq_bydoi")
    SourceRepo.upsert(ds, source("zzacq_ref", Some("10.1038/zzref001")), cfg.limits.chunkChars)
    val h = handlers(
      FakeDiscovery(refs = List(RefRow(doi = Some("10.1038/zzref001")))),
      oa = SomePdf())
    val r = h.ingest(IngestRequest("10.1038/zztest001")).fold(e => fail(e.message), identity)

    assertEquals(r.citekey, "zzacq_ingested")
    assert(r.created)
    assertEquals(r.pdf.map(_.source), Some("unpaywall"))
    assert(r.pdf.flatMap(_.spoolPath).exists(p => java.nio.file.Files.isRegularFile(Path.of(p))),
      "the fetched PDF must be spooled for the converter")
    assertEquals(r.edgesAdded, 1)
    assertEquals(r.dropIn, None)
    assertEquals(r.steps.map(_.step), List("metadata", "fetch", "references", "store", "assess"))
    assert(r.steps.forall(_.ok), r.steps.toString)

    val stored = SourceRepo.load(ds, "zzacq_ingested").getOrElse(fail("source was not created"))
    assertEquals(stored.title, "Massive migration test")
    assertEquals(stored.doi, Some("10.1038/zztest001"))
    assertEquals(stored.provenance, Some("unpaywall"))
    assertEquals(stored.edges.map(_.dst), List("zzacq_ref"))
    assertEquals(r.assessment.map(_.verdict), Some(Acquire.Verdicts.Current))
    // a freshly created source takes the verdict as its status
    assertEquals(stored.status, SourceStatus.Current)
    wipe("zzacq_ingested", "zzacq_ref")

  test("a fetched copy's licence becomes the source's, never over one already recorded (#2560)"):
    import com.augustnagro.magnum.*
    def license = transact(ds)(sql"SELECT license FROM sources WHERE citekey = 'zzacq_ingested'".query[Option[String]].run().headOption.flatten)
    wipe("zzacq_ingested")
    val r = handlers(oa = SomePdf("oabooks", Some(" CC-BY-4.0 "))).ingest(IngestRequest("10.1038/zztest001"))
      .fold(e => fail(e.message), identity)
    assert(r.steps.exists(s => s.step == "fetch" && s.detail.contains("licence CC-BY-4.0")), r.steps.toString)
    assert(r.steps.exists(s => s.step == "licence" && s.ok), r.steps.toString)
    assertEquals(license, Some("CC-BY-4.0"))
    // a re-fetch with another licence keeps the one recorded; a copy without one changes nothing
    val again = handlers(oa = SomePdf("oabooks", Some("CC-BY-NC-4.0")))
      .ingest(IngestRequest("10.1038/zztest001", refetch = true)).fold(e => fail(e.message), identity)
    assert(!again.steps.exists(_.step == "licence"), again.steps.toString)
    assertEquals(license, Some("CC-BY-4.0"))
    assert(!AcquireRepo.recordLicense(ds, "zzacq_ingested", "  "), "a blank licence is never written")
    wipe("zzacq_ingested")

  test("an open-access work no script may fetch goes to the acquisition list once, as a browser download (#1581)"):
    import com.augustnagro.magnum.*
    def items = QueueRepo.acquireNaming(ds, "10.1038/zztest001").filter(_.status == "open")
    def dropItems(): Unit = transact(ds)(items.foreach(i => sql"DELETE FROM queue_items WHERE id = ${i.id}".update.run()))
    wipe("zzacq_ingested"); dropItems()
    val gold = FakeDiscovery(target.copy(isOa = true, oaStatus = Some("gold"), oaUrl = Some("https://press.example.org/a/1")))
    val r = handlers(gold, oa = NoPdf).ingest(IngestRequest("10.1038/zztest001")).fold(e => fail(e.message), identity)
    assertEquals(items.size, 1, items.toString)
    assertEquals(items.head.topic, Some(Acquire.BrowserDownloadTopic))
    assert(items.head.text.contains("download it in the browser") && items.head.text.contains("--citekey zzacq_ingested"), items.head.text)
    assert(r.steps.exists(s => s.step == "acquire" && s.detail.contains(s"#${items.head.id}")), r.steps.toString)
    // again: the item that names it is reported, no second one
    val _ = handlers(gold, oa = NoPdf).ingest(IngestRequest("10.1038/zztest001")).fold(e => fail(e.message), identity)
    assertEquals(items.size, 1)
    // a closed work, or a watcher's legal-only ingest, adds none
    dropItems(); wipe("zzacq_ingested")
    val _ = handlers(oa = NoPdf).ingest(IngestRequest("10.1038/zztest001"))
    val _ = handlers(gold, oa = NoPdf).ingestLegal(IngestRequest("10.1038/zztest001"))
    assertEquals(items.size, 0)
    dropItems(); wipe("zzacq_ingested")

  test("#1581: a gold open-access work on a publisher that refuses scripts goes to the acquisition list, from a watcher's legal path too"):
    import com.augustnagro.magnum.*
    def items = QueueRepo.acquireNaming(ds, "10.1038/zztest001")
    def dropItems(): Unit = transact(ds)(items.foreach(i => sql"DELETE FROM queue_items WHERE id = ${i.id}".update.run()))
    wipe("zzacq_ingested"); dropItems()
    val gold = target.copy(isOa = true, oaStatus = Some("gold"), oaLicence = Some("cc-by"),
      oaUrl = Some("https://www.mdpi.com/zz/1/pdf"), oaLocations = List("https://www.mdpi.com/zz/1"))
    // the watcher's legal-only path: no source, but the item, with the location, the DOI, the licence and the command
    val legal = handlers(FakeDiscovery(gold), oa = NoPdf).ingestLegal(IngestRequest("10.1038/zztest001"))
    assert(legal.left.exists(e => IngestBacklog.isNoLegalCopy(e.message)), legal.toString)
    assertEquals(items.map(_.topic), Vector(Some(Acquire.BrowserDownloadTopic)))
    val text = items.head.text
    assert(List("https://doi.org/10.1038/zztest001", "gold open access, licence cc-by", "at https://www.mdpi.com/zz/1",
      "mdpi.com refuses downloads by scripts", "histmersion ingest <file> --citekey zzacq_ingested").forall(text.contains), text)
    assert(SourceRepo.load(ds, "zzacq_ingested").isEmpty, "the legal path creates no metadata-only source")
    // a person's ingest of the same work: the item is reported, not doubled
    val r = handlers(FakeDiscovery(gold), oa = NoPdf).ingest(IngestRequest("10.1038/zztest001")).fold(e => fail(e.message), identity)
    assertEquals(items.size, 1)
    assert(r.steps.exists(s => s.step == "acquire" && s.detail.contains(s"#${items.head.id} (already listed)")), r.steps.toString)
    // hybrid on the same host, or gold on a host that does not refuse scripts: the watcher adds nothing
    dropItems(); wipe("zzacq_ingested")
    val _ = handlers(FakeDiscovery(gold.copy(oaStatus = Some("hybrid"))), oa = NoPdf).ingestLegal(IngestRequest("10.1038/zztest001"))
    val _ = handlers(FakeDiscovery(gold.copy(oaUrl = Some("https://zenodo.org/records/1"), oaLocations = Nil)), oa = NoPdf)
      .ingestLegal(IngestRequest("10.1038/zztest001"))
    assertEquals(items.size, 0)
    // a copy found: nothing to download by hand
    val _ = handlers(FakeDiscovery(gold), oa = SomePdf()).ingest(IngestRequest("10.1038/zztest001"))
    assertEquals(items.size, 0)
    dropItems(); wipe("zzacq_ingested")

  test("no copy: the ingest report lists what each fetcher of the chain answered"):
    wipe("zzacq_ingested")
    import com.histmersion.plugin.{Access, Fetch, FetchContext, SourceFetcher, WorkKey}
    def answering(a: Fetch): SourceFetcher = new SourceFetcher:
      val access = Access.Open
      def fetch(w: WorkKey, c: FetchContext) = a
    val chain = FetchChain(List("unpaywall" -> answering(Fetch.Miss("no open-access location")),
      "oapen" -> answering(Fetch.Refused("20.500.12657/23223: bot challenge"))))
    val r = handlers(oa = chain).ingest(IngestRequest("10.1038/zztest001")).fold(e => fail(e.message), identity)
    assertEquals(r.steps.find(_.step == "chain").map(_.detail),
      Some("unpaywall: no copy — no open-access location; oapen: refused — 20.500.12657/23223: bot challenge"))
    assert(r.steps.exists(s => s.step == "fetch" && s.detail.contains("oapen: 20.500.12657/23223")), r.steps.toString)
    wipe("zzacq_ingested")

  test("no open-access copy: the source is still created and the manual drop-in is explained"):
    wipe("zzacq_ingested")
    val r = handlers(oa = NoPdf).ingest(IngestRequest("10.1038/zztest001"))
      .fold(e => fail(e.message), identity)
    assertEquals(r.pdf, None)
    assert(r.dropIn.exists(_.contains("--citekey zzacq_ingested")), r.dropIn.toString)
    assert(r.steps.exists(s => s.step == "fetch" && !s.ok))
    assert(SourceRepo.load(ds, "zzacq_ingested").isDefined, "metadata-only source must still exist")
    wipe("zzacq_ingested")

  test("re-ingesting an ENRICHED source updates metadata and never wipes the enrichment"):
    wipe("zzacq_ingested")
    // first ingest, then an agent enriches it
    val h = handlers(oa = SomePdf())
    val _ = h.ingest(IngestRequest("10.1038/zztest001"))
    val enriched = SourcePatch(
      notes = Some("The compiled article."),
      topics = Some(List("indo-european")),
      tags = Some(ListPatch(add = List("steppe"))),
      findings = Some(FindingsPatch(add = List(FindingInput("a claim", region = Some("Pontic steppe"))))))
    SourceRepo.patch(ds, "zzacq_ingested", enriched, cfg.limits.chunkChars)
      .fold(e => fail(e.message), identity)

    val again = h.ingest(IngestRequest("10.1038/zztest001")).fold(e => fail(e.message), identity)
    assert(!again.created, "an existing citekey must be updated, not recreated")
    val after = SourceRepo.load(ds, "zzacq_ingested").getOrElse(fail("gone"))
    assertEquals(after.notes, Some("The compiled article."))
    assertEquals(after.topics, List("indo-european"))
    assertEquals(after.tags, List("steppe"))
    assertEquals(after.findings.map(_.claim), List("a claim"))
    wipe("zzacq_ingested")

  test("re-ingesting a METADATA-ONLY source (no OA copy the first time) fetches and converts it, keeping its enrichment"):
    wipe("zzacq_ingested")
    val _ = handlers(oa = NoPdf).ingest(IngestRequest("10.1038/zztest001")).fold(e => fail(e.message), identity)
    SourceRepo.patch(ds, "zzacq_ingested", SourcePatch(notes = Some("Compiled.")), cfg.limits.chunkChars)
      .fold(e => fail(e.message), identity)
    val jobs = RecordingJobs(); val pdf = CountingPdf()
    val r = handlers(oa = pdf, jobs = jobs)
      .ingest(IngestRequest("10.16993/zzbay", citekey = Some("zzacq_ingested"))).fold(e => fail(e.message), identity)
    assertEquals(pdf.calls.get, 1)
    assert(jobs.files.peek().exists(java.nio.file.Files.isRegularFile(_)), "the fetched file goes to the OCR job")
    assert(r.steps.exists(s => s.step == "fetch" && s.ok && !s.detail.contains("skipped")), r.steps.toString)
    assertEquals(r.dropIn, None)
    assertEquals(SourceRepo.load(ds, "zzacq_ingested").flatMap(_.notes), Some("Compiled."))
    wipe("zzacq_ingested")

  test("re-ingesting a source that HAS its original fetches nothing and leaves the body; --refetch converts again"):
    wipe("zzacq_ingested")
    val _ = handlers(oa = NoPdf).ingest(IngestRequest("10.1038/zztest001")).fold(e => fail(e.message), identity)
    SourceRepo.patch(ds, "zzacq_ingested", SourcePatch(body = Some("# The converted text")), cfg.limits.chunkChars)
      .fold(e => fail(e.message), identity)
    val jobs = RecordingJobs(); val pdf = CountingPdf()
    val r = handlers(oa = pdf, jobs = jobs).ingest(IngestRequest("10.1038/zztest001")).fold(e => fail(e.message), identity)
    assertEquals(pdf.calls.get, 0, "nothing fetched")
    assertEquals(jobs.files.toArray.toList, List(None), "no file, so no OCR job")
    val fetch = r.steps.find(_.step == "fetch").get
    assert(fetch.ok && fetch.detail.contains("already has a converted body") && fetch.detail.contains("--refetch"), fetch.toString)
    assertEquals(r.dropIn, None, "no 'get the file by hand' advice for a source that has it")
    assert(AcquireMarkdown.ingest(r).contains("not fetched — the source already has its original"))
    assertEquals(SourceRepo.load(ds, "zzacq_ingested").flatMap(_.ocrMarkdown), Some("# The converted text"))
    // asked for: fetched and handed to the job, which replaces the body
    val again = handlers(oa = pdf, jobs = jobs).ingest(IngestRequest("10.1038/zztest001", refetch = true))
      .fold(e => fail(e.message), identity)
    assertEquals(pdf.calls.get, 1)
    assert(jobs.files.toArray.toList.last.asInstanceOf[Option[Path]].isDefined)
    assert(again.steps.exists(s => s.step == "fetch" && s.detail.contains("replacing a converted body")), again.steps.toString)
    wipe("zzacq_ingested")

  test("what original a source has: a body, else a stored file; nothing for a metadata-only or missing source"):
    assertEquals(AcquireHandlers.originalOf(Some(Some("text")), None), Some("a converted body"))
    assertEquals(AcquireHandlers.originalOf(Some(Some("  ")), Some("ab" * 32)), Some("a stored original (sha256 abababababab…)"))
    assertEquals(AcquireHandlers.originalOf(Some(None), None), None)
    assertEquals(AcquireHandlers.originalOf(None, Some("ab" * 32)), None)

  // ── archive.org ─────────────────────────────────────────────────────────────────────────────

  test("a lending-only archive.org item is refused, and nothing is written"):
    val item = ArchiveItem("blackathena", title = Some("Black Athena"), creator = List("Martin Bernal"),
      year = Some(1987), lendingOnly = true,
      files = List(ArchiveFile("blackathena.pdf", Some("Text PDF"), 12.0)))
    val r = handlers(archive = FakeArchive(item)).archiveImport(ArchiveImportRequest("blackathena"))
    assert(r.isLeft)
    assert(r.left.exists(_.message.contains("lending-only")), r.toString)
    assert(SourceRepo.load(ds, "zzacqbernal1987_black").isEmpty)

  test("a public archive.org item is imported with provenance archive_org"):
    wipe("zzacqcoon1939_races")
    val item = ArchiveItem("racesofeurope", title = Some("The Races Of Europe"),
      creator = List("Carleton Zzacqcoon"), year = Some(1939), publisher = Some("Macmillan"),
      files = List(ArchiveFile("racesofeurope.pdf", Some("Text PDF"), 40.0),
        ArchiveFile("racesofeurope.epub", Some("EPUB"), 3.0)))
    // the download is the acquisition chain's archive-org fetcher, over the same client
    val archive = FakeArchive(item)
    val chain   = FetchChain(List("archive-org" -> fetchers.ArchiveOrgFetcher(() => archive)))
    val r = handlers(archive = archive, oa = chain).archiveImport(ArchiveImportRequest("racesofeurope"))
      .fold(e => fail(e.message), identity)
    assertEquals(r.citekey, "zzacqcoon1939_races")
    assertEquals(SourceRepo.load(ds, "zzacqcoon1939_races").flatMap(_.provenance), Some("archive_org"))
    wipe("zzacqcoon1939_races")

  // ── calibre gaps ────────────────────────────────────────────────────────────────────────────

  test("calibre gaps lists sources with no calibre_id and non-calibre provenance"):
    wipe("zzacq_gap", "zzacq_incalibre")
    SourceRepo.upsert(ds, source("zzacq_gap").copy(provenance = Some("unpaywall")), cfg.limits.chunkChars)
    SourceRepo.upsert(ds, source("zzacq_incalibre").copy(provenance = Some("calibre"),
      calibreId = Some("1691")), cfg.limits.chunkChars)
    val g = handlers().calibreGaps(None)
    assert(g.gaps.exists(_.citekey == "zzacq_gap"))
    assert(!g.gaps.exists(_.citekey == "zzacq_incalibre"))
    // recording the push removes it from the gap list
    handlers().calibrePushed(CalibrePushRequest("zzacq_gap", "9999")).fold(e => fail(e.message), identity)
    assert(!handlers().calibreGaps(None).gaps.exists(_.citekey == "zzacq_gap"))
    // … and the push metadata now names the entry, which is what `calibre update` rewrites
    assertEquals(handlers().calibreMeta("zzacq_gap").map(_.calibreId), Right(Some("9999")))
    wipe("zzacq_gap", "zzacq_incalibre")

  test("calibre gaps say which originals the server stores — what `calibre sync` downloads for the push"):
    wipe("zzacq_stored", "zzacq_nofile", "zzacq_nohash")
    val spool = java.nio.file.Files.createTempDirectory("zz-spool-")
    val app = cfg.copy(converter = cfg.converter.copy(spoolDir = spool.toString))
    List("zzacq_stored", "zzacq_nofile", "zzacq_nohash").foreach(ck =>
      SourceRepo.upsert(ds, source(ck).copy(provenance = Some("unpaywall")), cfg.limits.chunkChars))
    val (stored, gone) = ("a" * 64, "b" * 64)
    com.augustnagro.magnum.connect(ds):
      import com.augustnagro.magnum.*
      sql"UPDATE sources SET sha256 = $stored WHERE citekey = 'zzacq_stored'".update.run()
      sql"UPDATE sources SET sha256 = $gone WHERE citekey = 'zzacq_nofile'".update.run()
    java.nio.file.Files.writeString(spool.resolve(s"$stored.pdf"), "%PDF")
    val mine = Set("zzacq_stored", "zzacq_nofile", "zzacq_nohash")
    val marks = handlers(app = app).calibreGaps(Some(1000)).gaps.filter(g => mine(g.citekey))
      .map(g => g.citekey -> g.storedOriginal).toMap
    assertEquals(marks, Map("zzacq_stored" -> Some(true), "zzacq_nofile" -> Some(false), "zzacq_nohash" -> Some(false)))
    assert(AcquireMarkdown.calibreGaps(handlers(app = app).calibreGaps(Some(1000))).contains("| stored |"))
    wipe("zzacq_stored", "zzacq_nofile", "zzacq_nohash")

  // ── error paths ─────────────────────────────────────────────────────────────────────────────

  test("a nonsense identifier is a readable error, not an exception"):
    val h = handlers()
    assert(h.assess(AssessRequest("this is not an id")).left.exists(_.message.contains("neither a DOI")))
    assert(h.refs(RefsRequest("also not an id")).isLeft)
    assert(h.ingest(IngestRequest("")).isLeft)

  test("a DOI no upstream knows is refused rather than stored as an `anonnd` junk source"):
    // the metadata guard, the counterpart of the Calibre one: no title upstream and no citekey given
    val blind = new DiscoveryClient:
      def search(q: String, since: Option[Int], sort: Option[String], limit: Int) = Right(Nil)
      def meta(doi: String) = Left(s"$doi: HTTP 404")
      def references(doi: String) = Right(("crossref", Nil))
      def updates(doi: String) = Right(Nil)
    val h = handlers(blind)
    val r = h.ingest(IngestRequest("10.1017/zzdoesnotexist"))
    assert(r.left.exists(_.message.contains("no upstream record")), r.toString)
    assert(SourceRepo.load(ds, "anonnd").isEmpty, "no junk source may be created")

    // …but an explicit citekey is a deliberate act, so that DOES create the source
    wipe("zzacq_manual")
    val forced = h.ingest(IngestRequest("10.1017/zzdoesnotexist", citekey = Some("zzacq_manual")))
      .fold(e => fail(e.message), identity)
    assertEquals(forced.citekey, "zzacq_manual")
    assert(SourceRepo.load(ds, "zzacq_manual").isDefined)
    wipe("zzacq_manual")
