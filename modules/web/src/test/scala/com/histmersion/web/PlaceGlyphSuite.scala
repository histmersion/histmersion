package com.histmersion
package web

import com.histmersion.testsupport.ComparedByValue.given

/** How a place is drawn (2026-10-09): a region or polity is a SHAPE, so one known only by a point is its name marked
  * "no boundary yet" — never a dot like a site — hidden when zoomed far out; and a chosen place is named by its label
  * and kind in every chip, never by its id.
  */
import com.histmersion.api.catalogue.Place
import com.histmersion.api.UiLabels
import com.histmersion.web.ui.Layout
import com.histmersion.lint.{tolerated, LintRule}
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class PlaceGlyphSuite extends munit.FunSuite:

  test("a polygon is an area; a point region or polity is a label; a point site is a dot"):
    assertEquals(
      PlaceGlyph.of(Place.Summary.Kind(Place.Kind.Polity), PlaceGlyph.GeometryType("MultiPolygon")),
      PlaceGlyph.Area
    )
    assertEquals(
      PlaceGlyph.of(Place.Summary.Kind(Place.Kind.Region), PlaceGlyph.GeometryType("Polygon")),
      PlaceGlyph.Area
    )
    assertEquals(
      PlaceGlyph.of(Place.Summary.Kind(Place.Kind.Region), PlaceGlyph.GeometryType("Point")),
      PlaceGlyph.Label
    )
    assertEquals(
      PlaceGlyph.of(Place.Summary.Kind(Place.Kind.Polity), PlaceGlyph.GeometryType("Point")),
      PlaceGlyph.Label
    )
    assertEquals(PlaceGlyph.of(Place.Summary.Kind(Place.Kind.Site), PlaceGlyph.GeometryType("Point")), PlaceGlyph.Dot)
    assertEquals(
      PlaceGlyph.of(Place.Summary.Kind(Place.Kind.Feature), PlaceGlyph.GeometryType("Point")),
      PlaceGlyph.Dot
    )
    assertEquals(
      PlaceGlyph.of(Place.Summary.Kind(Place.Kind.People), PlaceGlyph.GeometryType("Point")),
      PlaceGlyph.Label,
      "a people is named, never a dot"
    )

  test("a people's label says 'people', not that a boundary is missing"):
    val h = PlaceGlyph.labelHtml(UiLabels.Text("Ubii"), Place.Summary.Kind(Place.Kind.People)).value
    assert(h.contains(">people<"), h)
    assert(!h.contains("no boundary"), h)

  test("the label names the place, says it has no boundary yet, and escapes the name"):
    val h = PlaceGlyph.labelHtml(UiLabels.Text("Kingdom <of> Patones")).value
    assert(h.contains("Kingdom &lt;of&gt; Patones"), h)
    assert(h.contains("no boundary yet"), h)
    assert(h.contains("class=\"hd-area-point\""), h)
    assert(!h.contains("<of>"))

  test("labels hide when the map is zoomed out past the configured zoom"):
    assert(PlaceGlyph.far(PlaceGlyph.ZoomLevel(Layout.areaLabelMinZoom.value - 1)))
    assert(!PlaceGlyph.far(PlaceGlyph.ZoomLevel(Layout.areaLabelMinZoom.value)))

  test("a chosen place's chip: label and kind, never the (humanised) id"):
    val lookup = Map(
      PlacesInput.PlaceId("wikidata_q131839713") -> (
        UiLabels.Text("Kingdom of Patones"),
        PlacesInput.KnownPlace.Kind("polity")
      )
    ).get
    assertEquals(
      PlacesInput.chipText(PlacesInput.PlaceId("wikidata_q131839713"), lookup),
      "Kingdom of Patones (polity)"
    )
    val unknown = PlacesInput.chipText(PlacesInput.PlaceId("wikidata_q131839713"), _ => None)
    assert(!unknown.value.toLowerCase.contains("wikidata") && !unknown.value.contains("q131839713"), unknown.value)
