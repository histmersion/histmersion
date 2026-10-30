package com.histmersion.values

enum SourceStatus:
  case Foundational, Current, Contested, Superseded, Debunked, Retracted, Unassessed

  /** The wire / storage spelling (`current`), as the JSON codec writes it. */
  def wire: String = Ascii.lower(toString)

object SourceStatus:

  def fromString(s: String): SourceStatus =
    values.find(_.toString.equalsIgnoreCase(s)).getOrElse(Unassessed)

  given WireEnumValues[SourceStatus]:
    def all: Array[SourceStatus] = SourceStatus.values
    def wire(e: SourceStatus): String = e.toString.toLowerCase
    def fromWire(s: String): Option[SourceStatus] =
      SourceStatus.values.find(_.toString.equalsIgnoreCase(s))
