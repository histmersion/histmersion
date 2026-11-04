package com.histmersion.api

import com.histmersion.api.catalogue.PlacesMarkdown

import com.histmersion.api.views.PlaceDetail
import com.histmersion.api.catalogue.Place

/** The point-only seed regions (2026-10-09): which points are only an atlas grid square's centre. */
class PlaceCleanupSuite extends munit.FunSuite:

  test("grid centres: 5°, 1° and ½° cells, both coordinates; nothing else"):
    // the survey's points: Thessalia the province, Viennensis/Tarraconensis, Akkad
    assert(Place.Precision.gridCentre(22.5, 37.5))
    assert(Place.Precision.gridCentre(-2.5, 42.5))
    assert(Place.Precision.gridCentre(44.5, 32.5), "1° cell")
    assert(Place.Precision.gridCentre(33.75, 16.25), "½° cell")
    assert(Place.Precision.gridCentre(-0.25, 46.75), "west of Greenwich")
    assert(!Place.Precision.gridCentre(22.5, 37.0), "one coordinate only")
    assert(!Place.Precision.gridCentre(6.0, 47.0), "a cell corner is not its centre")
    assert(!Place.Precision.gridCentre(22.29543, 39.44387), "Thessalia the region, a real point")
    assert(Place.Precision.gridCentre(22.5000001, 37.5), "a micro-degree is the resolution")
    assert(!Place.Precision.gridCentre(22.50001, 37.5))

  test("only a Pleiades point is an atlas grid square; a Wikidata coordinate in whole minutes is not"):
    assertEquals(Place.Precision.of("pleiades_1001895", 22.5, 37.5), Place.Precision.GridCell)
    assertEquals(Place.Precision.of("curated_brittany", 22.5, 37.5), Place.Precision.Stated)
    assertEquals(Place.Precision.of("wikidata_q1", 22.25, 37.25), Place.Precision.Stated)
    assertEquals(Place.Precision.of("pleiades_1332", 22.29543, 39.44387), Place.Precision.Stated)
    assertEquals(Place.Precision.values.toList.map(_.wire), List("stated", "grid-cell"))
    assertEquals(Place.Precision.parse("grid-cell"), Some(Place.Precision.GridCell))

  test("the people kind: offered, parsed from foreign labels, and no area"):
    assert(Place.Kind.all.contains(Place.Kind.People))
    assertEquals(Place.Kind.parse("people, tribe"), Some(Place.Kind.People))
    assertEquals(Place.Kind.parse("tribe"), Some(Place.Kind.People))
    assert(!com.histmersion.api.catalogue.Place.Shape.isArea(Place.Kind.People))
    assert(!com.histmersion.api.catalogue.Place.Shape.pointOnlyArea(Place.Kind.People, hasBoundary = false))

  test("a place's Markdown: the shape and, for a grid-square point, the location"):
    val pt = Some("""{"type":"Point","coordinates":[22.5,37.5]}""")
    def detail(kind: String, precision: Place.Precision) = PlaceDetail(
      com.histmersion.api.catalogue.Place.Summary("pleiades_1001895", "Thessalia", kind, None, None, None, ""),
      pt,
      Nil,
      Nil,
      0,
      None,
      locationPrecision = precision
    )
    val thess = PlacesMarkdown.place(detail(Place.Kind.Polity, Place.Precision.GridCell))
    assert(thess.contains(s"**Shape:** ${com.histmersion.api.catalogue.Place.Shape.NoBoundaryNote}"), thess)
    assert(thess.contains("**Location:** approximate (atlas grid square)"), thess)
    assertEquals(
      detail(Place.Kind.Polity, Place.Precision.GridCell).shapeNotes,
      List(com.histmersion.api.catalogue.Place.Shape.NoBoundaryNote, "location: approximate (atlas grid square)")
    )
    val ubii = PlacesMarkdown.place(detail(Place.Kind.People, Place.Precision.Stated))
    assert(
      ubii.contains(s"**Shape:** ${com.histmersion.api.catalogue.Place.Shape.PeopleNote}") && !ubii.contains(
        "**Location:**"
      ),
      ubii
    )
