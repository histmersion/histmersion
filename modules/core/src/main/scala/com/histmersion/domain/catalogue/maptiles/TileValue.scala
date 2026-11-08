package com.histmersion
package domain.catalogue.maptiles

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A tile feature's property value, as the vector tile format types it. */
enum TileValue:
  case Text(value: TileValue.Text.Value)
  case Whole(value: TileValue.Whole.Value)
  case Real(value: TileValue.Real.Value)
  case Flag(value: TileValue.Flag.Value)

object TileValue:
  object Text:
    type Value = Value.T
    object Value extends RefinedType[String, Pure]
  object Whole:
    type Value = Value.T
    object Value extends RefinedType[Long, Pure]
  object Real:
    type Value = Value.T
    object Value extends RefinedType[Double, Pure]
  object Flag:
    type Value = Value.T
    object Value extends RefinedType[Boolean, Pure]
