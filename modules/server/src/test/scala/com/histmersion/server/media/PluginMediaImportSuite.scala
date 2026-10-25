package com.histmersion.server.media

import com.histmersion.pluginhost.{PluginHost, Generation, HttpTransport}

import ox.supervised
import com.augustnagro.magnum.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.{MediaRepo, Migrate, PlaceRepo}
import com.histmersion.plugin as spi
import com.histmersion.server.{Deps, Main}
import com.histmersion.server.plugins.Plugins
import io.circe.parser.decode
import org.ekrich.config.ConfigFactory

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import com.histmersion.values.{MediaRole, PlaceKind}
import com.histmersion.api.VocabularyCodecs.given

/** A THIRD-PARTY plugin's media collection (docs/research-plugins.md §6, §11) run by `media import`
  * through the registries, against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped otherwise): the
  * plugin states each record's licence, and the HOST judges it — a provider-stated CC BY-NC licence is
  * refused under the default allowlist and stored only once `media.collections.per-collection` allows
  * it for that collection; an all-rights-reserved one never, whatever the config says. The host's
  * match ties a record to the place (its basis on the link), its polite client carries the plugin's
  * requests (the host's User-Agent, never the plugin's), and the provider list shows the collection.
  * No socket: the plugin's search and the pictures are answered by a fake. Fixtures `zqpl_*`. */
class PluginMediaImportSuite extends com.histmersion.db.DbSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val Collection = "zq-portal"
  private val PlaceId    = "zqpl_site"

  /** The fixture plugin: one collection whose search asks its API through the host's client and
    * answers four records — CC BY, CC BY-NC, all rights reserved, and a CC0 one from another place. */
  private final class ZqPlugin extends spi.Plugin:
    type Config = ZqPluginConfig
    val info = spi.PluginInfo("zq-plugin", "0.1.0", spi.Spi.Version)
    override def defaultsResource = "zq-none.conf"
    def register(c: ZqPluginConfig, ctx: spi.PluginContext) = ctx.mediaCollections.add(Collection, ZqCollection)

  private object ZqCollection extends spi.MediaCollection:
    val title = "Zq portal objects"
    override val description = "A test fixture's collection"
    val role = spi.MediaCollection.Object
    override val requestIntervalMs = 0
    def search(place: spi.MediaPlace, http: spi.PluginHttp) =
      // the plugin asks with its own User-Agent: the host's replaces it
      http.get(s"https://zq.example/search?q=${place.label}", Map("User-Agent" -> "zq-bot/1.0", "Accept" -> "application/json"))
        .map { _ =>
          def item(key: String, licence: String, where: String) =
            Right(spi.MediaItem(key, s"Zq bowl $key", s"https://zq.example/objects/$key", Some(s"https://zq.example/img/$key.jpg"),
              Right(spi.MediaRights(licence, author = Some("Zq Museum"), credit = s"Zq bowl $key, Zq Museum, $licence")),
              geography = List("place_of_origin" -> where), period = Some((-1400L, -1300L))))
          List(item("by", "CC BY 4.0", "Zqplenai"), item("nc", "CC BY-NC 4.0", "Zqplenai"),
            item("arr", "All rights reserved", "Zqplenai"), item("far", "https://creativecommons.org/publicdomain/zero/1.0/", "Zqpl Nowhere"),
            Left("a record the API could not describe"))
        }

  private def fake: FakeMediaHttp = FakeMediaHttp { url =>
    if url.startsWith("https://zq.example/search") then FakeMediaHttp.json("""{"ok":true}""")
    else if url.startsWith("https://zq.example/img/") then FakeMediaHttp.bytes(Array[Byte](-1, -40, -1) ++ url.getBytes("UTF-8"), "image/jpeg")
    else Left(s"unexpected $url")
  }

  private def generation: Generation =
    PluginHost.build(Plugins.providers :+ ZqPlugin(), ConfigFactory.parseString("histmersion.plugins.zq-plugin.enabled = true"),
      ConfigFactory.empty, Plugins.services(() => new spi.PluginHttp { def get(u: String, h: Map[String, String]) = Left("no") },
        ConfigFactory.empty))

  private lazy val base =
    val b = AppConfig.load()
    b.copy(db = b.db.copy(url = testDb.get), media = b.media.copy(requestIntervalMs = 0))
  private lazy val ds =
    val d = testPool(base.db)
    Migrate.run(d)
    d

  /** The config with `allow` as this collection's own allowlist. */
  private def allowing(allow: String): AppConfig =
    base.copy(media = base.media.copy(collections = base.media.collections.copy(perCollection = Map(Collection -> allow))))

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM media_links WHERE target_id LIKE 'zqpl%'".update.run()
    val _ = sql"DELETE FROM assets WHERE provider = $Collection".update.run()
    val _ = sql"DELETE FROM media_checks WHERE target_id LIKE 'zqpl%'".update.run()
    val _ = sql"DELETE FROM finding_places WHERE place_id LIKE 'zqpl%'".update.run()
    val _ = sql"DELETE FROM findings WHERE citekey LIKE 'zqpl%'".update.run()
    val _ = sql"DELETE FROM sources WHERE citekey LIKE 'zqpl%'".update.run()
    val _ = sql"DELETE FROM places WHERE id LIKE 'zqpl%'".update.run()

  override def beforeAll(): Unit =
    if testDb.isDefined then
      clean()
      val _ = PlaceRepo.upsertPlaces(ds, List(PlaceInput(
        Place(PlaceId, "Zqplenae", PlaceKind.Site, Some("""{"type":"Point","coordinates":[142.0,-43]}""")),
        List(PlaceName("Zqplenae"), PlaceName("Zqplenai")))))
      connect(ds):
        sql"INSERT INTO sources (citekey, title) VALUES ('zqpl2020_x', 'Zqpl')".update.run()
        sql"""INSERT INTO findings (id, citekey, claim, period) VALUES
              ('zqpl2020_x#1', 'zqpl2020_x', 'a', int8range(-1600, -1100, '[]'))""".update.run()
        sql"INSERT INTO finding_places (finding_id, place_id) VALUES ('zqpl2020_x#1', $PlaceId)".update.run()
        ()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private def handlers(cfg: AppConfig, http: HttpTransport) = MediaHandlers(Deps(cfg, ds, ds, plugins = generation), http)

  test("dry run: the plugin's records judged by the host — a stated NC licence refused under the default allowlist"):
    val http = fake
    val r = handlers(base, http).importMedia(MediaImportRequest(Collection, place = Some(PlaceId)))
      .fold(e => fail(e.message), identity)
    assertEquals(r.provider, Collection)
    assertEquals(r.samples.map(s => (s.key, s.license, s.basis)),
      List(("by", "CC-BY-4.0", "place_of_origin = Zqplenai; 1400–1300 BCE within 1600–1100 BCE")))
    val rejected = r.rejected.map(c => c.key -> c.n).toMap
    assertEquals(rejected.get(s"licence cc-by-nc not allowed for $Collection"), Some(1))
    assertEquals(rejected.get(s"licence all-rights-reserved not allowed for $Collection"), Some(1))
    assertEquals(rejected.get("its place is not on the map"), Some(1))
    assertEquals(r.byLicence.map(c => (c.key, c.allowed)).toMap,
      Map("CC-BY-4.0" -> true, "CC-BY-NC-4.0" -> false, "all-rights-reserved" -> false, "CC0-1.0" -> true))
    assert(r.failed.exists(_.contains("a record the API could not describe")), r.failed.toString)
    // the plugin's request went through the host's polite client, with the host's User-Agent
    val (url, headers) = http.requests.head
    assert(url.startsWith("https://zq.example/search?q=Zqplenae"), url)
    assertEquals(headers.get("User-Agent"), Some(HttpPolicy.userAgent(base.media.userAgent)))
    assertEquals(headers.get("Accept"), Some("application/json"))
    assertEquals(MediaRepo.thumbs(ds, "place", PlaceId), Nil, "a dry run stores nothing")

  test("allowed for this collection by the user, the NC record is stored; all-rights-reserved never is"):
    val cfg = allowing("cc0,public-domain,cc-by,cc-by-nc,all-rights-reserved")
    val http = fake
    val r = handlers(cfg, http).importMedia(MediaImportRequest(Collection, dryRun = false, place = Some(PlaceId)))
      .fold(e => fail(e.message), identity)
    assertEquals((r.stored, r.linked), (2, 2), r.toString)
    assert(r.rejected.exists(_.key == s"licence all-rights-reserved not allowed for $Collection"), r.rejected.toString)
    val stored = connect(ds)(sql"""SELECT a.provider_key, a.license, a.credit, m.basis, m.role FROM assets a
          JOIN media_links m ON m.asset_uid = a.uid WHERE a.provider = $Collection ORDER BY a.provider_key"""
      .query[(String, Option[String], Option[String], Option[String], String)].run().toList)
    assertEquals(stored.map(s => (s._1, s._2, s._5)),
      List(("by", Some("CC-BY-4.0"), MediaRole.Object), ("nc", Some("CC-BY-NC-4.0"), MediaRole.Object)))
    assertEquals(stored.head._3, Some("Zq bowl by, Zq Museum, CC BY 4.0"))
    assertEquals(stored.head._4, Some("place_of_origin = Zqplenai; 1400–1300 BCE within 1600–1100 BCE"))
    assert(http.requests.exists(_._1 == "https://zq.example/img/nc.jpg"), "the host downloaded the rendition")
    val checked = connect(ds)(sql"SELECT found FROM media_checks WHERE provider = $Collection AND target_id = $PlaceId"
      .query[Int].run().toList)
    assertEquals(checked, List(2))

  test("the provider list and an unknown name come from the registries; the default allowlist is the host's"):
    val h = handlers(base, fake)
    val ps = h.providers
    assertEquals(ps.map(_.name), List("wikidata", "depicts", "met", "cleveland", "aic", "loc", "pleiades", "opencontext", Collection))
    assertEquals(ps.last, MediaProviderInfo(Collection, "Zq portal objects", "places", "A test fixture's collection", "zq-plugin",
      builtin = false, base.media.collections.licences))
    assert(h.importMedia(MediaImportRequest("rijks")).left.exists(_.message.contains(s"opencontext, $Collection;")))
    assert(handlers(base, fake).importMedia(MediaImportRequest(Collection, event = Some("e1"))).isLeft, "a collection is about places")
    // the plugin switched off: its collection is gone from the import and the list
    val off = PluginHost.build(Plugins.providers :+ ZqPlugin(), ConfigFactory.empty, ConfigFactory.empty,
      Plugins.services(() => new spi.PluginHttp { def get(u: String, hs: Map[String, String]) = Left("no") }, ConfigFactory.empty))
    val h2 = MediaHandlers(Deps(base, ds, ds, plugins = off), fake)
    assert(!h2.providers.exists(_.name == Collection))
    assert(h2.importMedia(MediaImportRequest(Collection)).left.exists(_.message.contains("unknown media provider 'zq-portal'")))

  test("through the server: GET /api/media/providers lists the plugin's collection; about lists what it registered"):
    val client = HttpClient.newHttpClient()
    supervised {
      val b   = Main.server(base, "127.0.0.1", 0, MediaHandlers.NoThumbnails, plugins = generation).start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        val r = client.send(HttpRequest.newBuilder(URI.create(s"$url/api/media/providers")).build(),
          HttpResponse.BodyHandlers.ofString())
        assertEquals(r.statusCode, 200, r.body)
        val ps = decode[List[MediaProviderInfo]](r.body).fold(e => fail(s"$e: ${r.body}"), identity)
        assertEquals(ps.map(_.name).last, Collection)
        assert(ps.find(_.name == "met").exists(p => p.builtin && p.plugin == "media-met"), ps.toString)
        assert(Markdown.mediaProviders(ps).contains(s"**`$Collection`** — Zq portal objects (places; plugin `zq-plugin`"))
        // the about report lists the plugin and what it registered
        val about = client.send(HttpRequest.newBuilder(URI.create(s"$url/api/about")).build(), HttpResponse.BodyHandlers.ofString())
        assert(about.body.contains("\"media-collection:zq-portal\""), about.body.take(3000))
      finally b.stop()
    }

/** The fixture plugin's (empty) settings. */
final case class ZqPluginConfig() derives hearth.kindlings.sconfigderivation.ConfigReader
