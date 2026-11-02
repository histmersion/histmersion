package com.histmersion.web

import com.histmersion.api.*
import com.raquo.laminar.api.L.*

import scala.scalajs.js

/** The web UI's shared building blocks. Each thing the UI shows in more than one place — a source link, a chip, a
  * confidence mark, a layer switch, an icon — is built HERE, once, so the sidebar, the tabs, the map popup and the
  * graph cannot drift into five looks and three behaviours. The server's pages use the same class names and attributes
  * (`server.HtmlRender`).
  */

/** Inline SVG icons drawn with `currentColor`, so they follow the button's text colour (and its active state) instead
  * of the platform's emoji font. 24×24 stroke paths.
  */
enum Icon(val paths: List[String]):
  /** Stacked sheets: the map's layers (and, on a phone, the drawer that holds them). */
  case Layers
      extends Icon(List("M12 3.5l8.5 4.5-8.5 4.5-8.5-4.5z", "M3.5 12l8.5 4.5 8.5-4.5", "M3.5 16l8.5 4.5 8.5-4.5"))
  case InView extends Icon(List("M4 9V4h5", "M15 4h5v5", "M20 15v5h-5", "M9 20H4v-5", "M11 12h2"))
  case Jobs extends Icon(List("M12 3a9 9 0 1 0 9 9", "M12 7v5l3 2", "M21 3v5h-5"))
  case Upload extends Icon(List("M12 16V4", "M7 9l5-5 5 5", "M4 16v4h16v-4"))
  case Acquire extends Icon(List("M5 3.5h11l3 3v14H5z", "M9 9h6", "M9 13h6", "M9 17h4"))

  /** A checklist: the backlog. */
  case Tasks
      extends Icon(
        List("M4 6.5l1.5 1.5 3-3", "M4 12.5l1.5 1.5 3-3", "M4 18.5l1.5 1.5 3-3", "M12 7h8", "M12 13h8", "M12 19h8")
      )

  /** A bound notebook: the worklog. */
  case Log extends Icon(List("M6 3.5h12v17H6z", "M9.5 3.5v17", "M13 8h2.5", "M13 12h2.5"))

  /** An i in a circle: about this server. */
  case Filter extends Icon(List("M3.5 5h17l-6.5 8v6l-4 1.5V13z"))
  case FoldLeft extends Icon(List("M14.5 5.5L8 12l6.5 6.5"))
  case NewTab extends Icon(List("M13.5 4.5h6v6", "M19.5 4.5L11 13", "M18 14v5.5H4.5V6H10"))

  /** A folded map: show it on the map. */
  case FoldedMap extends Icon(List("M9 4L3 6v14l6-2 6 2 6-2V4l-6 2z", "M9 4v14", "M15 6v14"))
  case Info extends Icon(List("M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z", "M12 11v6", "M12 7.5h.01"))
  case Key extends Icon(List("M11 14a4.5 4.5 0 1 1 2-2l8 8v2h-3v-2h-2v-2h-2z", "M7.5 9.5h.01"))
  case Edit extends Icon(List("M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4"))

  /** A gear with a spark: the embedded agent runs. */
  case Runs
      extends Icon(
        List(
          "M12 8.5a3.5 3.5 0 1 1 0 7 3.5 3.5 0 0 1 0-7z",
          "M12 2.5v3",
          "M12 18.5v3",
          "M2.5 12h3",
          "M18.5 12h3",
          "M5.3 5.3l2.1 2.1",
          "M16.6 16.6l2.1 2.1",
          "M5.3 18.7l2.1-2.1",
          "M16.6 7.4l2.1-2.1"
        )
      )

  /** A tick in a box: proposals waiting for a decision. */
  case Managers
      extends Icon(
        List("M4 5h10", "M4 12h7", "M4 19h10", "M17 9.5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5z", "M17 4v3", "M17 17v3")
      )
  case Approvals extends Icon(List("M4 4h16v16H4z", "M8 12.5l3 3 5-6"))

  /** A target: the research goals. */
  case Goals
      extends Icon(
        List(
          "M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z",
          "M12 7.5a4.5 4.5 0 1 1 0 9 4.5 4.5 0 0 1 0-9z",
          "M12 11.5a.5.5 0 1 1 0 1 .5.5 0 0 1 0-1z"
        )
      )

  /** Two paths joining: merging another collection into this one. */
  case Merge
      extends Icon(List("M6 3.5v4a5 5 0 0 0 5 5h1a5 5 0 0 1 5 5v3", "M18 3.5v4a5 5 0 0 1-5 5", "M14 17.5l3 3 3-3"))
  // the map's layers, each the SHAPE its markers are drawn as (docs/visualization-guide.md §2), outlined,
  // with a tiny sign of what it holds
  /** A diamond with a document's lines: the findings (a source's claims). */
  case PointFinding extends Icon(List("M12 2.5l9.5 9.5-9.5 9.5-9.5-9.5z", "M9.5 10.5h5", "M9.5 13.5h5"))

  /** A circle with a little temple: the sites. */
  case PointSite
      extends Icon(
        List("M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z", "M8 11h8l-4-3z", "M9.5 11v4", "M14.5 11v4", "M8 15.5h8")
      )

  /** A square (its corners barely rounded, as the markers') with a bolt: the events. */
  case PointEvent
      extends Icon(
        List(
          "M5 3.5h14a1.5 1.5 0 0 1 1.5 1.5v14a1.5 1.5 0 0 1-1.5 1.5H5a1.5 1.5 0 0 1-1.5-1.5V5A1.5 1.5 0 0 1 5 3.5z",
          "M13 7l-4 5.5h3.5l-1 4.5 4-5.5H12z"
        )
      )

  /** A landscape photo print — its mat a frame inside the edge, as the markers' — with a mountain: the pictures. */
  case PointMedia extends Icon(List("M2.5 5.5h19v13h-19z", "M5.5 8.5h13v7h-13z", "M7.5 15.5l3-3 2 2 1.5-1.5 2.5 2.5"))

  /** A solid square beside a dashed one: the events' tier (attested, catalogue only). */
  case TierPair
      extends Icon(
        List(
          "M3 7.5h7.5v9H3z",
          "M14 7.5h1.5",
          "M18 7.5h1.5",
          "M21 9.5v1.5",
          "M21 13v1.5",
          "M19.5 16.5h-1.5",
          "M15.5 16.5h-1.5",
          "M13.5 14.5v-1.5",
          "M13.5 11v-1.5"
        )
      )

  /** A mountain with a name line under it: the base maps' physical names. */
  case MapNames extends Icon(List("M3 15l5-8 3.5 5 2-3 4.5 6z", "M5 19.5h14"))

  /** Three nested contour rings: Physical's contour lines. */
  case MapContours
      extends Icon(
        List(
          "M12 3.5c5 0 9 3.6 9 8.3S17 20.5 12 20.5 3 16.5 3 11.8 7 3.5 12 3.5z",
          "M12 7c3 0 5.5 2.1 5.5 4.8S15 16.8 12 16.8 6.5 14.5 6.5 11.8 9 7 12 7z",
          "M12 10.3c1.3 0 2.3.7 2.3 1.6s-1 1.6-2.3 1.6-2.3-.7-2.3-1.6 1-1.6 2.3-1.6z"
        )
      )

  /** An irregular outline: the regions and polities (their boundaries). */
  case Region extends Icon(List("M4 8l6-4 7 2 3.5 6-3 7-8 1-5.5-5z"))

  /** The theme button (Lucide's contrast, sun and moon): following the system, light, dark. */
  case ThemeSystem extends Icon(List("M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z", "M12 17a5 5 0 0 0 0-10z"))
  case ThemeLight
      extends Icon(
        List(
          "M12 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8z",
          "M12 2v2",
          "M12 20v2",
          "M4.93 4.93l1.41 1.41",
          "M17.66 17.66l1.41 1.41",
          "M2 12h2",
          "M20 12h2",
          "M6.34 17.66l-1.41 1.41",
          "M19.07 4.93l-1.41 1.41"
        )
      )
  case ThemeDark extends Icon(List("M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9z"))
  case Graph
      extends Icon(
        List(
          "M6 6a2 2 0 1 0 0 .01",
          "M18 8a2 2 0 1 0 0 .01",
          "M12 18a2 2 0 1 0 0 .01",
          "M7.7 7.2l8.6 1.3",
          "M7 8l4 8.3",
          "M17 10l-4.2 6.4"
        )
      )

  /** A bookmark ribbon (filled while the item is bookmarked: `.is-on`). */
  case Bookmark extends Icon(List("M6 3.5h12v17l-6-4-6 4z"))

  /** A note: a sheet with a folded corner and lines. */
  case Note extends Icon(List("M4.5 3.5h15v11l-6 6h-9z", "M13.5 20.5v-6h6", "M8 8h8", "M8 11.5h5"))

  /** More actions: three dots. */
  case More extends Icon(List("M5 12h.01", "M12 12h.01", "M19 12h.01"))

  /** A padlock: the map or the timeline locked. */
  case Lock extends Icon(List("M6 10.5h12v10H6z", "M8.5 10.5V7.5a3.5 3.5 0 0 1 7 0v3"))

  /** Read: a tick in a circle. */
  case Read extends Icon(List("M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18z", "M8 12.5l2.5 2.5 5-5.5"))

object Icon:
  /** Pure: the icon as markup, for the places that are HTML strings (the map's key). */
  def html(i: Icon): String =
    s"""<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${i.paths
        .map(d => s"""<path d="$d"/>""")
        .mkString}</svg>"""

  /** The icon as a Laminar SVG element. */
  def apply(i: Icon): SvgElement =
    svg.svg(
      svg.cls := "icon", // size, stroke and fill: `.icon` in the stylesheet
      svg.viewBox := "0 0 24 24",
      aria.hidden := true,
      i.paths.map(d => svg.path(svg.d := d))
    )
