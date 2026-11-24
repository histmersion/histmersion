package com.histmersion
package server.media
import com.histmersion.util.json.RawJson

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.media.MediaMarkdown

import com.histmersion.domain.media.imports.ImportFixtures
import com.histmersion.domain.media.assets.MediaItemRow
import com.histmersion.server.MediaWiring

import com.histmersion.domain.catalogue.places.PlaceFixtures

import ox.supervised
import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.server.Composition
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import java.net.{InetSocketAddress, URI, URLDecoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import com.histmersion.domain.media.MediaRole
import com.histmersion.db.*
import com.histmersion.api.catalogue.Place
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.media.{AssetKind, MediaImportReport, MediaItem, MediaProvider}
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.server.ServerPlugins
import com.histmersion.domain.catalogue.Event
import com.histmersion.config.ServerConfig
import com.histmersion.api.media.ImportJobKinds
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given

/** A collection import (#1446) through the real server and database (HISTMERSION_TEST_DB_URL; skipped otherwise): the
  * places on the map and their name keys and periods as the match reads them (V094), a dry run's matches with their
  * reasons, the job storing a picture with the match's reason on its link, and the place recorded as asked. The Art
  * Institute of Chicago is a stand-in server on this machine (the config's API and IIIF URLs point at it) — no request
  * leaves it. Fixtures: ids `zqmc_*`, names `Zqmc…`.
  */
class MediaCollectionsJobSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val http = HttpClient.newHttpClient()
  private def get(url: String): (Int, String) =
    val r = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def post(url: String, body: String): (Int, String) =
    val r = http.send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build(),
      HttpResponse.BodyHandlers.ofString()
    )
    (r.statusCode, r.body)

  // `var`: the searches the stand-in answered (its handler runs on its own thread)
  @volatile private var searches = List.empty[String]

  /** The Art Institute's search (three works) and its IIIF images. */
  private def provider(): HttpServer =
    val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    srv.createContext(
      "/",
      (ex: HttpExchange) =>
        val path = ex.getRequestURI.getPath
        val query = Option(ex.getRequestURI.getRawQuery).getOrElse("")
        val (code, mime, body) =
          if path == "/api/artworks/search" then
            searches = searches :+ URLDecoder.decode(query, UTF_8)
            def work(id: Int, place: String, from: Int, to: Int, pd: Boolean) =
              s"""{"id":$id,"title":"Zqmc work $id","is_public_domain":$pd,"image_id":"img-$id","place_of_origin":"$place",
               "date_start":$from,"date_end":$to,"artist_title":null,"credit_line":"x"}"""
            val data = List(
              work(9001, "Zqmcenai", -1400, -1300, pd = true), // an alternative name of zqmc_site, of its period
              work(9002, "Zqmc Thebai", -1400, -1300, pd = true), // names two places on the map
              work(9003, "Zqmcenai", -1400, -1300, pd = false)
            ) // not public domain
            (200, "application/json", s"""{"data":[${data.mkString(",")}]}""".getBytes(UTF_8))
          else if path.startsWith("/iiif/") then (200, "image/jpeg", Array[Byte](-1, -40, -1) ++ path.getBytes(UTF_8))
          else (404, "text/plain", Array.emptyByteArray)
        ex.getResponseHeaders.add("Content-Type", mime)
        ex.sendResponseHeaders(code, if body.isEmpty then -1 else body.length.toLong)
        if body.nonEmpty then ex.getResponseBody.write(body)
        ex.close()
    )
    srv.start()
    srv

  private lazy val providerServer = provider()
  private lazy val providerUrl = s"http://127.0.0.1:${providerServer.getAddress.getPort}"

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
          .modify(_.aic)
          .setTo(
            base.media.aic
              .modify(_.apiUrl)
              .setTo(MediaCollectionConfig.ApiUrl(s"$providerUrl/api"))
              .modify(_.imageUrl)
              .setTo(MediaCollectionConfig.ImageUrl(s"$providerUrl/iiif"))
              .modify(_.requestIntervalMs)
              .setTo(MediaCollectionConfig.RequestIntervalMs(0))
          )
      )

  /** The plugins, supplied the same media settings as [[cfg]]: the collections ask the stand-in, never a real API. */
  private lazy val plugins = ServerPlugins.offlineWith(
    Nil,
    org.ekrich.config.ConfigFactory.parseString(
      s"""histmersion.media {
      |  request-interval-ms = 0
      |  aic { api-url = "$providerUrl/api", image-url = "$providerUrl/iiif", request-interval-ms = 0 }
      |}""".stripMargin
    )
  )
  private lazy val ds =
    val d = testPool(cfg.db)
    Migrate.run(d)
    d

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM media_links WHERE target_id LIKE 'zqmc%'".update.run()
    val _ =
      sql"DELETE FROM assets WHERE provider = ${MediaProvider.ArtInstitute} AND provider_key LIKE '900%'".update.run()
    val _ = sql"DELETE FROM media_checks WHERE target_id LIKE 'zqmc%'".update.run()
    val _ = sql"DELETE FROM finding_places WHERE place_id LIKE 'zqmc%'".update.run()
    val _ = sql"DELETE FROM findings WHERE citekey LIKE 'zqmc%'".update.run()
    val _ = sql"DELETE FROM sources WHERE citekey LIKE 'zqmc%'".update.run()
    val _ = sql"DELETE FROM places WHERE id LIKE 'zqmc%'".update.run()

  private def pt(lon: Longitude) = Some(Place.Geometry(s"""{"type":"Point","coordinates":[${lon.value},-41]}"""))

  override def beforeAll(): Unit =
    if testDb.isDefined then
      clean()
      val _ = PlaceFixtures.upsertPlaces(
        ds,
        List(
          Place.Input(
            Place(
              Place.Id("zqmc_site"),
              Place.Label("Zqmcenae"),
              Place.KindValue(domain.catalogue.Place.Kind.Site),
              pt(Longitude(140.0))
            ),
            List(Place.Name(Place.Name.Value("Zqmcenae")), Place.Name(Place.Name.Value("Zqmcenai")))
          ),
          Place.Input(
            Place(
              Place.Id("zqmc_thebai_a"),
              Place.Label("Zqmc Thebai"),
              Place.KindValue(domain.catalogue.Place.Kind.Site),
              pt(Longitude(141.0))
            ),
            List(Place.Name(Place.Name.Value("Zqmc Thebai")))
          ),
          Place.Input(
            Place(
              Place.Id("zqmc_thebai_b"),
              Place.Label("Zqmc Thebai"),
              Place.KindValue(domain.catalogue.Place.Kind.Site),
              pt(Longitude(150.0))
            ),
            List(Place.Name(Place.Name.Value("Zqmc Thebai")))
          )
        )
      )
      connect(ds):
        sql"INSERT INTO sources (citekey, title, creator) VALUES ('zqmc2020_x', 'Zqmc', jsonb_build_object('kind', 'unknown'))".update
          .run()
        // the site has no dates of its own: its period is the span of its findings
        sql"""INSERT INTO findings (id, citekey, claim, period, creator) VALUES
              ('zqmc2020_x#1', 'zqmc2020_x', 'a', int8range(-1600, -1500, '[]'), jsonb_build_object('kind', 'unknown')),
              ('zqmc2020_x#2', 'zqmc2020_x', 'b', int8range(-1200, -1100, '[]'), jsonb_build_object('kind', 'unknown')),
              ('zqmc2020_x#3', 'zqmc2020_x', 'c', int8range(-1400, -1300, '[]'), jsonb_build_object('kind', 'unknown'))""".update
          .run()
        sql"""INSERT INTO finding_places (finding_id, place_id) VALUES ('zqmc2020_x#1', 'zqmc_site'),
              ('zqmc2020_x#2', 'zqmc_site'), ('zqmc2020_x#3', 'zqmc_thebai_a'), ('zqmc2020_x#3', 'zqmc_thebai_b')""".update
          .run()
        ()
  override def afterSuite(): Unit =
    if testDb.isDefined then
      clean()
      providerServer.stop(0)

  test("the places as a match reads them: every name's key, the findings' span, and what a name names on the map"):
    val f = ImportFixtures.placeFacts(ds, List(domain.catalogue.Place.Id("zqmc_site")))(
      domain.catalogue.Place.Id("zqmc_site")
    )
    assertEquals(f.keys, Set("zqmcenae", "zqmcenai"))
    assertEquals(f.period, Some((-1600L, -1100L)))
    val named = ImportFixtures.mapPlacesNamed(
      ds,
      List(
        ImportFixtures.NameKey("zqmcenai"),
        ImportFixtures.NameKey("zqmc thebai"),
        ImportFixtures.NameKey("zqmc nowhere")
      )
    )
    assertEquals(
      named.view.mapValues(_.map(_.id.value).sorted).toMap,
      Map("zqmcenai" -> List("zqmc_site"), "zqmc thebai" -> List("zqmc_thebai_a", "zqmc_thebai_b"))
    )
    val ts = ImportFixtures
      .collectionTargets(
        ds,
        MediaItemRow.Provider(MediaProvider.ArtInstitute),
        places = ImportFixtures.Places(true),
        None,
        None,
        ImportFixtures.Limit(10_000),
        refresh = ImportFixtures.Refresh(false)
      )
      .fold(p => fail(p.sentence), identity)
    assert(Set("zqmc_site", "zqmc_thebai_a").subsetOf(ts.map(_.id.value).toSet), ts.toString)
    assert(
      ImportFixtures
        .collectionTargets(
          ds,
          MediaItemRow.Provider(MediaProvider.ArtInstitute),
          places = ImportFixtures.Places(true),
          None,
          Some(Event.Id("e")),
          ImportFixtures.Limit(1),
          ImportFixtures.Refresh(false)
        )
        .isLeft,
      "a collection is about places"
    )

  test("dry run, a job, the link's reason and the check — through the server"):
    supervised {
      val b = Composition
        .server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0), MediaWiring.NoThumbnails, plugins = plugins)
        .start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        val (us, ub) = post(s"$url/api/media/import", """{"provider":"rijks","dryRun":true}""")
        assertEquals(us, 400, ub)
        assert(ub.contains("one of: wikidata, depicts, met, cleveland, aic, loc"), ub)

        val (ds0, db0) = post(s"$url/api/media/import", """{"provider":"aic","dryRun":true,"place":"zqmc_site"}""")
        assertEquals(ds0, 200, db0)
        val dry = RawJson.decode[MediaImportReport](db0).fold(e => fail(s"$e: $db0"), identity)
        assertEquals(
          dry.samples.map(s => (s.key, s.basis)),
          List(("9001", "place_of_origin = Zqmcenai; 1400–1300 BCE within 1600–1100 BCE"))
        )
        assert(dry.rejected.exists(_.key.value == "names another place on the map"), dry.rejected.toString)
        assert(dry.rejected.exists(_.key.value == "not Open Access (is_public_domain is false)"), dry.rejected.toString)
        assertEquals(searches.size, 1)
        assert(searches.head.contains("q=Zqmcenae"), searches.head)

        val (js, jb) = post(s"$url/api/media/import/job", """{"provider":"aic","dryRun":false,"place":"zqmc_site"}""")
        assertEquals(js, 202, jb)
        val job = RawJson.decode[JobInfo](jb).fold(e => fail(s"$e: $jb"), identity)
        assert(
          job.detail
            .flatMap(ImportJobKinds.Media.of)
            .exists(_.value.contains("Art Institute of Chicago objects of zqmc_site")),
          job.toString
        )
        @annotation.tailrec
        def until(n: Int): JobInfo =
          val j = RawJson.decode[JobInfo](get(s"$url/api/jobs/${job.id}")._2).fold(e => fail(e.toString), identity)
          if j.isTerminal || n <= 0 then j else { Thread.sleep(50); until(n - 1) }
        val end = until(400)
        assertEquals(end.status, "done", end.toString)
        assert(end.progress.exists(_.value.contains("1 stored")), end.toString)

        val (uid, basis, role) =
          connect(ds)(sql"""SELECT a.uid::text, m.basis, m.role FROM media_links m JOIN assets a ON a.uid = m.asset_uid
              WHERE m.target_id = 'zqmc_site'""".query[(String, Option[String], String)].run().head)
        assertEquals(role, MediaRole.Object)
        assertEquals(basis, Some("place_of_origin = Zqmcenai; 1400–1300 BCE within 1600–1100 BCE"))
        val (ss, sb) = get(s"$url/api/media/$uid")
        assertEquals(ss, 200, sb)
        val item = RawJson.decode[MediaItem](sb).fold(e => fail(s"$e: $sb"), identity)
        assertEquals(
          (item.provider, item.license, item.kind),
          (Some(MediaProvider.ArtInstitute), Some("CC0-1.0"), Some(AssetKind.Photo))
        )
        assertEquals(item.credit, Some("Zqmc work 9001, The Art Institute of Chicago, CC0"))
        assertEquals(item.targets.flatMap(_.basis), basis.toList)
        assert(MediaMarkdown.mediaItem(item).contains("matched by place_of_origin = Zqmcenai"))
        val found = connect(ds)(sql"""SELECT found FROM media_checks WHERE provider = ${MediaProvider.ArtInstitute}
              AND target_id = 'zqmc_site'""".query[Int].run().headOption)
        assertEquals(found, Some(1))
        // the card shows it: a thumbnail came from the IIIF server
        assert(get(s"$url/api/places/zqmc_site?counts=true")._2.contains(uid))
      finally b.stop()
    }
