package com.histmersion
package util.data.exchange

import com.histmersion.WireEnumValues
import io.github.iltotore.iron.constraint.any.Pure
enum DecisionKind:
  case KeepLeft

  case KeepRight

  case Both

  case Concat

  case Hull

  case Map

  case RenameRight

  case RenameLeft

  case File

  case Merge

  case Drop

  case KeepUnanchored

  def takesArgument: Boolean = this match
    case Map | RenameRight | RenameLeft | File => (true)
    case _                                     => (false)

object DecisionKind:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  def wireOf(k: DecisionKind): Wire = k match
    case KeepLeft       => Wire("keep-left")
    case KeepRight      => Wire("keep-right")
    case Both           => Wire("both")
    case Concat         => Wire("concat")
    case Hull           => Wire("hull")
    case Map            => Wire("map")
    case RenameRight    => Wire("rename-right")
    case RenameLeft     => Wire("rename-left")
    case File           => Wire("file")
    case Merge          => Wire("merge")
    case Drop           => Wire("drop")
    case KeepUnanchored => Wire("keep-unanchored")

  given WireEnumValues[DecisionKind]:
    def all: Array[DecisionKind] = values
    def wire(e: DecisionKind): WireEnumValues.Wire = WireEnumValues.Wire((wireOf(e)).value)
    def fromWire(s: WireEnumValues.Wire): Option[DecisionKind] = values.find(k => wireOf(k) == Wire((s).value.trim))
