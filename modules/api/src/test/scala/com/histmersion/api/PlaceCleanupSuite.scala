package com.histmersion.api

import com.histmersion.api.catalogue.PlacesMarkdown

import com.histmersion.values.{LocationPrecision, PlaceKind}
import com.histmersion.api.catalogue.{PlaceShape, PlaceSummary}
import com.histmersion.api.views.PlaceDetail

/** The point-only seed regions (2026-10-09): which points are only an atlas grid square's centre. */
class PlaceCleanupSuite extends munit.FunSuite:

  test("grid centres: 5°, 1° and ½° cells, both coordinates; nothing else"):
    // the survey's points: Thessalia the province, Viennensis/Tarraconensis, Akkad
    assert(LocationPrecision.gridCentre(22.5, 37.5))
    assert(LocationPrecision.gridCentre(-2.5, 42.5))
    assert(LocationPrecision.gridCentre(44.5, 32.5), "1° cell")
    assert(LocationPrecision.gridCentre(33.75, 16.25), "½° cell")
    assert(LocationPrecision.gridCentre(-0.25, 46.75), "west of Greenwich")
    assert(!LocationPrecision.gridCentre(22.5, 37.0), "one coordinate only")
    assert(!LocationPrecision.gridCentre(6.0, 47.0), "a cell corner is not its centre")
    assert(!LocationPrecision.gridCentre(22.29543, 39.44387), "Thessalia the region, a real point")
    assert(LocationPrecision.gridCentre(22.5000001, 37.5), "a micro-degree is the resolution")
    assert(!LocationPrecision.gridCentre(22.50001, 37.5))

  test("only a Pleiades point is an atlas grid square; a Wikidata coordinate in whole minutes is not"):
    assertEquals(LocationPrecision.of("pleiades_1001895", 22.5, 37.5), LocationPrecision.GridCell)
    assertEquals(LocationPrecision.of("curated_brittany", 22.5, 37.5), LocationPrecision.Stated)
    assertEquals(LocationPrecision.of("wikidata_q1", 22.25, 37.25), LocationPrecision.Stated)
    assertEquals(LocationPrecision.of("pleiades_1332", 22.29543, 39.44387), LocationPrecision.Stated)
    assertEquals(LocationPrecision.values.toList.map(_.wire), List("stated", "grid-cell"))
    assertEquals(LocationPrecision.parse("grid-cell"), Some(LocationPrecision.GridCell))

  test("the people kind: offered, parsed from foreign labels, and no area"):
    assert(PlaceKind.all.contains(PlaceKind.People))
    assertEquals(PlaceKind.parse("people, tribe"), Some(PlaceKind.People))
    assertEquals(PlaceKind.parse("tribe"), Some(PlaceKind.People))
    assert(!PlaceShape.isArea(PlaceKind.People))
    assert(!PlaceShape.pointOnlyArea(PlaceKind.People, hasBoundary = false))

  test("a place's Markdown: the shape and, for a grid-square point, the location"):
    val pt = Some("""{"type":"Point","coordinates":[22.5,37.5]}""")
    def detail(kind: String, precision: LocationPrecision) = PlaceDetail(
      PlaceSummary("pleiades_1001895", "Thessalia", kind, None, None, None, ""),
      pt,
      Nil,
      Nil,
      0,
      None,
      locationPrecision = precision
    )
    val thess = PlacesMarkdown.place(detail(PlaceKind.Polity, LocationPrecision.GridCell))
    assert(thess.contains(s"**Shape:** ${PlaceShape.NoBoundaryNote}"), thess)
    assert(thess.contains("**Location:** approximate (atlas grid square)"), thess)
    assertEquals(
      detail(PlaceKind.Polity, LocationPrecision.GridCell).shapeNotes,
      List(PlaceShape.NoBoundaryNote, "location: approximate (atlas grid square)")
    )
    val ubii = PlacesMarkdown.place(detail(PlaceKind.People, LocationPrecision.Stated))
    assert(ubii.contains(s"**Shape:** ${PlaceShape.PeopleNote}") && !ubii.contains("**Location:**"), ubii)
