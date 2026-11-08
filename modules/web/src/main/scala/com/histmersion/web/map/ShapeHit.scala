package com.histmersion
package web.map

import org.scalajs.dom

import scala.scalajs.js
import io.github.iltotore.iron.constraint.any.Pure

/** A shape under the pointer: its set and its id. */
final case class ShapeHit(set: ShapeHit.Set, id: ShapeHit.Id) derives CanEqual

object ShapeHit:
  type Set = Set.T
  object Set extends Newtype[String, Pure]
  type Id = Id.T
  object Id extends Newtype[String, Pure]
