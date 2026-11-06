package com.histmersion.domain.catalogue.maptiles

/** One POINT feature of a vector tile: its place in tile units and its properties, in order. */
final case class TilePoint(x: TilePoint.X, y: TilePoint.Y, props: Seq[(TilePoint.PropsPart1, TileValue)])

object TilePoint:
  type PropKey = PropKey.T
  object PropKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type X = X.T
  object X extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Y = Y.T
  object Y extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PropsPart1 = com.histmersion.domain.catalogue.maptiles.TilePoint.PropKey
  val PropsPart1: _root_.com.histmersion.domain.catalogue.maptiles.TilePoint.PropKey.type = _root_.com.histmersion.domain.catalogue.maptiles.TilePoint.PropKey
