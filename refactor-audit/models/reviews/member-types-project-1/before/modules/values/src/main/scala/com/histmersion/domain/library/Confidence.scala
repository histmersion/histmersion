package com.histmersion.domain.library

import com.histmersion.{Ascii, WireEnumValues}
enum Confidence:
  case High, Medium, Low, Contested

  /** The wire / storage spelling (`high`). */
  def wire: String = Ascii.lower(toString)

object Confidence:
  def fromString(s: String): Option[Confidence] =
    values.find(_.toString.equalsIgnoreCase(s))

  given WireEnumValues[Confidence]:
    def all: Array[Confidence] = Confidence.values
    def wire(e: Confidence): String = e.toString.toLowerCase
    def fromWire(s: String): Option[Confidence] =
      Confidence.values.find(_.toString.equalsIgnoreCase(s))
