package com.histmersion.plugin

/** The media-collection and event-catalogue parts of the SPI (docs/research-plugins.md §6). */
class ProviderSpiSuite extends munit.FunSuite:

  private val offline = new PluginHttp { def get(url: String, headers: Map[String, String]) = Left("offline") }

  private object Portal extends MediaCollection:
    val title = "Portal objects"
    val role = MediaCollection.Object
    def search(place: MediaPlace, http: PluginHttp) =
      Right(List(Right(MediaItem("k", place.label, "https://portal.example/k", None, Left("not open access")))))

  private object Quakes extends EventCatalogue:
    val title = "Quakes"; val licence = Licence("CC0-1.0"); val citation = "Q"; val home = "https://q.example"
    def parts(q: CatalogueQuery) = List(s"to:${q.to}")
    def read(part: String, q: CatalogueQuery, http: PluginHttp) = Right(CataloguePage(Nil))

  test("a collection's defaults: the museum rule, three per place, a second between requests, complete = as is"):
    assertEquals(
      (Portal.matchRule, Portal.perPlace, Portal.requestIntervalMs, Portal.description),
      (MediaMatchRule.Museum, 3, 1000, "")
    )
    val item = Portal.search(MediaPlace("p", "Mycenae"), offline).toOption.get.head.toOption.get
    assertEquals(Portal.complete(item, offline), Right(item))
    assertEquals(item.rights, Left("not open access"))
    assertEquals(MediaCollection.Roles, Set("object", "map", "image"))

  test("a catalogue's defaults and its parts for a query"):
    assertEquals(Quakes.requestIntervalMs, 1000)
    assertEquals(Quakes.floorYear, None)
    assertEquals(Quakes.parts(CatalogueQuery(Some(Year(-3000)), Year(2026))), List("to:2026"))
    val e = CatalogueEvent("1", "u", "Quake", "earthquake", Some(Year(-1200)), Some(Year(-1200)))
    assertEquals((e.historicity, e.beginBy, e.endFrom), ("attested", None, None))

  test("a context written before media and events existed still compiles and refuses both"):
    val ctx = new PluginContext:
      def http = offline
      def log = new PluginLog { def info(m: String) = (); def warn(m: String) = () }
      def env(name: String) = None
      def secret(s: Secret) = Left("none")
      def secretVaults = Registry.refusing("no vaults")
    assert(ctx.mediaCollections.add("portal", Portal).left.exists(_.contains("no media import")))
    assert(ctx.eventCatalogues.add("quakes", Quakes).left.exists(_.contains("no event catalogues")))
    assertEquals(Spi.Version, 2)
