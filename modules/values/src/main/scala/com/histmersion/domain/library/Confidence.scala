package com.histmersion.domain.library

import com.histmersion.{Ascii, WireEnumValues}
enum Confidence:
  case High, Medium, Low, Contested

  /** The wire / storage spelling (`high`). */
  def wire: com.histmersion.domain.library.Confidence.WireResult = Ascii.lower(_root_.com.histmersion.Ascii.LowerS(toString))

object Confidence:
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromStringS = FromStringS.T
  object FromStringS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def fromString(s: com.histmersion.domain.library.Confidence.FromStringS): Option[Confidence] =
    values.find(_.toString.equalsIgnoreCase((s).value))

  given WireEnumValues[Confidence]:
    def all: Array[Confidence] = Confidence.values
    def wire(e: Confidence): _root_.com.histmersion.WireEnumValues.WireResult = _root_.com.histmersion.WireEnumValues.WireResult((_root_.com.histmersion.domain.library.Confidence.WireResult(e.toString.toLowerCase)).value)
    def fromWire(s: _root_.com.histmersion.WireEnumValues.FromWireS): _root_.scala.Option[Confidence] =
      Confidence.values.find(_.toString.equalsIgnoreCase((s).value))
