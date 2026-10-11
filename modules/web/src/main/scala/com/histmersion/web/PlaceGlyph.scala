package com.histmersion.web

import com.histmersion.core.PlaceShape
import com.histmersion.web.leaflet.*
import com.histmersion.web.ui.Layout

import scala.scalajs.js

/** How a gazetteer place is drawn on the map, from its kind and its geometry:
  *   - [[Area]]: a polygon — its outline, as before;
  *   - [[Label]]: a REGION or POLITY known only by a point (no boundary yet: all Pleiades and curated
  *     regions, a Wikidata state with only a coordinate). A region is a shape, so a point is never
  *     drawn like one — no dot, no pin: its name, marked "no boundary yet", hidden while the map is
  *     zoomed out further than [[Layout.areaLabelMinZoom]] (where it would claim a whole country);
  *   - [[Dot]]: a point of a site, city or landmark — what a point is for.
  * A PEOPLE (a tribe, the Ubii) is a group, not a place with a boundary: a [[Label]] too, marked
  * "people" instead.
  * The hover card and the click (choose as the place filter) are the same for all three. */
enum PlaceGlyph:
  case Area, Label, Dot

object PlaceGlyph:

  /** Pure: the glyph for a place of `kind` whose GeoJSON geometry type is `geometryType`. */
  def of(kind: String, geometryType: String): PlaceGlyph =
    val point = geometryType == "Point" || geometryType == "MultiPoint"
    if !point then Area
    else if PlaceShape.isArea(kind) || PlaceShape.isPeople(kind) then Label
    else Dot

  /** The words under a point-only region's name. */
  val NoBoundary: String = "no boundary yet"

  /** The words under a people's name. */
  val People: String = "people"

  /** Pure: the label's markup (escaped) for a place of `kind`; `hidden` starts it culled, for a label
    * the map's name pass ([[MapLabels]]) shows — it then fades and grows in. */
  def labelHtml(label: String, kind: String = com.histmersion.core.PlaceKind.Region, hidden: Boolean = false): String =
    val (note, title) =
      if PlaceShape.isPeople(kind) then (People, PlaceShape.PeopleNote) else (NoBoundary, PlaceShape.NoBoundaryNote)
    val cls = if hidden then "hd-area-point is-culled" else "hd-area-point"
    s"""<span class="$cls" title="${Fmt.esc(label)} — $title">""" +
      s"""<span class="hd-area-point-name">${Fmt.esc(label)}</span>""" +
      s"""<span class="hd-area-point-note">$note</span></span>"""

  /** Pure: is the map zoomed out too far for point-only region labels? */
  def far(zoom: Int): Boolean = zoom < Layout.areaLabelMinZoom

  /** The label marker of a point-only region at (`lat`, `lon`). Its look is `.hd-area-point`. */
  def labelMarker(lat: Double, lon: Double, label: String, kind: String = com.histmersion.core.PlaceKind.Region,
      wrapClass: String = "hd-area-point-wrap", hidden: Boolean = false): Marker =
    L.marker(js.Array(lat, lon), js.Dynamic.literal(
      icon = L.divIcon(js.Dynamic.literal(
        className = wrapClass,
        iconSize = null,
        html = labelHtml(label, kind, hidden)
      ).asInstanceOf[js.Object]),
      pane = MapPanes.AreaPoints,   // a region's name: above the shapes, below every dot
      keyboard = false
    ).asInstanceOf[js.Object])

  /** Keep the map container's `map-far` class equal to [[far]] of its zoom (the stylesheet hides
    * point-only region labels under it). */
  def followZoom(map: LeafletMap): Unit =
    def sync(): Unit =
      val c = map.getContainer()
      if far(map.getZoom()) then c.classList.add("map-far") else c.classList.remove("map-far")
    sync()
    map.on("zoomend", (_: LeafletEvent) => sync())
