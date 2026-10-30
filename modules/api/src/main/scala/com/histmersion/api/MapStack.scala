package com.histmersion.api

/** The map's stacking order, as data. The map draws its AREAS itself (region and polity shapes, the chosen place's
  * outline, an event's outline: shapes on its canvas, under everything); every other thing it shows is a DOM marker in
  * one of these PANES over the canvas, so what is on top does not depend on which request came back last.
  *
  * The rule the order encodes: a POINT (a site, a finding marker or bubble, a picture, an event) is always above an
  * AREA (a shape on the canvas, a region's name), so a dot inside a region answers the pointer and the region answers
  * it everywhere else. Each pane's `z-index` is in `web/style.css` (`.hd-pane--<name>`), checked against this order by
  * `CssTokensSuite`; a pane itself never catches the pointer (`pointer-events:none`), only its markers do.
  */
object MapStack:

  /** A layer of the map, bottom to top. `point`: it holds point features (always above every area); `canvas`: drawn by
    * the map on its canvas, not a DOM pane.
    */
  enum Pane(val name: String, val point: Boolean, val canvas: Boolean):
    /** Region and polity shapes, the chosen or previewed place's outline, an event's outline: the canvas. */
    case Areas extends Pane("areas", point = false, canvas = true)

    /** The name of a region, polity or people known only by a point (an interactive label). */
    case AreaPoints extends Pane("area-points", point = false, canvas = false)

    /** The names of areas (the web map's label pass). */
    case Labels extends Pane("labels", point = false, canvas = false)

    /** Site dots (and a chosen place that is a point). */
    case Sites extends Pane("sites", point = true, canvas = false)

    /** Finding markers, bubbles, pictures. */
    case Markers extends Pane("markers", point = true, canvas = false)

    /** The events: their markers, and playback's labels. */
    case Events extends Pane("events", point = true, canvas = false)

    /** The pane element's class. */
    def cssClass: String = s"hd-pane--$name"

  /** Bottom to top. */
  val order: List[Pane] = Pane.values.toList

  /** The DOM panes, bottom to top. */
  val domPanes: List[Pane] = order.filterNot(_.canvas)

  /** Pure: is the order sound — every point pane above every area pane? */
  def pointsAboveAreas(order: List[Pane]): Boolean =
    val firstPoint = order.indexWhere(_.point)
    firstPoint < 0 || order.drop(firstPoint).forall(_.point)
