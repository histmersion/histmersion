package com.histmersion
package server.plugins
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.server.ServerPlugins
import com.histmersion.util.plugins.{Plugin as PluginName, Plugins}

import com.histmersion.api.catalogue.CatalogRecord
import com.histmersion.api.plugins.PluginStatus
import com.histmersion.pluginhost.{Generation, PluginEvents, PluginHost, PluginMedia, Registries}

import com.histmersion.domain.media.imports.MediaRecord
import com.histmersion.plugin.*
import com.histmersion.domain.catalogue.places.BuiltinGazetteers
import com.histmersion.domain.catalogue.events.BuiltinCatalogues
import com.histmersion.domain.media.imports.{BuiltinMedia, MediaImporters, PluginCollection}
import com.histmersion.server.agents.BuiltinAi
import com.histmersion.api.VocabularyCodecs.given
import org.ekrich.config.ConfigFactory

import scala.collection.immutable.ListMap
import com.histmersion.plugin.Plugin
import com.histmersion.plugin.MediaCollection
import com.histmersion.plugin.EventCatalogue
import com.histmersion.domain.catalogue.events.CatalogProvider
import com.histmersion.config.AppConfig
import com.histmersion.domain.library.acquisition.CoreBudget
import com.histmersion.api.catalogue.CatalogProviderInfo
import com.histmersion.config.MediaPluginCollectionsConfig
import com.histmersion.api.media.MediaProvider
import com.histmersion.api.plugins.PluginDescription
import com.histmersion.plugin.Registry
import com.histmersion.config.ConfigReload
import com.histmersion.plugin.MediaItem
import com.histmersion.plugin.MediaRights
import com.histmersion.domain.DeepTime
import com.histmersion.plugin.CatalogueEvent
import com.histmersion.api.TimeSpan
import com.histmersion.upstreams.dating.CatalogDating

/** Media collections and event catalogues as plugins (docs/research-plugins.md §6, §19): the built-in ones registered
  * through the plugin host and switched off like any plugin; a third-party plugin's collection and catalogue registered
  * beside them, refused under a host name; the provider lists from the registries; the host's reading of a stated
  * licence and of a catalogue's record; and a reload that rebuilds a media/events plugin only when its own block
  * changed. No network.
  */
class ProviderPluginsSuite extends munit.FunSuite:

  private def providers(
      registered: ListMap[Registries.EventCataloguesKey, EventCatalogue]
  ): ListMap[CatalogRecord.Provider, EventCatalogue] =
    registered.map((k, c) => CatalogRecord.Provider(k.value) -> c)

  private val offline = new Plugin.Http {
    def get(u: Plugin.Http.Url, h: Map[Header.Name, Header.Value]) =
      Left(Plugin.Http.Failure.Transport(Problem.Unclassified(Legacy.Text("offline"))))
  }

  /** The class path's plugins (the first-party media collections among them, supplied the host's settings) and `extra`,
    * under `user`.
    */
  private def build(extra: List[Plugin], user: String = ""): Generation =
    ServerPlugins.offlineWith(extra, ConfigFactory.parseString(user))

  /** A third-party plugin: one media collection and one event catalogue under ids of its choosing. */
  final private class Portal(
      collection: Registry.AddId = Registry.AddId("zq-portal"),
      catalogue: Registry.AddId = Registry.AddId("zq-quakes"),
      role: MediaItem.Role = MediaItem.Role("object")
  ) extends Plugin:
    type Config = PortalConfig
    val info =
      Plugin.Info(Plugin.Info.Id("zq-plugin"), Plugin.Info.Version("0.1.0"), Plugin.Info.SpiVersion(Spi.Version))
    override def defaultsResource = Plugin.DefaultsResource("zq-none.conf")
    def register(c: PortalConfig, ctx: Plugin.Context) =
      for
        _ <- ctx.mediaCollections.add(collection, Collection(role))
        _ <- ctx.eventCatalogues.add(catalogue, Catalogue)
      yield ()

  final private class Collection(val role: MediaItem.Role) extends MediaCollection:
    val title = MediaCollection.Title("Zq portal objects")
    def search(place: MediaPlace, http: Plugin.Http) = Right(Nil)

  private object Catalogue extends EventCatalogue:
    val title = EventCatalogue.Title("Zq quakes"); val licence = Licence("CC0-1.0");
    val citation = EventCatalogue.Citation("Zq (2026)");
    val home = EventCatalogue.Home("https://zq.example")
    def parts(q: CatalogueQuery) = List(EventCatalogue.ReadPart("all"))
    def read(part: EventCatalogue.ReadPart, q: CatalogueQuery, http: Plugin.Http) = Right(CataloguePage(Nil))

  private val portalOn = "histmersion.plugins.zq-plugin.enabled = true"

  // ── the built-ins ──────────────────────────────────────────────────────────────────────────────

  test(
    "the built-in media collections and event catalogues register as plugins, enabled by default (the keyed ones off)"
  ):
    val g = ServerPlugins.offline
    assert(g.report.ok, g.report.toString)
    val vaults = Set("1password", "bitwarden", "keepassxc").map(PluginStatus.Id(_))
    assertEquals(
      g.report.plugins.map(_.id.value: String).toSet -- vaults.map(_.value: String),
      (BuiltinMedia.pluginIds ++ BuiltinCatalogues.pluginIds ++ BuiltinAi.pluginIds ++
        BuiltinGazetteers.pluginIds).map(_.value: String)
    )
    val keyed = BuiltinMedia.keyed.keySet.flatMap(BuiltinMedia.entry).map(_.plugin)
    assertEquals(keyed, Set("media-smithsonian", "media-europeana"))
    def isKeyed(p: PluginStatus) = keyed.exists(_.value == p.id.value)
    assert(
      g.report.plugins.filterNot(p => isKeyed(p) || vaults(p.id)).forall(_.state.value == "registered"),
      g.report.toString
    )
    assert(
      g.report.plugins.filter(isKeyed).forall(_.state.value == "disabled"),
      "a key of the user's own: off by default"
    )
    assertEquals(g.report.plugins.find(_.id.value == "media-met").map(_.provides), Some(List("media-collection:met")))
    assertEquals(g.report.plugins.find(_.id.value == "events-gvp").map(_.provides), Some(List("event-catalogue:gvp")))
    assertEquals(
      BuiltinMedia.ordered(g.registries.mediaCollections.keys.toList),
      List("wikidata", "depicts", "met", "cleveland", "aic", "loc", "pleiades", "opencontext")
    )
    assertEquals(
      BuiltinCatalogues.ordered(providers(g.registries.eventCatalogues).keys.toList),
      List("gvp", "noaa", "wikidata")
    )
    // the first-party plugins' catalogues state the terms the host showed for them before they moved
    assertEquals(
      BuiltinCatalogues.infos(providers(g.registries.eventCatalogues)),
      CatalogProvider.all
        .flatMap(CatalogProvider.info)
    )
    assert(g.registries.sourceFetchers.isEmpty, "offline: no fetcher")

  test("the full built-in set: the fetchers, the collections and the catalogues; no plugin needs a setting"):
    val loaded = AppConfig
      .loadWithUserText(ConfigFactory.empty, "")
      .fold(e => fail(e.sentence), identity)
    val g = ServerPlugins.load(
      loaded,
      transport = _ => fail("no HTTP at registration"),
      core = CoreBudget(() => java.time.Instant.now(java.time.Clock.systemUTC()))
    )
    assert(g.report.ok, g.report.toString)
    assertEquals(
      g.report.plugins.map(_.id).filterNot(Set("1password", "bitwarden", "keepassxc").map(PluginStatus.Id(_))).size,
      7 + 10 + 3 + 6 + 3 + 1
    ) // fetchers (four open-access indexes, D4), collections, catalogues, AI providers, gazetteers, Calibre; the vaults besides
    assert(!g.report.plugins.exists(_.state.value == "failed"), g.report.toString)

  test("a built-in switched off is out of its registry; the import names the switch"):
    val g = build(Nil, "histmersion.plugins { media-met.enabled = false, events-noaa.enabled = false }")
    assertEquals(g.report.plugins.find(_.id.value == "media-met").map(_.state), Some("disabled"))
    assert(!g.registries.mediaCollections.contains(Registries.MediaCollectionsKey("met")))
    assertEquals(
      MediaImporters
        .lookup(MediaImporters.ImporterKey("met"), g.registries.mediaCollections)
        .left
        .toOption
        .map(_.sentence),
      Some("Met objects (met) is switched off (histmersion.plugins.media-met.enabled = false)")
    )
    assertEquals(
      BuiltinCatalogues.resolve(CatalogProviderInfo.Id("ncei"), providers(g.registries.eventCatalogues)).left.toOption,
      Some("NOAA NCEI Natural Hazards (noaa) is switched off (histmersion.plugins.events-noaa.enabled = false)")
    )
    // the environment switches one off too
    val e = ServerPlugins.offlineWith(
      Nil,
      ConfigFactory.empty,
      ConfigFactory.parseString("HISTMERSION_PLUGIN_MEDIA_LOC_ENABLED = false")
    )
    assertEquals(e.report.plugins.find(_.id.value == "media-loc").map(_.state), Some("disabled"))

  test("names: aliases resolve to the built-ins; an unknown name lists what is registered, in order"):
    val c = ServerPlugins.offline.registries.mediaCollections
    assertEquals(MediaImporters.lookup(MediaImporters.ImporterKey(""), c).map(_._1), Right("wikidata"))
    assertEquals(MediaImporters.lookup(MediaImporters.ImporterKey("Wikimedia-Commons"), c).map(_._1), Right("wikidata"))
    assertEquals(MediaImporters.lookup(MediaImporters.ImporterKey("cma"), c).map(_._1), Right("cleveland"))
    assertEquals(MediaImporters.lookup(MediaImporters.ImporterKey("oc"), c).map(_._1), Right("opencontext"))
    assertEquals(MediaImporters.lookup(MediaImporters.ImporterKey("art-institute-chicago"), c).map(_._1), Right("aic"))
    val unknown = MediaImporters.lookup(MediaImporters.ImporterKey("rijks"), c).left.toOption.get
    assert(
      unknown.sentence.startsWith(
        "unknown media provider 'rijks' (one of: wikidata, depicts, met, cleveland, aic, loc, pleiades, opencontext;"
      ),
      unknown
    )
    val e = providers(ServerPlugins.offline.registries.eventCatalogues)
    assertEquals(BuiltinCatalogues.resolve(CatalogProviderInfo.Id("Smithsonian"), e), Right("gvp"))
    assertEquals(BuiltinCatalogues.resolve(CatalogProviderInfo.Id("wd"), e), Right("wikidata"))
    assertEquals(
      BuiltinCatalogues.resolve(CatalogProviderInfo.Id("usgs"), e),
      Left("unknown event catalogue 'usgs' (one of: gvp, noaa, wikidata)")
    )

  // ── the key-gated built-ins (Smithsonian, Europeana) ──────────────────────────────────────────────

  private val keyedOn = "histmersion.plugins { media-smithsonian.enabled = true, media-europeana.enabled = true }"

  private val smithsonian = Registries.MediaCollectionsKey("smithsonian")
  private val europeana = Registries.MediaCollectionsKey("europeana")

  private def keyedEntry(g: Generation, name: Registries.MediaCollectionsKey) = g.registries.mediaCollections.get(name)

  private val noKey =
    "Smithsonian Open Access objects needs an api.data.gov key: set histmersion.plugins.media-smithsonian.api-key " +
      "to a secret reference (\"env:<NAME>\" or a vault's, e.g. \"keepassxc:<entry>#password\"); it was not asked"

  test("keyed: off by default the import says it is off and what it needs; it is still a reserved name"):
    val c = ServerPlugins.offline.registries.mediaCollections
    assert(
      !c.contains(Registries.MediaCollectionsKey("smithsonian")) && !c.contains(
        Registries.MediaCollectionsKey("europeana")
      )
    )
    assertEquals(
      MediaImporters.lookup(MediaImporters.ImporterKey("smithsonian"), c).left.toOption.map(_.sentence),
      Some(
        "Smithsonian Open Access objects (smithsonian) " +
          "is switched off — it is off by default and needs an api.data.gov key: set histmersion.plugins.media-smithsonian.enabled " +
          "= true and its api-key (a secret reference)"
      )
    )
    assert(
      MediaImporters
        .lookup(MediaImporters.ImporterKey("SI"), c)
        .left
        .exists(_.sentence.contains("Smithsonian Open Access objects (smithsonian)")),
      "an alias"
    )
    assert(
      MediaImporters
        .lookup(MediaImporters.ImporterKey("europeana"), c)
        .left
        .exists(_.sentence.contains("needs a Europeana personal API key"))
    )

  test("keyed: switched on without a key, the collection is listed and refused at the import with the setting to set"):
    val g = build(Nil, keyedOn)
    assert(g.report.ok, g.report.toString)
    assertEquals(
      g.report.plugins.find(_.id.value == "media-europeana").map(_.provides),
      Some(List("media-collection:europeana"))
    )
    val si = keyedEntry(g, smithsonian).getOrElse(fail("not registered"))
    assertEquals(si.check.left.toOption.map(_.sentence), Some(noKey))
    val infos = MediaImporters.infos(
      g.registries.mediaCollections,
      _ => Plugin.Info.Id("?"),
      _ => MediaPluginCollectionsConfig.Licences("")
    )
    assertEquals(infos.map(_.name).takeRight(2), List("smithsonian", "europeana"), "after the keyless built-ins")
    assert(
      infos
        .find(_.name.value == "europeana")
        .exists(i =>
          i.builtin.value && i.plugin.value == "media-europeana" &&
            i.description.value.endsWith("NO KEY SET: not asked")
        ),
      infos.toString
    )

  test("keyed: the key is a secret reference resolved when the plugin is built; never shown"):
    val env = ConfigFactory.parseString("HISTMERSION_TEST_SI_KEY = \"si-secret-123\"\nHISTMERSION_TEST_EU_KEY = \"  \"")
    val user = ConfigFactory.parseString(keyedOn + """
      histmersion.plugins.media-smithsonian.api-key = "env:HISTMERSION_TEST_SI_KEY"
      histmersion.plugins.media-europeana.api-key = "env:HISTMERSION_TEST_EU_KEY"
    """)
    val g = ServerPlugins.offlineWith(Nil, user, env)
    assert(g.report.ok, g.report.toString)
    assertEquals(keyedEntry(g, smithsonian).map(_.check), Some(Right(())))
    assert(
      keyedEntry(g, europeana).exists(_.check.left.exists(_.sentence.contains("needs a Europeana personal API key"))),
      "a blank value is no key"
    )
    assert(!g.report.toString.contains("si-secret-123"), "the report shows the reference, never the value")
    assert(!keyedEntry(g, smithsonian).get.toString.contains("si-secret-123"))
    val off = ServerPlugins.offlineWith(
      Nil,
      user.withFallback(ConfigFactory.parseString("histmersion.media.smithsonian.enabled = false")),
      env
    )
    assertEquals(
      keyedEntry(off, smithsonian).map(_.check.left.map(_.sentence)),
      Some(Left("Smithsonian Open Access objects is switched off (media.smithsonian.enabled = false)"))
    )
    // an unresolvable reference fails that plugin alone, naming the reference
    val missing = ServerPlugins.offlineWith(
      Nil,
      ConfigFactory.parseString(
        keyedOn +
          "\nhistmersion.plugins.media-smithsonian.api-key = \"env:HISTMERSION_TEST_NOT_SET\""
      )
    )
    val st = missing.report.plugins.find(_.id.value == "media-smithsonian").get
    assertEquals(st.state, "failed")
    assert(st.error.exists(_.sentence.contains("HISTMERSION_TEST_NOT_SET")), st.toString)
    assert(
      missing.registries.mediaCollections.contains(Registries.MediaCollectionsKey("europeana")),
      "the other one is untouched"
    )

  // ── a third-party plugin ───────────────────────────────────────────────────────────────────────

  test("a third-party plugin's collection and catalogue register beside the built-ins and are listed after them"):
    val g = ServerPlugins.offlineWith(List(Portal()), ConfigFactory.parseString(portalOn))
    assertEquals(
      g.report.plugins.find(_.id.value == "zq-plugin").map(_.provides),
      Some(List("media-collection:zq-portal", "event-catalogue:zq-quakes"))
    )
    val c = g.registries.mediaCollections
    assertEquals(BuiltinMedia.ordered(c.keys.toList).last, "zq-portal")
    assertEquals(MediaImporters.lookup(MediaImporters.ImporterKey("ZQ-Portal"), c).map(_._1), Right("zq-portal"))
    val infos = MediaImporters.infos(
      c,
      n =>
        if n == Registries.MediaCollectionsKey("zq-portal") then Plugin.Info.Id("zq-plugin") else Plugin.Info.Id("?"),
      _ => MediaPluginCollectionsConfig.Licences("cc0,cc-by")
    )
    assertEquals(infos.map(_.name).take(2), List("wikidata", "depicts"))
    assertEquals(
      infos.last,
      MediaProvider
        .Info(
          com.histmersion.api.media.MediaItem.Provider("zq-portal"),
          MediaProvider.Info.Title("Zq portal objects"),
          MediaProvider.Info.Over("places"),
          MediaProvider.Info.Description(""),
          PluginDescription.Id("zq-plugin"),
          builtin = MediaProvider.Info.Builtin(false),
          MediaProvider.Info.Licences("cc0,cc-by")
        )
    )
    assert(
      infos.find(_.name.value == "met").exists(i => i.builtin.value && i.plugin.value == "media-met"),
      infos.toString
    )
    val cats = BuiltinCatalogues.infos(providers(g.registries.eventCatalogues))
    assertEquals(cats.map(_.id), List("gvp", "noaa", "wikidata", "zq-quakes"))
    assertEquals(
      cats.last,
      CatalogProviderInfo(
        CatalogProviderInfo.Id("zq-quakes"),
        CatalogProviderInfo.Title("Zq quakes"),
        CatalogProviderInfo.Licence("CC0-1.0"),
        CatalogProviderInfo.Citation("Zq (2026)"),
        CatalogProviderInfo.Home("https://zq.example")
      )
    )
    assertEquals(
      BuiltinCatalogues.resolve(CatalogProviderInfo.Id("zq-quakes"), providers(g.registries.eventCatalogues)),
      Right("zq-quakes")
    )

  test("a plugin may not take a name of the host's providers, nor a role the host does not store"):
    // a registered built-in's name is taken; an alias or a stored provider's name is reserved even when
    // its built-in is switched off
    val dup = build(List(Portal(collection = Registry.AddId("met"))), portalOn)
    assert(
      dup.report.plugins
        .find(_.id.value == "zq-plugin")
        .exists(_.error.exists(_.sentence.contains("'met' is already registered")))
    )
    assert(
      dup.registries.mediaCollections
        .get(Registries.MediaCollectionsKey("met"))
        .exists(_.getClass.getName.startsWith("com.histmersion.plugins.media.")),
      "the built-in is untouched"
    )
    for name <- List(
        "met",
        "commons",
        "met-museum",
        "wikimedia-commons",
        "pleiades",
        "source",
        "calibre",
        "oc",
        "smithsonian",
        "europeana",
        "si"
      ).map(Registry.AddId(_))
    do
      val g = build(
        List(Portal(collection = name)),
        s"$portalOn\nhistmersion.plugins { media-met.enabled = false, media-pleiades.enabled = false }"
      )
      val st = g.report.plugins.find(_.id.value == "zq-plugin").get
      assertEquals(st.state, "failed", name)
      assert(st.error.exists(_.sentence.contains("one of Histmersion's own media providers")), st.toString)
    val cat =
      build(List(Portal(catalogue = Registry.AddId("volcano"))), portalOn).report.plugins
        .find(_.id.value == "zq-plugin")
        .get
    assert(cat.error.exists(_.sentence.contains("one of Histmersion's own event catalogues")), cat.toString)
    val role =
      build(List(Portal(role = MediaItem.Role("cover"))), portalOn).report.plugins.find(_.id.value == "zq-plugin").get
    assert(
      role.error.exists(
        _.sentence.contains(
          "role 'cover' is not one of aerial-view, depiction, image, locator-map, map, object, plan, route-map"
        )
      ),
      role.toString
    )

  test("a host whose context predates media and events refuses those additions (SPI version stays 1)"):
    val ctx = new Plugin.Context:
      def http = offline
      val log = new Plugin.Log {
        def info(m: Plugin.Log.Line) = ()
        def warn(m: Plugin.Log.Line) = ()
      }
      def env(n: EnvVar.Name) = None
      def secret(s: Secret) = Left(Problem.Unclassified(Legacy.Text("none")))
      def secretVaults = Registry.refusing(Problem.Unclassified(Legacy.Text("x")))
    assert(ctx.mediaCollections.add(Registry.AddId("x"), Collection(MediaItem.Role("object"))).isLeft)
    assert(ctx.eventCatalogues.add(Registry.AddId("x"), Catalogue).isLeft)
    assertEquals(Spi.Version, 2)

  // ── reloads ────────────────────────────────────────────────────────────────────────────────────

  test(
    "reload: a host key rebuilds the plugins that read it — acquire.* the fetchers and third parties, media.* the collections"
  ):
    val g1 = ServerPlugins.offlineWith(List(Portal()), ConfigFactory.parseString(portalOn))
    // the secret vaults on the class path, off: a plugin's own block or a media key leaves them as they are
    val vaults = Set(PluginName.Id("1password"), PluginName.Id("bitwarden"), PluginName.Id("keepassxc"))
    val carried = Plugins.carried(g1, List(ConfigReload.Key("histmersion.acquire.chain")))
    assertEquals(
      carried.keySet.map(_.value: String),
      (BuiltinMedia.pluginIds ++ BuiltinCatalogues.pluginIds ++ BuiltinAi.pluginIds ++
        BuiltinGazetteers.pluginIds).map(_.value: String)
    )
    // their own block rebuilds them alone
    assertEquals(
      Plugins
        .carried(g1, List(ConfigReload.Key("histmersion.plugins.media-met.enabled")))
        .keySet
        .map(_.value: String),
      (BuiltinMedia.pluginIds ++ BuiltinCatalogues.pluginIds ++ BuiltinAi.pluginIds ++
        BuiltinGazetteers.pluginIds ++ vaults + PluginName.Id("zq-plugin") - PluginName.Id("media-met"))
        .map(_.value: String)
    )
    // a media setting rebuilds the media collections, which are supplied it, and nothing else
    assertEquals(
      Plugins.carried(g1, List(ConfigReload.Key("histmersion.media.met.licences"))).keySet.map(_.value: String),
      (BuiltinCatalogues.pluginIds ++ BuiltinAi.pluginIds ++ BuiltinGazetteers.pluginIds ++ vaults + PluginName.Id(
        "zq-plugin"
      )).map(_.value: String)
    )
    assertEquals(Plugins.carried(g1, List(ConfigReload.Key("histmersion.plugins.order"))), Map.empty)
    assertEquals(Plugins.carried(g1, List(ConfigReload.Key("histmersion.plugins"))), Map.empty)
    // the carried built-in is the same instance in the next generation
    val g2 = ServerPlugins.offlineWith(List(Portal()), ConfigFactory.parseString(portalOn), carried = carried)
    assert(
      g2.registries.mediaCollections(Registries.MediaCollectionsKey("met")) eq g1.registries.mediaCollections(
        Registries.MediaCollectionsKey("met")
      )
    )
    assertEquals(g2.rebuilt.filterNot(id => vaults.exists(_.value == id.value)), List("zq-plugin"))

  // ── the host's reading of a plugin's records ──────────────────────────────────────────────────

  private def rights(
      licence: Licence,
      url: Option[MediaRights.LicenceUrl] = None,
      credit: MediaRights.Credit = MediaRights.Credit("Zq, CC")
  ) =
    PluginMedia.rights(MediaRights(licence, url, Some(MediaRights.Author("A. Author")), credit))

  test("a stated licence is read by the host: ids, short names and Creative Commons URLs; unknown is never guessed"):
    assertEquals(
      rights(Licence("CC BY-NC 4.0")).map(_.licence),
      Right(
        com.histmersion.domain
          .Licence(com.histmersion.domain.Licence.Id("CC-BY-NC-4.0"), com.histmersion.domain.Licence.Family.CcByNc)
      )
    )
    assertEquals(rights(Licence("CC-BY-4.0")).map(_.licence.family), Right(com.histmersion.domain.Licence.Family.CcBy))
    assertEquals(
      rights(Licence("https://creativecommons.org/licenses/by-sa/4.0/")).map(_.licence.family),
      Right(com.histmersion.domain.Licence.Family.CcBySa)
    )
    assertEquals(
      rights(Licence("https://creativecommons.org/publicdomain/zero/1.0/")).map(_.licence.family),
      Right(com.histmersion.domain.Licence.Family.Cc0)
    )
    assertEquals(
      rights(Licence("Public domain")).map(_.licence.family),
      Right(com.histmersion.domain.Licence.Family.PublicDomain)
    )
    assertEquals(
      rights(Licence("All rights reserved")).map(_.licence.family),
      Right(com.histmersion.domain.Licence.Family.AllRightsReserved)
    )
    assertEquals(
      rights(Licence("free to use!")).map(_.licence.family),
      Right(com.histmersion.domain.Licence.Family.Unknown)
    )
    // an unclear statement: the licence URL decides when it is a known one
    assertEquals(
      rights(Licence("see link"), Some(MediaRights.LicenceUrl("https://creativecommons.org/licenses/by/4.0/")))
        .map(_.licence.family),
      Right(com.histmersion.domain.Licence.Family.CcBy)
    )
    assertEquals(
      rights(Licence("CC BY 4.0"), credit = MediaRights.Credit("  ")).left.map(_.sentence),
      Left("no credit line")
    )
    // whatever the family, the allowlist decides; unknown and all-rights-reserved never pass
    assert(
      !com.histmersion.domain.Licence
        .allowed(
          rights(Licence("free to use!")).toOption.get.licence,
          com.histmersion.domain.Licence.Family.allowable.toSet
        )
    )
    assert(
      !com.histmersion.domain.Licence
        .allowed(
          rights(Licence("CC BY-NC 4.0")).toOption.get.licence,
          com.histmersion.domain.Licence.allowList(com.histmersion.domain.Licence.AllowList("cc0,cc-by"))
        )
    )

  test("a plugin's item becomes the host's record: trimmed, its period converted, a refusal kept"):
    val it = MediaItem(
      MediaItem.Key(" k1 "),
      MediaItem.Title("  Bowl "),
      MediaItem.PageUrl("https://zq.example/k1"),
      Some(MediaItem.ImageUrl(" https://zq.example/k1.jpg ")),
      Right(MediaRights(Licence("CC0"), credit = MediaRights.Credit("Zq"))),
      geography = List(MediaItem.Geography.Field("find-spot") -> MediaItem.Geography.Value("Zqenai")),
      period = Period.from(Year(-1400), Year(-1300)).toOption,
      ids = List(GazetteerRef.Scheme("pleiades") -> GazetteerRef.Id("1"))
    )
    val r: MediaRecord = PluginMedia.record(it)
    assertEquals(
      (r.key, r.title, r.imageUrl, r.period),
      (
        "k1",
        "Bowl",
        Some("https://zq.example/k1.jpg"),
        DeepTime.span(DeepTime.SignedYear(-1400L), DeepTime.SignedYear(-1300L))
      )
    )
    assertEquals(r.rights.map(_.licence.family), Right(com.histmersion.domain.Licence.Family.Cc0))
    assertEquals(r.ids, List("pleiades" -> "1"))
    assertEquals(
      PluginMedia
        .record(it.modify(_.rights).setTo(Left(Problem.Unclassified(Legacy.Text("not open access")))))
        .rights
        .left
        .map(_.sentence),
      Left("not open access")
    )

  test("a plugin's failure is a value, never an exception through the import"):
    assertEquals(
      PluginMedia.guard[Int](throw IllegalStateException("boom")).left.map(_.sentence),
      Left("the plugin failed: IllegalStateException: boom")
    )
    assertEquals(PluginMedia.guard[Int](null).left.map(_.sentence), Left("the plugin answered nothing"))

  test("a catalogue's record is checked by the host before it is stored"):
    val ok = CatalogueEvent(
      CatalogueEvent.Id("q-1"),
      CatalogueEvent.Url("https://zq.example/q-1"),
      CatalogueEvent.Label("Zq Earthquake"),
      CatalogueEvent.Kind("Earthquake"),
      Some(Year(-1200)),
      Some(Year(-1200)),
      lat = Some(Latitude(38.0)),
      lon = Some(Longitude(23.0)),
      magnitude = Some(CatalogueEvent.Magnitude(6.5)),
      wikidata = Some(WikidataId("Q42")),
      names = List(CatalogueEvent.Names("Zq Earthquake"), CatalogueEvent.Names("Zq quake"))
    )
    val r = PluginEvents.record(CatalogRecord.Provider("zq-quakes"), ok).fold(p => fail(p.sentence), _.record)
    assertEquals(
      (r.provider, r.providerId, r.kind, r.wikidata, r.names),
      ("zq-quakes", "q-1", "earthquake", Some("Q42"), List("Zq quake"))
    )
    assertEquals(
      r.timeSpan,
      TimeSpan(
        Some(com.histmersion.api.catalogue.Year(-1200L)),
        None,
        None,
        Some(com.histmersion.api.catalogue.Year(-1200L))
      )
    )
    assertEquals(
      PluginEvents
        .record(CatalogRecord.Provider("x"), ok.modify(_.wikidata).setTo(Some(WikidataId("not-an-item"))))
        .map(_.warnings),
      Right(List("q-1: catalogue 'x': Wikidata id 'not-an-item' is not a Wikidata item (Q and its number), left out"))
    )
    assertEquals(
      PluginEvents
        .record(CatalogRecord.Provider("x"), ok.modify(_.id).setTo(CatalogueEvent.Id(" ")))
        .left
        .map(_.sentence),
      Left("a record without an id")
    )
    assertEquals(
      PluginEvents
        .record(CatalogRecord.Provider("x"), ok.modify(_.label).setTo(CatalogueEvent.Label("")))
        .left
        .map(_.sentence),
      Left("q-1: no label")
    )
    assert(
      PluginEvents
        .record(CatalogRecord.Provider("x"), ok.modify(_.historicity).setTo(CatalogueEvent.Historicity("certain")))
        .left
        .exists(_.sentence.contains("historicity 'certain'"))
    )
    assertEquals(
      PluginEvents
        .record(
          CatalogRecord.Provider("x"),
          ok.modify(_.notBefore).setTo(Some(Year(-1000))).modify(_.notAfter).setTo(Some(Year(-1200)))
        )
        .left
        .map(_.sentence),
      Left("q-1: its dating runs backwards")
    )
    assertEquals(
      PluginEvents
        .record(CatalogRecord.Provider("x"), ok.modify(_.lat).setTo(Some(Latitude(95.0))))
        .map(c => (c.record.lat, c.warnings.head)),
      Right((None, "q-1: catalogue 'x': latitude 95.0 is not between -90 and 90, left out"))
    )
    assertEquals(
      PluginEvents
        .record(CatalogRecord.Provider("x"), ok.modify(_.lon).setTo(None))
        .map(c => (c.record.lat, c.record.lon, c.warnings)),
      Right((None, None, List("q-1: catalogue 'x': a position needs both latitude and longitude, left out")))
    )

  test(
    "a plugin catalogue's record is dated as the host dates its own: a stated year ± when no span, held inside the limits"
  ):
    import com.histmersion.api.TimeSpan
    import com.histmersion.upstreams.dating.CatalogDating
    val base = PluginEvents
      .record(
        CatalogRecord.Provider("zq"),
        CatalogueEvent(
          CatalogueEvent.Id("e"),
          CatalogueEvent.Url("u"),
          CatalogueEvent.Label("Zq Flood"),
          CatalogueEvent.Kind("flood"),
          None,
          None,
          lat = Some(Latitude(38.0)),
          lon = Some(Longitude(23.0)),
          central = Some(Year(-1000)),
          plusMinus = Some(CatalogueEvent.PlusMinus(50L))
        )
      )
      .fold(p => fail(p.sentence), _.record)
    val now = CatalogDating.Limits(CatalogDating.HistoricalYear(2026))
    // the span the record does not give, from its stated year and ±; the statement itself kept
    val d = PluginEvents.derive(base, now)
    assertEquals(
      d.timeSpan,
      TimeSpan(
        Some(com.histmersion.api.catalogue.Year(-1050L)),
        None,
        None,
        Some(com.histmersion.api.catalogue.Year(-950L))
      )
    )
    assertEquals((d.central, d.plusMinus), (Some(-1000L), Some(50L)))
    // a span the record gives is the record's; only the limits touch it
    val spanned = base
      .modify(_.timeSpan)
      .setTo(
        TimeSpan(
          Some(com.histmersion.api.catalogue.Year(-1200L)),
          None,
          None,
          Some(com.histmersion.api.catalogue.Year(-1100L))
        )
      )
    assertEquals(PluginEvents.derive(spanned, now).timeSpan, spanned.timeSpan)
    // the present and the catalogue's floor hold every point, the central year stays as stated
    val future = base
      .modify(_.central)
      .setTo(Some(CatalogRecord.Central(2020L)))
      .modify(_.plusMinus)
      .setTo(Some(CatalogRecord.PlusMinus(10L)))
    assertEquals(
      PluginEvents.derive(future, now).timeSpan,
      TimeSpan(
        Some(com.histmersion.api.catalogue.Year(2010L)),
        None,
        None,
        Some(com.histmersion.api.catalogue.Year(2026L))
      )
    )
    assertEquals(PluginEvents.derive(future, now).central, Some(2020L))
    val early = spanned
      .modify(_.timeSpan)
      .setTo(
        TimeSpan(
          Some(com.histmersion.api.catalogue.Year(-3500L)),
          Some(com.histmersion.api.catalogue.Year(-3100L)),
          Some(com.histmersion.api.catalogue.Year(-2950L)),
          Some(com.histmersion.api.catalogue.Year(-2900L))
        )
      )
    assertEquals(
      PluginEvents
        .derive(
          early,
          CatalogDating.Limits(CatalogDating.HistoricalYear(2026), Some(CatalogDating.HistoricalYear(-3000L)))
        )
        .timeSpan,
      TimeSpan(
        Some(com.histmersion.api.catalogue.Year(-3000L)),
        Some(com.histmersion.api.catalogue.Year(-3000L)),
        Some(com.histmersion.api.catalogue.Year(-2950L)),
        Some(com.histmersion.api.catalogue.Year(-2900L))
      )
    )
    // undated and no stated year: nothing invented
    assertEquals(PluginEvents.derive(base.modify(_.central).setTo(None), now).timeSpan, TimeSpan())
    // derived once, derived again: the same record (what makes rederive a no-op after an import)
    List(base, spanned, future, early).foreach { r =>
      val l = CatalogDating.Limits(CatalogDating.HistoricalYear(2026), Some(CatalogDating.HistoricalYear(-3000L)))
      val once = PluginEvents.derive(r, l)
      assertEquals(PluginEvents.derive(once, l), once)
    }

  test("a plugin catalogue's limits: the present, and its own floor when it states one"):
    val floored = new EventCatalogue:
      val title = EventCatalogue.Title("t"); val licence = Licence("l");
      val citation = EventCatalogue.Citation("c"); val home = EventCatalogue.Home("h")
      override def floorYear = Some(Year(-9700))
      def parts(q: CatalogueQuery) = Nil
      def read(p: EventCatalogue.ReadPart, q: CatalogueQuery, h: Plugin.Http) =
        Left(Problem.Unclassified(Legacy.Text("no")))
    val throws = new EventCatalogue:
      val title = EventCatalogue.Title("t"); val licence = Licence("l");
      val citation = EventCatalogue.Citation("c"); val home = EventCatalogue.Home("h")
      override def floorYear = throw IllegalStateException("boom")
      def parts(q: CatalogueQuery) = Nil
      def read(p: EventCatalogue.ReadPart, q: CatalogueQuery, h: Plugin.Http) =
        Left(Problem.Unclassified(Legacy.Text("no")))
    import com.histmersion.upstreams.dating.CatalogDating.Limits
    assertEquals(
      PluginEvents.limits(Some(floored), DeepTime.SignedYear(2026)),
      Limits(CatalogDating.HistoricalYear(2026), Some(CatalogDating.HistoricalYear(-9700L)))
    )
    assertEquals(
      PluginEvents.limits(Some(throws), DeepTime.SignedYear(2026)),
      Limits(CatalogDating.HistoricalYear(2026), None)
    )
    assertEquals(PluginEvents.limits(None, DeepTime.SignedYear(2026)), Limits(CatalogDating.HistoricalYear(2026), None))

  test("the host's own catalogues and collections are never searched through the SPI"):
    val gvp = BuiltinCatalogues.entries.head
    assertEquals((gvp.plugin, gvp.name, gvp.title), ("events-gvp", "gvp", "Smithsonian Global Volcanism Program"))
    assertEquals(BuiltinMedia.reserved.intersect(BuiltinMedia.entries.map(_.name).toSet).size, 10)
    assert(
      Set(BuiltinMedia.ReservedName("smithsonian"), BuiltinMedia.ReservedName("europeana"))
        .subsetOf(BuiltinMedia.reserved)
    )

/** The fixture plugin's (empty) settings. */
final case class PortalConfig() derives hearth.kindlings.sconfigderivation.ConfigReader
