package com.histmersion
package domain.catalogue.maptiles

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.TwoCases

/** A tile feature's property value, as the vector tile format types it. */
enum TileValue derives CanEqual:
  case Text(value: TileValue.Text.Value)
  case Whole(value: TileValue.Whole.Value)
  case Real(value: TileValue.Real.Value)
  case Flag(value: TileValue.Flag.Value)

object TileValue:
  object Text:
    type Value = Value.T
    object Value extends Newtype[String, Pure]
  object Whole:
    type Value = Value.T
    object Value extends Newtype[Long, Pure]
  object Real:
    type Value = Value.T
    object Value extends Newtype[Double, Pure]
  object Flag:
    enum Value derives CanEqual:
      case On, Off
    object Value extends TwoCases[Value]:
      extension (value: Value) def on: Boolean = value == On
