package com.histmersion
package domain.catalogue.maptiles

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** One POINT feature of a vector tile: its place in tile units and its properties, in order. */
final case class TilePoint(x: TilePoint.X, y: TilePoint.Y, props: Seq[(TilePoint.PropsPart1, TileValue)])

object TilePoint:
  type PropKey = PropKey.T
  object PropKey extends RefinedType[String, Pure]
  type X = X.T
  object X extends RefinedType[Int, Pure]
  type Y = Y.T
  object Y extends RefinedType[Int, Pure]
  type PropsPart1 = PropKey
  val PropsPart1: PropKey.type = PropKey
