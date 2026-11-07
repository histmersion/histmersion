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
  def replaceRetractionWatch(rows: List[Acquire.RetractionRow]): com.histmersion.RowCount
  def retractionCount: com.histmersion.domain.library.acquisition.AssessmentService.RetractionCountResult

  /** Every notice the mirror records against this DOI. */
  def retractionsFor(doi: com.histmersion.domain.library.Doi): List[RetractionNotice]

  /** Record a verdict; its `checked_at` stamp. */
  def record(assessment: AssessmentRecord): com.histmersion.domain.library.acquisition.AssessmentService.RecordResult

  /** `(verdict, checkedAt)` of the source's last assessment. */
  def lastFor(citekey: com.histmersion.domain.library.Citekey): Option[(com.histmersion.domain.library.acquisition.AssessmentService.LastForResultPart1, com.histmersion.domain.library.acquisition.AssessmentService.LastForResultPart2)]

object AssessmentService:
  type RetractionCountResult = RetractionCountResult.T
  object RetractionCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordResult = RecordResult.T
  object RecordResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastForResultPart1 = LastForResultPart1.T
  object LastForResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastForResultPart2 = LastForResultPart2.T
  object LastForResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): AssessmentService = Implementation(db)

  final private class Implementation(db: Transactor) extends AssessmentService:
    def replaceRetractionWatch(rows: List[Acquire.RetractionRow]): com.histmersion.RowCount =
      db.transact(AssessmentRepo.replaceRetractionWatch(rows))
    def retractionCount: com.histmersion.domain.library.acquisition.AssessmentService.Implementation.RetractionCountResult = db.read(AssessmentRepo.retractionCount())
    def retractionsFor(doi: com.histmersion.domain.library.Doi): List[RetractionNotice] = db.read(AssessmentRepo.retractionsFor(doi))
    def record(assessment: AssessmentRecord): com.histmersion.domain.library.acquisition.AssessmentService.Implementation.RecordResult = db.transact(AssessmentRepo.putAssessment(assessment))
    def lastFor(citekey: com.histmersion.domain.library.Citekey): Option[(com.histmersion.domain.library.acquisition.AssessmentService.Implementation.LastForResultPart1, com.histmersion.domain.library.acquisition.AssessmentService.Implementation.LastForResultPart2)] = db.read(AssessmentRepo.assessmentFor(citekey))

  object Implementation:
    type RetractionCountResult = RetractionCountResult.T
    object RetractionCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordResult = RecordResult.T
    object RecordResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastForResultPart1 = LastForResultPart1.T
    object LastForResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastForResultPart2 = LastForResultPart2.T
    object LastForResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The mirror and the verdicts in memory; `clock` stands in for the database's `now()`. */
  final class InMemory(state: AtomicReference[AcquisitionState], clock: Clock) extends AssessmentService:
    private val Stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    def replaceRetractionWatch(rows: List[Acquire.RetractionRow]): com.histmersion.RowCount =
      val unique = rows.map(r => r.doi -> r).toMap
      val _ = state.updateAndGet(_.modify(_.retractions).setTo(unique))
      unique.size
    def retractionCount: com.histmersion.domain.library.acquisition.AssessmentService.InMemory.RetractionCountResult = state.get.retractions.size
    def retractionsFor(doi: com.histmersion.domain.library.Doi): List[RetractionNotice] =
      state.get.retractions
        .get(doi.toLowerCase)
        .toList
        .map(r => RetractionNotice(r.nature, r.reason, Acquire.rwDate(r.date)))
    def record(assessment: AssessmentRecord): com.histmersion.domain.library.acquisition.AssessmentService.InMemory.RecordResult =
      val at = Stamp.format(clock.instant())
      val _ = state.updateAndGet(_.modify(_.assessments).using(_.updated(assessment.citekey, (assessment, at))))
      at
    def lastFor(citekey: com.histmersion.domain.library.Citekey): Option[(com.histmersion.domain.library.acquisition.AssessmentService.InMemory.LastForResultPart1, com.histmersion.domain.library.acquisition.AssessmentService.InMemory.LastForResultPart2)] =
      state.get.assessments.get(citekey).map((a, at) => (a.verdict, at))

  object InMemory:
    type RetractionCountResult = RetractionCountResult.T
    object RetractionCountResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordResult = RecordResult.T
    object RecordResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastForResultPart1 = LastForResultPart1.T
    object LastForResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LastForResultPart2 = LastForResultPart2.T
    object LastForResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
