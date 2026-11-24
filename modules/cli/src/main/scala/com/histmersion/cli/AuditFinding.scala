package com.histmersion
package cli

import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** One Calibre entry that disagrees with the corpus, judged against the record it matches best (an entry shared by a
  * chapter and its volume is fine when it matches either).
  */
final case class AuditFinding(
    calibreId: AuditFinding.CalibreId,
    citekeys: List[AuditFinding.Citekeys],
    issues: List[AuditIssue],
    calibre: Option[Calibre.Book],
    record: AuditRecord
):
  /** What `calibre update` fixes: everything but a missing entry. The year included — Calibre shows the record's year,
    * not the printing on the shelf (the user's decision of 2026-10-03, #831).
    */
  def fixable: Boolean = !issues.contains(AuditIssue.Missing)

object AuditFinding:

  type CalibreId = CalibreId.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object CalibreId extends Newtype[Int, Pure]
  type Citekeys = Citekeys.T
  object Citekeys extends Newtype[String, Pure]
