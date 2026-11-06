package com.histmersion.util.data.exchange

import com.histmersion.WireEnumValues
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
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def wireOf(k: DecisionKind): com.histmersion.util.data.exchange.DecisionKind.Wire = k match
    case KeepLeft       => _root_.com.histmersion.util.data.exchange.DecisionKind.Wire("keep-left")
    case KeepRight      => _root_.com.histmersion.util.data.exchange.DecisionKind.Wire("keep-right")
    case Both           => _root_.com.histmersion.util.data.exchange.DecisionKind.Wire("both")
    case Concat         => _root_.com.histmersion.util.data.exchange.DecisionKind.Wire("concat")
    case Hull           => _root_.com.histmersion.util.data.exchange.DecisionKind.Wire("hull")
    case Map            => _root_.com.histmersion.util.data.exchange.DecisionKind.Wire("map")
    case RenameRight    => _root_.com.histmersion.util.data.exchange.DecisionKind.Wire("rename-right")
    case RenameLeft     => _root_.com.histmersion.util.data.exchange.DecisionKind.Wire("rename-left")
    case File           => _root_.com.histmersion.util.data.exchange.DecisionKind.Wire("file")
    case Merge          => _root_.com.histmersion.util.data.exchange.DecisionKind.Wire("merge")
    case Drop           => _root_.com.histmersion.util.data.exchange.DecisionKind.Wire("drop")
    case KeepUnanchored => _root_.com.histmersion.util.data.exchange.DecisionKind.Wire("keep-unanchored")

  given WireEnumValues[DecisionKind]:
    def all: Array[DecisionKind] = DecisionKind.values
    def wire(e: DecisionKind): _root_.com.histmersion.WireEnumValues.Wire = _root_.com.histmersion.WireEnumValues.Wire((wireOf(e)).value)
    def fromWire(s: _root_.com.histmersion.WireEnumValues.Wire): _root_.scala.Option[DecisionKind] = DecisionKind.values.find(k => wireOf(k) == _root_.com.histmersion.util.data.exchange.DecisionKind.Wire((s).value.trim))
