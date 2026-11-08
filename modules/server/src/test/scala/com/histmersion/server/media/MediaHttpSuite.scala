package com.histmersion
package server.media

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.media.assets.AssetFixtures
import com.histmersion.domain.media.imports.Target
import com.histmersion.server.MediaWiring

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import ox.supervised
import com.augustnagro.magnum.*
import com.histmersion.api.TimeSpan
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.server.{Composition, Deps}
import io.circe.parser.decode
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import com.histmersion.db.*
import com.histmersion.api.catalogue.{EventPut, Period, Place}
import com.histmersion.api.media.{
  FigureLicenceFill,
  MediaFacets,
  MediaGeoHit,
  MediaImportRequest,
  MediaItem,
  MediaList,
  MediaProvider
}
import com.histmersion.api.views.{EventDetail, PlaceDetail}
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.api.catalogue.Event
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.config.ServerConfig

/** Media from outside providers (#1446) through the server: an import against a FAKE provider into the test database
  * (HISTMERSION_TEST_DB_URL; skipped otherwise), then what the web UI and the CLI read — the place card's thumbnail,
  * the thumbnail bytes served locally, the list, facets and one picture, an event's Wikidata item, the import's
  * refusals, the figure-licence count. No live network.
  */
class MediaHttpSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg =
    val base = AppConfig.load()
    base
      .modify(_.db)
      .setTo(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.media)
      .setTo(base.media.modify(_.requestIntervalMs).setTo(MediaConfig.RequestIntervalMs(0)))
  private lazy val ds =
    val d = testPool(cfg.db)
    Migrate.run(d)
    d

  private val http = HttpClient.newHttpClient()
  private def get(url: String): (Int, String) =
    val r = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
    (r.statusCode, r.body)
  private def getBytes(url: String): (Int, Option[String], Array[Byte]) =
    val r = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray())
    (r.statusCode, r.headers().firstValue("content-type").map(Option(_)).orElse(None), r.body)
  private def send(method: String, url: String, body: String): (Int, String) =
    val r = http.send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("content-type", "application/json")
        .method(method, HttpRequest.BodyPublishers.ofString(body))
        .build(),
      HttpResponse.BodyHandlers.ofString()
    )
    (r.statusCode, r.body)

  private val PlaceId = Place.Id("zqmh_mycenae")
  private val PlaceTarget = Target.Id("zqmh_mycenae")
  private val EventId = BEventRow.Id("zqmh_battle")
  private val ApiEventId = Event.Id("zqmh_battle")

  private def clean(): Unit = connect(ds):
    val _ =
      sql"DELETE FROM assets WHERE provider_key IN ('File:Lions-Gate-Mycenae.jpg', 'File:Mycenae plan NC.png')".update
        .run()
    val _ = sql"DELETE FROM media_checks WHERE target_id LIKE 'zqmh%'".update.run()
    val _ = sql"DELETE FROM events WHERE id LIKE 'zqmh%'".update.run()
    val _ = sql"DELETE FROM places WHERE id LIKE 'zqmh%'".update.run()

  override def beforeAll(): Unit =
    if testDb.isDefined then
      clean()
      val _ = PlaceFixtures.upsertPlaces(
        ds,
        List(
          Place.Input(
            Place(
              Place.Id("zqmh_mycenae"),
              Place.Label("Zqmh Mycenae"),
              Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site),
              Some(Place.Geometry("""{"type":"Point","coordinates":[22.75,37.73]}""")),
              Some(Period(Year(-1600), Year(-1100)))
            ),
            List(Place.Name(Place.Name.Value("Zqmh Mycenae")))
          ),
          Place.Input(
            Place(
              Place.Id("zqmh_nolink"),
              Place.Label("Zqmh no link"),
              Place.KindValue(com.histmersion.domain.catalogue.Place.Kind.Site),
              Some(Place.Geometry("""{"type":"Point","coordinates":[22.0,37.0]}"""))
            ),
            List(Place.Name(Place.Name.Value("Zqmh no link")))
          )
        )
      )
      connect(ds)(sql"""INSERT INTO place_links (place_id, scheme, external_id, relation, import_source, import_ref)
            VALUES (${PlaceId.value}, 'wikidata', 'Q132564', 'exact_match', 'test', 'zqmh')""".update.run())
      EventFixtures.put(
        ds,
        EventId,
        EventPut(
          EventSummary.Label("Zqmh battle"),
          EventSummary.Kind("battle"),
          TimeSpan(Some(Year(-1200)), None, None, Some(Year(-1190))),
          placeId = Some(PlaceId)
        )
      )
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private def fakeProvider: FakeMediaHttp = FakeMediaHttp { url =>
    if url.contains("action=wbgetentities") then FakeMediaHttp.json(FakeMediaHttp.fixture("wbgetentities.json"))
    else if url.contains("iiurlwidth=1280") then FakeMediaHttp.json(FakeMediaHttp.fixture("imageinfo.json"))
    else if url.contains("prop=imageinfo") then
      FakeMediaHttp.json(FakeMediaHttp.fixture("imageinfo.json").replace("1280px-", "330px-"))
    else if url.startsWith("https://upload.example/") then
      FakeMediaHttp.bytes(Array[Byte](-1, -40, -1) ++ url.getBytes("UTF-8"), "image/jpeg")
    else Left(s"unexpected $url")
  }

  test("an import through the handler: dry run stores nothing; the real one stores, links, checks"):
    val fake = fakeProvider
    val h = MediaWiring(Deps(cfg, ds), fake)
    val dry =
      h.imports.importMedia(MediaImportRequest(place = Some(PlaceId))).fold(e => fail(e.message.value), identity)
    assert(dry.dryRun.value)
    assertEquals(dry.byLicence.map(c => (c.key, c.allowed)), List(("CC-BY-NC-SA-4.0", false), ("CC-BY-SA-2.5", true)))
    assertEquals(AssetFixtures.thumbs(ds, Target.TargetType("place"), PlaceTarget), Nil)
    val done =
      h.imports
        .importMedia(MediaImportRequest(dryRun = MediaImportRequest.DryRun(false), place = Some(PlaceId)))
        .fold(e => fail(e.message.value), identity)
    assertEquals((done.stored, done.linked, done.categories), (1, 1, 1))
    assertEquals(
      AssetFixtures.thumbs(ds, Target.TargetType("place"), PlaceTarget).map(_.license),
      List(Some("CC-BY-SA-2.5"))
    )
    assert(fake.requests.forall(_._2 == Map("User-Agent" -> cfg.media.userAgent.value)))
    val checked = connect(ds)(sql"SELECT found FROM media_checks WHERE target_id = ${PlaceId.value}".query[Int].run())
    assertEquals(checked, Vector(1))

  test("refusals: an unknown provider, a place with no Wikidata link, an event with no item"):
    val h = MediaWiring(Deps(cfg, ds), fakeProvider)
    assert(
      h.imports
        .importMedia(MediaImportRequest(provider = MediaItem.Provider("flickr")))
        .left
        .exists(_.message.value.contains("unknown media provider"))
    )
    assert(
      h.imports
        .importMedia(MediaImportRequest(place = Some(Place.Id("zqmh_nolink"))))
        .left
        .exists(_.message.value.contains("no Wikidata link"))
    )
    assert(
      h.imports
        .importMedia(MediaImportRequest(event = Some(ApiEventId)))
        .left
        .exists(_.message.value.contains("events patch"))
    )

  test("the server: the place card carries the thumbnail, served locally with its type; list, show, facets"):
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        val (ps, pb) = get(s"$url/api/places/$PlaceId?counts=true")
        assertEquals(ps, 200, pb)
        val d = decode[PlaceDetail](pb).fold(e => fail(e.getMessage), identity)
        val t = d.media.headOption.getOrElse(fail(pb))
        assertEquals(
          (t.role, t.license, t.credit),
          ("image", Some("CC-BY-SA-2.5"), "Andreas Trepte, CC BY-SA 2.5, via Wikimedia Commons")
        )
        assert(d.links.exists(l => l.scheme.value == "commons" && l.externalId.value == "Mycenae"), d.links.toString)
        assertEquals(
          decode[PlaceDetail](get(s"$url/api/places/$PlaceId")._2).map(_.media),
          Right(Nil),
          "only the card asks"
        )

        val (ts, tm, tb) = getBytes(s"$url${t.url}")
        assertEquals((ts, tm), (200, Some("image/jpeg")))
        assert(new String(tb, "UTF-8").contains("330px-"), "the 330 px thumbnail, from the database")
        assertEquals(getBytes(s"$url/media/${t.uid}/file")._1, 200)
        assertEquals(getBytes(s"$url/media/not-a-uid/thumb")._1, 404)
        assertEquals(getBytes(s"$url/media/00000000-0000-0000-0000-000000000000/thumb")._1, 404)

        val (ls, lb) = get(s"$url/api/media?place=$PlaceId")
        assertEquals(ls, 200, lb)
        val l = decode[MediaList](lb).fold(e => fail(e.getMessage), identity)
        assertEquals(l.items.map(_.uid), List(t.uid))
        val (ss, sb) = get(s"$url/api/media/${t.uid}")
        assertEquals(ss, 200, sb)
        assertEquals(
          decode[MediaItem](sb).map(_.targets.map(_.targetId.value: String)),
          Right(List(PlaceId.value: String))
        )
        val (fs, fb) = get(s"$url/api/media/facets")
        assertEquals(fs, 200, fb)
        assert(decode[MediaFacets](fb).exists(_.licences.exists(_.key.value == "CC-BY-SA-2.5")), fb)
        val (gs, gb) = get(s"$url/api/media-geo?provider=${MediaProvider.Commons}&license=CC-BY-SA-2.5&limit=1000")
        assertEquals(gs, 200, gb)
        assert(decode[List[MediaGeoHit]](gb).exists(_.exists(_.uid == t.uid)), gb)
        val (cs, cb) = send("POST", s"$url/api/media/fill-licences", "")
        assertEquals(cs, 200, cb)
        assert(decode[FigureLicenceFill](cb).exists(!_.applied.value), cb)
      finally b.stop()
    }

  test("the server: an event's Wikidata item is patched (and checked), and its card reads it"):
    supervised {
      val b = Composition.server(cfg, ServerConfig.Host("127.0.0.1"), ServerConfig.Port(0)).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        val (bad, bb) = send("PATCH", s"$url/api/events/$EventId", """{"wikidata":"132564"}""")
        assertEquals(bad, 400, bb)
        assert(bb.contains("not a Wikidata item id"), bb)
        val (ok, ob) = send("PATCH", s"$url/api/events/$EventId", """{"wikidata":"Q132564"}""")
        assertEquals(ok, 200, ob)
        assertEquals(decode[EventDetail](get(s"$url/api/events/$EventId")._2).map(_.wikidata), Right(Some("Q132564")))
        val (ds2, db2) =
          send("POST", s"$url/api/media/import", s"""{"event":"$EventId","dryRun":true,"provider":"bogus"}""")
        assertEquals(ds2, 400, db2)
      finally b.stop()
    }
