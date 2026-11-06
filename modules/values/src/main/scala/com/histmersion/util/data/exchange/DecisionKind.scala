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
  def takesArgument: com.histmersion.util.data.exchange.DecisionKind.TakesArgumentResult = this match
    case Map | RenameRight | RenameLeft | File => _root_.com.histmersion.util.data.exchange.DecisionKind.TakesArgumentResult(true)
    case _                                     => _root_.com.histmersion.util.data.exchange.DecisionKind.TakesArgumentResult(false)

object DecisionKind:
  type TakesArgumentResult = TakesArgumentResult.T
  object TakesArgumentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WireOfResult = WireOfResult.T
  object WireOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def wireOf(k: DecisionKind): com.histmersion.util.data.exchange.DecisionKind.WireOfResult = k match
    case KeepLeft       => _root_.com.histmersion.util.data.exchange.DecisionKind.WireOfResult("keep-left")
    case KeepRight      => _root_.com.histmersion.util.data.exchange.DecisionKind.WireOfResult("keep-right")
    case Both           => _root_.com.histmersion.util.data.exchange.DecisionKind.WireOfResult("both")
    case Concat         => _root_.com.histmersion.util.data.exchange.DecisionKind.WireOfResult("concat")
    case Hull           => _root_.com.histmersion.util.data.exchange.DecisionKind.WireOfResult("hull")
    case Map            => _root_.com.histmersion.util.data.exchange.DecisionKind.WireOfResult("map")
    case RenameRight    => _root_.com.histmersion.util.data.exchange.DecisionKind.WireOfResult("rename-right")
    case RenameLeft     => _root_.com.histmersion.util.data.exchange.DecisionKind.WireOfResult("rename-left")
    case File           => _root_.com.histmersion.util.data.exchange.DecisionKind.WireOfResult("file")
    case Merge          => _root_.com.histmersion.util.data.exchange.DecisionKind.WireOfResult("merge")
    case Drop           => _root_.com.histmersion.util.data.exchange.DecisionKind.WireOfResult("drop")
    case KeepUnanchored => _root_.com.histmersion.util.data.exchange.DecisionKind.WireOfResult("keep-unanchored")

  given WireEnumValues[DecisionKind]:
    def all: Array[DecisionKind] = DecisionKind.values
    def wire(e: DecisionKind): com.histmersion.util.data.exchange.DecisionKind.WireResult = wireOf(e)
    def fromWire(s: com.histmersion.util.data.exchange.DecisionKind.FromWireS): Option[DecisionKind] = DecisionKind.values.find(k => wireOf(k) == _root_.com.histmersion.util.data.exchange.DecisionKind.WireOfResult((s).value.trim))
