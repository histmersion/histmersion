package com.histmersion.api

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.GoalsMarkdown
import com.histmersion.api.automation.Goal
import com.histmersion.api.catalogue.{HistoricalPeriod, Lpf, Period, PeriodsMarkdown}
import com.histmersion.api.catalogue.Place

/** The one year module (#1798 item 4): parsing what a person types, the astronomical conversion, and every formatter
  * agreeing on BCE/CE and on year 0 (shown as 1 BCE, never "0 CE").
  */
class YearsSuite extends munit.FunSuite:

  // ── parse: a table of what a person types ─────────────────────────────────────────────────────
  private val accepted: List[(String, Long)] = List(
    "1300 BCE" -> -1300,
    "1300 BC" -> -1300,
    "1300bce" -> -1300,
    "1300 b.c." -> -1300,
    "1300 B.C.E." -> -1300,
    "c. 1200 BCE" -> -1200,
    "ca 1200 BC" -> -1200,
    "circa 3300 BCE" -> -3300,
    "1,200 BCE" -> -1200,
    "-1300" -> -1300,
    "-1300 BCE" -> -1300,
    "+530" -> 530,
    "530" -> 530,
    "530 CE" -> 530,
    "530 AD" -> 530,
    "AD 530" -> 530,
    "A.D. 530" -> 530,
    "530ce" -> 530,
    "1 BCE" -> -1,
    "1 CE" -> 1,
    "  2026  " -> 2026,
    "-3300000" -> -3300000
  )

  accepted.foreach { (in, want) =>
    test(s"parse '$in' = $want"):
      assertEquals(Years.parse(in), Right(want))
  }

  private val refused: List[String] =
    List("", "hello", "1300 BCE CE", "BCE", "13 00", "1300 years", "0", "0 CE", "0 BCE", "-530 CE", "AD -5")

  refused.foreach { in =>
    test(s"parse refuses '$in'"):
      assert(Years.parse(in).isLeft, Years.parse(in).toString)
  }

  test("parse: year 0 is refused with the convention, not as garbage"):
    assert(Years.parse(com.histmersion.api.Years.Text("0")).left.exists(_.contains(Years.NoYearZero)))

  // ── conversion ───────────────────────────────────────────────────────────────────────────────
  test("astronomical ↔ historical: no year 0 on the historical side"):
    assertEquals(Years.fromAstronomical(com.histmersion.api.Years.Astronomical(0)), -1L)
    assertEquals(Years.fromAstronomical(com.histmersion.api.Years.Astronomical(-51)), -52L)
    assertEquals(Years.fromAstronomical(com.histmersion.api.Years.Astronomical(640)), 640L)
    assertEquals(Years.toAstronomical(com.histmersion.api.catalogue.Year(-1)), 0L)
    assertEquals(Years.toAstronomical(com.histmersion.api.catalogue.Year(0)), 0L, "a stored 0 reads as 1 BCE")
    (-3000L to 3000L).filter(_ != 0).foreach(h => assertEquals(Years.fromAstronomical(Years.toAstronomical(h)), h))

  test("shift crosses the era boundary without a year 0"):
    assertEquals(Years.shift(com.histmersion.api.Years.ShiftH(-1), com.histmersion.api.Years.Shift(1)), 1L)
    assertEquals(Years.shift(com.histmersion.api.Years.ShiftH(1), com.histmersion.api.Years.Shift(-1)), -1L)
    assertEquals(Years.shift(com.histmersion.api.Years.ShiftH(-10), com.histmersion.api.Years.Shift(30)), 21L)

  test("ISO years are astronomical: parseIso and isoAstronomical"):
    assertEquals(Years.parseIso(com.histmersion.api.Years.Iso.Wire("0000")), Some(-1L))
    assertEquals(Years.parseIso(com.histmersion.api.Years.Iso.Wire("-2999")), Some(-3000L))
    assertEquals(Years.parseIso(com.histmersion.api.Years.Iso.Wire("about 3000 BC")), None)
    assertEquals(Years.isoAstronomical(com.histmersion.api.Years.Iso.Wire("-0051-07-01T00:00:00Z")), Some(-51L))
    assertEquals(Years.isoAstronomical(com.histmersion.api.Years.Iso.Wire("+2000-01-01")), Some(2000L))
    assertEquals(Years.isoAstronomical(com.histmersion.api.Years.Iso.Wire("x")), None)

  test("fromJson: numbers, decimal numbers and strings through the given reader"):
    import io.circe.Json
    assertEquals(Years.fromJson(Json.fromLong(-1400), _ => None), Some(-1400L))
    assertEquals(Years.fromJson(Json.fromDoubleOrNull(-1400.0), _ => None), Some(-1400L))
    assertEquals(Years.fromJson(Json.fromString("1400 BCE"), Lpf.parseYear), Some(-1400L))
    assertEquals(Years.fromJson(Json.Null, Lpf.parseYear), None)

  // ── formatting: every formatter agrees ───────────────────────────────────────────────────────
  test("YearFmt: BCE negative, CE positive, 0 as 1 BCE, an unknown year as ?"):
    assertEquals(YearFmt(-1300L), "1300 BCE")
    assertEquals(YearFmt(530L), "530 CE")
    assertEquals(YearFmt(0L), "1 BCE")
    assertEquals(YearFmt(None), "?")

  test("openPeriod: both, one, neither"):
    assertEquals(YearFmt.openPeriod(Some(com.histmersion.api.catalogue.Year(-27)), Some(com.histmersion.api.catalogue.Year(410))), "27 BCE–410 CE")
    assertEquals(YearFmt.openPeriod(Some(com.histmersion.api.catalogue.Year(-27)), None), "from 27 BCE")
    assertEquals(YearFmt.openPeriod(None, Some(com.histmersion.api.catalogue.Year(410))), "until 410 CE")
    assertEquals(YearFmt.openPeriod(None, None), "undated")
    assertEquals(YearFmt.openPeriod(None, None, ""), "")
    assertEquals(YearFmt.openPeriod(Some(com.histmersion.api.catalogue.Year(0)), Some(com.histmersion.api.catalogue.Year(0))), "1 BCE")

  test("an event's short and long date lines"):
    val ts = TimeSpan(Some(com.histmersion.api.catalogue.Year(-1650)), Some(com.histmersion.api.catalogue.Year(-1630)), Some(com.histmersion.api.catalogue.Year(-1600)), Some(com.histmersion.api.catalogue.Year(-1590)))
    assertEquals(YearFmt.span(ts), "1650 BCE–1590 BCE")
    assertEquals(YearFmt.span(TimeSpan()), "undated")
    assertEquals(YearFmt.span(TimeSpan(beginBy = Some(com.histmersion.api.catalogue.Year(-776)))), "776 BCE")
    assertEquals(YearFmt.spanLong(ts), "not before 1650 BCE, by 1630 BCE, until 1600 BCE, not after 1590 BCE")
    assertEquals(YearFmt.spanLong(TimeSpan()), "undated")

  test("goal periods print in BCE/CE, not as raw signed years (#1798)"):
    val g = Goal(com.histmersion.api.automation.Goal.Slug("g"), com.histmersion.api.automation.Goal.Title("A goal"), com.histmersion.api.automation.Goal.StatusValue("active"), None, Nil, Nil, Some(com.histmersion.api.catalogue.Year(-1200)), Some(com.histmersion.api.catalogue.Year(-1000)), None, None)
    assert(
      GoalsMarkdown.goalList(Goal.List(com.histmersion.api.RowCount(1), List(g))).contains("(1200 BCE–1000 BCE)"),
      GoalsMarkdown.goalList(Goal.List(com.histmersion.api.RowCount(1), List(g)))
    )
    assert(GoalsMarkdown.goal(g).contains("**Period:** 1200 BCE–1000 BCE"), GoalsMarkdown.goal(g))
    assert(!GoalsMarkdown.goal(g).contains("-1200"))
    val open = g.modify(_.periodEnd).setTo((None: _root_.scala.Option[_root_.scala.Long]))
    assert(GoalsMarkdown.goal(open).contains("**Period:** from 1200 BCE"), GoalsMarkdown.goal(open))

  test("the places and periods views use the same formatter"):
    assertEquals(Place.Overrides.periodText(Some(com.histmersion.api.catalogue.Year(0)), Some(com.histmersion.api.catalogue.Year(5))), "1 BCE–5 CE")
    assertEquals(Place.Dating.describe(Place.Dating.Stated, None, None), "")
    val md = PeriodsMarkdown.list(Vector(HistoricalPeriod(com.histmersion.api.catalogue.HistoricalPeriod.Id("p"), com.histmersion.api.catalogue.HistoricalPeriod.Label("Late Bronze Age"), period = Period(com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1100)))))
    assert(md.contains("1600 BCE–1100 BCE"), md)
