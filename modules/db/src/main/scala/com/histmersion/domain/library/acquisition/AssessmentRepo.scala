package com.histmersion.domain.library.acquisition

import com.histmersion.api.*

import com.histmersion.db.given
import com.augustnagro.magnum.*

/** The assessments' store: the local Retraction Watch mirror and the verdict recorded for each stored source. Every
  * method runs on the connection it is given.
  */
private[acquisition] object AssessmentRepo:

  final private case class RwRow(nature: Option[String], reason: Option[String], date: Option[String]) derives DbCodec
  final private case class AssessmentRow(
      citekey: String,
      checkedAt: String,
      retracted: Boolean,
      citedBy: Option[Int],
      influential: Option[Int],
      verdict: String
  ) derives DbCodec

  /** Replace the whole mirror: the CSV is a full export, so a partial refresh would leave a mixture of two vintages.
    * Batched — 60 000 single inserts take minutes.
    */
  def replaceRetractionWatch(rows: List[Acquire.RetractionRow])(using DbCon): Int =
    sql"DELETE FROM retraction_watch".update.run()
    // the export can list a DOI twice (a retraction and a later reinstatement); the last wins
    val unique = rows.map(r => r.doi -> r).toMap.values.toVector
    if unique.isEmpty then 0
    else
      val _ = batchUpdate(unique)(r => sql"""INSERT INTO retraction_watch (doi, reason, date, nature)
              VALUES (${r.doi}, ${r.reason}, ${Acquire.rwDate(r.date)}::date, ${r.nature})
              ON CONFLICT (doi) DO UPDATE SET
                reason = EXCLUDED.reason, date = EXCLUDED.date, nature = EXCLUDED.nature""".update)
      unique.size

  def retractionCount()(using DbCon): Int =
    sql"SELECT count(*)::int FROM retraction_watch".query[Int].run().headOption.getOrElse(0)

  /** Every Retraction Watch notice recorded against this DOI (usually zero or one). */
  def retractionsFor(doi: String)(using DbCon): List[RetractionNotice] =
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
  def assessmentFor(citekey: String)(using DbCon): Option[(String, String)] =
    sql"""SELECT citekey, to_char(checked_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
                 retracted, cited_by, influential, verdict
          FROM assessments WHERE citekey = $citekey"""
      .query[AssessmentRow]
      .run()
      .headOption
      .map(r => (r.verdict, r.checkedAt))
