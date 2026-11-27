package com.histmersion
package web

import com.histmersion.web.map.*
import com.histmersion.web.ui.Layout
import com.histmersion.api.catalogue.Place
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.UiLabels
import com.histmersion.api.Geo
import com.histmersion.api.Html
import com.histmersion.api.Html.html as markup
import com.histmersion.lint.{tolerated, LintRule}

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
enum PlaceGlyph derives CanEqual:
  case Area, Label, Dot

object PlaceGlyph:
  type GeometryType = GeometryType.T
  object GeometryType extends Newtype[String, Pure]
  enum LabelHidden derives CanEqual:
    case Hidden, Shown
  object LabelHidden:
    extension (labelHidden: LabelHidden) def hidden: Boolean = labelHidden == Hidden
  type ZoomLevel = ZoomLevel.T
  object ZoomLevel extends Newtype[Int, Pure]

  /** Pure: the glyph for a place of `kind` whose GeoJSON geometry type is `geometryType`. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(kind: Place.Summary.Kind, geometryType: GeometryType): PlaceGlyph =
    val point = geometryType.value == "Point" || geometryType.value == "MultiPoint"
    if !point then Area
    else if Place.Shape.isArea(kind) || Place.Shape.isPeople(kind) then Label
    else Dot

  /** The words under a point-only region's name. */
  val NoBoundary: Place.Card.Note = Place.Card.Note("no boundary yet")

  /** The words under a people's name. */
  val People: Place.Card.Note = Place.Card.Note("people")

  /** Pure: the label's markup (escaped) for a place of `kind`; `hidden` starts it culled, for a label the map's name
    * pass ([[MapLabels]]) shows — it then fades and grows in.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def labelHtml(
      label: UiLabels.Text,
      kind: Place.Summary.Kind = Place.Summary.Kind(Place.Kind.Region),
      hidden: LabelHidden = LabelHidden.Shown
  ): Html =
    val (note, title) =
      if Place.Shape.isPeople(kind) then (People, Place.Shape.PeopleNote) else (NoBoundary, Place.Shape.NoBoundaryNote)
    val cls = if hidden.hidden then "hd-area-point is-culled" else "hd-area-point"
    markup"""<span class="$cls" title="$label — $title">""" ++
      markup"""<span class="hd-area-point-name">$label</span>""" ++
      markup"""<span class="hd-area-point-note">$note</span></span>"""

  /** Pure: is the map zoomed out too far for point-only region labels? */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def far(zoom: ZoomLevel): Boolean = zoom.value < Layout.areaLabelMinZoom.value

  /** The label marker of a point-only region at (`lat`, `lon`). Its look is `.hd-area-point`; it is drawn in the
    * area-points pane (a region's name: above the shapes, below every dot).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def labelMarker(
      lat: Geo.Latitude,
      lon: Geo.Longitude,
      label: UiLabels.Text,
      kind: Place.Summary.Kind = Place.Summary.Kind(Place.Kind.Region),
      wrapClass: String = "hd-area-point-wrap",
      hidden: LabelHidden = LabelHidden.Shown
  ): DomMarker =
    new DomMarker(lat, lon, DomMarker.element(wrapClass, labelHtml(label, kind, hidden)))

  /** Keep the map container's `map-far` class equal to [[far]] of its zoom (the stylesheet hides point-only region
    * labels under it).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def followZoom(map: MapSurface): Unit =
    def sync(): Unit = map.container.classList.toggle("map-far", far(ZoomLevel(math.round(map.zoom.value).toInt)))
    sync()
    val _ = map.on(MapEventKind.ZoomEnd)(_ => sync())
