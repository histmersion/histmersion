package com.histmersion.domain.library.acquisition

import com.histmersion.util.database.given
import com.augustnagro.magnum.*
import com.histmersion.api.library.{Acquire, RetractionNotice}

/** The assessments' store: the local Retraction Watch mirror and the verdict recorded for each stored source. Every
  * method runs on the connection it is given.
  */
private[acquisition] object AssessmentRepo:


  final private case class RwRow(nature: Option[RwRow.Nature], reason: Option[RwRow.Reason], date: Option[RwRow.Date]) derives DbCodec

  object RwRow:
    type Nature = Nature.T
    object Nature extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Date = Date.T
    object Date extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  final private case class AssessmentRow(
      citekey: com.histmersion.domain.library.Citekey,
      checkedAt: AssessmentRow.CheckedAt,
      retracted: AssessmentRow.Retracted,
      citedBy: Option[AssessmentRow.CitedBy],
      influential: Option[AssessmentRow.Influential],
      verdict: AssessmentRow.Verdict
  ) derives DbCodec

  object AssessmentRow:
    type CheckedAt = CheckedAt.T
    object CheckedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Retracted = Retracted.T
    object Retracted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CitedBy = CitedBy.T
    object CitedBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Influential = Influential.T
    object Influential extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Verdict = Verdict.T
    object Verdict extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Replace the whole mirror: the CSV is a full export, so a partial refresh would leave a mixture of two vintages.
    * Batched — 60 000 single inserts take minutes.
    */
  def replaceRetractionWatch(rows: List[Acquire.RetractionRow])(using DbCon): com.histmersion.RowCount =
    sql"DELETE FROM retraction_watch".update.run()
    // the export can list a DOI twice (a retraction and a later reinstatement); the last wins
    val unique = rows.map(r => r.doi -> r).toMap.values.toVector
    if unique.isEmpty then com.histmersion.RowCount(0)
    else
      val _ = batchUpdate(unique)(r => sql"""INSERT INTO retraction_watch (doi, reason, date, nature)
              VALUES (${r.doi}, ${r.reason}, ${Acquire.rwDate(r.date)}::date, ${r.nature})
              ON CONFLICT (doi) DO UPDATE SET
                reason = EXCLUDED.reason, date = EXCLUDED.date, nature = EXCLUDED.nature""".update)
      unique.size

  def retractionCount()(using DbCon): com.histmersion.RowCount =
    sql"SELECT count(*)::int FROM retraction_watch".query[Int].run().headOption.getOrElse(0)

  /** Every Retraction Watch notice recorded against this DOI (usually zero or one). */
  def retractionsFor(doi: com.histmersion.domain.library.Doi)(using DbCon): List[RetractionNotice] =
    sql"""SELECT nature, reason, to_char(date, 'YYYY-MM-DD')
          FROM retraction_watch WHERE doi = ${doi.toLowerCase}"""
      .query[RwRow]
      .run()
      .map(r => RetractionNotice(r.nature, r.reason, r.date))
      .toList

  /** Record the verdict for a stored source; the `checked_at` stamp. */
  def putAssessment(a: AssessmentRecord)(using DbCon): String =
    sql"""INSERT INTO assessments (citekey, checked_at, retracted, notices, cited_by, influential, verdict)
          VALUES (${a.citekey}, now(), ${a.retracted}, ${a.noticesJson}::jsonb, ${a.citedBy}, ${a.influential},
                  ${a.verdict})
          ON CONFLICT (citekey) DO UPDATE SET
            checked_at = now(), retracted = EXCLUDED.retracted, notices = EXCLUDED.notices,
            cited_by = EXCLUDED.cited_by, influential = EXCLUDED.influential,
            verdict = EXCLUDED.verdict""".update.run()
    sql"""SELECT to_char(checked_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
          FROM assessments WHERE citekey = ${a.citekey}""".query[String].run().headOption.getOrElse("")

  /** `(verdict, checkedAt)` of the last assessment, when there is one. */
  def assessmentFor(citekey: com.histmersion.domain.library.Citekey)(using DbCon): Option[(com.histmersion.domain.library.acquisition.AssessmentRepo.AssessmentRow.CheckedAt, com.histmersion.domain.library.acquisition.AssessmentRepo.AssessmentRow.Verdict)] =
    sql"""SELECT citekey, to_char(checked_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                 retracted, cited_by, influential, verdict
          FROM assessments WHERE citekey = $citekey"""
      .query[AssessmentRow]
      .run()
      .headOption
      .map(r => (r.verdict, r.checkedAt))
