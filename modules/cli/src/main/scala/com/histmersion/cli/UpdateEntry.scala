package com.histmersion.cli

import scala.jdk.CollectionConverters.*

final case class UpdateEntry(calibreId: UpdateEntry.CalibreId, citekeys: List[UpdateEntry.Citekeys], outcome: UpdateOutcome)

object UpdateEntry:
  type CalibreId = com.histmersion.cli.Calibre.Book.Id
  val CalibreId: _root_.com.histmersion.cli.Calibre.Book.Id.type = _root_.com.histmersion.cli.Calibre.Book.Id
  type Citekeys = Citekeys.T
  object Citekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
