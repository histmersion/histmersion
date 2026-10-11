package com.histmersion.core

/** The map's stacking order, as data: every layer the web map draws goes into one of these Leaflet
  * PANES, never into the shared default `overlayPane`, so what is on top does not depend on which
  * request came back last. Inside one pane Leaflet stacks by insertion order — a region re-added on a
  * period change used to land on top of the site dots drawn before it, in the same SVG, and take their
  * clicks. Across panes the stylesheet's `z-index` decides, once.
  *
  * The rule the order encodes: a POINT (a site dot, a finding marker or cluster, a media dot, an
  * event) is always above an AREA (a region or polity shape, the chosen place's outline, an event's
  * outline, the names of areas), so a dot inside a region answers the pointer and the region answers
  * it everywhere else. Each pane's `z-index` is in `web/style.css` (`.leaflet-<name>-pane`; the marker
  * pane's is Leaflet's own), checked against this order by `CssTokensSuite`. A pane that holds an SVG
  * renderer lets the pointer through its empty area (`> svg { pointer-events:none }`): only its
  * shapes are targets, so the SVG of the sites above never blankets the regions below. */
object MapStack:

  /** A pane, bottom to top. `point`: it holds point features (always above every area pane);
    * `vector`: Leaflet draws SVG paths in it (its own renderer, whose root must not catch the pointer). */
  enum Pane(val name: String, val point: Boolean, val vector: Boolean):
    /** Region and polity shapes, the chosen or previewed place's outline, an event's outline. */
    case Areas      extends Pane("hd-areas", point = false, vector = true)
    /** The name of a region, polity or people known only by a point (an interactive label). */
    case AreaPoints extends Pane("hd-area-points", point = false, vector = false)
    /** The names of areas (the web map's label pass) — never a pointer target. */
    case Labels     extends Pane("hd-labels", point = false, vector = false)
    /** Site dots (and a chosen place that is a point). */
    case Sites      extends Pane("hd-sites", point = true, vector = true)
    /** Leaflet's own marker pane: finding markers, clusters, media dots. */
    case Markers    extends Pane("markerPane", point = true, vector = false)
    /** Playback's event labels and dots — never a pointer target. */
    case Events     extends Pane("hd-events", point = true, vector = false)

    /** The class Leaflet gives the pane's element (`createPane`: `leaflet-<name without "Pane">-pane`). */
    def cssClass: String = s"leaflet-${name.replace("Pane", "")}-pane"

    /** Leaflet creates its own panes; the others the map creates once. */
    def builtIn: Boolean = this == Markers

  /** Bottom to top. */
  val order: List[Pane] = Pane.values.toList

  /** Pure: is the order sound — every point pane above every area pane? */
  def pointsAboveAreas(order: List[Pane]): Boolean =
    val firstPoint = order.indexWhere(_.point)
    firstPoint < 0 || order.drop(firstPoint).forall(_.point)
