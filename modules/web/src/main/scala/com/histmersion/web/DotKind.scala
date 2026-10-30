package com.histmersion.web

import com.histmersion.api.*
import org.scalajs.dom

import scala.scalajs.js

/** What a dot on the map stands for. Every kind but the areas (regions, polities: shapes, never dots) joins the one
  * cluster group of the map ([[MapView]]), so dots near each other are drawn as one bubble whatever layer they come
  * from.
  */
enum DotKind(val one: String, val many: String):
  case Finding extends DotKind("finding", "findings")
  case Site extends DotKind("site", "sites")
  case Event extends DotKind("event", "events")
  case Media extends DotKind("picture", "pictures")
