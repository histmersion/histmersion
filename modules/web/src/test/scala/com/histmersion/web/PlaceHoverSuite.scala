package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.{ServiceState, Wording}

import scala.collection.mutable
import com.histmersion.api.instance.{About, BuildStamp, CorpusCounts, Health}
import com.histmersion.api.jobs.JobCounts
import com.histmersion.api.plugins.{PluginSetting, PluginsReport, PluginStatus}
import com.histmersion.api.settings.ConfigFileInfo
import com.histmersion.api.views.PlaceDetail
import com.histmersion.api.backup.Backup
import com.histmersion.api.catalogue.{Place, Year}

/** #716/#717/#718 — the map's place card, click-to-filter and the About window, in their pure parts. */
class PlaceHoverSuite extends munit.FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    UiState.mirrorHash = false
    UiState.store = Store.Memory()
    UiState.loadHash(com.histmersion.web.UiState.UrlHash(""))

  /** A fake clock, as in ThrottleSuite: `later` queues, `advance` runs what is due. */
  final private class Clock:
    var t: Double = 0
    private val queue = mutable.ListBuffer.empty[(Double, HoverIntent.TimerId, () => Unit)]
    private var ids = 0
    def later(ms: HoverIntent.DelayMs, g: () => Unit): HoverIntent.TimerId =
      ids += 1
      val id = HoverIntent.TimerId(ids)
      queue += ((t + ms.value, id, g))
      id
    def cancel(id: HoverIntent.TimerId): Unit = queue.filterInPlace(_._2 != id)
    def advance(ms: Double): Unit =
      t += ms
      val due = queue.filter(_._1 <= t).toList
      queue --= due
      due.foreach(_._3())

  test("#716: sweeping across places fetches nothing; resting on one fires it once") {
    val c = Clock(); val fired = mutable.ListBuffer.empty[String]
    val i = HoverIntent[String](com.histmersion.web.HoverIntent.DelayMs(180), fired += _, c.later, c.cancel)
    // the pointer crosses three shapes in quick succession
    i.enter("a"); c.advance(50); i.leave("a")
    i.enter("b"); c.advance(50)
    i.enter("c"); c.advance(100)
    assertEquals(fired.toList, Nil)
    // …and rests on the last one: one card, however long it stays or wiggles
    c.advance(100)
    assertEquals(fired.toList, List("c"))
    i.enter("c"); c.advance(1000)
    assertEquals(fired.toList, List("c", "c"))
  }

  test("#716: leaving a place drops only its own pending card") {
    val c = Clock(); val fired = mutable.ListBuffer.empty[String]
    val i = HoverIntent[String](com.histmersion.web.HoverIntent.DelayMs(100), fired += _, c.later, c.cancel)
    i.enter("a")
    i.leave("b") // a mouseout from another shape arriving late
    assert(i.isPending("a"))
    c.advance(100)
    assertEquals(fired.toList, List("a"))
    i.enter("a"); i.leave("a"); c.advance(500)
    assertEquals(fired.toList, List("a"))
  }

  test("#716: a place card shows kind, validity in BCE/CE, the parents and the counts") {
    def summary(id: Place.Id, label: Place.Summary.Label, kind: Place.Summary.Kind, ps: Option[Year] = None, pe: Option[Year] = None) =
      Place.Summary(id, label, kind, ps, pe, None, com.histmersion.api.catalogue.Place.Summary.Names(""))
    def named(id: String, label: String, kind: String, ps: Option[Year] = None, pe: Option[Year] = None) =
      summary(Place.Id(id), Place.Summary.Label(label), Place.Summary.Kind(kind), ps, pe)
    val d = PlaceDetail(
      place = named("hatti", "Hittite Empire", "polity", Some(Year(-1650)), Some(Year(-1180))),
      geometry = None,
      parents = List(named("anatolia", "Anatolia", "region"), named("near_east", "Near East", "region")),
      children = Nil,
      findings = com.histmersion.api.catalogue.Place.Counts.Findings(3),
      asOf = Some(com.histmersion.api.catalogue.Year(-1300)),
      counts = Some(Place.Counts(sources = com.histmersion.api.catalogue.Place.Counts.Sources(12), findings = com.histmersion.api.catalogue.Place.Counts.Findings(1), articles = com.histmersion.api.catalogue.Place.Counts.Articles(0), events = com.histmersion.api.catalogue.Place.Counts.Events(2)))
    )
    val c = Preview.card(d)
    assertEquals(c.title, "Hittite Empire")
    assertEquals(c.byline, "Polity · 1650 BCE–1180 BCE")
    assertEquals(c.where, "part of Anatolia, Near East")
    assertEquals(c.facts, List("12 sources · 1 finding · 0 articles · 2 events"))
    assertEquals(c.identifier, "hatti")
    // a disputed shape says so; no counts asked for, none shown; a site with no parents and no period
    val bare =
      Preview.card(d.copy(place = named("troy", "Troy", "site"), parents = Nil, counts = None, contested = true))
    assertEquals(bare.byline, "Site · contested extent")
    assertEquals(bare.where, "")
    assertEquals(bare.facts, Nil)
    // CE years too
    assertEquals(
      Preview.card(d.modify(_.place).setTo(named("x", "X", "polity", Some(Year(-30)), Some(Year(476))))).byline,
      "Polity · 30 BCE–476 CE"
    )
  }

  test("#716: the card sits beside the pointer and flips at the viewport's edges") {
    // room on both sides: below-right
    assertEquals(Preview.pointPlacement(com.histmersion.web.Preview.PointPlacementX(100), com.histmersion.web.Preview.PointPlacementY(100), com.histmersion.web.Preview.PointPlacementW(200), com.histmersion.web.Preview.PointPlacementH(80), com.histmersion.web.Preview.PointPlacementVw(1200), com.histmersion.web.Preview.PointPlacementVh(800)), (114.0, 114.0))
    // near the right and bottom edges: above-left of the pointer
    assertEquals(Preview.pointPlacement(com.histmersion.web.Preview.PointPlacementX(1150), com.histmersion.web.Preview.PointPlacementY(780), com.histmersion.web.Preview.PointPlacementW(200), com.histmersion.web.Preview.PointPlacementH(80), com.histmersion.web.Preview.PointPlacementVw(1200), com.histmersion.web.Preview.PointPlacementVh(800)), (936.0, 686.0))
    // a phone narrower than the card: pinned to the gutter, never off-screen
    val (x, _) = Preview.pointPlacement(com.histmersion.web.Preview.PointPlacementX(200), com.histmersion.web.Preview.PointPlacementY(100), com.histmersion.web.Preview.PointPlacementW(380), com.histmersion.web.Preview.PointPlacementH(80), com.histmersion.web.Preview.PointPlacementVw(400), com.histmersion.web.Preview.PointPlacementVh(800))
    assertEquals(x, 10.0)
  }

  test("a card never leaves the viewport: flipped, shifted, else capped so it scrolls") {
    import Preview.Fit
    // fits below: placed there, allowed the whole room below
    assertEquals(Preview.pointFit(com.histmersion.web.Preview.ScreenPixels(100), com.histmersion.web.Preview.ScreenPixels(100), com.histmersion.web.Preview.ElementSize(200), com.histmersion.web.Preview.PointFitH(300), com.histmersion.web.Preview.ViewportWidth(1200), com.histmersion.web.Preview.PointFitVh(800)), Fit(com.histmersion.web.Preview.Fit.X(114), com.histmersion.web.Preview.Fit.Y(114), com.histmersion.web.Preview.Fit.MaxH(800 - 10 - 114)))
    // too tall below, fits above: above the pointer, never over its top edge
    val up = Preview.pointFit(com.histmersion.web.Preview.ScreenPixels(100), com.histmersion.web.Preview.ScreenPixels(700), com.histmersion.web.Preview.ElementSize(200), com.histmersion.web.Preview.PointFitH(500), com.histmersion.web.Preview.ViewportWidth(1200), com.histmersion.web.Preview.PointFitVh(800))
    assertEquals(up, Fit(Fit.X(114), Fit.Y(700 - 14 - 500), Fit.MaxH(700 - 14 - 10)))
    assert(up.y.value >= 10)
    // too tall for either side: the roomier side, capped to it (the content scrolls)
    val big = Preview.pointFit(com.histmersion.web.Preview.ScreenPixels(100), com.histmersion.web.Preview.ScreenPixels(300), com.histmersion.web.Preview.ElementSize(200), com.histmersion.web.Preview.PointFitH(1000), com.histmersion.web.Preview.ViewportWidth(1200), com.histmersion.web.Preview.PointFitVh(800))
    assertEquals(big, Fit(com.histmersion.web.Preview.Fit.X(114), com.histmersion.web.Preview.Fit.Y(314), Fit.MaxH(800 - 10 - 314)))
    assert(big.y.value + big.maxH.value <= 790)
    val bigUp = Preview.pointFit(com.histmersion.web.Preview.ScreenPixels(100), com.histmersion.web.Preview.ScreenPixels(600), com.histmersion.web.Preview.ElementSize(200), com.histmersion.web.Preview.PointFitH(1000), com.histmersion.web.Preview.ViewportWidth(1200), com.histmersion.web.Preview.PointFitVh(800))
    assertEquals(bigUp, Fit(com.histmersion.web.Preview.Fit.X(114), com.histmersion.web.Preview.Fit.Y(10), Fit.MaxH(600 - 14 - 10)))
    // a short window with the pointer in the middle: the full height, over the pointer
    assertEquals(Preview.pointFit(com.histmersion.web.Preview.ScreenPixels(100), com.histmersion.web.Preview.ScreenPixels(150), com.histmersion.web.Preview.ElementSize(200), com.histmersion.web.Preview.PointFitH(1000), com.histmersion.web.Preview.ViewportWidth(1200), com.histmersion.web.Preview.PointFitVh(300)), Fit(com.histmersion.web.Preview.Fit.X(114), com.histmersion.web.Preview.Fit.Y(10), com.histmersion.web.Preview.Fit.MaxH(280)))
    // under a link: the same rules
    assertEquals(Preview.underFit(com.histmersion.web.Preview.ScreenPixels(500), com.histmersion.web.Preview.UnderFitTop(700), com.histmersion.web.Preview.UnderFitBottom(720), com.histmersion.web.Preview.ElementSize(60), com.histmersion.web.Preview.ElementSize(300), com.histmersion.web.Preview.UnderFitH(900), com.histmersion.web.Preview.ViewportWidth(1200), com.histmersion.web.Preview.UnderFitVh(800)), Fit(com.histmersion.web.Preview.Fit.X(380), com.histmersion.web.Preview.Fit.Y(10), Fit.MaxH(700 - 6 - 10)))
    // every fit stays inside, for any pointer and size
    for x <- 0 to 1200 by 150; y <- 0 to 800 by 100; h <- List(50, 400, 790, 2000) do
      val f = Preview.pointFit(Preview.ScreenPixels(x), Preview.ScreenPixels(y), com.histmersion.web.Preview.ElementSize(300), Preview.PointFitH(h), com.histmersion.web.Preview.ViewportWidth(1200), com.histmersion.web.Preview.PointFitVh(800))
      assert(f.x.value >= 10 && f.x.value + 300 <= 1190 && f.y.value >= 10 && f.y.value + math.min(h.toDouble, f.maxH.value) <= 790, s"$x,$y,$h → $f")
  }

  test("#717: a click makes a place THE place filter; a second click on it clears it") {
    assertEquals(UiState.toggledPlace(Nil, com.histmersion.web.UiState.ToggledPlaceId("hatti")), List("hatti"))
    assertEquals(UiState.toggledPlace(List("anatolia"), com.histmersion.web.UiState.ToggledPlaceId("hatti")), List("hatti")) // narrows, never widens
    assertEquals(UiState.toggledPlace(List("hatti"), com.histmersion.web.UiState.ToggledPlaceId("hatti")), Nil)
    assertEquals(UiState.toggledPlace(List("anatolia", "hatti"), com.histmersion.web.UiState.ToggledPlaceId("hatti")), List("anatolia"))
  }

  test("#717: the map's choice is the place filter the lists, the chips and the hash all read") {
    assert(UiState.togglePlace(com.histmersion.web.UiState.TogglePlaceId("hatti")))
    assertEquals(UiState.placeIds.now(), List("hatti"))
    assertEquals(UiState.toFilter().places, List("hatti"))
    assert(UiState.activeChips().exists(c => c.key.value == "placeIds" && c.value.value == "hatti"))
    assert(UiState.hashString.value.contains("placeId=hatti"), UiState.hashString.value)
    // the same place again: cleared, and gone from the hash
    assert(!UiState.togglePlace(com.histmersion.web.UiState.TogglePlaceId("hatti")))
    assertEquals(UiState.placeIds.now(), Nil)
    assert(!UiState.hashString.value.contains("placeId"), UiState.hashString.value)
    // the chip's ✕ is the same field
    UiState.togglePlace(com.histmersion.web.UiState.TogglePlaceId("troy"))
    UiState.removeChip(com.histmersion.web.UiState.RemoveChipKey("placeIds"), com.histmersion.web.UiState.FilterTerm("troy"))
    assertEquals(UiState.placeIds.now(), Nil)
  }

  test("#718: the About window is a window key like the others, and survives the hash") {
    assertEquals(WindowKey.parse(com.histmersion.web.WindowKey.Wire("about")), Some(WindowKey.About))
    assertEquals(WindowKey.About.wire, "about")
    assertEquals(WindowKey.parse(WindowKey.About.wire), Some(WindowKey.About))
  }

  private val about = About(
    name = com.histmersion.api.instance.About.Name("histmersion"),
    build = BuildStamp(com.histmersion.api.instance.BuildStamp.Version("1.2.3"), Some(com.histmersion.api.instance.BuildStamp.GitCommit("abc123def456")), Some(com.histmersion.api.instance.BuildStamp.CommitDate("2026-10-01T10:00:00+02:00"))),
    health = Health(com.histmersion.api.instance.Health.Status("degraded"), com.histmersion.api.instance.Health.Db("ok"), Some(com.histmersion.api.instance.Health.SchemaVersion("28")), Some(com.histmersion.api.instance.Health.Converter("busy: converting 2 documents"))),
    jobs = JobCounts(com.histmersion.api.jobs.JobCounts.Queued(1), com.histmersion.api.jobs.JobCounts.Running(2), com.histmersion.api.jobs.JobCounts.Done(3), com.histmersion.api.jobs.JobCounts.Failed(4)),
    counts = Some(CorpusCounts(com.histmersion.api.instance.CorpusCounts.Sources(10), com.histmersion.api.instance.CorpusCounts.Findings(200), com.histmersion.api.instance.CorpusCounts.Articles(5), com.histmersion.api.instance.CorpusCounts.Events(7), com.histmersion.api.instance.CorpusCounts.Places(15000), com.histmersion.api.instance.CorpusCounts.Periods(40), com.histmersion.api.instance.CorpusCounts.Presets(12))),
    lastBackup = Some(Backup.Info(com.histmersion.api.backup.Backup.Info.File("histmersion-20261001.dump"), com.histmersion.api.ByteCount(2_500_000L),com.histmersion.api.backup.Backup.Info.CreatedAt("2026-10-01T08:00:00Z"))),
    lastBackupAgeSeconds = Some(com.histmersion.api.instance.About.LastBackupAgeSeconds(90000))
  )

  test("#718: the About panel shows build, health with states, jobs, backup age and store counts") {
    val s = AboutView.sections(about)
    assertEquals(s.map(_.title), List("Server", "Health", "Job queue", "Backups", "Store"))
    def row(sec: String, label: String) = s.find(_.title == sec).flatMap(_.rows.find(_.label == label)).get
    assertEquals(row("Server", "commit").value, "abc123def456")
    assertEquals(row("Server", "schema version").value, "28")
    assertEquals(row("Health", "converter").state, Some(ServiceState.Busy))
    assertEquals(row("Health", "converter").detail, Some("busy: converting 2 documents"))
    assertEquals(row("Health", "database").state, Some(ServiceState.Ok))
    assertEquals(row("Health", "database").detail, None)
    assertEquals(row("Health", "overall").state, Some(ServiceState.Down))
    assertEquals(row("Job queue", "running").value, "2")
    assertEquals(row("Backups", "last backup").value, "1 d ago")
    assertEquals(row("Store", "gazetteer places").value, "15000")
    assertEquals(row("Store", "periods").value, "40")
    assertEquals(row("Store", "period presets").value, "12") // #904: presets counted apart from all periods
    assertEquals(row("Server", "config file").value, "none")
    val withFile =
      AboutView.sections(about.modify(_.configFile).setTo(ConfigFileInfo(Some(ConfigFileInfo.Path("/c/histmersion.conf")), loaded = ConfigFileInfo.Loaded(true))))
    val cf = withFile.head.rows.find(_.label == "config file").get
    assertEquals((cf.value, cf.detail), ("read", Some("/c/histmersion.conf")))
  }

  test(
    "#2018: the About panel lists the plugins — state badge, version, what each registered, settings as references"
  ) {
    val report = PluginsReport(
      com.histmersion.api.plugins.PluginsReport.SpiVersion(1),
      List(
        PluginStatus(com.histmersion.api.plugins.PluginStatus.Id("media-met"), com.histmersion.api.plugins.PluginStatus.Version("1.0.0"), com.histmersion.api.plugins.PluginStatus.SpiVersion(1), enabled = com.histmersion.api.plugins.PluginStatus.Enabled(true), com.histmersion.api.plugins.PluginStatus.State("registered"), List(com.histmersion.api.plugins.PluginDescription.Provides("media-collection:met"))),
        PluginStatus(
          com.histmersion.api.plugins.PluginStatus.Id("portal"),
          com.histmersion.api.plugins.PluginStatus.Version("0.2.0"),
          com.histmersion.api.plugins.PluginStatus.SpiVersion(1),
          enabled = com.histmersion.api.plugins.PluginStatus.Enabled(true),
          com.histmersion.api.plugins.PluginStatus.State("registered"),
          List(com.histmersion.api.plugins.PluginDescription.Provides("media-collection:portal"), com.histmersion.api.plugins.PluginDescription.Provides("event-catalogue:portal")),
          List(PluginSetting(com.histmersion.api.plugins.PluginSetting.Key("token"), com.histmersion.api.plugins.PluginSetting.Value("env:PORTAL_TOKEN")), PluginSetting(com.histmersion.api.plugins.PluginSetting.Key("key"), com.histmersion.api.plugins.PluginSetting.Value("value:***")))
        ),
        PluginStatus(com.histmersion.api.plugins.PluginStatus.Id("1password"), com.histmersion.api.plugins.PluginStatus.Version("1.0.0"), com.histmersion.api.plugins.PluginStatus.SpiVersion(1), enabled = com.histmersion.api.plugins.PluginStatus.Enabled(false), com.histmersion.api.plugins.PluginStatus.State("disabled")),
        PluginStatus(com.histmersion.api.plugins.PluginStatus.Id("broken"), com.histmersion.api.plugins.PluginStatus.Version("0.1.0"), com.histmersion.api.plugins.PluginStatus.SpiVersion(1), enabled = com.histmersion.api.plugins.PluginStatus.Enabled(true), com.histmersion.api.plugins.PluginStatus.State("failed"), error = Some(com.histmersion.api.plugins.PluginDescription.Error("histmersion.plugins.broken.x: bad")))
      ),
      List(com.histmersion.api.plugins.PluginsReport.Warnings("histmersion.plugins.helo configures no plugin in this binary — ignored"))
    )
    val s = AboutView.sections(about.modify(_.plugins).setTo(report))
    assertEquals(s.map(_.title), List("Server", "Health", "Job queue", "Backups", "Store", "Plugins"))
    val rows = s.find(_.title == "Plugins").get.rows
    def row(id: String) = rows.find(_.label == Wording.plugin(id)).get
    assertEquals((row("media-met").value, row("media-met").state), ("Registered", Some(ServiceState.Ok)))
    assertEquals(row("media-met").label, "Pictures: Met")
    assertEquals(row("media-met").detail, Some("media-met · v1.0.0 · media-collection:met"))
    assertEquals(
      row("portal").detail,
      Some(
        "portal · v0.2.0 · media-collection:portal, event-catalogue:portal · token = env:PORTAL_TOKEN, key = value:***"
      )
    )
    assertEquals(row("1password").state, Some(ServiceState.Unknown))
    assertEquals(
      (row("broken").state, row("broken").detail),
      (Some(ServiceState.Down), Some("broken · v0.1.0 · histmersion.plugins.broken.x: bad"))
    )
    assertEquals(rows.last, AboutView.Row("", "histmersion.plugins.helo configures no plugin in this binary — ignored"))
    assertEquals(AboutView.plugins(PluginsReport()), None)
  }

  test("#718: an unknown converter wording renders as unknown with its text; missing parts are said") {
    val odd = about.modify(
      _.health).setTo(about.health.modify(_.converter).setTo(Some(com.histmersion.api.instance.Health.Converter("calibrating flux")))).modify(_.counts).setTo(None).modify(_.lastBackup).setTo(None).modify(_.lastBackupAgeSeconds).setTo(None).modify(_.build).setTo(BuildStamp(BuildStamp.Version("1.2.3"))).modify(_.problems).setTo(List(About.Problems("store counts: connection refused"))
    )
    val s = AboutView.sections(odd)
    val conv = s.find(_.title == "Health").get.rows.find(_.label == "converter").get
    assertEquals(
      (conv.value, conv.state, conv.detail),
      ("unknown", Some(ServiceState.Unknown), Some("calibrating flux"))
    )
    assertEquals(s.map(_.title), List("Server", "Health", "Job queue", "Backups", "Problem"))
    assertEquals(s.find(_.title == "Backups").get.rows.head.value, "none yet")
    assertEquals(s.find(_.title == "Server").get.rows.find(_.label == "commit").get.value, "unknown")
    assertEquals(AboutView.check("converter", None).state, Some(ServiceState.Unknown))
  }
