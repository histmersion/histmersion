package com.histmersion.web

import com.histmersion.core.*

import scala.collection.mutable

/** #716/#717/#718 — the map's place card, click-to-filter and the About window, in their pure parts. */
class PlaceHoverSuite extends munit.FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    UiState.mirrorHash = false
    UiState.store = Store.Memory()
    UiState.loadHash("")

  /** A fake clock, as in ThrottleSuite: `later` queues, `advance` runs what is due. */
  private final class Clock:
    var t: Double = 0
    private val queue = mutable.ListBuffer.empty[(Double, Int, () => Unit)]
    private var ids = 0
    def later(ms: Double, g: () => Unit): Int =
      ids += 1; queue += ((t + ms, ids, g)); ids
    def cancel(id: Int): Unit = queue.filterInPlace(_._2 != id)
    def advance(ms: Double): Unit =
      t += ms
      val due = queue.filter(_._1 <= t).toList
      queue --= due
      due.foreach(_._3())

  test("#716: sweeping across places fetches nothing; resting on one fires it once") {
    val c = Clock(); val fired = mutable.ListBuffer.empty[String]
    val i = HoverIntent[String](180, fired += _, c.later, c.cancel)
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
    val i = HoverIntent[String](100, fired += _, c.later, c.cancel)
    i.enter("a")
    i.leave("b")          // a mouseout from another shape arriving late
    assert(i.isPending("a"))
    c.advance(100)
    assertEquals(fired.toList, List("a"))
    i.enter("a"); i.leave("a"); c.advance(500)
    assertEquals(fired.toList, List("a"))
  }

  test("#716: a place card shows kind, validity in BCE/CE, the parents and the counts") {
    def summary(id: String, label: String, kind: String, ps: Option[Long] = None, pe: Option[Long] = None) =
      PlaceSummary(id, label, kind, ps, pe, None, "")
    val d = PlaceDetail(
      place = summary("hatti", "Hittite Empire", "polity", Some(-1650), Some(-1180)),
      geometry = None,
      parents = List(summary("anatolia", "Anatolia", "region"), summary("near_east", "Near East", "region")),
      children = Nil, findings = 3, asOf = Some(-1300),
      counts = Some(PlaceCounts(sources = 12, findings = 1, articles = 0, events = 2)))
    val c = Preview.card(d)
    assertEquals(c.title, "Hittite Empire")
    assertEquals(c.byline, "Polity · 1650 BCE–1180 BCE")
    assertEquals(c.where, "part of Anatolia, Near East")
    assertEquals(c.facts, List("12 sources · 1 finding · 0 articles · 2 events"))
    assertEquals(c.identifier, "hatti")
    // a disputed shape says so; no counts asked for, none shown; a site with no parents and no period
    val bare = Preview.card(d.copy(place = summary("troy", "Troy", "site"), parents = Nil, counts = None,
      contested = true))
    assertEquals(bare.byline, "Site · contested extent")
    assertEquals(bare.where, "")
    assertEquals(bare.facts, Nil)
    // CE years too
    assertEquals(Preview.card(d.copy(place = summary("x", "X", "polity", Some(-30), Some(476)))).byline,
      "Polity · 30 BCE–476 CE")
  }

  test("#716: the card sits beside the pointer and flips at the viewport's edges") {
    // room on both sides: below-right
    assertEquals(Preview.pointPlacement(100, 100, 200, 80, 1200, 800), (114.0, 114.0))
    // near the right and bottom edges: above-left of the pointer
    assertEquals(Preview.pointPlacement(1150, 780, 200, 80, 1200, 800), (936.0, 686.0))
    // a phone narrower than the card: pinned to the gutter, never off-screen
    val (x, _) = Preview.pointPlacement(200, 100, 380, 80, 400, 800)
    assertEquals(x, 10.0)
  }

  test("a card never leaves the viewport: flipped, shifted, else capped so it scrolls") {
    import Preview.Fit
    // fits below: placed there, allowed the whole room below
    assertEquals(Preview.pointFit(100, 100, 200, 300, 1200, 800), Fit(114, 114, 800 - 10 - 114))
    // too tall below, fits above: above the pointer, never over its top edge
    val up = Preview.pointFit(100, 700, 200, 500, 1200, 800)
    assertEquals(up, Fit(114, 700 - 14 - 500, 700 - 14 - 10))
    assert(up.y >= 10)
    // too tall for either side: the roomier side, capped to it (the content scrolls)
    val big = Preview.pointFit(100, 300, 200, 1000, 1200, 800)
    assertEquals(big, Fit(114, 314, 800 - 10 - 314))
    assert(big.y + big.maxH <= 790)
    val bigUp = Preview.pointFit(100, 600, 200, 1000, 1200, 800)
    assertEquals(bigUp, Fit(114, 10, 600 - 14 - 10))
    // a short window with the pointer in the middle: the full height, over the pointer
    assertEquals(Preview.pointFit(100, 150, 200, 1000, 1200, 300), Fit(114, 10, 280))
    // under a link: the same rules
    assertEquals(Preview.underFit(500, 700, 720, 60, 300, 900, 1200, 800), Fit(380, 10, 700 - 6 - 10))
    // every fit stays inside, for any pointer and size
    for x <- 0 to 1200 by 150; y <- 0 to 800 by 100; h <- List(50, 400, 790, 2000) do
      val f = Preview.pointFit(x, y, 300, h, 1200, 800)
      assert(f.x >= 10 && f.x + 300 <= 1190 && f.y >= 10 && f.y + math.min(h.toDouble, f.maxH) <= 790,
        s"$x,$y,$h → $f")
  }

  test("#717: a click makes a place THE place filter; a second click on it clears it") {
    assertEquals(UiState.toggledPlace(Nil, "hatti"), List("hatti"))
    assertEquals(UiState.toggledPlace(List("anatolia"), "hatti"), List("hatti"))   // narrows, never widens
    assertEquals(UiState.toggledPlace(List("hatti"), "hatti"), Nil)
    assertEquals(UiState.toggledPlace(List("anatolia", "hatti"), "hatti"), List("anatolia"))
  }

  test("#717: the map's choice is the place filter the lists, the chips and the hash all read") {
    assert(UiState.togglePlace("hatti"))
    assertEquals(UiState.placeIds.now(), List("hatti"))
    assertEquals(UiState.toFilter().places, List("hatti"))
    assert(UiState.activeChips().exists(c => c.key == "placeIds" && c.value == "hatti"))
    assert(UiState.hashString.contains("placeId=hatti"), UiState.hashString)
    // the same place again: cleared, and gone from the hash
    assert(!UiState.togglePlace("hatti"))
    assertEquals(UiState.placeIds.now(), Nil)
    assert(!UiState.hashString.contains("placeId"), UiState.hashString)
    // the chip's ✕ is the same field
    UiState.togglePlace("troy")
    UiState.removeChip("placeIds", "troy")
    assertEquals(UiState.placeIds.now(), Nil)
  }

  test("#718: the About window is a window key like the others, and survives the hash") {
    assertEquals(WindowKey.parse("about"), Some(WindowKey.About))
    assertEquals(WindowKey.About.wire, "about")
    assertEquals(WindowKey.parse(WindowKey.About.wire), Some(WindowKey.About))
  }

  private val about = About(
    name = "histmersion",
    build = BuildStamp("1.2.3", Some("abc123def456"), Some("2026-10-01T10:00:00+02:00")),
    health = Health("degraded", "ok", Some("28"), Some("busy: converting 2 documents")),
    jobs = JobCounts(1, 2, 3, 4),
    counts = Some(CorpusCounts(10, 200, 5, 7, 15000, 40, 12)),
    lastBackup = Some(BackupInfo("histmersion-20261001.dump", 2_500_000, "2026-10-01T08:00:00Z")),
    lastBackupAgeSeconds = Some(90000))

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
    val withFile = AboutView.sections(about.copy(configFile = ConfigFileInfo(Some("/c/histmersion.conf"), loaded = true)))
    val cf = withFile.head.rows.find(_.label == "config file").get
    assertEquals((cf.value, cf.detail), ("read", Some("/c/histmersion.conf")))
  }

  test("#2018: the About panel lists the plugins — state badge, version, what each registered, settings as references") {
    val report = PluginsReport(1, List(
      PluginStatus("media-met", "1.0.0", 1, enabled = true, "registered", List("media-collection:met")),
      PluginStatus("portal", "0.2.0", 1, enabled = true, "registered", List("media-collection:portal", "event-catalogue:portal"),
        List(PluginSetting("token", "env:PORTAL_TOKEN"), PluginSetting("key", "value:***"))),
      PluginStatus("1password", "1.0.0", 1, enabled = false, "disabled"),
      PluginStatus("broken", "0.1.0", 1, enabled = true, "failed", error = Some("histmersion.plugins.broken.x: bad"))),
      List("histmersion.plugins.helo configures no plugin in this binary — ignored"))
    val s = AboutView.sections(about.copy(plugins = report))
    assertEquals(s.map(_.title), List("Server", "Health", "Job queue", "Backups", "Store", "Plugins"))
    val rows = s.find(_.title == "Plugins").get.rows
    def row(id: String) = rows.find(_.label == id).get
    assertEquals((row("media-met").value, row("media-met").state), ("registered", Some(ServiceState.Ok)))
    assertEquals(row("media-met").detail, Some("v1.0.0 · media-collection:met"))
    assertEquals(row("portal").detail,
      Some("v0.2.0 · media-collection:portal, event-catalogue:portal · token = env:PORTAL_TOKEN, key = value:***"))
    assertEquals(row("1password").state, Some(ServiceState.Unknown))
    assertEquals((row("broken").state, row("broken").detail),
      (Some(ServiceState.Down), Some("v0.1.0 · histmersion.plugins.broken.x: bad")))
    assertEquals(rows.last, AboutView.Row("", "histmersion.plugins.helo configures no plugin in this binary — ignored"))
    assertEquals(AboutView.plugins(PluginsReport()), None)
  }

  test("#718: an unknown converter wording renders as unknown with its text; missing parts are said") {
    val odd = about.copy(health = about.health.copy(converter = Some("calibrating flux")), counts = None,
      lastBackup = None, lastBackupAgeSeconds = None, build = BuildStamp("1.2.3"),
      problems = List("store counts: connection refused"))
    val s = AboutView.sections(odd)
    val conv = s.find(_.title == "Health").get.rows.find(_.label == "converter").get
    assertEquals((conv.value, conv.state, conv.detail), ("unknown", Some(ServiceState.Unknown), Some("calibrating flux")))
    assertEquals(s.map(_.title), List("Server", "Health", "Job queue", "Backups", "Problem"))
    assertEquals(s.find(_.title == "Backups").get.rows.head.value, "none yet")
    assertEquals(s.find(_.title == "Server").get.rows.find(_.label == "commit").get.value, "unknown")
    assertEquals(AboutView.check("converter", None).state, Some(ServiceState.Unknown))
  }
