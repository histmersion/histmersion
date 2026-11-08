package com.histmersion
package cli

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A corpus source as the audit sees it: the Calibre entry it names and what the corpus says. */
final case class AuditRecord(citekey: AuditRecord.Citekey, calibreId: AuditRecord.CalibreId, title: AuditRecord.Title, authors: List[AuditRecord.Authors], year: Option[AuditRecord.Year])

object AuditRecord:
  type Citekey = Citekey.T
  object Citekey extends RefinedType[String, Pure]
  type CalibreId = CalibreId.T
  object CalibreId extends RefinedType[Int, Pure]
  type Title = Title.T
  object Title extends RefinedType[String, Pure]
  type Authors = Authors.T
  object Authors extends RefinedType[String, Pure]
  type Year = Year.T
  object Year extends RefinedType[Int, Pure]
