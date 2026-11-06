package com.histmersion.plugins.calibre

/** A corpus source as the audit sees it: the Calibre entry it names and what the corpus says. */
final case class AuditRecord(citekey: AuditRecord.Citekey, calibreId: AuditRecord.CalibreId, title: AuditRecord.Title, authors: List[AuditRecord.Authors], year: Option[AuditRecord.Year])

object AuditRecord:
  type Citekey = Citekey.T
  object Citekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CalibreId = CalibreId.T
  object CalibreId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given _root_.scala.math.Ordering[T] = _root_.scala.math.Ordering.by[T, _root_.scala.Int](_.value)
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Authors = Authors.T
  object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Year = Year.T
  object Year extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
