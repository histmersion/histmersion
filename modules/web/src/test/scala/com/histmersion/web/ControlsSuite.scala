package com.histmersion.web

import com.histmersion.core.*

/** The pure halves of the shared controls: the toolbar's three groups and their order, the playback
  * speed menu, and the period-preset menu's grouping by region. */
class ControlsSuite extends munit.FunSuite:

  // ── the toolbar ────────────────────────────────────────────────────────────────────────────────

  test("the toolbar is three groups, in order: what to do · acquisition · the system"):
    assertEquals(Toolbar.groups.map(_._1), List(Toolbar.Group.Plan, Toolbar.Group.Acquire, Toolbar.Group.System))
    assertEquals(Toolbar.visible(agents = false, keyRequired = false), List(
      List("research goals", "backlog"),
      List("to acquire", "upload", "merge another collection"),
      List("jobs", "worklog", "about")))

  test("the agents' windows join the first group, the key the last — about stays last"):
    assertEquals(Toolbar.visible(agents = true, keyRequired = true), List(
      List("research goals", "backlog", "agent runs", "agent managers", "approvals"),
      List("to acquire", "upload", "merge another collection"),
      List("jobs", "worklog", "API key", "about")))

  test("every tool opens a different window, and every group has a name for a screen reader"):
    val keys = Toolbar.groups.flatMap(_._2).map(_.key)
    assertEquals(keys.distinct, keys)
    assert(Toolbar.Group.values.forall(_.label.nonEmpty))
    // no underscore identifiers where words are meant
    assert(Toolbar.groups.flatMap(_._2).forall(t => !t.label.contains("_")))

  // ── playback speed ─────────────────────────────────────────────────────────────────────────────

  test("the speed menu offers the speeds there are, slowest first, and reads them back"):
    assertEquals(TimelinePlayback.Speeds.map(TimelinePlayback.speedLabel), List("0.25×", "0.5×", "1×", "2×", "4×"))
    assertEquals(TimelinePlayback.Speeds, TimelinePlayback.Speeds.sorted)
    TimelinePlayback.Speeds.foreach(s =>
      assertEquals(TimelinePlayback.parseSpeed(TimelinePlayback.speedLabel(s)), Some(s)))
    assertEquals(TimelinePlayback.parseSpeed("3×"), None)
    assertEquals(TimelinePlayback.parseSpeed(" 2× "), Some(2.0))

  // ── the period-preset menu ─────────────────────────────────────────────────────────────────────

  private def preset(id: String, label: String, start: Long, r: PresetRegion, where: String) =
    HistoricalPeriod(id, label, Some(s"PeriodO $id · Some authority (2021)"), Period(start, start + 800),
      Some(where), None, Some(r))

  private val presets = List(
    preset("p1", "Bronze Age", -3600, PresetRegion.LevantMesopotamia, "Levant"),
    preset("p2", "Bronze Age", -1700, PresetRegion.Scandinavia, "Denmark"),
    preset("p3", "Neolithic", -3900, PresetRegion.Scandinavia, "Denmark"),
    preset("p4", "Bronze Age", -3200, PresetRegion.Aegean, "Greece"),
    HistoricalPeriod("p5", "Bronze Age", Some("PeriodO p5 · ROAD"), Period(-3300, -1500), Some("Eurasia")))

  test("presets are grouped by region, each region oldest first; a period without a heading is not offered"):
    val gs = PresetMenu.groups(presets)
    assertEquals(gs.map(_.region), List(PresetRegion.Aegean, PresetRegion.LevantMesopotamia, PresetRegion.Scandinavia))
    assertEquals(gs.last.presets.map(_.label), List("Neolithic", "Bronze Age"))
    assert(!gs.flatMap(_.presets).exists(_.id == "p5"))

  test("the same age is a different span in each region, and each row says where and who"):
    val bronze = PresetMenu.groups(presets).flatMap(_.presets).filter(_.label == "Bronze Age")
    assertEquals(bronze.map(p => p.region.get -> p.period.start).toSet,
      Set("Greece" -> -3200L, "Levant" -> -3600L, "Denmark" -> -1700L))
    bronze.foreach(p => assertEquals(PresetMenu.source(p), Some("Some authority (2021)")))

  test("looking at Denmark puts Scandinavia first, and only it is unfolded"):
    val denmark = BBox(8.0, 54.6, 12.7, 57.8)
    val gs = PresetMenu.groups(presets, Some(denmark))
    assertEquals(gs.head.region, PresetRegion.Scandinavia)
    assertEquals(gs.filter(_.inView).map(_.region), List(PresetRegion.Scandinavia))
    assertEquals(gs.tail.map(_.region), List(PresetRegion.Aegean, PresetRegion.LevantMesopotamia))
