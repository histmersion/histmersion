package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.catalogue.PlacesMarkdown

import com.histmersion.api.views.PlaceDetail
import com.histmersion.api.catalogue.{Latitude, Longitude, Place}

/** The point-only seed regions (2026-10-09): which points are only an atlas grid square's centre. */
class PlaceCleanupSuite extends munit.FunSuite:

  test("grid centres: 5°, 1° and ½° cells, both coordinates; nothing else"):
    // the survey's points: Thessalia the province, Viennensis/Tarraconensis, Akkad
    assert(Place.Precision.gridCentre(Longitude(22.5), Latitude(37.5)))
    assert(Place.Precision.gridCentre(Longitude(-2.5), Latitude(42.5)))
    assert(Place.Precision.gridCentre(Longitude(44.5), Latitude(32.5)), "1° cell")
    assert(Place.Precision.gridCentre(Longitude(33.75), Latitude(16.25)), "½° cell")
    assert(Place.Precision.gridCentre(Longitude(-0.25), Latitude(46.75)), "west of Greenwich")
    assert(!Place.Precision.gridCentre(Longitude(22.5), Latitude(37.0)), "one coordinate only")
    assert(!Place.Precision.gridCentre(Longitude(6.0), Latitude(47.0)), "a cell corner is not its centre")
    assert(!Place.Precision.gridCentre(Longitude(22.29543), Latitude(39.44387)), "Thessalia the region, a real point")
    assert(Place.Precision.gridCentre(Longitude(22.5000001), Latitude(37.5)), "a micro-degree is the resolution")
    assert(!Place.Precision.gridCentre(Longitude(22.50001), Latitude(37.5)))

  test("only a Pleiades point is an atlas grid square; a Wikidata coordinate in whole minutes is not"):
    assertEquals(
      Place.Precision.of(Place.Id("pleiades_1001895"), Longitude(22.5), Latitude(37.5)),
      Place.Precision.GridCell
    )
    assertEquals(
      Place.Precision.of(Place.Id("curated_brittany"), Longitude(22.5), Latitude(37.5)),
      Place.Precision.Stated
    )
    assertEquals(Place.Precision.of(Place.Id("wikidata_q1"), Longitude(22.25), Latitude(37.25)), Place.Precision.Stated)
    assertEquals(
      Place.Precision.of(Place.Id("pleiades_1332"), Longitude(22.29543), Latitude(39.44387)),
      Place.Precision.Stated
    )
    assertEquals(Place.Precision.values.toList.map(_.wire), List("stated", "grid-cell"))
    assertEquals(Place.Precision.parse(Place.Precision.Wire("grid-cell")), Some(Place.Precision.GridCell))

  test("the people kind: offered, parsed from foreign labels, and no area"):
    assert(Place.Kind.all.contains(Place.Kind.People))
    assertEquals(Place.Kind.parse(Place.Kind.ParseS("people, tribe")), Some(Place.Kind.People))
    assertEquals(Place.Kind.parse(Place.Kind.ParseS("tribe")), Some(Place.Kind.People))
    assert(!Place.Shape.isArea(Place.Shape.IsAreaKind(Place.Kind.People)))
    assert(!Place.Shape.pointOnlyArea(Place.Shape.PointOnlyAreaKind(Place.Kind.People), hasBoundary = false))

  test("a place's Markdown: the shape and, for a grid-square point, the location"):
    val pt = Some(Place.Geometry("""{"type":"Point","coordinates":[22.5,37.5]}"""))
    def detail(kind: Place.Summary.Kind, precision: Place.Precision) = PlaceDetail(
      Place.Summary(
        Place.Id("pleiades_1001895"),
        Place.Summary.Label("Thessalia"),
        kind,
        None,
        None,
        None,
        Place.Summary.Names("")
      ),
      pt,
      Nil,
      Nil,
      Place.Counts.Findings(0),
      None,
      locationPrecision = precision
    )
    val thess = PlacesMarkdown.place(detail(Place.Summary.Kind(Place.Kind.Polity), Place.Precision.GridCell))
    assert(thess.contains(s"**Shape:** ${Place.Shape.NoBoundaryNote}"), thess)
    assert(thess.contains("**Location:** approximate (atlas grid square)"), thess)
    assertEquals(
      detail(Place.Summary.Kind(Place.Kind.Polity), Place.Precision.GridCell).shapeNotes,
      List(Place.Shape.NoBoundaryNote, "location: approximate (atlas grid square)")
    )
    val ubii = PlacesMarkdown.place(detail(Place.Summary.Kind(Place.Kind.People), Place.Precision.Stated))
    assert(
      ubii.contains(s"**Shape:** ${Place.Shape.PeopleNote}") && !ubii.contains(
        "**Location:**"
      ),
      ubii
    )
