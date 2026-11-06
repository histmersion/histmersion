package com.histmersion.api.library

import com.histmersion.api.{Ascii, WireEnumValues}

enum Confidence:
  case High, Medium, Low, Contested

  /** The wire / storage spelling (`high`). */
  def wire: com.histmersion.api.library.Confidence.WireResult = Ascii.lower(_root_.com.histmersion.api.Ascii.LowerS(toString))

object Confidence:
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromStringS = FromStringS.T
  object FromStringS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def fromString(s: com.histmersion.api.library.Confidence.FromStringS): Option[Confidence] =
    values.find(_.toString.equalsIgnoreCase((s).value))

  given WireEnumValues[Confidence]:
    def all: Array[Confidence] = Confidence.values
    def wire(e: Confidence): com.histmersion.api.library.Confidence.WireResult = _root_.com.histmersion.api.library.Confidence.WireResult(e.toString.toLowerCase)
    def fromWire(s: com.histmersion.api.library.Confidence.FromWireS): Option[Confidence] =
      Confidence.values.find(_.toString.equalsIgnoreCase((s).value))
