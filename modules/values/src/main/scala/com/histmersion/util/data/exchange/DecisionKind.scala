package com.histmersion
package util.data.exchange

import com.histmersion.WireEnumValues
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
enum DecisionKind:
  /** Keep this collection's version. */
  case KeepLeft

  /** Take the bundle's version. */
  case KeepRight

  /** Keep both (two sources; the bundle's article under another slug). */
  case Both

  /** Join both texts (summary, notes). */
  case Concat

  /** The smallest period covering both. */
  case Hull

  /** One source, under the given citekey; the other citekey becomes an alias. */
  case Map

  /** Keep both, the bundle's under a new citekey. */
  case RenameRight

  /** Keep both, this collection's under a new citekey. */
  case RenameLeft

  /** The merged text is in a file (an article). */
  case File

  /** Two records are one: fold the bundle's into this collection's (a near-duplicate finding). */
  case Merge

  /** Leave the bundle's record out (an attestation that cannot be re-anchored). */
  case Drop

  /** Keep the attestation without a position in the text (its quote is kept). */
  case KeepUnanchored

  /** `true` when the decision needs an argument (`map:<citekey>`). */
  def takesArgument: Boolean = this match
    case Map | RenameRight | RenameLeft | File => (true)
    case _                                     => (false)

object DecisionKind:
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]

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
