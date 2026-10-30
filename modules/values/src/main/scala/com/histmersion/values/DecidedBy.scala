package com.histmersion.values

/** Who decided an issue. */
enum DecidedBy:
  /** A resolution (the file or `--resolve`). */
  case Resolution

  /** A policy (`--editions`). */
  case Policy

  /** The issue's default, nobody overrode it. */
  case Default

  /** The decision an earlier `merge apply` carried out for this very issue (the same records, unchanged): remembered,
    * as git's rerere does, so a decision is never asked twice.
    */
  case Remembered

object DecidedBy:
  given WireEnumValues[DecidedBy]:
    def all: Array[DecidedBy] = DecidedBy.values
    def wire(e: DecidedBy): String = Ascii.lower(e.toString)
    def fromWire(s: String): Option[DecidedBy] = DecidedBy.values.find(_.toString.equalsIgnoreCase(s.trim))
