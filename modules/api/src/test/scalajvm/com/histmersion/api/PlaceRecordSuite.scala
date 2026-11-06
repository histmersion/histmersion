package com.histmersion.api

import com.histmersion.api.catalogue.PlacesMarkdown
import com.histmersion.api.views.PlaceDetail
import com.histmersion.api.catalogue.Place

/** What a place's own record says beyond its card (where its dates come from, its heritage designations, the registers
  * that list it), in words and in the place's Markdown.
  */
class PlaceRecordSuite extends munit.FunSuite:

  test("where a place's dates come from, in words"):
    assertEquals(Place.Record.dateSourceText(com.histmersion.api.catalogue.Place.Record.DateSource("wikidata-p571")), "Wikidata: its inception")
    assertEquals(
      Place.Record.dateSourceText(com.histmersion.api.catalogue.Place.Record.DateSource("p2348-periodo:p0ds9qjabc+p0gtzvdx")),
      "Wikidata: its time period, dated by PeriodO"
    )
    assertEquals(Place.Record.dateSourceText(com.histmersion.api.catalogue.Place.Record.DateSource("lpf")), "lpf")

  test("a register is named when the extract keeps it, else by its property"):
    assertEquals(Place.Register(com.histmersion.api.catalogue.Place.Register.Property("P1936"), com.histmersion.api.catalogue.Place.Register.Id("123")).name, "DARE (Digital Atlas of the Roman Empire)")
    assertEquals(Place.Register(com.histmersion.api.catalogue.Place.Register.Property("P9999"), com.histmersion.api.catalogue.Place.Register.Id("x")).name, "P9999")

  test("the place's Markdown lists its record; none when it holds nothing"):
    val p = Place.Summary(com.histmersion.api.catalogue.Place.Id("wikidata_q1"), com.histmersion.api.catalogue.Place.Summary.Label("Larissa Castle"), com.histmersion.api.catalogue.Place.Summary.Kind("site"), None, None, None, com.histmersion.api.catalogue.Place.Summary.Names(""), com.histmersion.api.catalogue.Place.Summary.Contested(false))
    val rec = Place.Record(
      Some(com.histmersion.api.catalogue.Place.Record.DateSource("wikidata-p580")),
      List(com.histmersion.api.catalogue.Place.Record.Periods("Byzantine")),
      List(Place.Designation(com.histmersion.api.catalogue.Place.Designation.Id("Q1"), Some(com.histmersion.api.catalogue.Place.Designation.Label("listed monument")), Some(com.histmersion.api.catalogue.Place.Designation.Start("1922")), None)),
      List(Place.Register(com.histmersion.api.catalogue.Place.Register.Property("P2186"), com.histmersion.api.catalogue.Place.Register.Id("GR-1")))
    )
    val md = PlacesMarkdown.place(PlaceDetail(p, None, Nil, Nil, com.histmersion.api.catalogue.Place.Counts.Findings(0), None, record = rec))
    assert(md.contains("**Dates from:** Wikidata: its start and end time"), md)
    assert(md.contains("- listed monument `Q1` (from 1922)"), md)
    assert(md.contains("- Wiki Loves Monuments: `GR-1`"), md)
    assert(!PlacesMarkdown.place(PlaceDetail(p, None, Nil, Nil, com.histmersion.api.catalogue.Place.Counts.Findings(0), None)).contains("Heritage"))
    assert(Place.Record().isEmpty)
