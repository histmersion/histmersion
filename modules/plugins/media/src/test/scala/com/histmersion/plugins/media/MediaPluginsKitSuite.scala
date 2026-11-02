package com.histmersion.plugins.media

import com.histmersion.config.AppConfig
import com.histmersion.plugin.*
import com.histmersion.plugin.testkit.{HttpMode, PluginHarness, StubAnswer}
import com.histmersion.pluginhost.{FirstPartySettings, HostLicences, PluginHost}
import hearth.kindlings.sconfigderivation.ConfigReader
import org.ekrich.config.ConfigFactory

/** Histmersion's own media collections run as any plugin author runs theirs: through the plugin testkit, which loads a
  * plugin from the class path as the server does (the host's own `histmersion.media` settings supplied to a first-party
  * plugin), judges each record as the server's import does, and answers its requests from canned answers — never the
  * network. The two key-gated ones are off by default and are loaded with a fixture key.
  */
class MediaPluginsKitSuite extends munit.FunSuite:

  private def fixture(name: String): String =
    scala.io.Source.fromResource(s"media/$name")(using scala.io.Codec.UTF8).mkString

  private def json(s: String) = StubAnswer.text(s, "application/json")

  private def load(
      id: String,
      stubs: Map[String, StubAnswer] = Map.empty,
      config: String = "",
      env: Map[String, String] = Map.empty
  ) =
    PluginHarness.load(id, config, http = HttpMode.Stub(stubs), env = env).fold(e => fail(e), identity)

  /** Histmersion's own `media.*` defaults, as the host supplies them to a media plugin. */
  private lazy val media: MediaSettings =
    val loaded = AppConfig.loadWithUserText(ConfigFactory.empty, "").fold(e => fail(e), identity)
    val supplied = FirstPartySettings.fallback("media-met", loaded.merged)
    summon[ConfigReader[MediaPluginConfig]].from(supplied.root).fold(e => fail(e.toString), _.media)

  private val collections = List(
    "media-wikidata" -> "wikidata",
    "media-depicts" -> "depicts",
    "media-met" -> "met",
    "media-cleveland" -> "cleveland",
    "media-aic" -> "aic",
    "media-loc" -> "loc",
    "media-pleiades" -> "pleiades",
    "media-opencontext" -> "opencontext",
    "media-smithsonian" -> "smithsonian",
    "media-europeana" -> "europeana"
  )
  private val keyed = Set("media-smithsonian", "media-europeana")

  test("each plugin loads from the class path with its defaults and the host's settings, and registers its collection"):
    collections.foreach { (id, collection) =>
      val h = load(id)
      assertEquals(h.contributions.mediaCollections, List(collection), id)
      assertEquals(h.contributions.sourceFetchers ++ h.contributions.eventCatalogues, Nil, id)
      assertEquals(h.plugin.info.version, "0.1.0", id)
      assertEquals(h.plugin.info.spiVersion, Spi.Version, id)
      assertEquals(h.requests, Nil, s"$id asked an upstream while registering")
    }

  test("the keyless plugins are on by default, the two that need a key of the user's own off, with no key"):
    collections.foreach { (id, _) =>
      val h = load(id)
      val defaults = PluginHost.defaults(h.plugin).fold(e => fail(e), identity).resolve()
      assertEquals(defaults.getBoolean("enabled"), !keyed(id), id)
      if keyed(id) then assert(defaults.getIsNull("api-key"), s"$id ships no key")
    }

  test("a media plugin has no setting the host owns: an endpoint in its block is refused"):
    val refused = PluginHarness.load("media-met", "api-url = \"https://elsewhere.example\"", http = HttpMode.Offline)
    assert(refused.left.exists(_.contains("unknown setting")), refused.toString)

  test("media-met: a search at the host's endpoint, each object read, judged as the server judges it"):
    val api = media.met.apiUrl
    val ids = List(252448L, 248284L, 252447L, 252446L, 253574L)
    val obj = fixture("met-object.json")
    val stubs =
      Map(MetCollection.searchUrl(api, "Mycenae", media.met.candidates) -> json(fixture("met-search.json"))) ++
        Map(
          MetCollection.objectUrl(api, 252448L) -> json(obj),
          MetCollection.objectUrl(api, 248284L) -> json(
            obj.replace("252448", "248284").replace("\"isPublicDomain\": true", "\"isPublicDomain\": false")
          )
        )
    val h = load("media-met", stubs)
    val r = h.searchMedia("met", MediaPlace("pleiades_570491", "Mycenae"))
    assertEquals(r.failure, None)
    assertEquals(
      r.requests,
      s"GET ${MetCollection.searchUrl(api, "Mycenae", media.met.candidates)}" ::
        ids.map(id => s"GET ${MetCollection.objectUrl(api, id)}")
    )
    assertEquals(r.kept.map(v => (v.key, v.licence)), List(("252448", Some("CC0-1.0"))))
    assertEquals(
      r.verdicts.find(_.key == "248284").flatMap(_.refused),
      Some("not Open Access (isPublicDomain is false)")
    )
    assertEquals(
      r.verdicts.count(_.item.isEmpty),
      3,
      "the objects the stand-in has no record of: unreadable, not the end"
    )

  test("media-opencontext: the host lends its licence reading — a record stated by its URL is read as CC BY 4.0"):
    val api = media.openContext.apiUrl
    val stubs = Map(
      OpenContext.searchUrl(api, "Petra", media.openContext.candidates) -> json(fixture("oc-search.json")),
      OpenContext.itemUrl(api, "f589a368-62ed-43df-5738-4c84d2f5f765") -> json(fixture("oc-media.json"))
    )
    val r = load("media-opencontext", stubs).searchMedia("opencontext", MediaPlace("pleiades_697725", "Petra"))
    val bowls = r.verdicts.find(_.key == "f589a368-62ed-43df-5738-4c84d2f5f765").getOrElse(fail(r.toString))
    assertEquals((bowls.licence, bowls.refused), (Some("CC-BY-4.0"), None))
    assert(bowls.item.exists(_.imageUrl.exists(_.contains("ia-previews"))), "completed from its media item")

  test("media-smithsonian with a fixture key: registered, its key a header, never in a request line"):
    val key = "si-fixture-key-0123"
    val units = CollectionSettings.items(media.smithsonian.excludeProviders)
    val url = Smithsonian.searchUrl(media.smithsonian.apiUrl, "Egypt", media.smithsonian.candidates, units)
    val h = load(
      "media-smithsonian",
      Map(url -> json(fixture("si-search.json"))),
      "enabled = true\napi-key = \"env:ZQMT_SI_KEY\"",
      env = Map("ZQMT_SI_KEY" -> key)
    )
    assertEquals(h.contributions.mediaCollections, List("smithsonian"))
    val r = h.searchMedia("smithsonian", MediaPlace("geonames_357994", "Egypt"))
    assertEquals(r.requests, List(s"GET $url"))
    assert(r.requests.forall(!_.contains(key)))
    assertEquals(r.kept.map(_.key).headOption, Some("fsg_F1909.1185"))
    assertEquals(r.kept.flatMap(_.licence).distinct, List("CC0-1.0"))

  test("media-europeana with a fixture key: registered; switched off by its own block, not registered"):
    val on = load("media-europeana", config = "api-key = \"env:ZQMT_EU_KEY\"", env = Map("ZQMT_EU_KEY" -> "eu-key"))
    assertEquals(on.contributions.mediaCollections, List("europeana"))
    val off = PluginHarness.load("media-europeana", "enabled = false", http = HttpMode.Offline, env = Map.empty)
    assert(off.left.exists(_.contains("disabled")), off.toString)
    val unset = PluginHarness.load(
      "media-europeana",
      "api-key = \"env:ZQMT_NOT_SET\"",
      http = HttpMode.Offline,
      env = Map.empty
    )
    assert(unset.left.exists(_.contains("ZQMT_NOT_SET")), unset.toString)

  test("each collection's roles are ones the host stores"):
    val h = load("media-wikidata")
    val wikidata = WikidataPictures(media, HostLicences)
    assertEquals(wikidata.roles, Set("image", "locator-map", "plan", "aerial-view", "route-map"))
    assert(wikidata.roles.subsetOf(MediaCollection.Roles))
    assertEquals(CommonsDepictions(media, HostLicences).roles, Set(MediaCollection.Depiction))
    assertEquals(MetCollection(media.met).roles, Set(MediaCollection.Object))
    assertEquals(LocCollection(media.loc, media.thumbPx).roles, Set(MediaCollection.Map))
    assertEquals(h.contributions.mediaCollections, List("wikidata"))
