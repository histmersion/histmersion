package com.histmersion.web

import com.histmersion.values.PlaceKind

/** How a place is drawn (2026-10-09): a region or polity is a SHAPE, so one known only by a point is its name marked
  * "no boundary yet" — never a dot like a site — hidden when zoomed far out; and a chosen place is named by its label
  * and kind in every chip, never by its id.
  */
class PlaceGlyphSuite extends munit.FunSuite:

  test("a polygon is an area; a point region or polity is a label; a point site is a dot"):
    assertEquals(PlaceGlyph.of(PlaceKind.Polity, "MultiPolygon"), PlaceGlyph.Area)
    assertEquals(PlaceGlyph.of(PlaceKind.Region, "Polygon"), PlaceGlyph.Area)
    assertEquals(PlaceGlyph.of(PlaceKind.Region, "Point"), PlaceGlyph.Label)
    assertEquals(PlaceGlyph.of(PlaceKind.Polity, "Point"), PlaceGlyph.Label)
    assertEquals(PlaceGlyph.of(PlaceKind.Site, "Point"), PlaceGlyph.Dot)
    assertEquals(PlaceGlyph.of(PlaceKind.Feature, "Point"), PlaceGlyph.Dot)
    assertEquals(PlaceGlyph.of(PlaceKind.People, "Point"), PlaceGlyph.Label, "a people is named, never a dot")

  test("a people's label says 'people', not that a boundary is missing"):
    val h = PlaceGlyph.labelHtml("Ubii", PlaceKind.People)
    assert(h.contains(">people<"), h)
    assert(!h.contains("no boundary"), h)

  test("the label names the place, says it has no boundary yet, and escapes the name"):
    val h = PlaceGlyph.labelHtml("Kingdom <of> Patones")
    assert(h.contains("Kingdom &lt;of&gt; Patones"), h)
    assert(h.contains("no boundary yet"), h)
    assert(h.contains("class=\"hd-area-point\""), h)
    assert(!h.contains("<of>"))

  test("labels hide when the map is zoomed out past the configured zoom"):
    assert(PlaceGlyph.far(ui.Layout.areaLabelMinZoom - 1))
    assert(!PlaceGlyph.far(ui.Layout.areaLabelMinZoom))

  test("a chosen place's chip: label and kind, never the (humanised) id"):
    val lookup = Map("wikidata_q131839713" -> ("Kingdom of Patones", "polity")).get
    assertEquals(PlacesInput.chipText("wikidata_q131839713", lookup), "Kingdom of Patones (polity)")
    val unknown = PlacesInput.chipText("wikidata_q131839713", _ => None)
    assert(!unknown.toLowerCase.contains("wikidata") && !unknown.contains("q131839713"), unknown)
