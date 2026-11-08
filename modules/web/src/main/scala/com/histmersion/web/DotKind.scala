package com.histmersion
package web

import org.scalajs.dom

import scala.scalajs.js
import com.histmersion.api.library.Finding
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** What a dot on the map stands for. Every kind but the areas (regions, polities: shapes, never dots) joins the one
  * cluster group of the map ([[MapView]]), so dots near each other are drawn as one bubble whatever layer they come
  * from.
  */
enum DotKind(val one: DotKind.One, val many: DotKind.Many):
  case Finding extends DotKind(DotKind.One("finding"), DotKind.Many("findings"))
  case Site extends DotKind(DotKind.One("site"), DotKind.Many("sites"))
  case Event extends DotKind(DotKind.One("event"), DotKind.Many("events"))
  case Media extends DotKind(DotKind.One("picture"), DotKind.Many("pictures"))

object DotKind:
  type One = One.T
  object One extends RefinedType[String, Pure]
  type Many = Many.T
  object Many extends RefinedType[String, Pure]
