package com.histmersion.domain.library

import com.histmersion.{Ascii, WireEnumValues}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

object Source:

  enum Status:
    case Foundational, Current, Contested, Superseded, Debunked, Retracted, Unassessed

    /** The wire / storage spelling (`current`), as the JSON codec writes it. */
    def wire: com.histmersion.domain.library.Source.Status.WireResult = Ascii.lower(_root_.com.histmersion.Ascii.LowerS(toString))

  object Status:

    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromStringS = FromStringS.T
    object FromStringS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def fromString(s: com.histmersion.domain.library.Source.Status.FromStringS): Status =
      values.find(_.toString.equalsIgnoreCase(s)).getOrElse(Unassessed)

    given WireEnumValues[Status]:
      def all: Array[Status] = Status.values
      def wire(e: Status): _root_.com.histmersion.WireEnumValues.WireResult = _root_.com.histmersion.WireEnumValues.WireResult((_root_.com.histmersion.domain.library.Source.Status.WireResult(e.toString.toLowerCase)).value)
      def fromWire(s: _root_.com.histmersion.WireEnumValues.FromWireS): _root_.scala.Option[Status] =
        Status.values.find(_.toString.equalsIgnoreCase((s).value))

  /** A source's stable identity across renames, merges and databases (`sources.uid`). */
  type Uid = Uid.T

  object Uid extends RefinedType[java.util.UUID, Pure]
