package com.histmersion
package domain.library

import com.histmersion.WireEnumValues
import io.github.iltotore.iron.constraint.any.Pure
enum MatchOutcome:
  case Same

  case AutoMerge

  case NeedsDecision

  case New

  case DeletedHere

object MatchOutcome:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  def wireOf(o: MatchOutcome): Wire = o match
    case Same          => Wire("same")
    case AutoMerge     => Wire("auto-merge")
    case NeedsDecision => Wire("needs-decision")
    case New           => Wire("new")
    case DeletedHere   => Wire("deleted-here")
  given WireEnumValues[MatchOutcome]:
    def all: Array[MatchOutcome] = values
    def wire(e: MatchOutcome): WireEnumValues.Wire = WireEnumValues.Wire((wireOf(e)).value)
    def fromWire(s: WireEnumValues.Wire): Option[MatchOutcome] = values.find(o => wireOf(o) == Wire((s).value.trim))
