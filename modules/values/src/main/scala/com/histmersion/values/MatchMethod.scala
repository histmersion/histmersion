package com.histmersion.values

/** How a bundle's source was paired with one here. */
enum MatchMethod:
  /** The same uid. */
  case Uid

  /** A redirect (`uid_aliases`) or the ledger of an earlier merge. */
  case Ledger

  /** A strong key: the same DOI, original file (sha256) or ISBN. */
  case StrongKey

  /** The weighted score. */
  case Score

  /** Nothing here is that work. */
  case Unmatched

object MatchMethod:
  given WireEnumValues[MatchMethod]:
    def all: Array[MatchMethod] = MatchMethod.values
    def wire(e: MatchMethod): String = Ascii.lower(e.toString)
    def fromWire(s: String): Option[MatchMethod] = MatchMethod.values.find(_.toString.equalsIgnoreCase(s.trim))
