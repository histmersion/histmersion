package com.histmersion.cli

/** One Calibre entry that disagrees with the corpus, judged against the record it matches best (an entry shared by a
  * chapter and its volume is fine when it matches either).
  */
final case class AuditFinding(
    calibreId: Int,
    citekeys: List[String],
    issues: List[AuditIssue],
    calibre: Option[Calibre.Book],
    record: AuditRecord
):
  /** What `calibre update` fixes: everything but a missing entry. The year included — Calibre shows the record's year,
    * not the printing on the shelf (the user's decision of 2026-10-03, #831).
    */
  def fixable: Boolean = !issues.contains(AuditIssue.Missing)
