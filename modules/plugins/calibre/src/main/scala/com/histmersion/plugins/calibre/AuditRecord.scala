package com.histmersion
package plugins.calibre

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** A corpus source as the audit sees it: the Calibre entry it names and what the corpus says. */
final case class AuditRecord(
    citekey: AuditRecord.Citekey,
    calibreId: AuditRecord.CalibreId,
    title: AuditRecord.Title,
    authors: List[AuditRecord.Authors],
    year: Option[AuditRecord.Year]
)

object AuditRecord:
  type Citekey = Citekey.T
  object Citekey extends Newtype[String, Pure]
  type CalibreId = CalibreId.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object CalibreId extends Newtype[Int, Pure]
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Authors = Authors.T
  object Authors extends Newtype[String, Pure]
  type Year = Year.T
  object Year extends Newtype[Int, Pure]
