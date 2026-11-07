package com.histmersion.server.media

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.media.imports.{ImportFixtures, Target}
import com.histmersion.domain.media.assets.MediaItemRow
import com.histmersion.domain.catalogue.places.ExternalIds
import com.histmersion.server.MediaWiring

import com.histmersion.domain.catalogue.places.PlaceFixtures

import ox.supervised
import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.media.MediaRole
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.server.Composition
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import io.circe.parser.decode
import java.net.{InetSocketAddress, URI, URLDecoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import com.histmersion.db.*
import com.histmersion.api.catalogue.Place
import com.histmersion.api.jobs.JobInfo
import com.histmersion.api.media.{MediaImportReport, MediaItem, MediaProvider}

/** The keyless portals (#1446) through the real server and database (HISTMERSION_TEST_DB_URL; skipped otherwise): the
  * Pleiades ids and gazetteer ids places carry, the places on the map an id names, the Pleiades import's targets
  * (places with a Pleiades id only), an Open Context dry run tied by a record's own Pleiades id, and both jobs storing
  * a picture with its basis on the link. Open Context, Pleiades and Commons are one stand-in server on this machine
  * (the config's URLs point at it) — no request leaves it. Fixtures: ids `zqmp_*` and `pleiades_9990000…`, names
  * `Zqmp…`.
  */
class MediaPortalsJobSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
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

  private val Uuid = "0e000000-0000-4000-8000-000000000001"

  // `var`: the paths the stand-in answered (its handler runs on its own thread)
  @volatile private var asked = List.empty[String]

  private def provider(): HttpServer =
    val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    srv.createContext(
      "/",
      (ex: HttpExchange) =>
        val base = s"http://127.0.0.1:${srv.getAddress.getPort}"
        val path = ex.getRequestURI.getPath
        val query = URLDecoder.decode(Option(ex.getRequestURI.getRawQuery).getOrElse(""), UTF_8)
        asked = asked :+ s"$path?$query"
        val json = "application/json"
        val (code, mime, body) =
          if path == "/query/.json" then
            (
              200,
              json,
              s"""{"totalResults":1,"oc-api:has-results":[{"label":"Zqmp photo","uri":"http://opencontext.org/media/$Uuid",
            "href":"$base/media/$Uuid","citation uri":"https://n2t.net/ark:/28722/zqmp","project label":"Zqmp Excavations",
            "context label":"Asia/Zqmpjordan/Zqmp Temple/Trench 1","early bce/ce":-200.0,"late bce/ce":360.0,
            "item category":"Image media","thumbnail":"$base/img/thumb.jpg","published":"2010-11-03T00:00:00Z",
            "Creator":["Zqmp Excavator"],"Spatial Coverage":["Zqmpetra"],
            "Spatial Coverage [URI]":["https://pleiades.stoa.org/places/99900001"],
            "License":["Attribution 4.0 International (CC BY 4.0)"],"License [URI]":["https://creativecommons.org/licenses/by/4.0"]}]}"""
            )
          else if path == s"/media/$Uuid.json" then
            (
              200,
              json,
              s"""{"category":"oc-gen:image","oc-gen:has-files":[
            {"id":"$base/img/thumb.jpg","type":"oc-gen:thumbnail"},{"id":"$base/img/preview.jpg","type":"oc-gen:preview"},
            {"id":"https://archive.org/download/zqmp/full.jpg","type":"oc-gen:fullfile"}],
            "dc-terms:license":[{"id":"https://creativecommons.org/licenses/by/4.0","label":"Attribution 4.0 International (CC BY 4.0)"}]}"""
            )
          else if path == "/places/99900002/json" then
            (
              200,
              json,
              """{"id":"99900002","references":[
            {"accessURI":"https://commons.wikimedia.org/wiki/File:Zqmp_plan.jpg","type":"seeFurther"},
            {"accessURI":"https://www.flickr.com/photos/zqmp/1/","type":"citesAsRelated"}]}"""
            )
          else if path == "/w/api.php" && query.contains("prop=imageinfo") then
            (
              200,
              json,
              s"""{"query":{"pages":[{"pageid":1,"ns":6,"title":"File:Zqmp plan.jpg","imageinfo":[{"size":100,
            "width":10,"height":10,"thumburl":"$base/img/commons.jpg","thumbwidth":10,"thumbheight":10,
            "url":"$base/img/commons-original.jpg","descriptionurl":"https://commons.wikimedia.org/wiki/File:Zqmp_plan.jpg",
            "mime":"image/jpeg","extmetadata":{"Artist":{"value":"Zqmp"},"LicenseShortName":{"value":"CC BY-SA 4.0"},
            "License":{"value":"cc-by-sa-4.0"}}}]}]}}"""
            )
          else if path.startsWith("/img/") then (200, "image/jpeg", "")
          else (404, "text/plain", "")
        val bytes =
          if mime == "image/jpeg" then Array[Byte](-1, -40, -1) ++ path.getBytes(UTF_8) else body.getBytes(UTF_8)
        ex.getResponseHeaders.add("Content-Type", mime)
        ex.sendResponseHeaders(code, if bytes.isEmpty then -1 else bytes.length.toLong)
        if bytes.nonEmpty then ex.getResponseBody.write(bytes)
        ex.close()
    )
    srv.start()
    srv

  private lazy val providerServer = provider()
  private lazy val providerUrl = s"http://127.0.0.1:${providerServer.getAddress.getPort}"

  private lazy val cfg =
    val base = AppConfig.load()
    val m = base.media
    base.modify(
      _.db).setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get))).modify(_.media).setTo(m.modify(
        _.requestIntervalMs).setTo(MediaConfig.RequestIntervalMs(0)).modify(_.commons).setTo(m.commons.modify(_.apiUrl).setTo(MediaCommonsConfig.ApiUrl(s"$providerUrl/w/api.php"))).modify(_.pleiades).setTo(m.pleiades.modify(_.apiUrl).setTo(MediaCollectionConfig.ApiUrl(providerUrl)).modify(_.requestIntervalMs).setTo(MediaCollectionConfig.RequestIntervalMs(0))).modify(_.openContext).setTo(m.openContext.modify(_.apiUrl).setTo(MediaCollectionConfig.ApiUrl(providerUrl)).modify(_.requestIntervalMs).setTo(MediaCollectionConfig.RequestIntervalMs(0))
      )
    )

  /** The plugins, supplied the same media settings as [[cfg]]: the collections ask the stand-in, never a real API. */
  private lazy val plugins = com.histmersion.server.ServerPlugins.offlineWith(
    Nil,
    org.ekrich.config.ConfigFactory.parseString(
      s"""histmersion.media {
      |  request-interval-ms = 0
      |  commons.api-url = "$providerUrl/w/api.php"
      |  pleiades { api-url = "$providerUrl", request-interval-ms = 0 }
      |  open-context { api-url = "$providerUrl", request-interval-ms = 0 }
      |}""".stripMargin
    )
  )
  private lazy val ds =
    val d = testPool(cfg.db)
    Migrate.run(d)
    d

  private val Ids = List(
    com.histmersion.domain.catalogue.Place.Id("zqmp_site"),
    com.histmersion.domain.catalogue.Place.Id("zqmp_region"),
    com.histmersion.domain.catalogue.Place.Id("pleiades_99900002")
  )
  private val IdTexts: List[String] = Ids.map(_.value)

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM media_links WHERE target_id = ANY($IdTexts::text[])".update.run()
    val _ = sql"""DELETE FROM assets WHERE (provider = ${MediaProvider.OpenContext} AND provider_key = $Uuid)
                  OR (provider = ${MediaProvider.Commons} AND provider_key = 'File:Zqmp plan.jpg')""".update.run()
    val _ = sql"DELETE FROM media_checks WHERE target_id = ANY($IdTexts::text[])".update.run()
    val _ = sql"DELETE FROM finding_places WHERE place_id = ANY($IdTexts::text[])".update.run()
    val _ = sql"DELETE FROM findings WHERE citekey LIKE 'zqmp%'".update.run()
    val _ = sql"DELETE FROM sources WHERE citekey LIKE 'zqmp%'".update.run()
    val _ = sql"DELETE FROM place_links WHERE place_id = ANY($IdTexts::text[])".update.run()
    val _ = sql"DELETE FROM places WHERE id = ANY($IdTexts::text[])".update.run()

  private def pt(lon: com.histmersion.Longitude) = Some(Place.Geometry(s"""{"type":"Point","coordinates":[${lon.value},-42]}"""))

  override def beforeAll(): Unit =
    if testDb.isDefined then
      clean()
      val _ = PlaceFixtures.upsertPlaces(
        ds,
        List(
          Place.Input(
            Place(com.histmersion.api.catalogue.Place.Id("zqmp_site"), com.histmersion.api.catalogue.Place.Label("Zqmpetra"), Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site), pt(com.histmersion.Longitude(140.0))),
            List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zqmpetra")))
          ),
          Place.Input(
            Place(com.histmersion.api.catalogue.Place.Id("zqmp_region"), com.histmersion.api.catalogue.Place.Label("Zqmpjordan"), Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Region), pt(com.histmersion.Longitude(141.0))),
            List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zqmpjordan")))
          ),
          Place.Input(
            Place(com.histmersion.api.catalogue.Place.Id("pleiades_99900002"), com.histmersion.api.catalogue.Place.Label("Zqmp Zygouries"), Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site), pt(com.histmersion.Longitude(142.0))),
            List(Place.Name(com.histmersion.api.catalogue.Place.Name.Value("Zqmp Zygouries")))
          )
        )
      )
      connect(ds):
        sql"""INSERT INTO place_links (place_id, scheme, external_id, relation) VALUES
              ('zqmp_site', 'pleiades', '99900001', 'exact_match'), ('zqmp_site', 'geonames', '99900009', 'exact_match'),
              ('zqmp_region', 'pleiades', '99900003', 'close_match')""".update.run()
        sql"INSERT INTO sources (citekey, title) VALUES ('zqmp2020_x', 'Zqmp')".update.run()
        sql"""INSERT INTO findings (id, citekey, claim, period) VALUES
              ('zqmp2020_x#1', 'zqmp2020_x', 'a', int8range(-300, 100, '[]'))""".update.run()
        sql"""INSERT INTO finding_places (finding_id, place_id) VALUES ('zqmp2020_x#1', 'zqmp_site'),
              ('zqmp2020_x#1', 'zqmp_region'), ('zqmp2020_x#1', 'pleiades_99900002')""".update.run()
        ()
  override def afterSuite(): Unit =
    if testDb.isDefined then
      clean()
      providerServer.stop(0)

  test(
    "the Pleiades ids and gazetteer ids places carry; the places on the map an id names; the Pleiades import's targets"
  ):
    assertEquals(
      ImportFixtures.pleiadesIds(ds, Ids),
      Map("zqmp_site" -> List("99900001"), "pleiades_99900002" -> List("99900002")),
      "a close match is not an id"
    )
    val own = ImportFixtures.placeExternalIds(ds, Ids)
    val pleiades = ExternalIds.Scheme("pleiades")
    assertEquals(own.get(Ids.head), Some(Set("pleiades" -> "99900001", "geonames" -> "99900009")))
    assert(own.get(Ids(2)).exists(_.contains(pleiades -> ExternalIds.ExternalId("99900002"))), own.toString)
    val byId =
      ImportFixtures.mapPlacesWithIds(
        ds,
        List(pleiades -> ExternalIds.ExternalId("99900001"), pleiades -> ExternalIds.ExternalId("99900002"), pleiades -> ExternalIds.ExternalId("99900003"))
      )
    assertEquals(
      byId.view.mapValues(_.map(_.id)).toMap,
      Map(("pleiades", "99900001") -> List("zqmp_site"), ("pleiades", "99900002") -> List("pleiades_99900002"))
    )
    val ts = ImportFixtures
      .collectionTargets(
        ds,
        MediaItemRow.Provider(MediaProvider.Pleiades),
        places = ImportFixtures.Places(true),
        None,
        None,
        ImportFixtures.Limit(100_000),
        refresh = ImportFixtures.Refresh(false),
        pleiadesOnly = ImportFixtures.PleiadesOnly(true)
      )
      .fold(p => fail(p.value), identity)
      .map(_.id)
      .toSet
    assert(Set(Target.Id("zqmp_site"), Target.Id("pleiades_99900002")).subsetOf(ts), ts.toString)
    assert(!ts.contains(Target.Id("zqmp_region")))
    val none = ImportFixtures.collectionTargets(
      ds,
      MediaItemRow.Provider(MediaProvider.Pleiades),
      places = ImportFixtures.Places(true),
      Some(com.histmersion.domain.catalogue.Place.Id("zqmp_region")),
      None,
      ImportFixtures.Limit(1),
      ImportFixtures.Refresh(false),
      pleiadesOnly = ImportFixtures.PleiadesOnly(true)
    )
    assert(none.left.exists(_.value.contains("no Pleiades id")), none.toString)
    assert(ImportFixtures.collectionPending(ds, MediaItemRow.Provider(MediaProvider.Pleiades), places = ImportFixtures.Places(true), pleiadesOnly = ImportFixtures.PleiadesOnly(true)).value >= 2)

  test(
    "Open Context and Pleiades through the server: a dry run's reason, both jobs storing with the basis on the link"
  ):
    supervised {
      val b = Composition.server(cfg, com.histmersion.config.ServerConfig.Host("127.0.0.1"), com.histmersion.config.ServerConfig.Port(0), MediaWiring.NoThumbnails, plugins = plugins).start()
      val url = s"http://127.0.0.1:${b.port}"
      def job(body: String): JobInfo =
        val (js, jb) = post(s"$url/api/media/import/job", body)
        assertEquals(js, 202, jb)
        val id = decode[JobInfo](jb).fold(e => fail(s"$e: $jb"), identity).id
        @annotation.tailrec
        def until(n: Int): JobInfo =
          val j = decode[JobInfo](get(s"$url/api/jobs/$id")._2).fold(e => fail(e.toString), identity)
          if j.isTerminal || n <= 0 then j else { Thread.sleep(50); until(n - 1) }
        until(400)
      try
        val (us, ub) = post(s"$url/api/media/import", """{"provider":"rijks","dryRun":true}""")
        assertEquals(us, 400, ub)
        assert(ub.contains("pleiades, opencontext"), ub)

        val (ds0, db0) =
          post(s"$url/api/media/import", """{"provider":"opencontext","dryRun":true,"place":"zqmp_site"}""")
        assertEquals(ds0, 200, db0)
        val dry = decode[MediaImportReport](db0).fold(e => fail(s"$e: $db0"), identity)
        val basis = "pleiades:99900001 (the record's place id); 200 BCE–360 CE within 300 BCE–100 CE"
        assertEquals(dry.samples.map(s => (s.key, s.basis)), List((Uuid, basis)))
        assert(asked.exists(a => a.startsWith("/query/.json?") && a.contains("q=Zqmpetra")), asked.toString)

        val (rs, rb) =
          post(s"$url/api/media/import", """{"provider":"opencontext","dryRun":true,"place":"zqmp_region"}""")
        assertEquals(rs, 200, rb)
        val region = decode[MediaImportReport](rb).fold(e => fail(s"$e: $rb"), identity)
        assert(
          region.samples.isEmpty && region.rejected.exists(_.key.value == "its place id names another place on the map"),
          region.toString
        )

        val oc = job("""{"provider":"opencontext","dryRun":false,"place":"zqmp_site"}""")
        assertEquals(oc.status, "done", oc.toString)
        val (uid, b1, role) = connect(ds)(
          sql"""SELECT a.uid::text, m.basis, m.role FROM media_links m
              JOIN assets a ON a.uid = m.asset_uid WHERE m.target_id = 'zqmp_site'"""
            .query[(String, Option[String], String)]
            .run()
            .head
        )
        assertEquals((b1, role), (Some(basis), MediaRole.Object))
        val item = decode[MediaItem](get(s"$url/api/media/$uid")._2).fold(e => fail(e.toString), identity)
        assertEquals((item.provider, item.license), (Some(MediaProvider.OpenContext), Some("CC-BY-4.0")))
        assertEquals(item.originalUrl, Some("https://archive.org/download/zqmp/full.jpg"))
        assert(
          item.credit.exists(_.value.contains("Open Context. https://n2t.net/ark:/28722/zqmp. CC BY 4.0")),
          item.toString
        )
        assert(asked.contains(s"/media/$Uuid.json?"), asked.toString)

        val pl = job("""{"provider":"pleiades","dryRun":false,"place":"pleiades_99900002"}""")
        assertEquals(pl.status, "done", pl.toString)
        val (p, b2, r2, prop) = connect(ds)(
          sql"""SELECT a.provider, m.basis, m.role, m.property FROM media_links m
              JOIN assets a ON a.uid = m.asset_uid WHERE m.target_id = 'pleiades_99900002'"""
            .query[(Option[String], Option[String], String, Option[String])]
            .run()
            .head
        )
        assertEquals(
          (p, b2, r2, prop),
          (Some(MediaProvider.Commons), Some("Pleiades place 99900002 links it (seeFurther)"), MediaRole.Image, None)
        )
        val checks =
          connect(ds)(sql"""SELECT provider, target_id, found FROM media_checks WHERE target_id = ANY($Ids::text[])
              ORDER BY provider""".query[(String, String, Int)].run().toList)
        assertEquals(
          checks,
          List((MediaProvider.OpenContext, "zqmp_site", 1), (MediaProvider.Pleiades, "pleiades_99900002", 1))
        )
        assert(!asked.exists(_.contains("flickr")), "Flickr is never asked")
      finally b.stop()
    }
