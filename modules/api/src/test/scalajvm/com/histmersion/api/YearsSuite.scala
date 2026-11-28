package com.histmersion
package api
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.GoalsMarkdown
import com.histmersion.api.automation.Goal
import com.histmersion.api.catalogue.{HistoricalPeriod, Lpf, Period, PeriodsMarkdown}
import com.histmersion.api.catalogue.Place
import com.histmersion.api.catalogue.Year
import com.histmersion.lint.{tolerated, LintRule}

/** The one year module (#1798 item 4): parsing what a person types, the astronomical conversion, and every formatter
  * agreeing on BCE/CE and on year 0 (shown as 1 BCE, never "0 CE").
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
      assertEquals(Year.parse(Year.Text(in)), Right(want))
  }

  private val refused: List[String] =
    List("", "hello", "1300 BCE CE", "BCE", "13 00", "1300 years", "0", "0 CE", "0 BCE", "-530 CE", "AD -5")

  refused.foreach { in =>
    test(s"parse refuses '$in'"):
      assert(Year.parse(Year.Text(in)).isLeft, Year.parse(Year.Text(in)).toString)
  }

  test("parse: year 0 is refused with the convention, not as garbage"):
    assert(Year.parse(Year.Text("0")).left.exists(_.sentence.contains(Year.NoYearZero.sentence)))

  // ── conversion ───────────────────────────────────────────────────────────────────────────────
  test("astronomical ↔ historical: no year 0 on the historical side"):
    assertEquals(Year.fromAstronomical(Year.Astronomical(0)), -1L)
    assertEquals(Year.fromAstronomical(Year.Astronomical(-51)), -52L)
    assertEquals(Year.fromAstronomical(Year.Astronomical(640)), 640L)
    assertEquals(Year(-1).toAstronomical, 0L)
    assertEquals(Year(0).toAstronomical, 0L, "a stored 0 reads as 1 BCE")
    (-3000L to 3000L)
      .filter(_ != 0)
      .foreach(h => assertEquals(Year.fromAstronomical(Year(h).toAstronomical), h))

  test("shift crosses the era boundary without a year 0"):
    assertEquals(Year(-1).shift(Year.Shift(1)), 1L)
    assertEquals(Year(1).shift(Year.Shift(-1)), -1L)
    assertEquals(Year(-10).shift(Year.Shift(30)), 21L)

  test("ISO years are astronomical: parseIso and isoAstronomical"):
    assertEquals(Year.parseIso(Year.Iso.Wire("0000")), Some(-1L))
    assertEquals(Year.parseIso(Year.Iso.Wire("-2999")), Some(-3000L))
    assertEquals(Year.parseIso(Year.Iso.Wire("about 3000 BC")), None)
    assertEquals(Year.isoAstronomical(Year.Iso.Wire("-0051-07-01T00:00:00Z")), Some(-51L))
    assertEquals(Year.isoAstronomical(Year.Iso.Wire("+2000-01-01")), Some(2000L))
    assertEquals(Year.isoAstronomical(Year.Iso.Wire("x")), None)

  test("fromJson: numbers, decimal numbers and strings through the given reader"):
    assertEquals(Year.fromJson(Json.fromLong(-1400), _ => None), Some(-1400L))
    assertEquals(Year.fromJson(Json.fromDoubleOrNull(-1400.0), _ => None), Some(-1400L))
    assertEquals(Year.fromJson(Json.fromString("1400 BCE"), raw => Lpf.parseYear(raw.value)), Some(-1400L))
    assertEquals(Year.fromJson(Json.Null, raw => Lpf.parseYear(raw.value)), None)

  // ── formatting: every formatter agrees ───────────────────────────────────────────────────────
  test("YearFmt: BCE negative, CE positive, 0 as 1 BCE, an unknown year as ?"):
    assertEquals(Year.show(Year(-1300L)), "1300 BCE")
    assertEquals(Year.show(Year(530L)), "530 CE")
    assertEquals(Year.show(Year(0L)), "1 BCE")
    assertEquals(Year.show(None), "?")

  test("openPeriod: both, one, neither"):
    assertEquals(Year.openPeriod(Some(Year(-27)), Some(Year(410))), "27 BCE–410 CE")
    assertEquals(Year.openPeriod(Some(Year(-27)), None), "from 27 BCE")
    assertEquals(Year.openPeriod(None, Some(Year(410))), "until 410 CE")
    assertEquals(Year.openPeriod(None, None), "undated")
    assertEquals(Year.openPeriod(None, None, ""), "")
    assertEquals(Year.openPeriod(Some(Year(0)), Some(Year(0))), "1 BCE")

  test("an event's short and long date lines"):
    val ts = TimeSpan(Some(Year(-1650)), Some(Year(-1630)), Some(Year(-1600)), Some(Year(-1590)))
    assertEquals(ts.show, "1650 BCE–1590 BCE")
    assertEquals(TimeSpan().show, "undated")
    assertEquals(TimeSpan(beginBy = Some(Year(-776))).show, "776 BCE")
    assertEquals(ts.showLong, "not before 1650 BCE, by 1630 BCE, until 1600 BCE, not after 1590 BCE")
    assertEquals(TimeSpan().showLong, "undated")

  test("goal periods print in BCE/CE, not as raw signed years (#1798)"):
    val g = Goal(
      Goal.Slug("g"),
      Goal.Title("A goal"),
      Goal.StatusValue("active"),
      None,
      Nil,
      Nil,
      Some(Year(-1200)),
      Some(Year(-1000)),
      None,
      None
    )
    assert(
      GoalsMarkdown.goalList(Goal.List(RowCount(1), List(g))).contains("(1200 BCE–1000 BCE)"),
      GoalsMarkdown.goalList(Goal.List(RowCount(1), List(g)))
    )
    assert(GoalsMarkdown.goal(g).contains("**Period:** 1200 BCE–1000 BCE"), GoalsMarkdown.goal(g))
    assert(!GoalsMarkdown.goal(g).contains("-1200"))
    val open = g.modify(_.periodEnd).setTo(None)
    assert(GoalsMarkdown.goal(open).contains("**Period:** from 1200 BCE"), GoalsMarkdown.goal(open))

  test("the places and periods views use the same formatter"):
    assertEquals(Place.Overrides.periodText(Some(Year(0)), Some(Year(5))), "1 BCE–5 CE")
    assertEquals(Place.Dating.describe(Place.Dating.Stated, None, None), "")
    val md = PeriodsMarkdown.list(
      Vector(
        HistoricalPeriod(
          HistoricalPeriod.Id("p"),
          HistoricalPeriod.Label("Late Bronze Age"),
          period = Period(Year(-1600), Year(-1100))
        )
      )
    )
    assert(md.contains("1600 BCE–1100 BCE"), md)
