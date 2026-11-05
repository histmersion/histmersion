package com.histmersion.cli

import scala.jdk.CollectionConverters.*

final case class UpdateEntry(calibreId: UpdateEntry.CalibreId, citekeys: List[UpdateEntry.Citekeys], outcome: UpdateOutcome)

object UpdateEntry:
  type CalibreId = CalibreId.T
  object CalibreId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Citekeys = Citekeys.T
  object Citekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
