package com.histmersion.domain.catalogue.maptiles

/** A tile feature's property value, as the vector tile format types it. */
enum TileValue:
  case Text(value: TileValue.Text.Value)
  case Whole(value: TileValue.Whole.Value)
  case Real(value: TileValue.Real.Value)
  case Flag(value: TileValue.Flag.Value)

object TileValue:
  object Text:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Whole:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Real:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Flag:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
