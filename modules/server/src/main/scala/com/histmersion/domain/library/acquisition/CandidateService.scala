package com.histmersion.domain.library.acquisition

import com.histmersion.api.library.{CandidateCount, Candidates, CandidateWatch}
import com.histmersion.util.database.Transactor

import hearth.kindlings.optics.syntax.*

import java.time.{Clock, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.library.Source

/** The new-source search's records: each goal's watches with their watermarks, and the candidates found, one per goal
  * and DOI.
  */
trait CandidateService:
  /** A goal's watches as they were last read, by kind, subject and index. */
  def watches(goal: com.histmersion.domain.automation.Goal.Slug): List[CandidateWatch]

  /** A watch read: its new watermark (none on a failed read, which keeps the old one), what it found, or why it failed.
    */
  def recordWatch(watch: CandidateWatch, watermark: Option[com.histmersion.domain.library.acquisition.CandidateService.RecordWatchWatermark], error: Option[com.histmersion.domain.library.acquisition.CandidateService.Problem], found: com.histmersion.RowCount): Unit

  /** Store a candidate: a new one (`true`), or one found again. */
  def upsert(candidate: Source.Candidate): Boolean

  /** The DOIs a goal has candidates for, in any state. */
  def dois(goal: com.histmersion.domain.automation.Goal.Slug): Set[com.histmersion.domain.library.Doi]

  /** A page of the candidates of one goal (every goal: `None`) in one state, oldest found first. */
  def list(goal: Option[com.histmersion.domain.automation.Goal.Slug], state: com.histmersion.domain.library.acquisition.CandidateService.State, limit: com.histmersion.domain.library.acquisition.CandidateService.ListLimit, offset: com.histmersion.Offset): List[Source.Candidate]

  /** How many candidates there are in each state, in the states' order. */
  def counts(goal: Option[com.histmersion.domain.automation.Goal.Slug]): List[CandidateCount]

  /** Set the state of these candidates; how many there were. */
  def mark(ids: List[com.histmersion.domain.library.acquisition.CandidateService.MarkIds], state: com.histmersion.domain.library.acquisition.CandidateService.State): com.histmersion.RowCount

  /** Set the state of a goal's candidates with these DOIs. */
  def markDois(goal: com.histmersion.domain.automation.Goal.Slug, dois: List[com.histmersion.domain.library.Doi], state: com.histmersion.domain.library.acquisition.CandidateService.State): com.histmersion.RowCount

object CandidateService:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type State = State.T
  object State extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordWatchWatermark = RecordWatchWatermark.T
  object RecordWatchWatermark extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListLimit = ListLimit.T
  object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkIds = MarkIds.T
  object MarkIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): CandidateService = Implementation(db)

  final private class Implementation(db: Transactor) extends CandidateService:
    def watches(goal: com.histmersion.domain.automation.Goal.Slug): List[CandidateWatch] = db.read(CandidateRepo.watches(goal))
    def recordWatch(watch: CandidateWatch, watermark: Option[com.histmersion.domain.library.acquisition.CandidateService.Implementation.RecordWatchWatermark], error: Option[com.histmersion.domain.library.acquisition.CandidateService.Problem], found: com.histmersion.RowCount): Unit =
      db.transact(CandidateRepo.recordWatch(watch, watermark, error, found))
    def upsert(candidate: Source.Candidate): Boolean = db.transact(CandidateRepo.upsert(candidate))
    def dois(goal: com.histmersion.domain.automation.Goal.Slug): Set[com.histmersion.domain.library.Doi] = db.read(CandidateRepo.dois(goal))
    def list(goal: Option[com.histmersion.domain.automation.Goal.Slug], state: com.histmersion.domain.library.acquisition.CandidateService.State, limit: com.histmersion.domain.library.acquisition.CandidateService.Implementation.ListLimit, offset: com.histmersion.Offset): List[Source.Candidate] =
      db.read(CandidateRepo.list(goal, state, limit, offset))
    def counts(goal: Option[com.histmersion.domain.automation.Goal.Slug]): List[CandidateCount] = db.read(CandidateRepo.counts(goal))
    def mark(ids: List[com.histmersion.domain.library.acquisition.CandidateService.Implementation.MarkIds], state: com.histmersion.domain.library.acquisition.CandidateService.State): com.histmersion.RowCount =
      if ids.isEmpty then com.histmersion.RowCount(0) else db.transact(CandidateRepo.mark(ids, state))
    def markDois(goal: com.histmersion.domain.automation.Goal.Slug, dois: List[com.histmersion.domain.library.Doi], state: com.histmersion.domain.library.acquisition.CandidateService.State): com.histmersion.RowCount =
      if dois.isEmpty then com.histmersion.RowCount(0) else db.transact(CandidateRepo.markDois(goal, dois, state))

  object Implementation:
    type RecordWatchWatermark = RecordWatchWatermark.T
    object RecordWatchWatermark extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListLimit = ListLimit.T
    object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkIds = MarkIds.T
    object MarkIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The candidates and watches in memory; `clock` stands in for the database's `now()`, ids count from 1. */
  final class InMemory(state: AtomicReference[AcquisitionState], clock: Clock) extends CandidateService:
    private val Stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
    private def now: String = Stamp.format(clock.instant())
    private val Order = List(Candidates.State.New, Candidates.State.Seen, Candidates.State.Queued).map(_.wire)

    def watches(goal: com.histmersion.domain.automation.Goal.Slug): List[CandidateWatch] =
      state.get.watches.values.filter(_.goal == goal).toList.sortBy(w => (w.kind, w.subject, w.index))

    def recordWatch(watch: CandidateWatch, watermark: Option[com.histmersion.domain.library.acquisition.CandidateService.InMemory.RecordWatchWatermark], error: Option[com.histmersion.domain.library.acquisition.CandidateService.Problem], found: com.histmersion.RowCount): Unit =
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
              Some(at),
              error,
              found
            )
          )
        }
      )

    def upsert(candidate: Source.Candidate): Boolean =
      val at = now
      var created = false
      val _ = state.updateAndGet { st =>
        st.candidates.indexWhere(c => c.goal == candidate.goal && c.doi == candidate.doi) match
          case -1 =>
            created = true
            val id = st.candidates.map(_.id).maxOption.getOrElse(0L) + 1
            st.modify(_.candidates)
              .using(
                _ :+ candidate
                  .modify(_.id)
                  .setTo(id)
                  .modify(_.state)
                  .setTo(Candidates.State.New.wire)
                  .modify(_.firstSeen)
                  .setTo(at)
                  .modify(_.lastSeen)
                  .setTo(at)
              )
          case i =>
            created = false
            val c = st.candidates(i)
            st.modify(_.candidates)
              .using(
                _.updated(
                  i,
                  c.modify(_.lastSeen)
                    .setTo(at)
                    .modify(_.foundBy)
                    .setTo((c.foundBy ++ candidate.foundBy).distinct.sorted)
                    .modify(_.abstractText)
                    .setTo(c.abstractText.orElse(candidate.abstractText))
                    .modify(_.isOa)
                    .setTo(c.isOa || candidate.isOa)
                )
              )
      }
      created

    def dois(goal: com.histmersion.domain.automation.Goal.Slug): Set[com.histmersion.domain.library.Doi] = state.get.candidates.filter(_.goal == goal).map(_.doi.value.toString).toSet

    def list(goal: Option[com.histmersion.domain.automation.Goal.Slug], st: com.histmersion.domain.library.acquisition.CandidateService.State, limit: com.histmersion.domain.library.acquisition.CandidateService.InMemory.ListLimit, offset: com.histmersion.Offset): List[Source.Candidate] =
      state.get.candidates
        .filter(c => goal.forall(_ == c.goal) && c.state == st)
        .sortBy(_.id)
        .slice(offset, offset + limit)

    def counts(goal: Option[com.histmersion.domain.automation.Goal.Slug]): List[CandidateCount] =
      state.get.candidates
        .filter(c => goal.forall(_ == c.goal))
        .groupBy(_.state)
        .toList
        .sortBy((s, _) => Order.indexOf(s) match { case -1 => Order.size; case i => i })
        .map((s, cs) => CandidateCount(s, cs.size))

    private def marked(pick: Source.Candidate => Boolean, to: com.histmersion.domain.library.acquisition.CandidateService.State): com.histmersion.RowCount =
      var n = 0
      val _ = state.updateAndGet { st =>
        n = st.candidates.count(pick)
        st.modify(_.candidates).using(_.map(c => if pick(c) then c.modify(_.state).setTo(to) else c))
      }
      n

    def mark(ids: List[com.histmersion.domain.library.acquisition.CandidateService.InMemory.MarkIds], st: com.histmersion.domain.library.acquisition.CandidateService.State): com.histmersion.RowCount = if ids.isEmpty then com.histmersion.RowCount(0) else marked(c => ids.contains(c.id), st)

    def markDois(goal: com.histmersion.domain.automation.Goal.Slug, dois: List[com.histmersion.domain.library.Doi], st: com.histmersion.domain.library.acquisition.CandidateService.State): com.histmersion.RowCount =
      if dois.isEmpty then com.histmersion.RowCount(0) else marked(c => c.goal == goal && dois.contains(c.doi), st)

  object InMemory:
    type RecordWatchWatermark = RecordWatchWatermark.T
    object RecordWatchWatermark extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListLimit = ListLimit.T
    object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkIds = MarkIds.T
    object MarkIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
