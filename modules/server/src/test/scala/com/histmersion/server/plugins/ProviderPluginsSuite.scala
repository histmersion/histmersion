package com.histmersion.server.plugins

import com.histmersion.core.{MediaLicence, MediaRecord}
import com.histmersion.plugin.*
import com.histmersion.server.events.{BuiltinCatalogues, HostEventCatalogue, PluginEvents}
import com.histmersion.server.media.{BuiltinMedia, MediaImporters, PluginCollection}
import org.ekrich.config.ConfigFactory

import scala.collection.immutable.ListMap

/** Media collections and event catalogues as plugins (docs/research-plugins.md §6, §19): the built-in
  * ones registered through the plugin host and switched off like any plugin; a third-party plugin's
  * collection and catalogue registered beside them, refused under a host name; the provider lists
  * from the registries; the host's reading of a stated licence and of a catalogue's record; and a
  * reload that rebuilds a media/events plugin only when its own block changed. No network. */
class ProviderPluginsSuite extends munit.FunSuite:

  private val offline = new PluginHttp { def get(u: String, h: Map[String, String]) = Left("offline") }

  private def build(plugins: List[Plugin], user: String = ""): Generation =
    PluginHost.build(plugins, ConfigFactory.parseString(user), ConfigFactory.empty,
      Plugins.services(() => offline, ConfigFactory.empty))

  /** A third-party plugin: one media collection and one event catalogue under ids of its choosing. */
  private final class Portal(collection: String = "zq-portal", catalogue: String = "zq-quakes", role: String = "object")
      extends Plugin:
    type Config = PortalConfig
    val info = PluginInfo("zq-plugin", "0.1.0", Spi.Version)
    override def defaultsResource = "zq-none.conf"
    def register(c: PortalConfig, ctx: PluginContext) =
      for
        _ <- ctx.mediaCollections.add(collection, Collection(role))
        _ <- ctx.eventCatalogues.add(catalogue, Catalogue)
      yield ()

  private final class Collection(val role: String) extends MediaCollection:
    val title = "Zq portal objects"
    def search(place: MediaPlace, http: PluginHttp) = Right(Nil)

  private object Catalogue extends EventCatalogue:
    val title = "Zq quakes"; val licence = "CC0-1.0"; val citation = "Zq (2026)"; val home = "https://zq.example"
    def parts(q: CatalogueQuery) = List("all")
    def read(part: String, q: CatalogueQuery, http: PluginHttp) = Right(CataloguePage(Nil))

  private val portalOn = "histmersion.plugins.zq-plugin.enabled = true"

  // ── the built-ins ──────────────────────────────────────────────────────────────────────────────

  test("the built-in media collections and event catalogues register as plugins, enabled by default"):
    val g = Plugins.offline
    assert(g.report.ok, g.report.toString)
    assertEquals(g.report.plugins.map(_.id).toSet, BuiltinMedia.pluginIds ++ BuiltinCatalogues.pluginIds)
    assert(g.report.plugins.forall(_.state == "registered"), g.report.toString)
    assertEquals(g.report.plugins.find(_.id == "media-met").map(_.provides), Some(List("media-collection:met")))
    assertEquals(g.report.plugins.find(_.id == "events-gvp").map(_.provides), Some(List("event-catalogue:gvp")))
    assertEquals(BuiltinMedia.ordered(g.registries.mediaCollections.keys.toList),
      List("wikidata", "depicts", "met", "cleveland", "aic", "loc", "pleiades", "opencontext"))
    assertEquals(BuiltinCatalogues.ordered(g.registries.eventCatalogues.keys.toList), List("gvp", "noaa", "wikidata"))
    assert(g.registries.sourceFetchers.isEmpty, "offline: no fetcher")

  test("the full built-in set: the fetchers, the collections and the catalogues; no plugin needs a setting"):
    val all = Plugins.builtins(com.histmersion.core.AppConfig.load(), () => fail("no HTTP at registration"))
    val g   = build(all)
    assert(g.report.ok, g.report.toString)
    assertEquals(g.report.plugins.size, 7 + 8 + 3) // fetchers (four open-access indexes, D4), collections, catalogues

  test("a built-in switched off is out of its registry; the import names the switch"):
    val g = build(Plugins.providers, "histmersion.plugins { media-met.enabled = false, events-noaa.enabled = false }")
    assertEquals(g.report.plugins.find(_.id == "media-met").map(_.state), Some("disabled"))
    assert(!g.registries.mediaCollections.contains("met"))
    assertEquals(MediaImporters.lookup("met", g.registries.mediaCollections).left.toOption,
      Some("Met objects (met) is switched off (histmersion.plugins.media-met.enabled = false)"))
    assertEquals(BuiltinCatalogues.resolve("ncei", g.registries.eventCatalogues).left.toOption,
      Some("NOAA NCEI Natural Hazards (noaa) is switched off (histmersion.plugins.events-noaa.enabled = false)"))
    // the environment switches one off too
    val e = PluginHost.build(Plugins.providers, ConfigFactory.empty,
      ConfigFactory.parseString("HISTMERSION_PLUGIN_MEDIA_LOC_ENABLED = false"),
      Plugins.services(() => offline, ConfigFactory.empty))
    assertEquals(e.report.plugins.find(_.id == "media-loc").map(_.state), Some("disabled"))

  test("names: aliases resolve to the built-ins; an unknown name lists what is registered, in order"):
    val c = Plugins.offline.registries.mediaCollections
    assertEquals(MediaImporters.lookup("", c).map(_._1), Right("wikidata"))
    assertEquals(MediaImporters.lookup("Wikimedia-Commons", c).map(_._1), Right("wikidata"))
    assertEquals(MediaImporters.lookup("cma", c).map(_._1), Right("cleveland"))
    assertEquals(MediaImporters.lookup("oc", c).map(_._1), Right("opencontext"))
    assertEquals(MediaImporters.lookup("art-institute-chicago", c).map(_._1), Right("aic"))
    val unknown = MediaImporters.lookup("rijks", c).left.toOption.get
    assert(unknown.startsWith("unknown media provider 'rijks' (one of: wikidata, depicts, met, cleveland, aic, loc, pleiades, opencontext"),
      unknown)
    val e = Plugins.offline.registries.eventCatalogues
    assertEquals(BuiltinCatalogues.resolve("Smithsonian", e), Right("gvp"))
    assertEquals(BuiltinCatalogues.resolve("wd", e), Right("wikidata"))
    assertEquals(BuiltinCatalogues.resolve("usgs", e), Left("unknown event catalogue 'usgs' (one of: gvp, noaa, wikidata)"))

  // ── a third-party plugin ───────────────────────────────────────────────────────────────────────

  test("a third-party plugin's collection and catalogue register beside the built-ins and are listed after them"):
    val g = build(Plugins.providers :+ Portal(), portalOn)
    assertEquals(g.report.plugins.find(_.id == "zq-plugin").map(_.provides),
      Some(List("media-collection:zq-portal", "event-catalogue:zq-quakes")))
    val c = g.registries.mediaCollections
    assertEquals(BuiltinMedia.ordered(c.keys.toList).last, "zq-portal")
    assertEquals(MediaImporters.lookup("ZQ-Portal", c).map(_._1), Right("zq-portal"))
    val infos = MediaImporters.infos(c, n => if n == "zq-portal" then "zq-plugin" else "?", _ => "cc0,cc-by")
    assertEquals(infos.map(_.name).take(2), List("wikidata", "depicts"))
    assertEquals(infos.last, com.histmersion.core.MediaProviderInfo("zq-portal", "Zq portal objects", "places", "",
      "zq-plugin", builtin = false, "cc0,cc-by"))
    assert(infos.find(_.name == "met").exists(i => i.builtin && i.plugin == "media-met"), infos.toString)
    val cats = BuiltinCatalogues.infos(g.registries.eventCatalogues)
    assertEquals(cats.map(_.id), List("gvp", "noaa", "wikidata", "zq-quakes"))
    assertEquals(cats.last, com.histmersion.core.CatalogProviderInfo("zq-quakes", "Zq quakes", "CC0-1.0", "Zq (2026)", "https://zq.example"))
    assertEquals(BuiltinCatalogues.resolve("zq-quakes", g.registries.eventCatalogues), Right("zq-quakes"))

  test("a plugin may not take a name of the host's providers, nor a role the host does not store"):
    // a registered built-in's name is taken; an alias or a stored provider's name is reserved even when
    // its built-in is switched off
    val dup = build(Plugins.providers :+ Portal(collection = "met"), portalOn)
    assert(dup.report.plugins.find(_.id == "zq-plugin").exists(_.error.exists(_.contains("'met' is already registered"))))
    assert(dup.registries.mediaCollections.get("met").exists(_.isInstanceOf[BuiltinMedia.Entry]), "the built-in is untouched")
    for name <- List("met", "commons", "met-museum", "wikimedia-commons", "pleiades", "source", "calibre", "oc") do
      val g = build(Plugins.providers :+ Portal(collection = name),
        s"$portalOn\nhistmersion.plugins { media-met.enabled = false, media-pleiades.enabled = false }")
      val st = g.report.plugins.find(_.id == "zq-plugin").get
      assertEquals(st.state, "failed", name)
      assert(st.error.exists(_.contains("one of Histmersion's own media providers")), st.toString)
    val cat = build(Plugins.providers :+ Portal(catalogue = "volcano"), portalOn).report.plugins.find(_.id == "zq-plugin").get
    assert(cat.error.exists(_.contains("one of Histmersion's own event catalogues")), cat.toString)
    val role = build(Plugins.providers :+ Portal(role = "cover"), portalOn).report.plugins.find(_.id == "zq-plugin").get
    assert(role.error.exists(_.contains("role 'cover' is not one of image, map, object")), role.toString)

  test("a host whose context predates media and events refuses those additions (SPI version stays 1)"):
    val ctx = new PluginContext:
      def http = offline
      val log = new PluginLog { def info(m: String) = (); def warn(m: String) = () }
      def env(n: String) = None
      def secret(s: Secret) = Left("none")
      def secretVaults = Registry.refusing("x")
    assert(ctx.mediaCollections.add("x", Collection("object")).isLeft)
    assert(ctx.eventCatalogues.add("x", Catalogue).isLeft)
    assertEquals(Spi.Version, 1)

  // ── reloads ────────────────────────────────────────────────────────────────────────────────────

  test("reload: a host key (acquire.*) rebuilds the fetchers and third-party plugins, never the media/events built-ins"):
    val g1 = build(Plugins.providers :+ Portal(), portalOn)
    val carried = Plugins.carried(g1, List("histmersion.acquire.chain"))
    assertEquals(carried.keySet, BuiltinMedia.pluginIds ++ BuiltinCatalogues.pluginIds)
    // their own block rebuilds them alone
    assertEquals(Plugins.carried(g1, List("histmersion.plugins.media-met.enabled")).keySet,
      BuiltinMedia.pluginIds ++ BuiltinCatalogues.pluginIds + "zq-plugin" - "media-met")
    // a media setting is no plugin key: nothing is rebuilt (the import reads it at each run)
    assertEquals(Plugins.carried(g1, List("histmersion.media.met.licences")).keySet,
      BuiltinMedia.pluginIds ++ BuiltinCatalogues.pluginIds + "zq-plugin")
    assertEquals(Plugins.carried(g1, List("histmersion.plugins.order")), Map.empty)
    assertEquals(Plugins.carried(g1, List("histmersion.plugins")), Map.empty)
    // the carried built-in is the same instance in the next generation
    val g2 = PluginHost.build(Plugins.providers :+ Portal(), ConfigFactory.parseString(portalOn), ConfigFactory.empty,
      Plugins.services(() => offline, ConfigFactory.empty), carried = carried)
    assert(g2.registries.mediaCollections("met") eq g1.registries.mediaCollections("met"))
    assertEquals(g2.rebuilt, List("zq-plugin"))

  // ── the host's reading of a plugin's records ──────────────────────────────────────────────────

  private def rights(licence: String, url: Option[String] = None, credit: String = "Zq, CC") =
    PluginCollection.rights(com.histmersion.plugin.MediaRights(licence, url, Some("A. Author"), credit))

  test("a stated licence is read by the host: ids, short names and Creative Commons URLs; unknown is never guessed"):
    assertEquals(rights("CC BY-NC 4.0").map(_.licence), Right(MediaLicence.Licence("CC-BY-NC-4.0", MediaLicence.CcByNc)))
    assertEquals(rights("CC-BY-4.0").map(_.licence.family), Right(MediaLicence.CcBy))
    assertEquals(rights("https://creativecommons.org/licenses/by-sa/4.0/").map(_.licence.family), Right(MediaLicence.CcBySa))
    assertEquals(rights("https://creativecommons.org/publicdomain/zero/1.0/").map(_.licence.family), Right(MediaLicence.Cc0))
    assertEquals(rights("Public domain").map(_.licence.family), Right(MediaLicence.PublicDomain))
    assertEquals(rights("All rights reserved").map(_.licence.family), Right(MediaLicence.AllRights))
    assertEquals(rights("free to use!").map(_.licence.family), Right(MediaLicence.Unknown))
    // an unclear statement: the licence URL decides when it is a known one
    assertEquals(rights("see link", Some("https://creativecommons.org/licenses/by/4.0/")).map(_.licence.family),
      Right(MediaLicence.CcBy))
    assertEquals(rights("CC BY 4.0", credit = "  "), Left("no credit line"))
    // whatever the family, the allowlist decides; unknown and all-rights-reserved never pass
    assert(!MediaLicence.allowed(rights("free to use!").toOption.get.licence, MediaLicence.Families.toSet))
    assert(!MediaLicence.allowed(rights("CC BY-NC 4.0").toOption.get.licence, MediaLicence.allowList("cc0,cc-by")))

  test("a plugin's item becomes the host's record: trimmed, a reversed period put right, a refusal kept"):
    val it = MediaItem(" k1 ", "  Bowl ", "https://zq.example/k1", Some(" https://zq.example/k1.jpg "),
      Right(com.histmersion.plugin.MediaRights("CC0", credit = "Zq")), geography = List("find-spot" -> "Zqenai"),
      period = Some((-1300L, -1400L)), ids = List("pleiades" -> "1"))
    val r: MediaRecord = PluginCollection.record(it)
    assertEquals((r.key, r.title, r.imageUrl, r.period), ("k1", "Bowl", Some("https://zq.example/k1.jpg"), Some((-1400L, -1300L))))
    assertEquals(r.rights.map(_.licence.family), Right(MediaLicence.Cc0))
    assertEquals(r.ids, List("pleiades" -> "1"))
    assertEquals(PluginCollection.record(it.copy(rights = Left("not open access"))).rights, Left("not open access"))

  test("a plugin's failure is a value, never an exception through the import"):
    assertEquals(PluginCollection.guard[Int](throw IllegalStateException("boom")),
      Left("the plugin failed: IllegalStateException: boom"))
    assertEquals(PluginCollection.guard[Int](null), Left("the plugin answered nothing"))

  test("a catalogue's record is checked by the host before it is stored"):
    val ok = CatalogueEvent("q-1", "https://zq.example/q-1", "Zq Earthquake", "Earthquake", Some(-1200L), Some(-1200L),
      lat = Some(38.0), lon = Some(23.0), magnitude = Some(6.5), wikidata = Some("q42"), names = List("Zq Earthquake", "Zq quake"))
    val r = PluginEvents.record("zq-quakes", ok).fold(fail(_), identity)
    assertEquals((r.provider, r.providerId, r.kind, r.wikidata, r.names), ("zq-quakes", "q-1", "earthquake", Some("Q42"), List("Zq quake")))
    assertEquals(r.timeSpan, com.histmersion.core.TimeSpan(Some(-1200L), None, None, Some(-1200L)))
    assertEquals(PluginEvents.record("x", ok.copy(wikidata = Some("not-an-item"))).map(_.wikidata), Right(None))
    assertEquals(PluginEvents.record("x", ok.copy(id = " ")), Left("a record without an id"))
    assertEquals(PluginEvents.record("x", ok.copy(label = "")), Left("q-1: no label"))
    assert(PluginEvents.record("x", ok.copy(historicity = "certain")).left.exists(_.contains("historicity 'certain'")))
    assertEquals(PluginEvents.record("x", ok.copy(notBefore = Some(-1000L), notAfter = Some(-1200L))),
      Left("q-1: its dating runs backwards"))
    assertEquals(PluginEvents.record("x", ok.copy(lat = Some(95.0))), Left("q-1: its position is not on the globe"))
    assertEquals(PluginEvents.record("x", ok.copy(lon = None)), Left("q-1: a position needs both latitude and longitude"))

  test("a plugin catalogue's record is dated as the host dates its own: a stated year ± when no span, held inside the limits"):
    import com.histmersion.core.{CatalogDating, TimeSpan}
    val base = PluginEvents.record("zq", CatalogueEvent("e", "u", "Zq Flood", "flood", None, None, lat = Some(38.0),
      lon = Some(23.0), central = Some(-1000L), plusMinus = Some(50L))).fold(fail(_), identity)
    val now = CatalogDating.Limits(2026)
    // the span the record does not give, from its stated year and ±; the statement itself kept
    val d = PluginEvents.derive(base, now)
    assertEquals(d.timeSpan, TimeSpan(Some(-1050L), None, None, Some(-950L)))
    assertEquals((d.central, d.plusMinus), (Some(-1000L), Some(50L)))
    // a span the record gives is the record's; only the limits touch it
    val spanned = base.copy(timeSpan = TimeSpan(Some(-1200L), None, None, Some(-1100L)))
    assertEquals(PluginEvents.derive(spanned, now).timeSpan, spanned.timeSpan)
    // the present and the catalogue's floor hold every point, the central year stays as stated
    val future = base.copy(central = Some(2020L), plusMinus = Some(10L))
    assertEquals(PluginEvents.derive(future, now).timeSpan, TimeSpan(Some(2010L), None, None, Some(2026L)))
    assertEquals(PluginEvents.derive(future, now).central, Some(2020L))
    val early = spanned.copy(timeSpan = TimeSpan(Some(-3500L), Some(-3100L), Some(-2950L), Some(-2900L)))
    assertEquals(PluginEvents.derive(early, CatalogDating.Limits(2026, Some(-3000L))).timeSpan,
      TimeSpan(Some(-3000L), Some(-3000L), Some(-2950L), Some(-2900L)))
    // undated and no stated year: nothing invented
    assertEquals(PluginEvents.derive(base.copy(central = None), now).timeSpan, TimeSpan())
    // derived once, derived again: the same record (what makes rederive a no-op after an import)
    List(base, spanned, future, early).foreach { r =>
      val l = CatalogDating.Limits(2026, Some(-3000L))
      val once = PluginEvents.derive(r, l)
      assertEquals(PluginEvents.derive(once, l), once)
    }

  test("a plugin catalogue's limits: the present, and its own floor when it states one"):
    val floored = new EventCatalogue:
      val title = "t"; val licence = "l"; val citation = "c"; val home = "h"
      override def floorYear = Some(-9700L)
      def parts(q: CatalogueQuery) = Nil
      def read(p: String, q: CatalogueQuery, h: PluginHttp) = Left("no")
    val throws = new EventCatalogue:
      val title = "t"; val licence = "l"; val citation = "c"; val home = "h"
      override def floorYear = throw IllegalStateException("boom")
      def parts(q: CatalogueQuery) = Nil
      def read(p: String, q: CatalogueQuery, h: PluginHttp) = Left("no")
    import com.histmersion.core.CatalogDating.Limits
    assertEquals(PluginEvents.limits(Some(floored), 2026), Limits(2026, Some(-9700L)))
    assertEquals(PluginEvents.limits(Some(throws), 2026), Limits(2026, None))
    assertEquals(PluginEvents.limits(None, 2026), Limits(2026, None))

  test("the host's own catalogues and collections are never searched through the SPI"):
    val gvp = BuiltinCatalogues.entries.head
    assertEquals((gvp.plugin, gvp.name, gvp.title), ("events-gvp", "gvp", "Smithsonian Global Volcanism Program"))
    assert(gvp.read("x", CatalogueQuery(None, 0), offline).isLeft)
    assert(HostEventCatalogue("p", "nope").licence.isEmpty)
    val met = BuiltinMedia.entries.find(_.name == "met").get
    assert(met.search(MediaPlace("p", "P"), offline).isLeft)
    assertEquals(BuiltinMedia.reserved.intersect(BuiltinMedia.entries.map(_.name).toSet).size, 8)

/** The fixture plugin's (empty) settings. */
final case class PortalConfig() derives hearth.kindlings.sconfigderivation.ConfigReader
