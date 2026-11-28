package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.catalogue.PlacesMarkdown
import com.histmersion.api.views.PlaceDetail
import com.histmersion.api.catalogue.Place

/** What a place's own record says beyond its card (where its dates come from, its heritage designations, the registers
  * that list it), in words and in the place's Markdown.
  */
class PlaceRecordSuite extends munit.FunSuite:

  test("where a place's dates come from, in words"):
    assertEquals(Place.Record.dateSourceText(Place.Record.DateSource("wikidata-p571")), "Wikidata: its inception")
    assertEquals(
      Place.Record.dateSourceText(Place.Record.DateSource("p2348-periodo:p0ds9qjabc+p0gtzvdx")),
      "Wikidata: its time period, dated by PeriodO"
    )
    assertEquals(Place.Record.dateSourceText(Place.Record.DateSource("lpf")), "lpf")

  test("a register is named when the extract keeps it, else by its property"):
    assertEquals(
      Place.Register(Place.Register.Property("P1936"), Place.Register.Id("123")).name,
      "DARE (Digital Atlas of the Roman Empire)"
    )
    assertEquals(Place.Register(Place.Register.Property("P9999"), Place.Register.Id("x")).name, "P9999")

  test("the place's Markdown lists its record; none when it holds nothing"):
    val p = Place.Summary(
      Place.Id("wikidata_q1"),
      Place.Summary.Label("Larissa Castle"),
      Place.Summary.Kind("site"),
      None,
      None,
      None,
      Place.Summary.Names(""),
      Place.Summary.Contested.Settled
    )
    val rec = Place.Record(
      Some(Place.Record.DateSource("wikidata-p580")),
      List(Place.Record.Periods("Byzantine")),
      List(
        Place.Designation(
          Place.Designation.Id("Q1"),
          Some(Place.Designation.Label("listed monument")),
          Some(Place.Designation.Start("1922")),
          None
        )
      ),
      List(Place.Register(Place.Register.Property("P2186"), Place.Register.Id("GR-1")))
    )
    val md = PlacesMarkdown.place(PlaceDetail(p, None, Nil, Nil, Place.Counts.Findings(0), None, record = rec))
    assert(md.contains("**Dates from:** Wikidata: its start and end time"), md)
    assert(md.contains("- listed monument `Q1` (from 1922)"), md)
    assert(md.contains("- Wiki Loves Monuments: `GR-1`"), md)
    assert(!PlacesMarkdown.place(PlaceDetail(p, None, Nil, Nil, Place.Counts.Findings(0), None)).contains("Heritage"))
    assert(Place.Record().isEmpty)
