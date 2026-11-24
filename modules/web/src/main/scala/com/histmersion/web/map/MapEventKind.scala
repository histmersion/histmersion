package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.lint.{LintRule, tolerated}

/** What happened on the map. */
@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
enum MapEventKind derives CanEqual:
  case MoveStart, Move, MoveEnd, ZoomStart, ZoomEnd, Resize

  /** A click on the map (the app's markers answer their own clicks: see [[MapEvent.overMarker]]). */
  case Click

  /** The pointer moved over the map, or left it. */
  case PointerMove, PointerOut

  /** A new base map is drawn (its first frame with every source asked for). */
  case BaseMapReady
