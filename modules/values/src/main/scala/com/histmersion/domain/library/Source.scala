package com.histmersion.domain.library

import com.histmersion.{Ascii, WireEnumValues}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

object Source:

  enum Status:
    case Foundational, Current, Contested, Superseded, Debunked, Retracted, Unassessed

    /** The wire / storage spelling (`current`), as the JSON codec writes it. */
    def wire: String = Ascii.lower(toString)

  object Status:

    def fromString(s: String): Status =
      values.find(_.toString.equalsIgnoreCase(s)).getOrElse(Unassessed)

    given WireEnumValues[Status]:
      def all: Array[Status] = Status.values
      def wire(e: Status): String = e.toString.toLowerCase
      def fromWire(s: String): Option[Status] =
        Status.values.find(_.toString.equalsIgnoreCase(s))

  /** A source's stable identity across renames, merges and databases (`sources.uid`). */
  type Uid = Uid.T

  object Uid extends RefinedType[java.util.UUID, Pure]
