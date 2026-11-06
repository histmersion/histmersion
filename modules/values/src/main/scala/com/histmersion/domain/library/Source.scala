package com.histmersion.domain.library

import com.histmersion.{Ascii, WireEnumValues}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

object Source:

  enum Status:
    case Foundational, Current, Contested, Superseded, Debunked, Retracted, Unassessed

    /** The wire / storage spelling (`current`), as the JSON codec writes it. */
    def wire: com.histmersion.domain.library.Source.Status.Wire = Ascii.lower((toString))

  object Status:
    type Wire = Wire.T
    object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


    def fromString(s: com.histmersion.domain.library.Source.Status.Wire): Status =
      values.find(_.toString.equalsIgnoreCase((s).value)).getOrElse(Unassessed)

    given WireEnumValues[Status]:
      def all: Array[Status] = Status.values
      def wire(e: Status): _root_.com.histmersion.WireEnumValues.Wire = _root_.com.histmersion.WireEnumValues.Wire((_root_.com.histmersion.domain.library.Source.Status.Wire(e.toString.toLowerCase)).value)
      def fromWire(s: _root_.com.histmersion.WireEnumValues.Wire): _root_.scala.Option[Status] =
        Status.values.find(_.toString.equalsIgnoreCase((s).value))

  /** A source's stable identity across renames, merges and databases (`sources.uid`). */
  type Uid = Uid.T

  object Uid extends RefinedType[java.util.UUID, Pure]
