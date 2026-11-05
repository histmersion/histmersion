package com.histmersion.plugins.calibre

/** A source linked to a Calibre entry, with what the corpus would send there. */
final case class LinkedRecord(citekey: LinkedRecord.Citekey, calibreId: LinkedRecord.CalibreId, meta: Calibre.Push)

object LinkedRecord:
  type Citekey = Citekey.T
  object Citekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CalibreId = CalibreId.T
  object CalibreId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
