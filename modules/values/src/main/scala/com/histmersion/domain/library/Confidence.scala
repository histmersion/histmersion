package com.histmersion.domain.library

import com.histmersion.{Ascii, WireEnumValues}
enum Confidence:
  case High, Medium, Low, Contested

  /** The wire / storage spelling (`high`). */
  def wire: com.histmersion.domain.library.Confidence.Wire = Ascii.lower((toString))

object Confidence:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def fromString(s: com.histmersion.domain.library.Confidence.Wire): Option[Confidence] =
    values.find(_.toString.equalsIgnoreCase((s).value))

  given WireEnumValues[Confidence]:
    def all: Array[Confidence] = Confidence.values
    def wire(e: Confidence): _root_.com.histmersion.WireEnumValues.Wire = _root_.com.histmersion.WireEnumValues.Wire((_root_.com.histmersion.domain.library.Confidence.Wire(e.toString.toLowerCase)).value)
    def fromWire(s: _root_.com.histmersion.WireEnumValues.Wire): _root_.scala.Option[Confidence] =
      Confidence.values.find(_.toString.equalsIgnoreCase((s).value))
