package com.histmersion.api

import com.histmersion.api.catalogue.places.PlacesMarkdown

/** What a place's own record says beyond its card (where its dates come from, its heritage designations, the registers
  * that list it), in words and in the place's Markdown.
  */
class PlaceRecordSuite extends munit.FunSuite:

  test("where a place's dates come from, in words"):
    assertEquals(PlaceRecord.dateSourceText("wikidata-p571"), "Wikidata: its inception")
    assertEquals(
      PlaceRecord.dateSourceText("p2348-periodo:p0ds9qjabc+p0gtzvdx"),
      "Wikidata: its time period, dated by PeriodO"
    )
    assertEquals(PlaceRecord.dateSourceText("lpf"), "lpf")

  test("a register is named when the extract keeps it, else by its property"):
    assertEquals(PlaceRegister("P1936", "123").name, "DARE (Digital Atlas of the Roman Empire)")
    assertEquals(PlaceRegister("P9999", "x").name, "P9999")

  test("the place's Markdown lists its record; none when it holds nothing"):
    val p = PlaceSummary("wikidata_q1", "Larissa Castle", "site", None, None, None, "", false)
    val rec = PlaceRecord(
      Some("wikidata-p580"),
      List("Byzantine"),
      List(PlaceDesignation("Q1", Some("listed monument"), Some("1922"), None)),
      List(PlaceRegister("P2186", "GR-1"))
    )
    val md = PlacesMarkdown.place(PlaceDetail(p, None, Nil, Nil, 0, None, record = rec))
    assert(md.contains("**Dates from:** Wikidata: its start and end time"), md)
    assert(md.contains("- listed monument `Q1` (from 1922)"), md)
    assert(md.contains("- Wiki Loves Monuments: `GR-1`"), md)
    assert(!PlacesMarkdown.place(PlaceDetail(p, None, Nil, Nil, 0, None)).contains("Heritage"))
    assert(PlaceRecord().isEmpty)
