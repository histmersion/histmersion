package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given
import com.histmersion.api.BBox
import com.histmersion.api.catalogue.{HistoricalPeriod, Latitude, Longitude, Period, PresetMenu, PresetRegion, Year}
import com.histmersion.api.UiLabels
import com.histmersion.lint.{LintRule, tolerated}

/** The pure halves of the shared controls: the playback speed menu, and the period-preset menu's grouping by region.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class ControlsSuite extends munit.FunSuite:

  // ── playback speed ─────────────────────────────────────────────────────────────────────────────

  test("the speed menu offers the speeds there are, slowest first, and reads them back"):
    assertEquals(
      TimelinePlayback.Speeds.map(TimelinePlayback.speedLabel).map(_.value),
      List("0.25×", "0.5×", "1×", "2×", "4×")
    )
    assertEquals(TimelinePlayback.Speeds.map(_.value), TimelinePlayback.Speeds.map(_.value).sorted)
    TimelinePlayback.Speeds.foreach(s =>
      assertEquals(TimelinePlayback.parseSpeed(TimelinePlayback.speedLabel(s)), Some(s))
    )
    assertEquals(TimelinePlayback.parseSpeed(UiLabels.Text("3×")), None)
    assertEquals(TimelinePlayback.parseSpeed(UiLabels.Text(" 2× ")).map(_.value), Some(2.0))

  test("playback moves by whole years and carries the fraction: a century at 1× takes its 30 s at 60 frames a second"):
    val (years, carried, frames) = Iterator
      .iterate((0L, 0.0, 0)) { (y, c, n) =>
        val (whole, left) = TimelinePlayback.wholeYears(c, 100.0 / 30, 1.0 / 60)
        (y + whole, left, n + 1)
      }
      .dropWhile(_._1 < 100)
      .next()
    assertEquals(years, 100L)
    assert(frames >= 1795 && frames <= 1805, s"$frames frames")
    assert(carried >= 0 && carried < 1, s"carried $carried")
    assertEquals(TimelinePlayback.wholeYears(0.0, 2.0, 0.25), (0L, 0.5))
    assertEquals(TimelinePlayback.wholeYears(0.5, 2.0, 0.25), (1L, 0.0))
    assertEquals(TimelinePlayback.wholeYears(0.25, 1000.0, 0.1)._1, 100L)

  // ── the period-preset menu ─────────────────────────────────────────────────────────────────────

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def preset(
      id: HistoricalPeriod.Id,
      label: HistoricalPeriod.Label,
      period: Period,
      r: PresetRegion,
      where: HistoricalPeriod.Region
  ) =
    HistoricalPeriod(
      id,
      label,
      Some(HistoricalPeriod.Authority(s"PeriodO ${id.value} · Some authority (2021)")),
      period,
      Some(where),
      None,
      Some(r)
    )

  private val presets = List(
    preset(
      HistoricalPeriod.Id("p1"),
      HistoricalPeriod.Label("Bronze Age"),
      Period(Year(-3600), Year(-2800)),
      PresetRegion.LevantMesopotamia,
      HistoricalPeriod.Region("Levant")
    ),
    preset(
      HistoricalPeriod.Id("p2"),
      HistoricalPeriod.Label("Bronze Age"),
      Period(Year(-1700), Year(-900)),
      PresetRegion.Scandinavia,
      HistoricalPeriod.Region("Denmark")
    ),
    preset(
      HistoricalPeriod.Id("p3"),
      HistoricalPeriod.Label("Neolithic"),
      Period(Year(-3900), Year(-3100)),
      PresetRegion.Scandinavia,
      HistoricalPeriod.Region("Denmark")
    ),
    preset(
      HistoricalPeriod.Id("p4"),
      HistoricalPeriod.Label("Bronze Age"),
      Period(Year(-3200), Year(-2400)),
      PresetRegion.Aegean,
      HistoricalPeriod.Region("Greece")
    ),
    HistoricalPeriod(
      HistoricalPeriod.Id("p5"),
      HistoricalPeriod.Label("Bronze Age"),
      Some(HistoricalPeriod.Authority("PeriodO p5 · ROAD")),
      Period(Year(-3300), Year(-1500)),
      Some(HistoricalPeriod.Region("Eurasia"))
    )
  )

  test("presets are grouped by region, each region oldest first; a period without a heading is not offered"):
    val gs = PresetMenu.groups(presets)
    assertEquals(gs.map(_.region), List(PresetRegion.Aegean, PresetRegion.LevantMesopotamia, PresetRegion.Scandinavia))
    assertEquals(gs.last.presets.map(_.label.value), List("Neolithic", "Bronze Age"))
    assert(!gs.flatMap(_.presets).exists(_.id.value == "p5"))

  test("the same age is a different span in each region, and each row says where and who"):
    val bronze = PresetMenu.groups(presets).flatMap(_.presets).filter(_.label.value == "Bronze Age")
    assertEquals(
      bronze.map(p => p.region.get.value -> p.period.start.value).toSet,
      Set("Greece" -> -3200L, "Levant" -> -3600L, "Denmark" -> -1700L)
    )
    bronze.foreach(p => assertEquals(PresetMenu.source(p).map(_.value), Some("Some authority (2021)")))

  test("looking at Denmark puts Scandinavia first, and only it is unfolded"):
    val denmark = BBox(Longitude(8.0), Latitude(54.6), Longitude(12.7), Latitude(57.8))
    val gs = PresetMenu.groups(presets, Some(denmark))
    assertEquals(gs.head.region, PresetRegion.Scandinavia)
    assertEquals(gs.filter(_.inView).map(_.region), List(PresetRegion.Scandinavia))
    assertEquals(gs.tail.map(_.region), List(PresetRegion.Aegean, PresetRegion.LevantMesopotamia))
