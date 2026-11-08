package com.histmersion
package domain.catalogue.maptiles

import io.github.iltotore.iron.constraint.any.Pure

/** One POINT feature of a vector tile: its place in tile units and its properties, in order. */
final case class TilePoint(x: TilePoint.X, y: TilePoint.Y, props: Seq[(TilePoint.PropKey, TileValue)])

object TilePoint:
  type PropKey = PropKey.T
  object PropKey extends Newtype[String, Pure]
  type X = X.T
  object X extends Newtype[Int, Pure]
  type Y = Y.T
  object Y extends Newtype[Int, Pure]
