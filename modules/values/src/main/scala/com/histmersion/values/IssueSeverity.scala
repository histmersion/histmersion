package com.histmersion.values

/** How much an issue holds the merge up. */

import com.histmersion.{Ascii, WireEnumValues}
enum IssueSeverity:
  /** Nothing is merged until it has a decision. */
  case Blocking

  /** Has a default that is applied unless overridden. */
  case Default

  /** Reported only. */
  case Info

object IssueSeverity:
  given WireEnumValues[IssueSeverity]:
    def all: Array[IssueSeverity] = IssueSeverity.values
    def wire(e: IssueSeverity): String = Ascii.lower(e.toString)
    def fromWire(s: String): Option[IssueSeverity] = IssueSeverity.values.find(_.toString.equalsIgnoreCase(s.trim))
