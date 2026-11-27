package com.histmersion
package server.media
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.database.Transactor

import com.histmersion.domain.media.imports.ImportFixtures
import com.histmersion.domain.media.imports.ImportsControllers
import com.histmersion.domain.media.assets.AssetFixtures
import com.histmersion.domain.media.assets.ProviderAsset
import com.histmersion.domain.media.assets.NewFigure
import com.histmersion.domain.media.assets.MediaItemRow
import com.histmersion.domain.library.Citekey
import com.histmersion.util.jobs.Job
import com.histmersion.server.MediaWiring

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.util.ocr.ConverterClient

import com.histmersion.pluginhost.HttpAnswer

import ox.supervised
import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.util.jobs.JobRepo
import com.histmersion.server.{Composition, Deps}
import com.histmersion.util.ocr.ConverterClient
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import com.histmersion.domain.media.MediaRole
import com.histmersion.db.*
import com.histmersion.api.catalogue.Place
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.media.{
  MediaCoversReport,
  MediaCoversRequest,
  MediaImportRequest,
  MediaItem,
  MediaProvider,
  MediaSourceLine
}
import com.histmersion.api.ocr.ConvertedDoc
import com.histmersion.api.views.PlaceDetail
import com.histmersion.api.library.Source
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.util.ocr.Pixels
import com.histmersion.util.storage.Blob
import com.histmersion.api.library.RepairAcquire
import com.histmersion.config.ServerConfig
import com.histmersion.domain.media.imports.Target
import com.histmersion.api.media.ImportJobKinds
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** The second slice of #1446 through the server, against the test database (HISTMERSION_TEST_DB_URL; skipped otherwise)
  * and fakes only — no network, no sidecar: a paper's illustration chosen from its figures with a thumbnail from the
  * (fake) converter and shown on the source's card; a Calibre cover uploaded and shown before it; Open Library's cover
  * by ISBN, politely, 404 as "none"; a place's card through an equivalent; the import as a JOB, batch by batch,
  * followed in `jobs`. Fixtures: `zqms`.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class MediaSourcesSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val cfg =
    val base = AppConfig.load()
    base
      .modify(_.db)
      .setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.media)
      .setTo(
        base.media
          .modify(_.requestIntervalMs)
          .setTo(MediaConfig.RequestIntervalMs(0))
          .modify(_.importBatch)
          .setTo(MediaConfig.ImportBatch(1))
          .modify(_.covers)
          .setTo(base.media.covers.modify(_.requestIntervalMs).setTo(MediaCoversConfig.RequestIntervalMs(0)))
      )
  private lazy val ds =
    val d = testPool(cfg.db)
    Migrate.run(d)
    d

  private val http = HttpClient.newHttpClient()
  private def get(url: String): (Int, String) =
    val r = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def send(method: String, url: String, body: Array[Byte], ct: String): (Int, String) =
    val r = http.send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", ct)
        .method(method, HttpRequest.BodyPublishers.ofByteArray(body))
        .build(),
      HttpResponse.BodyHandlers.ofString()
    )
    (r.statusCode, r.body)

  private val Paper = Citekey("zqms2024_paper")
  private val ApiPaper = api.library.Citekey("zqms2024_paper")
  private val Book = Citekey("zqms2010_book")
  private val ApiBook = api.library.Citekey("zqms2010_book")
  private val Png = Array[Byte](-119, 80, 78, 71, 13, 10, 26, 10)
  private val Jpeg = Array[Byte](-1, -40, -1, -32, 0, 16)

  /** A converter whose only work is thumbnails: a fixed small JPEG, and every call recorded. */
  final private class ThumbConverter extends ConverterClient:
    val calls = scala.collection.mutable.ListBuffer.empty[(ConverterClient.Filename, Pixels)]
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    def convert(
        file: Blob,
        filename: ConverterClient.Filename,
        quality: ConverterClient.HighQuality
    ): Either[Problem, ConvertedDoc] = Left(
      Problem.Unclassified(Legacy.Text("not used"))
    )
    def health: Either[Problem, ConverterClient.HealthSummary] = Right(
      ConverterClient.HealthSummary("ok")
    )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def thumbnail(
        bytes: ConverterClient.DocumentBytes,
        filename: ConverterClient.Filename,
        maxPx: Pixels
    ): Either[Problem, ConverterClient.Thumb] =
      calls += filename -> maxPx
      Right(
        ConverterClient.Thumb(
          ConverterClient.Thumb.Bytes(Jpeg ++ Array[Byte](42)),
          ConverterClient.Thumb.Mime("image/jpeg"),
          ConverterClient.Thumb.Width(330),
          ConverterClient.Thumb.Height(250)
        )
      )

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM media_links WHERE target_id LIKE 'zqms%'".update.run()
    val _ =
      sql"DELETE FROM assets WHERE citekey LIKE 'zqms%' OR provider_key LIKE 'zqms%' OR provider_key LIKE 'isbn:9780000000%'".update
        .run()
    val _ = sql"DELETE FROM media_checks WHERE target_id LIKE 'zqms%'".update.run()
    val _ = sql"DELETE FROM sources WHERE citekey LIKE 'zqms%'".update.run()
    val _ = sql"DELETE FROM places WHERE id LIKE 'zqms%'".update.run()

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def beforeAll(): Unit =
    if testDb.isDefined then
      clean()
      connect(ds):
        sql"""INSERT INTO sources (citekey, title, ocr_markdown, creator) VALUES
              ($Paper, 'Zqms paper', 'Title ![](assets/fig-0001-01.png) ![](assets/fig-0002-01.png) ![](assets/fig-0001-01.png) ![](assets/fig-0001-01.png)', jsonb_build_object('kind', 'unknown')),
              ($Book, 'Zqms book', NULL, jsonb_build_object('kind', 'unknown'))""".update.run()
        sql"""INSERT INTO source_identifiers (source_uid, scheme, value)
              SELECT uid, 'isbn', '978-0-00-000000-2' FROM sources WHERE citekey = $Book""".update.run()
        ()
      val _ = AssetFixtures.add(
        ds,
        Paper,
        List(
          NewFigure(
            NewFigure.Name("fig-0001-01.png"),
            NewFigure.Mime("image/png"),
            NewFigure.Bytes(Png ++ Array[Byte](1)),
            Some(NewFigure.Width(80)),
            Some(NewFigure.Height(80))
          ), // a logo, on every page
          NewFigure(
            NewFigure.Name("fig-0002-01.png"),
            NewFigure.Mime("image/png"),
            NewFigure.Bytes(Png ++ Array[Byte](2)),
            Some(NewFigure.Width(900)),
            Some(NewFigure.Height(650))
          )
        )
      ) // the map
      val _ = PlaceFixtures.upsertPlaces(
        ds,
        List(
          Place.Input(
            Place(
              Place.Id("zqms_site"),
              Place.Label("Zqms Mykenai"),
              Place.KindValue(domain.catalogue.Place.Kind.Site),
              Some(Place.Geometry("""{"type":"Point","coordinates":[-60,-50]}"""))
            ),
            List(Place.Name(Place.Name.Value("Zqms Mykenai")))
          ),
          Place.Input(
            Place(
              Place.Id("zqms_dup"),
              Place.Label("Zqms Mykenai"),
              Place.KindValue(domain.catalogue.Place.Kind.Site),
              Some(Place.Geometry("""{"type":"Point","coordinates":[-60.005,-50]}"""))
            ),
            List(Place.Name(Place.Name.Value("Zqms Mykenai")))
          )
        )
      )
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  test("a paper's illustration: the map, not the logo; its thumbnail from the converter; dry run first"):
    val conv = ThumbConverter()
    val h = MediaWiring(Deps(cfg, ds), FakeMediaHttp(u => Left(s"unexpected $u")), conv)
    val dry =
      h.covers
        .covers(MediaCoversRequest(citekey = Some(ApiPaper), covers = MediaCoversRequest.Covers(false)))
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(
      dry.lines,
      List(
        MediaSourceLine(
          ApiPaper,
          MediaSourceLine.Role("illustration"),
          MediaItem.Provider("source"),
          MediaSourceLine.Detail("fig-0002-01.png")
        )
      )
    )
    assertEquals(AssetFixtures.sourceThumb(ds, Paper), None, "a dry run links nothing")
    val done = h.covers
      .covers(
        MediaCoversRequest(
          dryRun = RepairAcquire.RepairRequest.DryRun(false),
          citekey = Some(ApiPaper),
          covers = MediaCoversRequest.Covers(false)
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(done.illustrations, 1)
    assertEquals(
      conv.calls.toList.map((file, px) => file -> px.value),
      List("fig-0002-01.png" -> cfg.media.thumbPx.value)
    )
    val t = AssetFixtures.sourceThumb(ds, Paper).get
    assertEquals((t.role, t.width, t.height), ("illustration", Some(330), Some(250)))
    assertEquals(AssetFixtures.thumb(ds, MediaItemRow.Uid(t.uid.value)).map(_._1), Some("image/jpeg"))
    assert(
      h.covers.covers(MediaCoversRequest(dryRun = RepairAcquire.RepairRequest.DryRun(false))).isLeft,
      "a run over every source is a job"
    )

  test("Open Library: the cover by ISBN, at its own pace, as the source's cover; a 404 is 'none'"):
    val fake = FakeMediaHttp { url =>
      if url.contains("/b/isbn/9780000000002-L.jpg") then FakeMediaHttp.bytes(Jpeg ++ Array[Byte](1), "image/jpeg")
      else if url.contains("/b/isbn/9780000000002-M.jpg") then FakeMediaHttp.bytes(Jpeg ++ Array[Byte](2), "image/jpeg")
      else Right(HttpAnswer(HttpAnswer.Code(404), Map.empty, HttpAnswer.Body(Array.emptyByteArray)))
    }
    val h = MediaWiring(Deps(cfg, ds), fake, ThumbConverter())
    val r = h.covers
      .covers(
        MediaCoversRequest(
          dryRun = RepairAcquire.RepairRequest.DryRun(false),
          citekey = Some(ApiBook),
          illustrations = MediaCoversRequest.Illustrations(false)
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(
      r.lines,
      List(
        MediaSourceLine(
          ApiBook,
          MediaSourceLine.Role("cover"),
          MediaItem.Provider("open-library"),
          MediaSourceLine.Detail("isbn:9780000000002")
        )
      )
    )
    assert(
      fake.requests.forall(_._2 == Map("User-Agent" -> cfg.media.userAgent.value)),
      "the project's User-Agent, nothing else"
    )
    assert(fake.requests.forall(_._1.startsWith(cfg.media.covers.openLibraryUrl.value)))
    val t = AssetFixtures.sourceThumb(ds, Book).get
    assertEquals(
      (t.role, t.credit, t.license, t.sourceUrl),
      ("cover", "Cover: Open Library", None, Some("https://openlibrary.org/isbn/9780000000002"))
    )
    assertEquals(
      AssetFixtures.thumb(ds, MediaItemRow.Uid(t.uid.value)).map(_._2.value.last),
      Some(2.toByte),
      "Open Library's M size is the thumbnail"
    )
    // another ISBN Open Library has no cover for: none, and remembered as asked
    val none = h.covers
      .covers(
        MediaCoversRequest(
          dryRun = RepairAcquire.RepairRequest.DryRun(false),
          citekey = Some(ApiPaper),
          illustrations = MediaCoversRequest.Illustrations(false),
          isbn = Some(MediaCoversRequest.Isbn("9780000000019"))
        )
      )
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((none.covers, none.skipped.map(_.key)), (0, List("cover: Open Library has no cover")))

  test("the server: a Calibre cover uploaded is the source's card picture, before its illustration"):
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), ThumbConverter()).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        val (bad, bb) = send(
          "PUT",
          s"$url/api/media/sources/$Paper/cover?key=77",
          "not an image".getBytes,
          "application/octet-stream"
        )
        assertEquals(bad, 400, bb)
        val (nf, nb) = send("PUT", s"$url/api/media/sources/zqms_nope/cover?key=77", Jpeg, "application/octet-stream")
        assertEquals(nf, 404, nb)
        val (ok, ob) = send(
          "PUT",
          s"$url/api/media/sources/$Paper/cover?provider=calibre&key=zqms77",
          Jpeg,
          "application/octet-stream"
        )
        assertEquals(ok, 200, ob)
        val m = RawJson.decode[MediaItem](ob).fold(e => fail(e), identity)
        assertEquals((m.provider, m.providerKey, m.license), (Some("calibre"), Some("zqms77"), None))
        assertEquals(
          m.targets.map(t => (t.targetType, t.targetId.value: String, t.role)),
          List(("source", Paper, "cover"))
        )
        val (ss, sb) = get(s"$url/api/sources/$Paper?body=false")
        assertEquals(ss, 200, sb)
        val sw = RawJson.decode[Source.WithFindings](sb).fold(e => fail(e), identity)
        assertEquals(sw.illustration.map(t => (t.uid, t.role)), Some((m.uid, "cover")))
        val (ts, _) = get(s"$url${sw.illustration.get.url}")
        assertEquals(ts, 200)
        // the covers dry run over everything answers from the database alone
        val (ds2, db2) =
          send("POST", s"$url/api/media/covers", """{"dryRun":true,"covers":false}""".getBytes, "application/json")
        assertEquals(ds2, 200, db2)
        assert(RawJson.decode[MediaCoversReport](db2).isRight, db2)
      finally b.stop()
    }

  test("a place's card through an equivalent: the same site's other gazetteer point"):
    val (uid, _) = AssetFixtures.save(
      ds,
      ProviderAsset(
        ProviderAsset.Provider(MediaProvider.Commons),
        ProviderAsset.Key("File:Zqms lions.jpg"),
        ProviderAsset.Name("zqms.jpg"),
        ProviderAsset.Mime("image/jpeg"),
        ProviderAsset.Bytes(Jpeg),
        Some(ProviderAsset.Width(1)),
        Some(ProviderAsset.Height(1)),
        Some(ProviderAsset.Thumb(Jpeg)),
        Some(ProviderAsset.ThumbMime("image/jpeg")),
        Some(ProviderAsset.ThumbWidth(1)),
        Some(ProviderAsset.ThumbHeight(1)),
        ProviderAsset.License("CC0-1.0"),
        None,
        None,
        ProviderAsset.Credit("Ann, CC0, via Wikimedia Commons"),
        ProviderAsset.SourceUrl("https://commons.wikimedia.org/wiki/File:Zqms_lions.jpg"),
        None,
        None
      ),
      (Target.TargetType("place"), Target.Id("zqms_dup"))
    )
    AssetFixtures.link(
      ds,
      uid,
      Target.TargetType("place"),
      Target.Id("zqms_dup"),
      MediaRole.Wire(MediaRole.Image),
      Some(AssetFixtures.LinkProperty("P18")),
      Some(AssetFixtures.LinkItem("Q90000077")),
      AssetFixtures.LinkRank(0)
    )
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        val (ps, pb) = get(s"$url/api/places/zqms_site?counts=true")
        assertEquals(ps, 200, pb)
        assertEquals(
          RawJson.decode[PlaceDetail](pb).map(_.media.map(_.uid.value: String)),
          Right(List(uid.value: String))
        )
      finally b.stop()
    }
    connect(ds)(sql"DELETE FROM assets WHERE provider_key = 'File:Zqms lions.jpg'".update.run())

  test("the import as a job: batch by batch, followed in `jobs`, one run at a time"):
    val started = System.currentTimeMillis()
    val fake = FakeMediaHttp { url =>
      if url.contains("action=wbgetentities") then FakeMediaHttp.json("""{"entities":{}}""")
      else Left(s"unexpected $url")
    }
    val h = MediaWiring(Deps(cfg, ds), fake)
    assert(
      h.imports.importJob(MediaImportRequest(dryRun = MediaImportRequest.DryRun(true))).isLeft,
      "a dry run is not a job"
    )
    val j =
      h.imports
        .importJob(
          MediaImportRequest(dryRun = MediaImportRequest.DryRun(false), limit = Some(MediaImportRequest.Limit(3)))
        )
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(j.kind.value: String, ImportsControllers.ImportJobKind.value: String)
    assert(
      j.detail.flatMap(ImportJobKinds.Media.of).exists(_.value.contains("up to 3")),
      j.toString
    )
    @annotation.tailrec
    def await(n: Int): JobInfo =
      val now = Transactor(ds).read(JobRepo.get(Job.Id.assume(j.id.value))).get
      if now.isTerminal || n <= 0 then now else { Thread.sleep(100); await(n - 1) }
    val end = await(300)
    assertEquals(end.status, "done", end.toString)
    val pendingNow = ImportFixtures.pending(ds, MediaItemRow.Provider(MediaProvider.Commons))
    assert(
      end.progress.exists(p => p.value.contains("asked in") && p.value.contains(s"$pendingNow not asked yet")),
      end.toString
    )
    val batches = "in (\\d+) batch".r.findFirstMatchIn(end.progress.get.value).map(_.group(1).toInt).get
    assert(batches <= 3, end.toString)
    assertEquals(fake.requests.count(_._1.contains("wbgetentities")), batches, "one Wikidata call per batch of 1")
    connect(ds)(sql"""DELETE FROM media_checks WHERE provider = ${MediaProvider.Commons}
          AND checked_at >= to_timestamp($started::double precision / 1000)""".update.run())
