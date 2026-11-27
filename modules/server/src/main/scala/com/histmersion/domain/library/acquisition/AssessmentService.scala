package com.histmersion
package domain.library.acquisition

import com.histmersion.api.library.{Acquire, RetractionNotice}
import com.histmersion.util.database.Transactor

import hearth.kindlings.optics.syntax.*

import java.time.{Clock, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.domain.library.Doi
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** What an assessment rests on and what it leaves: the local Retraction Watch mirror, and the verdict recorded against
  * a stored source.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait AssessmentService:
  /** Replace the whole mirror at once; how many rows it holds now. */
  def replaceRetractionWatch(rows: List[Acquire.RetractionRow])(using WritePermit): RowCount
  def retractionCount: RowCount

  /** Every notice the mirror records against this DOI. */
  def retractionsFor(doi: Doi): List[RetractionNotice]

  /** Record a verdict; its `checked_at` stamp. */
  def record(assessment: AssessmentRecord)(using WritePermit)(using Creator.New): AssessmentService.CheckedAt

  /** `(verdict, checkedAt)` of the source's last assessment. */
  def lastFor(citekey: Citekey): Option[(AssessmentRecord.Verdict, AssessmentService.CheckedAt)]

object AssessmentService:
  type CheckedAt = CheckedAt.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object CheckedAt extends Newtype[String, Pure]

  def apply(db: Transactor): AssessmentService = Implementation(db)

  final private class Implementation(db: Transactor) extends AssessmentService:
    def replaceRetractionWatch(rows: List[Acquire.RetractionRow])(using WritePermit): RowCount =
      db.write(AssessmentRepo.replaceRetractionWatch(rows))
    def retractionCount: RowCount = db.read(AssessmentRepo.retractionCount())
    def retractionsFor(doi: Doi): List[RetractionNotice] = db.read(AssessmentRepo.retractionsFor(doi))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def record(assessment: AssessmentRecord)(using WritePermit)(using Creator.New): CheckedAt = CheckedAt(
      db.write(AssessmentRepo.putAssessment(assessment))
    )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def lastFor(citekey: Citekey): Option[(AssessmentRecord.Verdict, CheckedAt)] =
      db.read(AssessmentRepo.assessmentFor(citekey))
        .map((v, at) => (AssessmentRecord.Verdict(v.value), CheckedAt(at.value)))

  /** The mirror and the verdicts in memory; `clock` stands in for the database's `now()`. */
  final class InMemory(state: AtomicReference[AcquisitionState], clock: Clock) extends AssessmentService:
    private val Stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def replaceRetractionWatch(rows: List[Acquire.RetractionRow])(using WritePermit): RowCount =
      val unique = rows.map(r => AcquisitionState.RetractionsKey(r.doi.value) -> r).toMap
      val _ = state.updateAndGet(_.modify(_.retractions).setTo(unique))
      RowCount(unique.size)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def retractionCount: RowCount = RowCount(state.get.retractions.size)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def retractionsFor(doi: Doi): List[RetractionNotice] =
      state.get.retractions
        .get(AcquisitionState.RetractionsKey(doi.value.toLowerCase))
        .toList
        .map(r => RetractionNotice(r.nature, r.reason, Acquire.rwDate(r.date)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def record(assessment: AssessmentRecord)(using WritePermit)(using Creator.New): CheckedAt =
      val at = Stamp.format(clock.instant())
      val _ = state.updateAndGet(
        _.modify(_.assessments).using(
          _.updated(
            AcquisitionState.AssessmentsKey(assessment.citekey.value),
            (assessment, CheckedAt(at))
          )
        )
      )
      CheckedAt(at)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def lastFor(citekey: Citekey): Option[(AssessmentRecord.Verdict, CheckedAt)] =
      state.get.assessments
        .get(AcquisitionState.AssessmentsKey(citekey.value))
        .map((a, at) => (a.verdict, CheckedAt(at.value)))
