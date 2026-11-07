package com.histmersion.plugins.calibre

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
    * not the printing on the shelf (the user's decision of 2026-10-03).
    */
  def fixable: Boolean = (!issues.contains(AuditIssue.Missing))

object AuditFinding:

  type CalibreId = CalibreId.T
  object CalibreId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Citekeys = Citekeys.T
  object Citekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
