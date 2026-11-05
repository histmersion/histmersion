package com.histmersion.api.exchange

import com.histmersion.api.{Ascii, WireEnumValues}

enum EditionsPolicy:
  /** Merge them (the year issue then takes the earliest year). */
  case Merge

  /** Keep them as two sources. */
  case Separate

object EditionsPolicy:
  given WireEnumValues[EditionsPolicy]:
    def all: Array[EditionsPolicy] = EditionsPolicy.values
    def wire(e: EditionsPolicy): String = Ascii.lower(e.toString)
    def fromWire(s: String): Option[EditionsPolicy] = EditionsPolicy.values.find(_.toString.equalsIgnoreCase(s.trim))
