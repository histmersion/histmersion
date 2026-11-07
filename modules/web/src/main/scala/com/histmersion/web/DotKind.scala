package com.histmersion.web

import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.library.Finding

/** What a dot on the map stands for. Every kind but the areas (regions, polities: shapes, never dots) joins the one
  * cluster group of the map ([[MapView]]), so dots near each other are drawn as one bubble whatever layer they come
  * from.
  */
enum DotKind(val one: DotKind.One, val many: DotKind.Many):
  case Finding extends DotKind(com.histmersion.web.DotKind.One("finding"), com.histmersion.web.DotKind.Many("findings"))
  case Site extends DotKind(com.histmersion.web.DotKind.One("site"), com.histmersion.web.DotKind.Many("sites"))
  case Event extends DotKind(com.histmersion.web.DotKind.One("event"), com.histmersion.web.DotKind.Many("events"))
  case Media extends DotKind(com.histmersion.web.DotKind.One("picture"), com.histmersion.web.DotKind.Many("pictures"))

object DotKind:
  type One = One.T
  object One extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Many = Many.T
  object Many extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
