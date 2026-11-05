package com.histmersion.web.map

import org.scalajs.dom

import scala.scalajs.js

/** A shape under the pointer: its set and its id. */
final case class ShapeHit(set: ShapeHit.Set, id: ShapeHit.Id)

object ShapeHit:
  type Set = Set.T
  object Set extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Id = Id.T
  object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
