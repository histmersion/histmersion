package com.histmersion
package server.media
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.plugin.Header

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.media.MediaMarkdown

import com.histmersion.domain.media.assets.AssetFixtures
import com.histmersion.server.MediaWiring

import com.histmersion.domain.catalogue.places.PlaceFixtures

import com.histmersion.server.ServerPlugins

import com.histmersion.pluginhost.{Generation, HttpTransport, PluginHost}

import ox.supervised
import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.util.database.Migrate
import com.histmersion.plugin as spi
import com.histmersion.server.{Composition, Deps}
import org.ekrich.config.ConfigFactory

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import com.histmersion.domain.media.MediaRole
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.api.catalogue.Place
import com.histmersion.api.media.MediaImportRequest
import com.histmersion.api.media.MediaCount
import com.histmersion.api.media.MediaItem
import com.histmersion.api.media.MediaProvider
import com.histmersion.api.plugins.PluginDescription
import com.histmersion.domain.media.imports.Target
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.plugin.Registry
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.Spi
import com.histmersion.plugin.MediaCollection
import com.histmersion.plugin.MediaPlace
import com.histmersion.plugin.MediaRights
import com.histmersion.plugin.Period
import com.histmersion.plugin.Year
import com.histmersion.api.catalogue.Event
import com.histmersion.config.ServerConfig
import com.histmersion.util.database.NewtypeColumns.given
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** A THIRD-PARTY plugin's media collection (docs/research-plugins.md §6, §11) run by `media import` through the
  * registries, against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped otherwise): the plugin states each record's
  * licence, and the HOST judges it — a provider-stated CC BY-NC licence is refused under the default allowlist and
  * stored only once `media.collections.per-collection` allows it for that collection; an all-rights-reserved one never,
  * whatever the config says. The host's match ties a record to the place (its basis on the link), its polite client
  * carries the plugin's requests (the host's User-Agent, never the plugin's), and the provider list shows the
  * collection. No socket: the plugin's search and the pictures are answered by a fake. Fixtures `zqpl_*`.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class PluginMediaImportSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val Collection = MediaItem.Provider("zq-portal")
  private val CollectionAdd = Registry.AddId("zq-portal")
  private val CollectionKey = MediaPluginCollectionsConfig.PerCollectionKey("zq-portal")
  private val PlaceId = Place.Id("zqpl_site")
  private val PlaceTarget = Target.Id("zqpl_site")

  /** The fixture plugin: one collection whose search asks its API through the host's client and answers four records —
    * CC BY, CC BY-NC, all rights reserved, and a CC0 one from another place.
    */
  final private class ZqPlugin extends Plugin:
    type Config = ZqPluginConfig
    val info =
      Plugin.Info(Plugin.Info.Id("zq-plugin"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
    override def defaultsResource = Plugin.DefaultsResource("zq-none.conf")
    def register(c: ZqPluginConfig, ctx: Plugin.Context) =
      ctx.mediaCollections.add(CollectionAdd, ZqCollection)

  private object ZqCollection extends MediaCollection:
    val title = MediaCollection.Title("Zq portal objects")
    override val description = MediaCollection.Description("A test fixture's collection")
    val role = MediaCollection.Object
    override val requestIntervalMs = MediaCollection.RequestIntervalMs(0)
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    def search(place: MediaPlace, http: Plugin.Http) =
      // the plugin asks with its own User-Agent: the host's replaces it
      http
        .get(
          Plugin.Http.Url(s"https://zq.example/search?q=${place.label}"),
          Map(
            Header.Name("User-Agent") -> Header.Value("zq-bot/1.0"),
            Header.Name("Accept") -> Header.Value("application/json")
          )
        )
        .map { _ =>
          def item(key: spi.MediaItem.Key, licence: spi.Licence, where: spi.MediaItem.Geography.Value) =
            Right(
              spi.MediaItem(
                key,
                spi.MediaItem.Title(s"Zq bowl $key"),
                spi.MediaItem.PageUrl(s"https://zq.example/objects/$key"),
                Some(spi.MediaItem.ImageUrl(s"https://zq.example/img/$key.jpg")),
                Right(
                  MediaRights(
                    licence,
                    author = Some(MediaRights.Author("Zq Museum")),
                    credit = MediaRights.Credit(s"Zq bowl $key, Zq Museum, $licence")
                  )
                ),
                geography = List(spi.MediaItem.Geography.Field("place_of_origin") -> where),
                period = Period.from(Year(-1400), Year(-1300)).toOption
              )
            )
          List(
            item(spi.MediaItem.Key("by"), spi.Licence("CC BY 4.0"), spi.MediaItem.Geography.Value("Zqplenai")),
            item(spi.MediaItem.Key("nc"), spi.Licence("CC BY-NC 4.0"), spi.MediaItem.Geography.Value("Zqplenai")),
            item(
              spi.MediaItem.Key("arr"),
              spi.Licence("All rights reserved"),
              spi.MediaItem.Geography.Value("Zqplenai")
            ),
            item(
              spi.MediaItem.Key("far"),
              spi.Licence("https://creativecommons.org/publicdomain/zero/1.0/"),
              spi.MediaItem.Geography.Value("Zqpl Nowhere")
            ),
            Left(
              Problem.Unclassified(Legacy.Text("a record the API could not describe"))
            )
          )
        }

  private def fake: FakeMediaHttp = FakeMediaHttp { url =>
    if url.startsWith("https://zq.example/search") then FakeMediaHttp.json("""{"ok":true}""")
    else if url.startsWith("https://zq.example/img/") then
      FakeMediaHttp.bytes(Array[Byte](-1, -40, -1) ++ url.getBytes("UTF-8"), "image/jpeg")
    else Left(s"unexpected $url")
  }

  private def generation: Generation =
    ServerPlugins.offlineWith(
      List(ZqPlugin()),
      ConfigFactory.parseString("histmersion.plugins.zq-plugin.enabled = true")
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val base =
    val b = AppConfig.load()
    b.modify(_.db)
      .setTo(b.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.media)
      .setTo(b.media.modify(_.requestIntervalMs).setTo(MediaConfig.RequestIntervalMs(0)))
  private lazy val ds =
    val d = testPool(base.db)
    Migrate.run(d)
    d

  /** The config with `allow` as this collection's own allowlist. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def allowing(allow: String): AppConfig =
    base
      .modify(_.media)
      .setTo(
        base.media
          .modify(_.collections)
          .setTo(
            base.media.collections
              .modify(_.perCollection)
              .setTo(Map(CollectionKey -> MediaPluginCollectionsConfig.Licences(allow)))
          )
      )

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM media_links WHERE target_id LIKE 'zqpl%'".update.run()
    val _ = sql"DELETE FROM assets WHERE provider = $Collection".update.run()
    val _ = sql"DELETE FROM media_checks WHERE target_id LIKE 'zqpl%'".update.run()
    val _ = sql"DELETE FROM finding_places WHERE place_id LIKE 'zqpl%'".update.run()
    val _ = sql"DELETE FROM findings WHERE citekey LIKE 'zqpl%'".update.run()
    val _ = sql"DELETE FROM sources WHERE citekey LIKE 'zqpl%'".update.run()
    val _ = sql"DELETE FROM places WHERE id LIKE 'zqpl%'".update.run()

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  override def beforeAll(): Unit =
    if testDb.isDefined then
      clean()
      val _ = PlaceFixtures.upsertPlaces(
        ds,
        List(
          Place.Input(
            Place(
              PlaceId,
              Place.Label("Zqplenae"),
              Place.KindValue(domain.catalogue.Place.Kind.Site),
              Some(Place.Geometry("""{"type":"Point","coordinates":[142.0,-43]}"""))
            ),
            List(Place.Name(Place.Name.Value("Zqplenae")), Place.Name(Place.Name.Value("Zqplenai")))
          )
        )
      )
      connect(ds):
        sql"INSERT INTO sources (citekey, title, creator) VALUES ('zqpl2020_x', 'Zqpl', jsonb_build_object('kind', 'unknown'))".update
          .run()
        sql"""INSERT INTO findings (id, citekey, claim, period, creator) VALUES
              ('zqpl2020_x#1', 'zqpl2020_x', 'a', int8range(-1600, -1100, '[]'), jsonb_build_object('kind', 'unknown'))""".update
          .run()
        sql"INSERT INTO finding_places (finding_id, place_id) VALUES ('zqpl2020_x#1', $PlaceId)".update.run()
        ()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private def handlers(cfg: AppConfig, http: HttpTransport) =
    MediaWiring(Deps(cfg, ds, ds, plugins = generation), http).imports

  test("dry run: the plugin's records judged by the host — a stated NC licence refused under the default allowlist"):
    val http = fake
    val r = handlers(base, http)
      .importMedia(MediaImportRequest(Collection, place = Some(PlaceId)))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(r.provider, Collection)
    assertEquals(
      r.samples.map(s => (s.key, s.license, s.basis)),
      List(("by", "CC-BY-4.0", "place_of_origin = Zqplenai; 1400–1300 BCE within 1600–1100 BCE"))
    )
    val rejected = r.rejected.map(c => c.key -> c.n).toMap
    // the NC record and the all-rights-reserved one: a reason each, naming its licence family
    assertEquals(rejected.get(MediaCount.Key("licence cc-by-nc not allowed")), Some(1))
    assertEquals(rejected.get(MediaCount.Key("licence all-rights-reserved not allowed")), Some(1))
    assertEquals(rejected.get(MediaCount.Key("its place is not on the map")), Some(1))
    assertEquals(
      r.byLicence.map(c => (c.key, c.allowed)).toMap,
      Map("CC-BY-4.0" -> true, "CC-BY-NC-4.0" -> false, "all-rights-reserved" -> false, "CC0-1.0" -> true)
    )
    assert(r.failed.exists(_.value.contains("a record the API could not describe")), r.failed.toString)
    // the plugin's request went through the host's polite client, with the host's User-Agent
    val (url, headers) = http.requests.head
    assert(url.startsWith("https://zq.example/search?q=Zqplenae"), url)
    assertEquals(
      headers.get("User-Agent"),
      Some(HttpPolicy.userAgent(HttpPolicy.UserAgent(base.media.userAgent.value)).value)
    )
    assertEquals(headers.get("Accept"), Some("application/json"))
    assertEquals(AssetFixtures.thumbs(ds, Target.TargetType("place"), PlaceTarget), Nil, "a dry run stores nothing")

  test("allowed for this collection by the user, the NC record is stored; all-rights-reserved never is"):
    val cfg = allowing("cc0,public-domain,cc-by,cc-by-nc,all-rights-reserved")
    val http = fake
    val r = handlers(cfg, http)
      .importMedia(MediaImportRequest(Collection, dryRun = MediaImportRequest.DryRun(false), place = Some(PlaceId)))
      .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((r.stored, r.linked), (2, 2), r.toString)
    assert(
      r.rejected.exists(c => c.key.value == "licence all-rights-reserved not allowed" && c.n == MediaCount.N(1)),
      r.rejected.toString
    )
    val stored = connect(ds)(
      sql"""SELECT a.provider_key, a.license, a.credit, m.basis, m.role FROM assets a
          JOIN media_links m ON m.asset_uid = a.uid WHERE a.provider = $Collection ORDER BY a.provider_key"""
        .query[(String, Option[String], Option[String], Option[String], String)]
        .run()
        .toList
    )
    assertEquals(
      stored.map(s => (s._1, s._2, s._5)),
      List(("by", Some("CC-BY-4.0"), MediaRole.Object), ("nc", Some("CC-BY-NC-4.0"), MediaRole.Object))
    )
    assertEquals(stored.head._3, Some("Zq bowl by, Zq Museum, CC BY 4.0"))
    assertEquals(stored.head._4, Some("place_of_origin = Zqplenai; 1400–1300 BCE within 1600–1100 BCE"))
    assert(http.requests.exists(_._1 == "https://zq.example/img/nc.jpg"), "the host downloaded the rendition")
    val checked = connect(ds)(
      sql"SELECT found FROM media_checks WHERE provider = $Collection AND target_id = $PlaceId"
        .query[Int]
        .run()
        .toList
    )
    assertEquals(checked, List(2))

  test("the provider list and an unknown name come from the registries; the default allowlist is the host's"):
    val h = MediaWiring(Deps(base, ds, ds, plugins = generation), fake)
    val ps = h.imports.providers
    assertEquals(
      ps.map(_.name),
      List("wikidata", "depicts", "met", "cleveland", "aic", "loc", "pleiades", "opencontext", Collection)
    )
    assertEquals(
      ps.last,
      MediaProvider.Info(
        Collection,
        MediaProvider.Info.Title("Zq portal objects"),
        MediaProvider.Info.Over("places"),
        MediaProvider.Info.Description("A test fixture's collection"),
        PluginDescription.Id("zq-plugin"),
        builtin = MediaProvider.Info.Builtin(false),
        MediaProvider.Info.Licences(base.media.collections.licences.value)
      )
    )
    assert(
      h.imports
        .importMedia(MediaImportRequest(MediaItem.Provider("rijks")))
        .left
        .exists(_.problems.sentences.mkString("; ").contains(s"opencontext, $Collection;"))
    )
    assert(
      handlers(base, fake).importMedia(MediaImportRequest(Collection, event = Some(Event.Id("e1")))).isLeft,
      "a collection is about places"
    )
    // the plugin switched off: its collection is gone from the import and the list
    val off = ServerPlugins.offlineWith(List(ZqPlugin()), ConfigFactory.empty)
    val h2 = MediaWiring(Deps(base, ds, ds, plugins = off), fake)
    assert(!h2.imports.providers.exists(_.name == Collection))
    assert(
      h2.imports
        .importMedia(MediaImportRequest(Collection))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("unknown media provider 'zq-portal'"))
    )

  test("through the server: GET /api/media/providers lists the plugin's collection; about lists what it registered"):
    val client = HttpClient.newHttpClient()
    supervised {
      val b = Composition
        .server(
          base,
          ServerConfig.Host("127.0.0.1"),
          ServerConfig.Port(0),
          MediaWiring.NoThumbnails,
          plugins = generation
        )
        .start()
      val url = s"http://127.0.0.1:${b.port}"
      try
        val r = client.send(
          HttpRequest.newBuilder(URI.create(s"$url/api/media/providers")).build(),
          HttpResponse.BodyHandlers.ofString()
        )
        assertEquals(r.statusCode, 200, r.body)
        val ps = RawJson.decode[List[MediaProvider.Info]](r.body).fold(e => fail(s"$e: ${r.body}"), identity)
        assertEquals(ps.map(_.name).last, Collection)
        assert(
          ps.find(_.name == MediaItem.Provider("met"))
            .exists(p => p.builtin.value && p.plugin == PluginDescription.Id("media-met")),
          ps.toString
        )
        assert(
          MediaMarkdown
            .mediaProviders(ps)
            .contains(s"**`$Collection`** — Zq portal objects (places; plugin `zq-plugin`")
        )
        // the about report lists the plugin and what it registered
        val about = client.send(
          HttpRequest.newBuilder(URI.create(s"$url/api/about")).build(),
          HttpResponse.BodyHandlers.ofString()
        )
        assert(about.body.contains("\"media-collection:zq-portal\""), about.body.take(3000))
      finally b.stop()
    }

/** The fixture plugin's (empty) settings. */
final case class ZqPluginConfig() derives hearth.kindlings.sconfigderivation.ConfigReader
