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
    case Map | RenameRight | RenameLeft | File => true
    case _                                     => false

object DecisionKind:
  def wireOf(k: DecisionKind): String = k match
    case KeepLeft       => "keep-left"
    case KeepRight      => "keep-right"
    case Both           => "both"
    case Concat         => "concat"
    case Hull           => "hull"
    case Map            => "map"
    case RenameRight    => "rename-right"
    case RenameLeft     => "rename-left"
    case File           => "file"
    case Merge          => "merge"
    case Drop           => "drop"
    case KeepUnanchored => "keep-unanchored"

  given WireEnumValues[DecisionKind]:
    def all: Array[DecisionKind] = DecisionKind.values
    def wire(e: DecisionKind): String = wireOf(e)
    def fromWire(s: String): Option[DecisionKind] = DecisionKind.values.find(k => wireOf(k) == s.trim)
