package com.histmersion.web

import com.histmersion.web.map.*
import com.histmersion.web.ui.Layout
import com.histmersion.api.catalogue.Place

/** How a gazetteer place is drawn on the map, from its kind and its geometry:
  *   - [[Area]]: a polygon — its outline, as before;
  *   - [[Label]]: a REGION or POLITY known only by a point (no boundary yet: all Pleiades and curated regions, a
  *     Wikidata state with only a coordinate). A region is a shape, so a point is never drawn like one — no dot, no
  *     pin: its name, marked "no boundary yet", hidden while the map is zoomed out further than
  *     [[Layout.areaLabelMinZoom]] (where it would claim a whole country);
  *   - [[Dot]]: a point of a site, city or landmark — what a point is for.
  * A PEOPLE (a tribe, the Ubii) is a group, not a place with a boundary: a [[Label]] too, marked "people" instead. The
  * hover card and the click (choose as the place filter) are the same for all three.
  */
enum PlaceGlyph:
  case Area, Label, Dot

object PlaceGlyph:

  /** Pure: the glyph for a place of `kind` whose GeoJSON geometry type is `geometryType`. */
  type OfKind = OfKind.T
  object OfKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfGeometryType = OfGeometryType.T
  object OfGeometryType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoBoundary = NoBoundaryValue
  type NoBoundaryValue = NoBoundaryValue.T
  object NoBoundaryValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type People = PeopleValue
  type PeopleValue = PeopleValue.T
  object PeopleValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelHtmlResult = LabelHtmlResult.T
  object LabelHtmlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelHtmlLabel = LabelHtmlLabel.T
  object LabelHtmlLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelHtmlKind = LabelHtmlKind.T
  object LabelHtmlKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelHtmlHidden = LabelHtmlHidden.T
  object LabelHtmlHidden extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FarResult = FarResult.T
  object FarResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FarZoom = FarZoom.T
  object FarZoom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelMarkerLat = LabelMarkerLat.T
  object LabelMarkerLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelMarkerLon = LabelMarkerLon.T
  object LabelMarkerLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelMarkerLabel = _root_.com.histmersion.web.PlaceGlyph.LabelHtmlLabel
  val LabelMarkerLabel: _root_.com.histmersion.web.PlaceGlyph.LabelHtmlLabel.type = _root_.com.histmersion.web.PlaceGlyph.LabelHtmlLabel
  type LabelMarkerKind = _root_.com.histmersion.web.PlaceGlyph.LabelHtmlKind
  val LabelMarkerKind: _root_.com.histmersion.web.PlaceGlyph.LabelHtmlKind.type = _root_.com.histmersion.web.PlaceGlyph.LabelHtmlKind
  type LabelMarkerWrapClass = LabelMarkerWrapClass.T
  object LabelMarkerWrapClass extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelMarkerHidden = _root_.com.histmersion.web.PlaceGlyph.LabelHtmlHidden
  val LabelMarkerHidden: _root_.com.histmersion.web.PlaceGlyph.LabelHtmlHidden.type = _root_.com.histmersion.web.PlaceGlyph.LabelHtmlHidden

  def of(kind: com.histmersion.web.PlaceGlyph.OfKind, geometryType: com.histmersion.web.PlaceGlyph.OfGeometryType): PlaceGlyph =
    val point = geometryType == "Point" || geometryType == "MultiPoint"
    if !point then Area
    else if Place.Shape.isArea(kind) || Place.Shape.isPeople(kind) then Label
    else Dot

  /** The words under a point-only region's name. */
  val NoBoundary: com.histmersion.web.PlaceGlyph.NoBoundary = "no boundary yet"

  /** The words under a people's name. */
  val People: com.histmersion.web.PlaceGlyph.People = "people"

  /** Pure: the label's markup (escaped) for a place of `kind`; `hidden` starts it culled, for a label the map's name
    * pass ([[MapLabels]]) shows — it then fades and grows in.
    */
  def labelHtml(
      label: com.histmersion.web.PlaceGlyph.LabelHtmlLabel,
      kind: com.histmersion.web.PlaceGlyph.LabelHtmlKind = com.histmersion.api.catalogue.Place.Kind.Region,
      hidden: com.histmersion.web.PlaceGlyph.LabelHtmlHidden = false
  ): com.histmersion.web.PlaceGlyph.LabelHtmlResult =
    val (note, title) =
      if Place.Shape.isPeople(kind) then (People, Place.Shape.PeopleNote) else (NoBoundary, Place.Shape.NoBoundaryNote)
    val cls = if hidden then "hd-area-point is-culled" else "hd-area-point"
    s"""<span class="$cls" title="${Fmt.esc(label)} — $title">""" +
      s"""<span class="hd-area-point-name">${Fmt.esc(label)}</span>""" +
      s"""<span class="hd-area-point-note">$note</span></span>"""

  /** Pure: is the map zoomed out too far for point-only region labels? */
  def far(zoom: com.histmersion.web.PlaceGlyph.FarZoom): com.histmersion.web.PlaceGlyph.FarResult = zoom < Layout.areaLabelMinZoom

  /** The label marker of a point-only region at (`lat`, `lon`). Its look is `.hd-area-point`; it is drawn in the
    * area-points pane (a region's name: above the shapes, below every dot).
    */
  def labelMarker(
      lat: com.histmersion.web.PlaceGlyph.LabelMarkerLat,
      lon: com.histmersion.web.PlaceGlyph.LabelMarkerLon,
      label: com.histmersion.web.PlaceGlyph.LabelMarkerLabel,
      kind: com.histmersion.web.PlaceGlyph.LabelMarkerKind = com.histmersion.api.catalogue.Place.Kind.Region,
      wrapClass: com.histmersion.web.PlaceGlyph.LabelMarkerWrapClass = "hd-area-point-wrap",
      hidden: com.histmersion.web.PlaceGlyph.LabelMarkerHidden = false
  ): DomMarker =
    DomMarker(lat, lon, DomMarker.element(wrapClass, labelHtml(label, kind, hidden)))

  /** Keep the map container's `map-far` class equal to [[far]] of its zoom (the stylesheet hides point-only region
    * labels under it).
    */
  def followZoom(map: MapSurface): Unit =
    def sync(): Unit = map.container.classList.toggle("map-far", far(math.round(map.zoom).toInt))
    sync()
    val _ = map.on(MapEventKind.ZoomEnd)(_ => sync())
