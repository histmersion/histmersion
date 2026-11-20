package com.histmersion
package domain.library.acquisition
import com.histmersion.util.problem.Problem
import io.scalaland.chimney.dsl.*

import com.histmersion.util.database.given
import com.histmersion.util.database.NewtypeColumns.given
import com.augustnagro.magnum.*
import com.histmersion.api.library.{Acquire, RetractionNotice}
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.domain.library.Doi
import com.histmersion.util.database.Unused

/** The assessments' store: the local Retraction Watch mirror and the verdict recorded for each stored source. Every
  * method runs on the connection it is given.
  */
private[acquisition] object AssessmentRepo:

  final private case class RwRow(
      nature: Option[RetractionNotice.Nature],
      reason: Option[RetractionNotice.Account],
      date: Option[RetractionNotice.Date]
  ) derives DbCodec

  final private case class AssessmentRow(
      citekey: Citekey,
      checkedAt: AssessmentRow.CheckedAt,
      retracted: AssessmentRow.Retracted,
      citedBy: Option[AssessmentRow.CitedBy],
      influential: Option[AssessmentRow.Influential],
      verdict: AssessmentRow.Verdict
  ) derives DbCodec

  object AssessmentRow:
    type CheckedAt = CheckedAt.T
    object CheckedAt extends Newtype[String, Pure]
    type Retracted = Retracted.T
    object Retracted extends Newtype[Boolean, Pure]
    type CitedBy = CitedBy.T
    object CitedBy extends Newtype[Int, Pure]
    type Influential = Influential.T
    object Influential extends Newtype[Int, Pure]
    type Verdict = Verdict.T
    object Verdict extends Newtype[String, Pure]

  /** Replace the whole mirror: the CSV is a full export, so a partial refresh would leave a mixture of two vintages.
    * Batched — 60 000 single inserts take minutes.
    */
  def replaceRetractionWatch(rows: List[Acquire.RetractionRow])(using DbCon): RowCount =
    Unused(sql"DELETE FROM retraction_watch".update.run(), Unused.Why.Idempotent)
    // the export can list a DOI twice (a retraction and a later reinstatement); the last wins
    val unique = rows.map(r => r.doi -> r).toMap.values.toVector
    if unique.isEmpty then RowCount(0)
    else
      val _ = batchUpdate(unique)(r => sql"""INSERT INTO retraction_watch (doi, reason, date, nature)
              VALUES (${r.doi}, ${r.reason}, ${Acquire.rwDate(r.date)}::date, ${r.nature})
              ON CONFLICT (doi) DO UPDATE SET
                reason = EXCLUDED.reason, date = EXCLUDED.date, nature = EXCLUDED.nature""".update)
      RowCount(unique.size)

  def retractionCount()(using DbCon): RowCount =
    RowCount(sql"SELECT count(*)::int FROM retraction_watch".query[Int].run().headOption.getOrElse(0))

  /** Every Retraction Watch notice recorded against this DOI (usually zero or one). */
  def retractionsFor(doi: Doi)(using DbCon): List[RetractionNotice] =
    sql"""SELECT nature, reason, to_char(date, 'YYYY-MM-DD')
          FROM retraction_watch WHERE doi = ${doi.value.toLowerCase}"""
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
  def assessmentFor(citekey: Citekey)(using DbCon): Option[(AssessmentRow.Verdict, AssessmentRow.CheckedAt)] =
    sql"""SELECT citekey, to_char(checked_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                 retracted, cited_by, influential, verdict
          FROM assessments WHERE citekey = $citekey"""
      .query[AssessmentRow]
      .run()
      .headOption
      .map(r => (r.verdict, r.checkedAt))
