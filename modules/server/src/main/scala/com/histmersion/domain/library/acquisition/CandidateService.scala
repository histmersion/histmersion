package com.histmersion
package domain.library.acquisition
import com.histmersion.util.problem.Problem
import io.scalaland.chimney.dsl.*

import com.histmersion.api.library.{CandidateCount, Candidates, CandidateWatch}
import com.histmersion.util.database.Transactor

import hearth.kindlings.optics.syntax.*

import java.time.{Clock, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.library.Source
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.library.Doi
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{LintRule, tolerated}

/** The new-source search's records: each goal's watches with their watermarks, and the candidates found, one per goal
  * and DOI.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait CandidateService:
  /** A goal's watches as they were last read, by kind, subject and index. */
  def watches(goal: Goal.Slug): List[CandidateWatch]

  /** A watch read: its new watermark (none on a failed read, which keeps the old one), what it found, or why it failed.
    */
  def recordWatch(
      watch: CandidateWatch,
      watermark: Option[CandidateWatch.Watermark],
      error: Option[Problem],
      found: CandidateWatch.LastFound
  )(using WritePermit)(using Creator.New): Unit

  /** Store a candidate: a new one (`true`), or one found again. */
  def upsert(candidate: Source.Candidate)(using WritePermit)(using Creator.New): Boolean

  /** The DOIs a goal has candidates for, in any state. */
  def dois(goal: Goal.Slug): Set[Doi]

  /** A page of the candidates of one goal (every goal: `None`) in one state, oldest found first. */
  def list(
      goal: Option[Goal.Slug],
      state: Source.Candidate.State,
      limit: CandidateService.ListLimit,
      offset: Offset
  ): List[Source.Candidate]

  /** How many candidates there are in each state, in the states' order. */
  def counts(goal: Option[Goal.Slug]): List[CandidateCount]

  /** Set the state of these candidates; how many there were. */
  def mark(ids: List[Source.Candidate.Id], state: Source.Candidate.State)(using WritePermit): RowCount

  /** Set the state of a goal's candidates with these DOIs. */
  def markDois(goal: Goal.Slug, dois: List[Doi], state: Source.Candidate.State)(using WritePermit): RowCount

object CandidateService:
  type ListLimit = ListLimit.T
  object ListLimit extends Newtype[Int, Pure]

  def apply(db: Transactor): CandidateService = Implementation(db)

  final private class Implementation(db: Transactor) extends CandidateService:
    def watches(goal: Goal.Slug): List[CandidateWatch] = db.read(CandidateRepo.watches(goal))
    def recordWatch(
        watch: CandidateWatch,
        watermark: Option[CandidateWatch.Watermark],
        error: Option[Problem],
        found: CandidateWatch.LastFound
    )(using WritePermit)(using Creator.New): Unit =
      db.write(CandidateRepo.recordWatch(watch, watermark, error, found))
    def upsert(candidate: Source.Candidate)(using WritePermit)(using Creator.New): Boolean =
      db.write(CandidateRepo.upsert(candidate))
    def dois(goal: Goal.Slug): Set[Doi] = db.read(CandidateRepo.dois(goal))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def list(
        goal: Option[Goal.Slug],
        state: Source.Candidate.State,
        limit: ListLimit,
        offset: Offset
    ): List[Source.Candidate] =
      db.read(CandidateRepo.list(goal, state, CandidateRepo.Limit(limit.value), offset))
    def counts(goal: Option[Goal.Slug]): List[CandidateCount] = db.read(CandidateRepo.counts(goal))
    def mark(ids: List[Source.Candidate.Id], state: Source.Candidate.State)(using WritePermit): RowCount =
      if ids.isEmpty then RowCount(0) else db.write(CandidateRepo.mark(ids, state))
    def markDois(goal: Goal.Slug, dois: List[Doi], state: Source.Candidate.State)(using WritePermit): RowCount =
      if dois.isEmpty then RowCount(0) else db.write(CandidateRepo.markDois(goal, dois, state))

  /** The candidates and watches in memory; `clock` stands in for the database's `now()`, ids count from 1. */
  final class InMemory(state: AtomicReference[AcquisitionState], clock: Clock) extends CandidateService:
    private val Stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
    private def now: String = Stamp.format(clock.instant())
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private val Order = List(Candidates.State.New, Candidates.State.Seen, Candidates.State.Queued).map(s =>
      Source.Candidate.State(s.wire.value)
    )

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def watches(goal: Goal.Slug): List[CandidateWatch] =
      state.get.watches.values
        .filter(_.goal.value == goal.value)
        .toList
        .sortBy(w => (w.kind.value, w.subject.value, w.index.value))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def recordWatch(
        watch: CandidateWatch,
        watermark: Option[CandidateWatch.Watermark],
        error: Option[Problem],
        found: CandidateWatch.LastFound
    )(using WritePermit)(using Creator.New): Unit =
      val key = (watch.goal, watch.kind, watch.subject, watch.index)
      val at = now
      val _ = state.updateAndGet(
        _.modify(_.watches).using { ws =>
          val before = ws.get(key).flatMap(_.watermark)
          ws.updated(
            key,
            CandidateWatch(
              watch.goal,
              watch.kind,
              watch.subject,
              watch.index,
              watermark.orElse(before),
              Some(CandidateWatch.LastRun(at)),
              error,
              found
            )
          )
        }
      )

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def upsert(candidate: Source.Candidate)(using WritePermit)(using Creator.New): Boolean =
      val at = now
      var created = false
      val _ = state.updateAndGet { st =>
        st.candidates.indexWhere(c => c.goal == candidate.goal && c.doi == candidate.doi) match
          case -1 =>
            created = true
            val id = st.candidates.map(_.id.value).maxOption.getOrElse(0L) + 1
            st.modify(_.candidates)
              .using(
                _ :+ candidate
                  .modify(_.id)
                  .setTo(Source.Candidate.Id(id))
                  .modify(_.state)
                  .setTo(Source.Candidate.State(Candidates.State.New.wire.value))
                  .modify(_.firstSeen)
                  .setTo(Source.Candidate.FirstSeen(at))
                  .modify(_.lastSeen)
                  .setTo(Source.Candidate.LastSeen(at))
              )
          case i =>
            created = false
            val c = st.candidates(i)
            st.modify(_.candidates)
              .using(
                _.updated(
                  i,
                  c.modify(_.lastSeen)
                    .setTo(Source.Candidate.LastSeen(at))
                    .modify(_.foundBy)
                    .setTo((c.foundBy ++ candidate.foundBy).distinct.sorted)
                    .modify(_.abstractText)
                    .setTo(c.abstractText.orElse(candidate.abstractText))
                    .modify(_.isOa)
                    .setTo(Source.Candidate.IsOa(c.isOa.value || candidate.isOa.value))
                )
              )
      }
      created

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def dois(goal: Goal.Slug): Set[Doi] =
      state.get.candidates.filter(_.goal.value == goal.value).map(c => Doi.assume(c.doi.value)).toSet

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def list(
        goal: Option[Goal.Slug],
        st: Source.Candidate.State,
        limit: ListLimit,
        offset: Offset
    ): List[Source.Candidate] =
      state.get.candidates
        .filter(c => goal.forall(_.value == c.goal.value) && c.state == st)
        .sortBy[Long](_.id.value)
        .slice(offset.value, offset.value + limit.value)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def counts(goal: Option[Goal.Slug]): List[CandidateCount] =
      state.get.candidates
        .filter(c => goal.forall(_.value == c.goal.value))
        .groupBy(_.state)
        .toList
        .sortBy((s, _) => Order.indexOf(s) match { case -1 => Order.size; case i => i })
        .map((s, cs) => CandidateCount(s, CandidateCount.Count(cs.size)))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def marked(pick: Source.Candidate => Boolean, to: Source.Candidate.State): RowCount =
      var n = 0
      val _ = state.updateAndGet { st =>
        n = st.candidates.count(pick)
        st.modify(_.candidates).using(_.map(c => if pick(c) then c.modify(_.state).setTo(to) else c))
      }
      RowCount(n)

    def mark(ids: List[Source.Candidate.Id], st: Source.Candidate.State)(using WritePermit): RowCount =
      if ids.isEmpty then RowCount(0) else marked(c => ids.contains(c.id), st)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def markDois(goal: Goal.Slug, dois: List[Doi], st: Source.Candidate.State)(using WritePermit): RowCount =
      if dois.isEmpty then RowCount(0)
      else marked(c => c.goal.value == goal.value && dois.exists(_.value == c.doi.value), st)
