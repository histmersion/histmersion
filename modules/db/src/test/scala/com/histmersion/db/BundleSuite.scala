package com.histmersion.db

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.worklists.QueueRows

import com.histmersion.util.database.Transactor

import com.histmersion.domain.research.articles.ArticleFixtures

import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.domain.media.assets.AssetFixtures
import com.histmersion.domain.media.links.AssetLinkFixtures
import com.histmersion.domain.media.assets.NewFigure

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.api.{GeoPoint, TimeSpan}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Queue.{Kind, QueueAdd, QueuePatch}

import com.augustnagro.magnum.*
import com.histmersion.util.data.exchange.TestExchange
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.UUID
import javax.sql.DataSource
import com.histmersion.api.library.Confidence
import io.scalaland.chimney.dsl.*
import com.histmersion.util.data.exchange.{BundleFormat, BundleReader, BundleTar}
import com.histmersion.util.data.exchange.BundleExport
import com.histmersion.domain.automation.goals.GoalRepo
import com.histmersion.util.data.exchange.{BundleImport, BundleRepo}
import com.histmersion.util.database.Sql
import com.histmersion.api.catalogue.{EventDating, EventEdge, EventParticipant, EventPut, Period, Place}
import com.histmersion.api.exchange.{Bundle, MergeResolution, MergeResolutions}
import com.histmersion.api.library.{Edge, Finding, Source}
import com.histmersion.api.media.AssetPatch
import com.histmersion.api.research.Article
import com.histmersion.api.exchange.MergeIssue as Issue
import com.histmersion.api.automation.Goal
import com.histmersion.api.catalogue.Attestation
import com.histmersion.api.media.AssetLink
import com.histmersion.util.database.given

/** The portable bundle against real Postgres (docs/research-merge-import.md, milestones M0–M2): identity and tombstones
  * (M0), a deterministic export (M1), and an import without matching — into an empty instance and back into its own —
  * that round-trips byte for byte (M2).
  *
  * Every test works in databases of its OWN (`histmersion_bundle_*_test`, created from the test cluster and dropped
  * afterwards), because an export reads the whole database and an "empty instance" must really be empty. Skipped
  * without HISTMERSION_TEST_DB_URL.
  */
class BundleSuite extends com.histmersion.db.DbSuite with Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val cfg = AppConfig.load()
  private lazy val merge = cfg.merge.transformInto[MergeSettings]
  private lazy val admin = testPool(cfg.db.modify(_.url).setTo(testDb.get))
  private val tmp = Files.createTempDirectory("histmersion-bundle-suite")

  // ── fresh databases ────────────────────────────────────────────────────────────────────────

  private def exec(ds: DataSource, stmt: String): Unit =
    val c = ds.getConnection
    try {
      val st = c.createStatement();
      try { val _ = st.execute(stmt) }
      finally st.close()
    } finally c.close()

  /** A new, migrated, EMPTY database — `n` makes the name unique, so no pool ever outlives its DB. */
  private def fresh(n: String): DataSource =
    val ds = scratch(n)
    // the reference data both sides share (an event points into it)
    val _ = PlaceFixtures.upsertPlaces(
      ds,
      List(Place.Input(Place(com.histmersion.api.catalogue.Place.Id("zzb_place"), com.histmersion.api.catalogue.Place.Label("Hattusa"), com.histmersion.api.catalogue.Place.KindValue("site"), Some("""{"type":"Point","coordinates":[34.61,40.02]}"""))))
    )
    ds

  // every database a test makes: a copy of one migrated once (#3765)
  private lazy val scratch = ScratchDatabases("histmersion_bundle", cfg.db.modify(_.url).setTo(testDb.get))

  override def afterSuite(): Unit =
    if testDb.isDefined then scratch.dropAll()

  private def spool(n: String): Path = Files.createDirectories(tmp.resolve(n))

  // ── the crafted corpus ────────────────────────────────────────────────────────────────────

  private val A1 = "zzb2001_alpha"
  private val B0 = "zzb2025_beta" // renamed to B1 below: the alias case
  private val B1 = "zzb1999_beta"
  private val G1 = "zzb2003_gamma"
  private val Calibre = "zzcalibre4242"
  private val OriginalBytes = "%PDF-1.4 the stored original of alpha\n".getBytes(UTF_8)
  private val Png = Array[Byte](
    0x89.toByte,
    'P',
    'N',
    'G',
    13,
    10,
    26,
    10,
    0,
    0,
    0,
    13,
    'I',
    'H',
    'D',
    'R',
    0,
    0,
    0,
    2,
    0,
    0,
    0,
    3,
    8,
    2,
    0,
    0,
    0,
    1,
    2,
    3
  )

  private def source(
      ck: String,
      body: Option[String],
      findings: Int,
      edges: List[Edge] = Nil,
      calibre: Option[String] = None
  ) = Source(
    citekey = com.histmersion.api.library.Citekey(ck),
    title = com.histmersion.api.library.Title(s"Title of $ck — Ünïcödé"),
    authors = (List("Güterbock, Hans G.", "Jane Harrison")).map(com.histmersion.api.library.AuthorName.apply),
    doi = (Some(s"10.1234/$ck")).map(com.histmersion.api.library.Doi.apply),
    year = (Some(2001)).map(com.histmersion.api.catalogue.Year.apply),
    venue = (Some("JNES")).map(com.histmersion.api.library.Source.Venue.apply),
    status = com.histmersion.api.library.Source.Status.Current,
    topics = List(com.histmersion.api.library.Source.Topics("hittites")),
    regions = List(com.histmersion.api.library.Source.Regions("Anatolia")),
    tags = List(com.histmersion.api.library.Source.Tags("bronze-age"), com.histmersion.api.library.Source.Tags("z")),
    period = Some(Period(com.histmersion.api.catalogue.Year(-1650), com.histmersion.api.catalogue.Year(-1180))),
    summary = Some(s"summary of $ck"),
    citedByCount = Some(com.histmersion.api.library.Source.CitedByCount(12)),
    influentialCitations = None,
    provenance = Some(com.histmersion.api.library.Source.Provenance("oa")),
    calibreId = calibre,
    ocrMarkdown = body,
    findings = (0 until findings).toList.map(i =>
      Finding(
        s"$ck#$i",
        com.histmersion.api.library.Citekey(ck),
        s"claim $i of $ck",
        Some(com.histmersion.api.library.Finding.Region("Anatolia")),
        Option.when(i % 2 == 0)(GeoPoint(34.615 + i * 0.000000000000123, 40.019)),
        Some(Period(-1300 - i, com.histmersion.api.catalogue.Year(-1200))),
        Some(com.histmersion.api.library.Finding.PeriodName("LBA")),
        List(com.histmersion.api.library.Finding.Tags("t$i")),
        Some(Confidence.High),
        Option.when(i == 0)(com.histmersion.api.library.Finding.Significance("primary_topic"))
      )
    ),
    edges = edges,
    notes = Some(s"notes on $ck with [[zzb_topic]]")
  )

  private def put(ds: DataSource, s: Source): Unit = SourceFixtures.upsert(ds, s, cfg.limits.chunkChars)

  /** A corpus with one of everything a bundle carries, and every tricky shape: unicode, a rename (alias), an unresolved
    * edge, an article with versions, a deleted article and finding and queue item (tombstones), assets with geometry, a
    * link and an attestation, events with a parent, an edge, datings and participants, goals with a parent, an
    * assessment, a stored original, and a Calibre id that must NOT travel.
    */
  private def populate(ds: DataSource, originals: Path): Unit =
    put(ds, source(B0, None, 1))
    put(
      ds,
      source(
        A1,
        Some("# Alpha\n\nThe body of alpha, with Ünïcödé and a NUL-free tail.\n\n" + ("x" * 9000)),
        4,
        List(Edge(com.histmersion.api.library.Citekey(A1), com.histmersion.api.library.Citekey(B0), com.histmersion.api.library.Edge.Kind("references")), Edge(com.histmersion.api.library.Citekey(A1), com.histmersion.api.library.Citekey("zzbmissing2020_x"), com.histmersion.api.library.Edge.Kind("challenges"))),
        Some(Calibre)
      )
    )
    put(ds, source(G1, Some("# Gamma\n\nshort"), 0, List(Edge(com.histmersion.api.library.Citekey(G1), com.histmersion.api.library.Citekey(A1), com.histmersion.api.library.Edge.Kind("superseded_by")))))
    val _ = SourceFixtures.rename(ds, B0, B1)
    // a finding dropped by a later write leaves a tombstone
    SourceFixtures.load(ds, A1).foreach(s => put(ds, s.modify(_.findings).setTo(s.findings.filterNot(_.id == s"$A1#3"))))
    val _ =
      com.histmersion.domain.library.acquisition.AcquisitionFixtures.putAssessment(
        ds,
        com.histmersion.domain.library.acquisition
          .AssessmentRecord(A1, com.histmersion.domain.library.acquisition.AssessmentRecord.Verdict("current"), com.histmersion.domain.library.acquisition.AssessmentRecord.Retracted(false), """{"notices":[{"kind":"x","n":1}]}""", Some(com.histmersion.domain.library.acquisition.AssessmentRecord.CitedBy(12)), Some(com.histmersion.domain.library.acquisition.AssessmentRecord.Influential(3)))
      )
    val _ = AssetFixtures.add(
      ds,
      A1,
      List(
        NewFigure(com.histmersion.domain.media.assets.NewFigure.Name("fig1.png"), com.histmersion.domain.media.assets.NewFigure.Mime("image/png"), Png, None, None),
        NewFigure(com.histmersion.domain.media.assets.NewFigure.Name("fig2.png"), com.histmersion.domain.media.assets.NewFigure.Mime("image/png"), Png :+ 7.toByte, Some(com.histmersion.domain.media.assets.NewFigure.Width(2)), Some(com.histmersion.domain.media.assets.NewFigure.Height(3)))
      )
    )
    val _ = AssetFixtures.patch(
      ds,
      A1,
      "fig1.png",
      AssetPatch(
        caption = Some(com.histmersion.api.media.MediaItem.Caption("a seal")),
        lat = Some(40.02),
        lon = Some(34.61),
        periodStart = Some(com.histmersion.api.catalogue.Year(-1300)),
        periodEnd = Some(com.histmersion.api.catalogue.Year(-1250))
      )
    )
    val _ =
      AssetLinkFixtures.create(
        ds,
        AssetLink.Put(A1, com.histmersion.api.exchange.Bundle.Asset.FileName("fig1.png"), com.histmersion.api.media.AssetRelation.Wire("depicts"), com.histmersion.api.media.MediaTarget.TargetType("finding"), s"$A1#0", xywh = Some(com.histmersion.api.media.AssetLink.Xywh("0,0,1,1")))
      )
    val _ = AttestationRows.create(
      ds,
      Attestation.Put(
        A1,
        com.histmersion.api.catalogue.Attestation.TargetType("finding"),
        s"$A1#1",
        quote = Some(com.histmersion.api.catalogue.Attestation.Quote("The body")),
        prefix = Some(com.histmersion.api.catalogue.Attestation.Prefix("# Alpha\n\n")),
        suffix = Some(com.histmersion.api.catalogue.Attestation.Suffix(" of alpha")),
        charStart = Some(com.histmersion.api.catalogue.Attestation.CharStart(9)),
        charEnd = Some(com.histmersion.api.catalogue.Attestation.CharEnd(17)),
        page = Some("42")
      )
    )
    val sha = BundleFormat.sha256Hex(OriginalBytes)
    SourceFixtures.setSha256(ds, A1, sha)
    Files.write(Files.createDirectories(originals).resolve(s"$sha.pdf"), OriginalBytes)
    val _ =
      ArticleFixtures.put(ds, Article(com.histmersion.api.research.Article.Slug("zzb_topic"), com.histmersion.api.research.Article.Title("Topic"), com.histmersion.api.research.Article.KindValue("concept"), s"First draft citing [[$A1]].", Some(com.histmersion.api.research.Article.SummaryText("v1"))))
    val _ = ArticleFixtures.put(
      ds,
      Article(
        com.histmersion.api.research.Article.Slug("zzb_topic"),
        com.histmersion.api.research.Article.Title("Topic"),
        com.histmersion.api.research.Article.KindValue("concept"),
        s"Second draft citing [[$A1]] and [[$B1]].",
        Some(com.histmersion.api.research.Article.SummaryText("v2")),
        List(com.histmersion.api.research.Article.Tags("t")),
        List(com.histmersion.api.research.Article.Regions("Anatolia")),
        Some(Period(com.histmersion.api.catalogue.Year(-1500), com.histmersion.api.catalogue.Year(-1100)))
      )
    )
    val _ = ArticleFixtures.put(ds, Article(com.histmersion.api.research.Article.Slug("zzb_gone"), com.histmersion.api.research.Article.Title("Gone"), com.histmersion.api.research.Article.KindValue("report"), com.histmersion.api.research.Article.Body("to be deleted")))
    val _ = ArticleFixtures.put(ds, Article(com.histmersion.api.research.Article.Slug("zzb_gone"), com.histmersion.api.research.Article.Title("Gone"), com.histmersion.api.research.Article.KindValue("report"), com.histmersion.api.research.Article.Body("to be deleted, v2")))
    val _ = ArticleFixtures.delete(ds, "zzb_gone")
    EventFixtures.put(
      ds,
      "zzb_e1",
      EventPut(
        com.histmersion.api.catalogue.EventSummary.Label("Siege"),
        com.histmersion.api.catalogue.EventSummary.Kind("battle"),
        TimeSpan(Some(com.histmersion.api.catalogue.Year(-1200)), Some(com.histmersion.api.catalogue.Year(-1195)), Some(com.histmersion.api.catalogue.Year(-1190)), None),
        placeId = Some(com.histmersion.api.catalogue.Place.Id("zzb_place")),
        citekey = Some(A1),
        description = Some("d"),
        scale = Some("regional"),
        datings = List(EventDating(com.histmersion.api.catalogue.Event.Id("zzb_e1"), com.histmersion.api.library.Citekey(A1), TimeSpan(Some(-1210), None, None, Some(-1180)), Some("textual"))),
        participants = List(EventParticipant(com.histmersion.api.catalogue.Event.Id("zzb_e1"), "Hattusili", "ruler", (Some(A1)).map(com.histmersion.api.library.Citekey.apply)))
      )
    )
    EventFixtures.put(
      ds,
      "zzb_e2",
      EventPut(
        com.histmersion.api.catalogue.EventSummary.Label("Aftermath"),
        geometry = Some("""{"type":"Point","coordinates":[34.6,40.0]}"""),
        parentId = Some("zzb_e1"),
        edges = List(EventEdge(com.histmersion.api.catalogue.Event.Id("zzb_e2"), com.histmersion.api.catalogue.Event.Id("zzb_e1"), com.histmersion.api.catalogue.EventEdge.Kind("after"), A1, Some(com.histmersion.api.catalogue.EventEdge.Note("n"))))
      )
    )
    eventSets(ds)
    transact(ds)(
      GoalRepo.put(com.histmersion.domain.automation.Goal.Slug("zzb_goal"), Goal.Put(com.histmersion.api.automation.Goal.Title("Hittites"), com.histmersion.api.automation.Goal.StatusValue("seeding"), Some(com.histmersion.api.automation.Goal.Notes("notes")), List(com.histmersion.api.automation.Goal.Tags("t")), Nil, Some(com.histmersion.api.catalogue.Year(-1650)), Some(com.histmersion.api.catalogue.Year(-1180))))
    )
    transact(ds)(GoalRepo.put(com.histmersion.domain.automation.Goal.Slug("zzb_goal_sub"), Goal.Put(com.histmersion.api.automation.Goal.Title("Sub"), parentSlug = Some(com.histmersion.api.automation.Goal.Slug("zzb_goal")))))
    val q1 = QueueRows.add(ds, Kind.Tasks, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("read alpha"), Some(com.histmersion.api.automation.Queue.QueueItem.Topic("Hittites")), reason = Some(com.histmersion.api.automation.Queue.QueueItem.Reason("why"))))
    val _ = QueueRows.add(ds, Kind.Log, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("2026-10-03: did things"), createdAt = Some(com.histmersion.api.automation.Queue.QueueItem.CreatedAt("2026-10-03T09:00:00Z"))))
    val q3 = QueueRows.add(ds, Kind.Acquire, QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("buy a book")))
    val _ = QueueRows.patch(ds, q1.id, QueuePatch(status = Some(com.histmersion.api.automation.Queue.QueueItem.Status("done"))))
    val _ = QueueRows.delete(ds, q3.id)

  /** The event-catalogue sets (#1647): the catalogue's columns of an event, its catalogue record, a candidate decided
    * and one proposed, a check, a correction of an event and of a place (a JSON null value, a JSON null and an absent
    * refresh), and the site type of a gazetteer place.
    */
  private def eventSets(ds: DataSource): Unit =
    exec(
      ds,
      """UPDATE events SET catalogue = 'gvp', names = '{Thera,Santorini}', wikidata = 'Q1', central = -1560,
                   plus_minus = 10, dating_method = 'radiocarbon' WHERE id = 'zzb_e1'"""
    )
    exec(
      ds,
      """INSERT INTO event_catalogue (provider, provider_id, event_id, url, label, kind, not_before, not_after,
                   lat, lon, vei, magnitude, deaths, historicity, record)
                 VALUES ('gvp', '212040', 'zzb_e1', 'https://volcano.si.edu/212040', 'Santorini — Ünïcödé', 'eruption',
                   -1610, -1600, 36.404, 25.396, 7, 6.5, 12, 'attested', '{"VEI":7,"names":["Thera"],"nested":{"a":[1,2.5]}}')"""
    )
    exec(
      ds,
      s"""INSERT INTO event_link_candidates (event_id, citekey, score, status, findings, passages, reasons)
                  VALUES ('zzb_e1', '$A1', 0.75, 'confirmed', '{$A1#0}', '[{"quote":"The body","charStart":9}]', '{near,named}'),
                         ('zzb_e2', '$G1', 0.5, 'proposed', '{}', '[]', '{near}')"""
    )
    exec(ds, "INSERT INTO event_link_checks (event_id) VALUES ('zzb_e1')")
    exec(
      ds,
      """INSERT INTO corrections (target_kind, target_id, field, value, upstream, upstream_value, upstream_now, status, checked_at)
                 VALUES ('event', 'zzb_e1', 'label', '"Thera"', 'gvp', '"Santorini"', 'null', 'conflict', now()),
                        ('place', 'zzb_place', 'label', 'null', 'pleiades', '"Hattusa"', NULL, 'active', NULL)"""
    )
    exec(ds, "UPDATE places SET site_type = 'settlement', site_type_checked_at = now() WHERE id = 'zzb_place'")

  // ── bundle plumbing ───────────────────────────────────────────────────────────────────────

  private def exportOf(ds: DataSource, originals: Path, since: Option[String] = None): Array[Byte] =
    val out = java.io.ByteArrayOutputStream()
    BundleExport
      .write(ds, out, since, com.histmersion.util.storage.ContentDirectory(originals), TestExchange.kinds)
      .fold(e => fail(e), identity)
    out.toByteArray

  private def entries(bundle: Array[Byte]): List[(String, Array[Byte])] =
    val r = BundleTar.Reader(java.io.ByteArrayInputStream(bundle))
    Iterator
      .continually(r.next().fold(e => fail(e), identity))
      .takeWhile(_.isDefined)
      .flatten
      .map(e => e.name -> r.readAll())
      .toList

  private def manifestOf(bundle: Array[Byte]): Bundle.Manifest =
    BundleFormat.parseManifest(entries(bundle).head._2).fold(e => fail(e), identity)

  private def importInto(
      ds: DataSource,
      bundle: Array[Byte],
      originals: Path,
      dryRun: Boolean = false,
      resolutions: MergeResolutions = MergeResolutions()
  ): Bundle.ImportResult =
    val f = Files.createTempFile(tmp, "in-", ".hmx")
    Files.write(f, bundle)
    com.histmersion.util.data.exchange.BundleImporter
      .run(
        ds,
        com.histmersion.util.storage.ByteSource.file(f),
        BundleFormat.sha256Hex(bundle),
        BundleImport.Options(dryRun, cfg.limits.chunkChars, cfg.places.pointToleranceKm),
        com.histmersion.util.storage.ContentDirectory(originals),
        resolutions,
        merge,
        TestExchange.kinds,
        TestExchange.rules
      )
      .fold(e => fail(e), identity)

  private def text(entries: List[(String, Array[Byte])], path: String): String =
    String(entries.find(_._1 == path).map(_._2).getOrElse(fail(s"no $path")), UTF_8)

  private def count(ds: DataSource, q: String): Int =
    connect(ds)(Sql.raw(q).frag.query[Int].run().head)

  private def instanceId(ds: DataSource): UUID = Transactor(ds).read(BundleRepo.instance).id

  // ── M0 ─────────────────────────────────────────────────────────────────────────────────────

  test("M0: every row has a uid and its origin is this instance; a rename keeps the uid"):
    val ds = fresh("m0")
    put(ds, source(B0, None, 1))
    val before = connect(ds)(sql"SELECT uid FROM sources WHERE citekey = $B0".query[UUID].run().head)
    populate(ds, spool("m0"))
    val after = connect(ds)(sql"SELECT uid FROM sources WHERE citekey = $B1".query[UUID].run().head)
    assertEquals(after, before, "the rename changed the uid")
    // …and the wire shapes carry it (get_source, its findings, an article)
    val (detail, fs) = SourceFixtures.get(ds, B1).getOrElse(fail("no source"))
    assertEquals(detail.uid, Some(after))
    assertEquals(detail.origin, Some(instanceId(ds)))
    assert(fs.nonEmpty && fs.forall(_.uid.isDefined), fs.toString)
    assert(ArticleFixtures.get(ds, "zzb_topic").flatMap(_.uid).isDefined)
    val inst = instanceId(ds)
    List(
      "sources",
      "findings",
      "articles",
      "article_versions",
      "assets",
      "asset_links",
      "attestations",
      "events",
      "goals",
      "queue_items",
      "places",
      "periods"
    ).foreach { t =>
      assertEquals(count(ds, s"SELECT count(*)::int FROM $t WHERE uid IS NULL OR origin IS NULL"), 0, t)
      assertEquals(count(ds, s"SELECT count(*)::int FROM $t WHERE origin <> '$inst'"), 0, t)
    }
    // the strong keys follow the source, by uid
    assertEquals(
      count(ds, s"SELECT count(*)::int FROM source_identifiers WHERE source_uid = '$after' AND scheme = 'doi'"),
      1
    )
    assertEquals(
      count(
        ds,
        s"SELECT count(*)::int FROM source_identifiers i JOIN sources s ON s.uid = i.source_uid " +
          s"WHERE s.citekey = '$A1' AND i.scheme = 'sha256'"
      ),
      1
    )

  test("M0: a delete — direct or by cascade — leaves a tombstone of the right kind"):
    val ds = fresh("tomb")
    populate(ds, spool("tomb"))
    val kinds = connect(ds)(
      sql"SELECT kind, count(*)::int FROM tombstones GROUP BY kind ORDER BY kind"
        .query[(String, Int)]
        .run()
        .toMap
    )
    assertEquals(kinds.get("article"), Some(1)) // zzb_gone
    assertEquals(kinds.get("article-version"), Some(1)) // its one version, by cascade
    assertEquals(kinds.get("finding"), Some(1)) // alpha#3, dropped by a write
    assertEquals(kinds.get("queue-item"), Some(1))
    // V030: a change to an owned set moves its owner
    val t0 = connect(ds)(sql"SELECT updated_at::text FROM sources WHERE citekey = $G1".query[String].run().head)
    val _ = SourceFixtures.addEdges(ds, List(Edge(com.histmersion.api.library.Citekey(G1), com.histmersion.api.library.Citekey(B1), com.histmersion.api.library.Edge.Kind("challenges"))))
    val t1 = connect(ds)(sql"SELECT updated_at::text FROM sources WHERE citekey = $G1".query[String].run().head)
    assertNotEquals(t1, t0)

  // ── M1 ─────────────────────────────────────────────────────────────────────────────────────

  test("M1: two exports of an unchanged database are byte-identical, and the bundle verifies"):
    val ds = fresh("m1")
    val sp = spool("m1")
    populate(ds, sp)
    val one = exportOf(ds, sp)
    val two = exportOf(ds, sp)
    assert(java.util.Arrays.equals(one, two), "the two exports differ")
    val v = BundleReader.verify(java.io.ByteArrayInputStream(one), connect(ds)(BundleRepo.schemaVersion))
    assert(v.ok, v.problems.mkString("\n"))
    val es = entries(one)
    assertEquals(es.head._1, Bundle.ManifestPath)
    val m = manifestOf(one)
    assertEquals(m.instance.id, instanceId(ds))
    assertEquals(m.files.map(_.path), Bundle.Files.all)
    assertEquals(
      m.files.find(_.path == com.histmersion.api.exchange.Bundle.Kind.Source.file).map(_.lines),
      Some(3)
    )
    // the blobs: two bodies, two figures, one version body, the original
    assertEquals(m.blobs.count, 6, es.map(_._1).mkString("\n"))
    assert(es.exists(_._1 == Bundle.blobPath(BundleFormat.sha256Hex(OriginalBytes))))
    // §3a: Calibre never travels
    val all = String(es.filterNot(_._1.startsWith("blobs/")).flatMap(_._2).toArray, UTF_8)
    assert(!all.contains(Calibre), "the calibre id was exported")
    assert(!all.toLowerCase.contains("calibre"), "a calibre field was exported")
    // tombstones travel; the renamed source's alias too
    assert(text(es, Bundle.Files.Tombstones).contains("\"kind\":\"article\""))
    assert(text(es, Bundle.Files.Aliases).contains(B0))

  test("M1: --since carries only what changed after it, plus the deletions"):
    val ds = fresh("since")
    val sp = spool("since")
    populate(ds, sp)
    val full = manifestOf(exportOf(ds, sp))
    val asOf = full.asOf.getOrElse(fail("no asOf"))
    Thread.sleep(5)
    exec(ds, "DELETE FROM asset_links")
    val _ = SourceFixtures.patch(ds, G1, Source.Patch(summary = Some(com.histmersion.api.library.Source.Summary("changed"))), cfg.limits.chunkChars)
    val inc = exportOf(ds, sp, Some(asOf))
    val es = entries(inc)
    val m = manifestOf(inc)
    assertEquals(m.since, Some(asOf))
    assertEquals(
      m.files.find(_.path == com.histmersion.api.exchange.Bundle.Kind.Source.file).map(_.lines),
      Some(1)
    )
    assert(text(es, com.histmersion.api.exchange.Bundle.Kind.Source.file).contains(G1))
    assertEquals(
      m.files.find(_.path == com.histmersion.api.exchange.Bundle.Kind.Finding.file).map(_.lines),
      Some(0)
    )
    assert(text(es, Bundle.Files.Tombstones).contains("asset-link"))
    assert(text(es, Bundle.Files.Edges).nonEmpty, "the changed source carries its edges")
    // the one blob a changed line refers to: gamma's body (the receiver may not have it yet)
    assertEquals(m.blobs.count, 1)
    assert(es.exists(_._1.startsWith("blobs/")))
    assertEquals(
      m.files.find(_.path == com.histmersion.api.exchange.Bundle.Kind.Asset.file).map(_.lines),
      Some(0)
    )

  // ── M2 ─────────────────────────────────────────────────────────────────────────────────────

  test("M2: export → import into an empty instance → export is byte-equal (but for who exported)"):
    val (a, b) = (fresh("rta"), fresh("rtb"))
    val (sa, sb) = (spool("rta"), spool("rtb"))
    populate(a, sa)
    val first = exportOf(a, sa)
    val result = importInto(b, first, sb)
    assert(result.applied, result.problems.mkString("\n"))
    assertEquals(result.problems, Nil)
    assertEquals(result.conflicts, Nil)
    val second = exportOf(b, sb)
    val (e1, e2) = (entries(first), entries(second))
    assertEquals(e2.map(_._1), e1.map(_._1))
    e1.zip(e2).tail.foreach { case ((p, x), (_, y)) =>
      assert(
        java.util.Arrays.equals(x, y),
        s"$p differs:\n${String(x, UTF_8).take(2000)}\n---\n${String(y, UTF_8).take(2000)}"
      )
    }
    val (m1, m2) = (manifestOf(first), manifestOf(second))
    assertEquals(m2.modify(_.instance).setTo(m1.instance).modify(_.bundleId).setTo(m1.bundleId), m1)
    assertNotEquals(m2.instance.id, m1.instance.id)
    // what is derived, re-derived; what is local, local
    assert(count(b, s"SELECT count(*)::int FROM source_chunks WHERE citekey = '$A1'") > 0)
    assertEquals(
      count(b, s"SELECT count(*)::int FROM article_links WHERE src_slug = 'zzb_topic' AND target_kind = 'source'"),
      2
    )
    assertEquals(count(b, s"SELECT count(*)::int FROM sources WHERE calibre_id IS NOT NULL"), 0)
    assert(Files.exists(sb.resolve(s"${BundleFormat.sha256Hex(OriginalBytes)}.pdf")))
    assertEquals(count(b, s"SELECT count(*)::int FROM source_aliases WHERE alias = '$B0'"), 1)
    assertEquals(count(b, "SELECT count(*)::int FROM unresolved_edges WHERE dst = 'zzbmissing2020_x'"), 1)
    assertEquals(count(b, "SELECT count(*)::int FROM merges"), 1)
    // a second import of the same bundle changes nothing, not even the ledger
    val again = importInto(b, first, sb)
    assertEquals(again.changed, 0, again.toString)
    assertEquals(again.mergeId, None)
    assertEquals(count(b, "SELECT count(*)::int FROM merges"), 1)
    assert(java.util.Arrays.equals(exportOf(b, sb), second), "a repeated import changed the export")

  test("a bundle carries a provenance it does not know verbatim"):
    val (a, b) = (fresh("prova"), fresh("provb"))
    val (sa, sb) = (spool("prova"), spool("provb"))
    put(a, source(G1, Some("# Gamma\n\nshort"), 0))
    exec(a, s"UPDATE sources SET source = 'Unheard-Of' WHERE citekey = '$G1'")
    val result = importInto(b, exportOf(a, sa), sb)
    assert(result.applied, result.problems.mkString("\n"))
    assertEquals(count(b, s"SELECT count(*)::int FROM sources WHERE citekey = '$G1' AND source = 'Unheard-Of'"), 1)

  /** The bundle with the event-catalogue files taken out, as a bundle written before they existed. */
  private def withoutEventSets(bundle: Array[Byte]): Array[Byte] =
    val gone = Set(
      Bundle.Files.EventCatalogue,
      Bundle.Files.EventCandidates,
      Bundle.Files.EventChecks,
      Bundle.Files.EventCorrections,
      Bundle.Files.PlaceSiteTypes
    )
    val m = manifestOf(bundle)
    val out = java.io.ByteArrayOutputStream()
    val w = BundleTar.Writer(out)
    val _ =
      w.bytes(Bundle.ManifestPath, BundleFormat.manifestBytes(m.modify(_.files).setTo(m.files.filterNot(f => gone(f.path)))))
    entries(bundle).tail.filterNot(e => gone(e._1)).foreach((n, b) => w.bytes(n, b).fold(e => fail(e), identity))
    w.finish()
    out.toByteArray

  test("#1647: the event-catalogue sets travel, merge by their own rules, and an older bundle still imports"):
    val (a, b, c) = (fresh("eva"), fresh("evb"), fresh("evc"))
    val (sa, sb, sc) = (spool("eva"), spool("evb"), spool("evc"))
    populate(a, sa)
    val first = exportOf(a, sa)
    val es = entries(first)
    List(
      Bundle.Files.EventCatalogue -> 1,
      Bundle.Files.EventCandidates -> 2,
      Bundle.Files.EventChecks -> 1,
      Bundle.Files.EventCorrections -> 2,
      Bundle.Files.PlaceSiteTypes -> 1
    ).foreach((f, n) => assertEquals(manifestOf(first).files.find(_.path == f).map(_.lines), Some(n), f))
    assert(
      text(es, com.histmersion.api.exchange.Bundle.Kind.Event.file).contains("\"catalogue\":\"gvp\""),
      text(es, com.histmersion.api.exchange.Bundle.Kind.Event.file)
    )
    val r1 = importInto(b, first, sb)
    assert(r1.applied, r1.problems.mkString("\n"))
    def one(ds: DataSource, q: String): String = connect(ds)(Sql.raw(q).frag.query[String].run().head)
    assertEquals(
      one(b, "SELECT names::text || plus_minus || dating_method FROM events WHERE id = 'zzb_e1'"),
      "{Thera,Santorini}10radiocarbon"
    )
    assertEquals(one(b, "SELECT record->>'VEI' FROM event_catalogue WHERE provider_id = '212040'"), "7")
    assertEquals(one(b, s"SELECT status FROM event_link_candidates WHERE citekey = '$A1'"), "confirmed")
    assertEquals(one(b, "SELECT site_type FROM places WHERE id = 'zzb_place'"), "settlement")
    // a JSON null and an absent refresh stay apart
    assertEquals(
      one(b, "SELECT coalesce(upstream_now::text, 'SQL NULL') FROM corrections WHERE target_kind = 'event'"),
      "null"
    )
    assertEquals(
      one(b, "SELECT coalesce(upstream_now::text, 'SQL NULL') FROM corrections WHERE target_kind = 'place'"),
      "SQL NULL"
    )
    assertEquals(one(b, "SELECT value::text FROM corrections WHERE target_kind = 'place'"), "null")

    // here the person rejects the proposal; there the candidates are recomputed (newer evidence, still a
    // proposal) and the site type refined again: the evidence and the site type come over, the decision stays
    exec(b, s"UPDATE event_link_candidates SET status = 'rejected' WHERE citekey = '$G1'")
    Thread.sleep(5)
    exec(a, s"UPDATE event_link_candidates SET score = 0.9, computed_at = now() WHERE citekey = '$G1'")
    exec(a, "UPDATE places SET site_type = 'fortification', site_type_checked_at = now() WHERE id = 'zzb_place'")
    // and a correction made HERE later than the bundle's stays
    exec(
      b,
      "UPDATE corrections SET value = '\"Thira\"', updated_at = now() + interval '1 day' WHERE target_kind = 'event'"
    )
    val second = exportOf(a, sa)
    val r2 = importInto(b, second, sb)
    assert(r2.applied, r2.problems.mkString("\n"))
    assertEquals(
      one(b, s"SELECT status || ' ' || score FROM event_link_candidates WHERE citekey = '$G1'"),
      "rejected 0.9"
    )
    assertEquals(one(b, "SELECT site_type FROM places WHERE id = 'zzb_place'"), "fortification")
    assertEquals(one(b, "SELECT value::text FROM corrections WHERE target_kind = 'event'"), "\"Thira\"")
    // idempotent: the same bundle again writes nothing
    val before = exportOf(b, sb)
    val r3 = importInto(b, second, sb)
    assert(r3.applied, r3.problems.mkString("\n"))
    assert(java.util.Arrays.equals(exportOf(b, sb), before), "a repeated import changed the event-catalogue sets")

    // a bundle written before these files existed imports cleanly — the events, without the sets — and
    // leaves the sets of a collection that has them alone
    val old = withoutEventSets(first)
    val v = BundleReader.verify(java.io.ByteArrayInputStream(old), connect(c)(BundleRepo.schemaVersion))
    assert(v.ok, v.problems.mkString("\n"))
    val r4 = importInto(c, old, sc)
    assert(r4.applied, r4.problems.mkString("\n"))
    assertEquals(count(c, "SELECT count(*)::int FROM events WHERE id IN ('zzb_e1', 'zzb_e2')"), 2)
    assertEquals(count(c, "SELECT count(*)::int FROM event_catalogue"), 0)
    assertEquals(count(c, "SELECT count(*)::int FROM event_link_candidates"), 0)
    val r5 = importInto(b, old, sb)
    assert(r5.applied, r5.problems.mkString("\n"))
    assertEquals(count(b, "SELECT count(*)::int FROM event_link_candidates"), 2)
    assertEquals(count(b, "SELECT count(*)::int FROM corrections"), 2)

  test("M2: importing an instance's own bundle changes 0 rows"):
    val a = fresh("own")
    val sa = spool("own")
    populate(a, sa)
    val before = exportOf(a, sa)
    val r = importInto(a, before, sa)
    assert(r.applied, r.problems.mkString("\n"))
    assertEquals(r.changed, 0, r.toString)
    assertEquals(r.counts.map(_.keptLocal).sum + r.counts.map(_.conflicts).sum, 0)
    assertEquals(count(a, "SELECT count(*)::int FROM merges"), 0)
    assertEquals(count(a, "SELECT count(*)::int FROM merge_ledger"), 0)
    assert(java.util.Arrays.equals(exportOf(a, sa), before), "importing its own bundle changed the export")
    // …and after a local change, an OLD own bundle never wins over it
    val _ = SourceFixtures.patch(a, A1, Source.Patch(summary = Some(com.histmersion.api.library.Source.Summary("newer here"))), cfg.limits.chunkChars)
    val r2 = importInto(a, before, sa)
    assertEquals(r2.changed, 0)
    assertEquals(
      r2.counts.find(_.kind == com.histmersion.api.exchange.Bundle.Kind.Source).map(_.keptLocal),
      Some(1)
    )
    assertEquals(SourceFixtures.load(a, A1).flatMap(_.summary), Some("newer here"))

  test("M2: the ledger fast-forwards what changed there only, keeps what changed here only, and reports both"):
    val (a, b) = (fresh("lga"), fresh("lgb"))
    val (sa, sb) = (spool("lga"), spool("lgb"))
    populate(a, sa)
    val _ = importInto(b, exportOf(a, sa), sb)
    // changed over there only → fast-forward
    val _ = SourceFixtures.patch(a, A1, Source.Patch(summary = Some(com.histmersion.api.library.Source.Summary("work edit"))), cfg.limits.chunkChars)
    val r1 = importInto(b, exportOf(a, sa), sb)
    assertEquals(
      r1.counts.find(_.kind == com.histmersion.api.exchange.Bundle.Kind.Source).map(_.updated),
      Some(1),
      r1.toString
    )
    assertEquals(SourceFixtures.load(b, A1).flatMap(_.summary), Some("work edit"))
    // changed here only → kept
    val _ = SourceFixtures.patch(b, A1, Source.Patch(notes = Some(com.histmersion.api.library.Source.Notes("home edit"))), cfg.limits.chunkChars)
    val r2 = importInto(b, exportOf(a, sa), sb)
    assertEquals(
      r2.counts.find(_.kind == com.histmersion.api.exchange.Bundle.Kind.Source).map(_.keptLocal),
      Some(1),
      r2.toString
    )
    assertEquals(SourceFixtures.load(b, A1).flatMap(_.notes), Some("home edit"))
    // changed on both sides → an open issue: the import is refused, nothing written (M3)…
    val _ = SourceFixtures.patch(a, A1, Source.Patch(summary = Some(com.histmersion.api.library.Source.Summary("work again"))), cfg.limits.chunkChars)
    val third = exportOf(a, sa)
    val r3 = importInto(b, third, sb)
    assert(!r3.applied)
    assertEquals(
      r3.counts.find(_.kind == com.histmersion.api.exchange.Bundle.Kind.Source).map(_.conflicts),
      Some(1),
      r3.toString
    )
    assert(r3.conflicts.exists(_.contains(A1)), r3.conflicts.toString)
    val conflict = r3.issues.find(_.kind == Issue.Kind.ChangedBothSides).getOrElse(fail(r3.toString))
    assert(r3.problems.exists(_.startsWith(conflict.id)), r3.problems.toString)
    assertEquals(SourceFixtures.load(b, A1).flatMap(_.summary), Some("work edit"))
    // …until it is decided: keep-left keeps ours and records that it was decided
    val keep = MergeResolutions(List(MergeResolution(conflict.id, com.histmersion.api.exchange.MergeResolution.Decision("keep-left"))))
    val r3b = importInto(b, third, sb, resolutions = keep)
    assert(r3b.applied, r3b.problems.mkString("\n"))
    assertEquals(SourceFixtures.load(b, A1).flatMap(_.summary), Some("work edit"))
    assertEquals(SourceFixtures.load(b, A1).flatMap(_.notes), Some("home edit"))
    // the same foreign version does not ask again, with or without the resolution
    val r3c = importInto(b, third, sb)
    assert(r3c.applied, r3c.problems.mkString("\n"))
    assertEquals(r3c.issues, Nil)
    // a deletion over there of a row unchanged here since the last import deletes it here
    val _ = Transactor(a).transact(GoalRepo.deleteIn(com.histmersion.domain.automation.Goal.Slug("zzb_goal_sub")))
    val r4 = importInto(b, exportOf(a, sa), sb)
    assertEquals(
      r4.counts.find(_.kind == com.histmersion.api.exchange.Bundle.Kind.Goal).map(_.deleted),
      Some(1),
      r4.toString
    )
    assertEquals(count(b, "SELECT count(*)::int FROM goals WHERE slug = 'zzb_goal_sub'"), 0)
    // a rename over there is followed here: the citekey, its findings and an alias
    val _ = SourceFixtures.rename(a, G1, "zzb2004_gamma")
    val r5 = importInto(b, exportOf(a, sa), sb)
    assert(r5.applied, r5.problems.mkString("\n"))
    assertEquals(count(b, "SELECT count(*)::int FROM sources WHERE citekey = 'zzb2004_gamma'"), 1)
    assertEquals(count(b, s"SELECT count(*)::int FROM source_aliases WHERE alias = '$G1'"), 1)

  test("an old citekey renamed back here is not brought back as an alias by the next import (#827)"):
    val (a, b) = (fresh("rba"), fresh("rbb"))
    val (sa, sb) = (spool("rba"), spool("rbb"))
    populate(a, sa)
    val bundle = exportOf(a, sa)
    val _ = importInto(b, bundle, sb)
    assertEquals(count(b, s"SELECT count(*)::int FROM source_aliases WHERE alias = '$B0'"), 1)
    // here, the source goes back to its old key: the alias B0 is dropped
    assert(SourceFixtures.rename(b, B1, B0).isRight)
    assertEquals(count(b, s"SELECT count(*)::int FROM alias_tombstones WHERE alias = '$B0'"), 1)
    val r = importInto(b, bundle, sb)
    assert(r.applied, r.problems.mkString("\n"))
    assertEquals(
      count(b, s"SELECT count(*)::int FROM source_aliases WHERE alias = '$B0'"),
      0,
      "the bundle's alias was made before it was dropped here"
    )
    assertEquals(count(b, s"SELECT count(*)::int FROM sources WHERE citekey = '$B0'"), 1)

  test("M2: a row deleted here is not brought back, and what needs it is skipped with it"):
    val (a, b) = (fresh("dla"), fresh("dlb"))
    val (sa, sb) = (spool("dla"), spool("dlb"))
    populate(a, sa)
    val bundle = exportOf(a, sa)
    val _ = importInto(b, bundle, sb)
    exec(b, "DELETE FROM assets WHERE name = 'fig1.png'")
    val r = importInto(b, bundle, sb)
    assert(r.applied, r.problems.mkString("\n"))
    assertEquals(
      r.counts.find(_.kind == com.histmersion.api.exchange.Bundle.Kind.Asset).map(_.skippedDeleted),
      Some(1),
      r.toString
    )
    assertEquals(count(b, "SELECT count(*)::int FROM assets WHERE name = 'fig1.png'"), 0)

  test("M2/M3: records another instance has under the same labels are refused — every one listed, nothing written"):
    val (a, c) = (fresh("cla"), fresh("clc"))
    val (sa, sc) = (spool("cla"), spool("clc"))
    populate(a, sa)
    put(c, source(A1, None, 1))
    val _ = ArticleFixtures.put(c, Article(com.histmersion.api.research.Article.Slug("zzb_topic"), com.histmersion.api.research.Article.Title("Other topic"), com.histmersion.api.research.Article.KindValue("concept"), com.histmersion.api.research.Article.Body("other")))
    val before = exportOf(c, sc)
    val r = importInto(c, exportOf(a, sa), sc)
    assert(!r.applied)
    // the same work (same DOI) under another uid: merging is merge apply's (M5); its findings go with it
    assert(r.problems.exists(p => p.contains(s"source $A1") && p.contains("merge apply")), r.problems.mkString("\n"))
    assert(r.problems.exists(_.contains("article zzb_topic")), r.problems.mkString("\n"))
    assert(r.issues.exists(i => i.kind == Issue.Kind.ArticleDiverged && i.open), r.issues.toString)
    assert(java.util.Arrays.equals(exportOf(c, sc), before), "a refused import wrote something")

  test("M2: a dry run reports and writes nothing"):
    val (a, b) = (fresh("dra"), fresh("drb"))
    val (sa, sb) = (spool("dra"), spool("drb"))
    populate(a, sa)
    val empty = exportOf(b, sb)
    val r = importInto(b, exportOf(a, sa), sb, dryRun = true)
    assert(!r.applied)
    assert(r.dryRun)
    assertEquals(r.problems, Nil)
    assertEquals(
      r.counts.find(_.kind == com.histmersion.api.exchange.Bundle.Kind.Source).map(_.inserted),
      Some(3)
    )
    assert(java.util.Arrays.equals(exportOf(b, sb), empty), "a dry run wrote something")

  test("verify: a changed entity line, a corrupt blob and a truncated file are each reported"):
    val a = fresh("ver")
    val sa = spool("ver")
    populate(a, sa)
    val es = entries(exportOf(a, sa))
    def rebuild(f: ((String, Array[Byte])) => (String, Array[Byte])): Array[Byte] =
      val out = java.io.ByteArrayOutputStream()
      val w = BundleTar.Writer(out)
      es.map(f).foreach((p, b) => w.bytes(p, b).fold(e => fail(e), identity))
      w.finish()
      out.toByteArray
    def verify(b: Array[Byte]) = BundleReader.verify(java.io.ByteArrayInputStream(b), Some(com.histmersion.api.exchange.Bundle.Manifest.SchemaVersion("999")))
    val edited = rebuild { case (p, b) =>
      if p == com.histmersion.api.exchange.Bundle.Kind.Goal.file then
        p -> String(b, UTF_8).replace("Hittites", "Hattians").getBytes(UTF_8)
      else p -> b
    }
    val v1 = verify(edited)
    assert(!v1.ok)
    assert(
      v1.problems.exists(_.contains(s"${com.histmersion.api.exchange.Bundle.Kind.Goal.file}: sha256")),
      v1.problems.mkString("\n")
    )
    assert(v1.problems.exists(_.contains("does not match its content")), v1.problems.mkString("\n"))
    val corrupt = rebuild { case (p, b) =>
      if p.startsWith("blobs/") && b.length > 5 then p -> b.updated(0, (b(0) ^ 1).toByte) else p -> b
    }
    assert(verify(corrupt).problems.exists(_.contains("its content hashes to")))
    val whole = rebuild(identity)
    val v3 = verify(whole.take(whole.length / 2))
    assert(!v3.ok)
    assert(verify(whole).ok, verify(whole).problems.mkString("\n"))
    // and a server older than the bundle refuses it
    assert(BundleReader.verify(java.io.ByteArrayInputStream(whole), Some(com.histmersion.api.exchange.Bundle.Manifest.SchemaVersion("1"))).problems.exists(_.contains("newer")))

  test("property: random corpora round-trip export → import → export, byte for byte"):
    val rnd = scala.util.Random(20261003L)
    (1 to 3).foreach { i =>
      val (a, b) = (fresh(s"pa$i"), fresh(s"pb$i"))
      val (sa, sb) = (spool(s"pa$i"), spool(s"pb$i"))
      val n = 2 + rnd.nextInt(6)
      val cks = (0 until n).map(k => f"zzp${1900 + rnd.nextInt(120)}%d_s$k%d").toList
      cks.foreach { ck =>
        val edges = cks.filter(_ != ck).filter(_ => rnd.nextBoolean()).map(Edge(com.histmersion.api.library.Citekey(ck), com.histmersion.api.library.Citekey(_), com.histmersion.api.library.Edge.Kind("references")))
        put(
          a,
          source(
            ck,
            Option.when(rnd.nextBoolean())(rnd.alphanumeric.take(rnd.nextInt(5000)).mkString),
            rnd.nextInt(5),
            edges
          )
        )
      }
      (0 until rnd.nextInt(4)).foreach { k =>
        (0 to rnd.nextInt(3)).foreach { v =>
          val _ = ArticleFixtures.put(
            a,
            Article(
              s"zzp_art$k",
              s"A$k",
              com.histmersion.api.research.Article.KindValue("concept"),
              s"v$v ${cks.filter(_ => rnd.nextBoolean()).map(c => s"[[$c]]").mkString(" ")}"
            )
          )
        }
      }
      if rnd.nextBoolean() then
        val _ = QueueRows.add(a, Kind.Tasks, QueueAdd(rnd.alphanumeric.take(20).mkString))
      val first = exportOf(a, sa)
      val r = importInto(b, first, sb)
      assert(r.applied, s"iteration $i: ${r.problems.mkString("\n")}")
      val second = exportOf(b, sb)
      entries(first).zip(entries(second)).tail.foreach { case ((p, x), (_, y)) =>
        assert(java.util.Arrays.equals(x, y), s"iteration $i: $p differs")
      }
    }
