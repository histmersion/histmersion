package com.histmersion.domain.library.acquisition

import com.histmersion.api.library.{Acquire, RetractionNotice}
import com.histmersion.util.database.Transactor

import hearth.kindlings.optics.syntax.*

import java.time.{Clock, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

/** What an assessment rests on and what it leaves: the local Retraction Watch mirror, and the verdict recorded against
  * a stored source.
  */
trait AssessmentService:
  /** Replace the whole mirror at once; how many rows it holds now. */
  def replaceRetractionWatch(rows: List[Acquire.RetractionRow]): Int
  def retractionCount: Int

  /** Every notice the mirror records against this DOI. */
  def retractionsFor(doi: String): List[RetractionNotice]

  /** Record a verdict; its `checked_at` stamp. */
  def record(assessment: AssessmentRecord): String

  /** `(verdict, checkedAt)` of the source's last assessment. */
  def lastFor(citekey: String): Option[(String, String)]

object AssessmentService:
  def apply(db: Transactor): AssessmentService = Implementation(db)

  final private class Implementation(db: Transactor) extends AssessmentService:
    def replaceRetractionWatch(rows: List[Acquire.RetractionRow]): Int =
      db.transact(AssessmentRepo.replaceRetractionWatch(rows))
    def retractionCount: Int = db.read(AssessmentRepo.retractionCount())
    def retractionsFor(doi: String): List[RetractionNotice] = db.read(AssessmentRepo.retractionsFor(doi))
    def record(assessment: AssessmentRecord): String = db.transact(AssessmentRepo.putAssessment(assessment))
    def lastFor(citekey: String): Option[(String, String)] = db.read(AssessmentRepo.assessmentFor(citekey))

  /** The mirror and the verdicts in memory; `clock` stands in for the database's `now()`. */
  final class InMemory(state: AtomicReference[AcquisitionState], clock: Clock) extends AssessmentService:
    private val Stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    def replaceRetractionWatch(rows: List[Acquire.RetractionRow]): Int =
      val unique = rows.map(r => r.doi -> r).toMap
      val _ = state.updateAndGet(_.modify(_.retractions).setTo(unique))
      unique.size
    def retractionCount: Int = state.get.retractions.size
    def retractionsFor(doi: String): List[RetractionNotice] =
      state.get.retractions
        .get(doi.toLowerCase)
        .toList
        .map(r => RetractionNotice(r.nature, r.reason, Acquire.rwDate(r.date)))
    def record(assessment: AssessmentRecord): String =
      val at = Stamp.format(clock.instant())
      val _ = state.updateAndGet(_.modify(_.assessments).using(_.updated(assessment.citekey, (assessment, at))))
      at
    def lastFor(citekey: String): Option[(String, String)] =
      state.get.assessments.get(citekey).map((a, at) => (a.verdict, at))
