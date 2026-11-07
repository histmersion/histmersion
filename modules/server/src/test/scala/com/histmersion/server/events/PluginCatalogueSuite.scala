package com.histmersion.server.events

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.server.CatalogueWiring

import com.histmersion.domain.catalogue.events.*
import com.histmersion.domain.catalogue.evidence.*

import com.histmersion.server.ServerPlugins
import com.histmersion.util.plugins.Plugins

import com.histmersion.pluginhost.{Generation, PluginHost}

import com.augustnagro.magnum.*
import com.histmersion.api.TimeSpan
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.TestDb
import com.histmersion.util.database.Migrate
import com.histmersion.plugin as spi
import com.histmersion.server.Deps
import com.histmersion.server.media.FakeMediaHttp
import com.histmersion.api.VocabularyCodecs.given
import org.ekrich.config.ConfigFactory
import com.histmersion.api.catalogue.{CatalogProviderInfo, CatalogRecord, EventImportReport, EventImportRequest}

/** A THIRD-PARTY plugin's event catalogue (docs/research-plugins.md §6) read by `events import` through the registries,
  * against a real PostGIS (HISTMERSION_TEST_DB_URL; skipped otherwise): its parts in order, a paged part followed, a
  * failed part reported and left to read again, a record the host's checks refuse reported (the others stored as
  * catalogue events under the catalogue's id), a second import reading only what is left, the catalogue listed with its
  * terms and named on the event's card. No socket: the plugin's requests are answered by a fake. Fixtures: catalogue
  * `zq-quakes`.
  */
class PluginCatalogueSuite extends com.histmersion.db.DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private val Catalogue = CatalogRecord.Provider("zq-quakes")
  private val CatalogueRequest = EventImportRequest.Provider("zq-quakes")
  private val CatalogueAdd = spi.Registry.AddId("zq-quakes")
  private val CatalogueInfo = CatalogProviderInfo.Id("zq-quakes")

  final private class ZqPlugin extends spi.Plugin:
    type Config = ZqCatalogueConfig
    val info = spi.Plugin.Info(com.histmersion.plugin.Plugin.Info.Id("zq-plugin"), com.histmersion.plugin.Plugin.Info.Version("0.1.0"), spi.Plugin.Info.SpiVersion(spi.Spi.Version))
    override def defaultsResource = com.histmersion.plugin.Plugin.DefaultsResourceResult("zq-none.conf")
    def register(c: ZqCatalogueConfig, ctx: spi.Plugin.Context) = ctx.eventCatalogues.add(CatalogueAdd, ZqQuakes).left.map(e => spi.Plugin.RegisterResultPart1(e.value))

  // `var`: whether the API answers part p2 (it fails on the first import)
  @volatile private var p2Up = false

  private object ZqQuakes extends spi.EventCatalogue:
    val title = com.histmersion.plugin.EventCatalogue.TitleResult("Zq battle catalogue"); val licence = spi.Licence("CC0-1.0"); val citation = com.histmersion.plugin.EventCatalogue.CitationResult("Zq et al. (2026)");
    val home = com.histmersion.plugin.EventCatalogue.HomeResult("https://zq.example")
    override val requestIntervalMs = com.histmersion.plugin.EventCatalogue.RequestIntervalMsResult(0)
    def parts(q: spi.CatalogueQuery) = List(com.histmersion.plugin.EventCatalogue.PartsResult("p1"), com.histmersion.plugin.EventCatalogue.PartsResult("p2"))
    private def ev(id: spi.CatalogueEvent.Id, label: spi.CatalogueEvent.Label, year: spi.Year) =
      spi.CatalogueEvent(
        id,
        spi.CatalogueEvent.Url(s"https://zq.example/$id"),
        label,
        com.histmersion.plugin.CatalogueEvent.Kind("Battle"),
        Some(year),
        Some(year),
        lat = Some(spi.Latitude(37.9)),
        lon = Some(spi.Longitude(23.7)),
        names = List(spi.CatalogueEvent.Names(s"$label (alt)"))
      )
    def read(part: spi.EventCatalogue.ReadPart, q: spi.CatalogueQuery, http: spi.Plugin.Http) =
      http.get(spi.Plugin.Http.GetUrl(s"https://zq.example/api/$part"), Map.empty).left.map(e => spi.EventCatalogue.ReadResultPart1(e.value)).flatMap { _ =>
        part.value match
          case "p1" =>
            Right(
              spi.CataloguePage(
                List(
                  ev(spi.CatalogueEvent.Id("b1"), spi.CatalogueEvent.Label("Zq Battle One"), spi.Year(-1200)),
                  ev(spi.CatalogueEvent.Id("bad"), spi.CatalogueEvent.Label("Zq Backwards"), spi.Year(-1200)).modify(_.notBefore).setTo(Some(spi.Year(-1000)))
                ),
                next = Some(spi.CataloguePage.Next("p1b"))
              )
            )
          case "p1b" => Right(spi.CataloguePage(List(ev(spi.CatalogueEvent.Id("b2"), spi.CatalogueEvent.Label("Zq Battle Two"), spi.Year(-1150)))))
          case "p2"  =>
            if p2Up then Right(spi.CataloguePage(List(ev(spi.CatalogueEvent.Id("b3"), spi.CatalogueEvent.Label("Zq Battle Three"), spi.Year(-1100))))) else Left(spi.EventCatalogue.ReadResultPart1("upstream down"))
          case other => Left(spi.EventCatalogue.ReadResultPart1(s"no part $other"))
      }

  private def fake = FakeMediaHttp(url =>
    if url.startsWith("https://zq.example/api/") then FakeMediaHttp.json("{}") else Left(s"unexpected $url")
  )

  private def generation: Generation =
    ServerPlugins.offlineWith(
      List(ZqPlugin()),
      ConfigFactory.parseString("histmersion.plugins.zq-plugin.enabled = true")
    )

  private lazy val base = AppConfig.load()
  private lazy val ds =
    val d = testPool(base.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d)
    d
  private lazy val cfg = base.modify(_.media).setTo(base.media.modify(_.requestIntervalMs).setTo(MediaConfig.RequestIntervalMs(0)))

  private def clean(): Unit = connect(ds):
    val _ = sql"DELETE FROM event_catalogue WHERE provider = ${Catalogue.value}".update.run()
    val _ = sql"DELETE FROM events WHERE id LIKE 'zq_quakes%'".update.run()
    val _ = sql"DELETE FROM event_import_parts WHERE provider = ${Catalogue.value}".update.run()
    val _ = sql"DELETE FROM event_catalogue WHERE provider = ${Floods.value}".update.run()
    val _ = sql"DELETE FROM events WHERE id LIKE 'zq_floods%'".update.run()
    val _ = sql"DELETE FROM event_import_parts WHERE provider = ${Floods.value}".update.run()

  override def beforeAll(): Unit = if testDb.isDefined then clean()
  override def afterSuite(): Unit = if testDb.isDefined then clean()

  private def run(h: CatalogueWiring, dry: EventImportRequest.DryRun): EventImportReport =
    h.eventImporter.runImport(Catalogue, EventImportRequest(CatalogueRequest, dryRun = dry), None, _ => ())

  test("the plugin's catalogue: parts in order, a page followed, a refused record and a failed part reported"):
    val http = fake
    val h = CatalogueWiring(Deps(cfg, ds, ds, plugins = generation), http)
    val r = run(h, dry = EventImportRequest.DryRun(false))
    assertEquals((r.records, r.kept, r.created), (2, 2, 2), r.toString)
    assert(r.failed.contains(EventImportReport.Failed("p1: bad: its dating runs backwards")), r.failed.toString)
    assert(r.failed.contains(EventImportReport.Failed("p2: upstream down")), r.failed.toString)
    assertEquals((r.licence, r.citation), ("CC0-1.0", "Zq et al. (2026)"))
    assertEquals(r.pending, 1, "p2 is still to read")
    assertEquals(
      http.requests.map(_._1).toList,
      List("https://zq.example/api/p1", "https://zq.example/api/p1b", "https://zq.example/api/p2")
    )
    assert(http.requests.forall(_._2.get("User-Agent").contains(HttpPolicy.userAgent(HttpPolicy.UserAgent(cfg.media.userAgent.value)).value)))
    assertEquals(EventFixtures.partsDone(ds, Catalogue).map(_.value.split(" @ ").head), Set("p1", "p1b"))
    val e = EventFixtures.get(ds, com.histmersion.domain.catalogue.events.BEventRow.Id("zq_quakes_b1")).getOrElse(fail("not stored"))
    assertEquals((e.event.label, e.event.kind), ("Zq Battle One", "battle"))
    // the card names the plugin's catalogue with its terms
    val card = h.eventLinks.context(e)
    assertEquals(
      card.catalogue.map(c => (c.provider, c.providerId, c.title, c.licence)),
      List((Catalogue, "b1", "Zq battle catalogue", "CC0-1.0"))
    )
    assert(card.names.contains("Zq Battle One (alt)"), card.names.toString)

  test("a second import reads only the part left; the catalogue is listed beside the built-ins"):
    p2Up = true
    val http = fake
    val h = CatalogueWiring(Deps(cfg, ds, ds, plugins = generation), http)
    val r = run(h, dry = EventImportRequest.DryRun(false))
    assertEquals((r.records, r.created, r.pending), (1, 1, 0), r.toString)
    assertEquals(http.requests.map(_._1).toList, List("https://zq.example/api/p2"))
    assertEquals(h.eventImporter.catalogues.map(_.id), List("gvp", "noaa", "wikidata", Catalogue))
    // the built-ins still answer by their aliases; an unknown one lists the plugin's
    val job = h.eventImports.importJob(Caller.Local, EventImportRequest(com.histmersion.api.catalogue.EventImportRequest.Provider("usgs"), dryRun = com.histmersion.api.catalogue.EventImportRequest.DryRun(true)))
    assert(job.left.exists(_.message.value.contains(s"one of: gvp, noaa, wikidata, $Catalogue")), job.toString)

  test("the plugin switched off: its catalogue is out of the import; its events stay on the map"):
    val off = ServerPlugins.offlineWith(List(ZqPlugin()), ConfigFactory.empty)
    val h = CatalogueWiring(Deps(cfg, ds, ds, plugins = off), fake)
    assert(
      h.eventImports
        .importJob(Caller.Local, EventImportRequest(CatalogueRequest, dryRun = com.histmersion.api.catalogue.EventImportRequest.DryRun(true)))
        .left
        .exists(_.message.value.contains("unknown event catalogue"))
    )
    assert(!h.eventImporter.catalogues.exists(_.id == CatalogueInfo))
    val e = EventFixtures.get(ds, com.histmersion.domain.catalogue.events.BEventRow.Id("zq_quakes_b1")).getOrElse(fail("gone"))
    assertEquals(h.eventLinks.context(e).catalogue.map(_.provider), List(Catalogue))

  // ── a catalogue whose records need holding inside the limits (§19: the host dates them at the import) ──

  private val Floods = CatalogRecord.Provider("zq-floods")
  private val FloodsRequest = EventImportRequest.Provider("zq-floods")
  private val FloodsAdd = spi.Registry.AddId("zq-floods")

  /** A catalogue of floods from 3000 BCE (its floor) whose records reach past both ends of what may be, and one dated
    * only by its stated year ±.
    */
  private object ZqFloods extends spi.EventCatalogue:
    val title = com.histmersion.plugin.EventCatalogue.TitleResult("Zq flood catalogue"); val licence = spi.Licence("CC0-1.0"); val citation = com.histmersion.plugin.EventCatalogue.CitationResult("Zq (2026)");
    val home = com.histmersion.plugin.EventCatalogue.HomeResult("https://zq.example")
    override val requestIntervalMs = com.histmersion.plugin.EventCatalogue.RequestIntervalMsResult(0)
    override val floorYear = Some(spi.Year(-3000))
    def parts(q: spi.CatalogueQuery) = List(com.histmersion.plugin.EventCatalogue.PartsResult("all"))
    private def ev(id: spi.CatalogueEvent.Id, nb: Option[spi.Year], na: Option[spi.Year]) =
      spi.CatalogueEvent(
        id,
        spi.CatalogueEvent.Url(s"https://zq.example/$id"),
        spi.CatalogueEvent.Label(s"Zq Flood $id"),
        com.histmersion.plugin.CatalogueEvent.Kind("flood"),
        nb,
        na,
        lat = Some(spi.Latitude(30.0)),
        lon = Some(spi.Longitude(31.0))
      )
    def read(part: spi.EventCatalogue.ReadPart, q: spi.CatalogueQuery, http: spi.Plugin.Http) =
      http
        .get(com.histmersion.plugin.Plugin.Http.GetUrl("https://zq.example/api/floods"), Map.empty)
        .left
        .map(e => spi.EventCatalogue.ReadResultPart1(e.value))
        .map(_ =>
          spi.CataloguePage(
            List(
              ev(spi.CatalogueEvent.Id("future"), Some(spi.Year(2000)), Some(spi.Year(2500))), // past the present
              ev(spi.CatalogueEvent.Id("early"), Some(spi.Year(-3500)), Some(spi.Year(-2900))).modify(_.beginBy).setTo(Some(spi.Year(-3100))), // before the floor
              ev(spi.CatalogueEvent.Id("stated"), None, None).modify(_.central).setTo(Some(spi.Year(-1000))).modify(_.plusMinus).setTo(Some(spi.CatalogueEvent.PlusMinus(50L))), // a year ± only
              ev(spi.CatalogueEvent.Id("plain"), Some(spi.Year(-1200)), Some(spi.Year(-1200)))
            )
          )
        ) // nothing to hold

  final private class FloodsPlugin extends spi.Plugin:
    type Config = ZqCatalogueConfig
    val info = spi.Plugin.Info(com.histmersion.plugin.Plugin.Info.Id("zq-floods-plugin"), com.histmersion.plugin.Plugin.Info.Version("0.1.0"), spi.Plugin.Info.SpiVersion(spi.Spi.Version))
    override def defaultsResource = com.histmersion.plugin.Plugin.DefaultsResourceResult("zq-none.conf")
    def register(c: ZqCatalogueConfig, ctx: spi.Plugin.Context) = ctx.eventCatalogues.add(FloodsAdd, ZqFloods).left.map(e => spi.Plugin.RegisterResultPart1(e.value))

  private def floodsGeneration: Generation =
    PluginHost.build(
      ServerPlugins.providers :+ FloodsPlugin(),
      ConfigFactory.parseString("histmersion.plugins.zq-floods-plugin.enabled = true"),
      ConfigFactory.empty,
      Plugins.services(
        () => new spi.Plugin.Http { def get(u: spi.Plugin.Http.GetUrl, h: Map[spi.Plugin.Http.GetHeadersPart1, spi.Plugin.Http.GetHeadersPart2]) = Left(spi.Plugin.Http.GetResultPart1("no")) },
        ConfigFactory.empty
      )
    )

  private def storedFloods: Map[CatalogRecord.ProviderId, (TimeSpan, TimeSpan, Option[CatalogRecord.Central], Option[CatalogRecord.PlusMinus])] =
    val events = EventFixtures.stored(ds).map(e => e.id -> e.timeSpan).toMap
    EventFixtures
      .records(ds, Some(Floods))
      ._1
      .map((id, _, r) =>
        r.providerId -> (r.timeSpan, events.getOrElse(id, fail(s"no event $id")), r.central, r.plusMinus)
      )
      .toMap

  test("a plugin's records are held inside the present and its floor AT THE IMPORT; rederive then changes nothing"):
    val h = CatalogueWiring(Deps(cfg, ds, ds, plugins = floodsGeneration), fake2, () => com.histmersion.domain.DeepTime.SignedYear(2026L))
    val r =
      h.eventImporter.runImport(Floods, EventImportRequest(FloodsRequest, dryRun = com.histmersion.api.catalogue.EventImportRequest.DryRun(false), to = Some(EventImportRequest.To(3000L))), None, _ => ())
    assertEquals((r.records, r.kept, r.created, r.failed), (4, 4, 4, Nil), r.toString)
    val at = storedFloods
    // the record and its event both carry the held dating
    assertEquals(at(CatalogRecord.ProviderId("future"))._1, TimeSpan(Some(com.histmersion.api.catalogue.Year(2000L)), None, None, Some(com.histmersion.api.catalogue.Year(2026L))))
    assertEquals(at(CatalogRecord.ProviderId("early"))._1, TimeSpan(Some(com.histmersion.api.catalogue.Year(-3000L)), Some(com.histmersion.api.catalogue.Year(-3000L)), None, Some(com.histmersion.api.catalogue.Year(-2900L))))
    assertEquals(at(CatalogRecord.ProviderId("stated"))._1, TimeSpan(Some(com.histmersion.api.catalogue.Year(-1050L)), None, None, Some(com.histmersion.api.catalogue.Year(-950L))))
    assertEquals((at(CatalogRecord.ProviderId("stated"))._3, at(CatalogRecord.ProviderId("stated"))._4), (Some(-1000L), Some(50L)), "the stated year and ± kept as stated")
    assertEquals(at(CatalogRecord.ProviderId("plain"))._1, TimeSpan(Some(com.histmersion.api.catalogue.Year(-1200L)), None, None, Some(com.histmersion.api.catalogue.Year(-1200L))))
    at.foreach((k, v) => assertEquals(v._2, v._1, s"$k: the event's dating is its record's"))
    // re-derived from the stored records (no network): the same datings, nothing changed
    val again = h.eventImporter.runRederive(Some(Floods), dryRun = false, _ => ())
    assertEquals((again.records, again.changed, again.fromRaw), (4, 0, 0), again.toString)
    assertEquals(storedFloods, at)
    // and with the plugin switched off since (no floor known): still nothing to change
    val off = CatalogueWiring(Deps(cfg, ds, ds, plugins = ServerPlugins.offline), fake2, () => com.histmersion.domain.DeepTime.SignedYear(2026L))
    assertEquals(off.eventImporter.runRederive(Some(Floods), dryRun = true, _ => ()).changed, 0)

  private def fake2 = FakeMediaHttp(url =>
    if url == "https://zq.example/api/floods" then FakeMediaHttp.json("{}") else Left(s"unexpected $url")
  )

/** The fixture plugin's (empty) settings. */
final case class ZqCatalogueConfig() derives hearth.kindlings.sconfigderivation.ConfigReader
