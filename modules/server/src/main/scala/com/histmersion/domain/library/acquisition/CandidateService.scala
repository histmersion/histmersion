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
  def watches(goal: com.histmersion.domain.library.acquisition.CandidateService.WatchesGoal): List[CandidateWatch]

  /** A watch read: its new watermark (none on a failed read, which keeps the old one), what it found, or why it failed.
    */
  def recordWatch(watch: CandidateWatch, watermark: Option[com.histmersion.domain.library.acquisition.CandidateService.RecordWatchWatermark], error: Option[com.histmersion.domain.library.acquisition.CandidateService.RecordWatchError], found: com.histmersion.domain.library.acquisition.CandidateService.RecordWatchFound): Unit

  /** Store a candidate: a new one (`true`), or one found again. */
  def upsert(candidate: Source.Candidate): com.histmersion.domain.library.acquisition.CandidateService.UpsertResult

  /** The DOIs a goal has candidates for, in any state. */
  def dois(goal: com.histmersion.domain.library.acquisition.CandidateService.DoisGoal): Set[com.histmersion.domain.library.acquisition.CandidateService.DoisResult]

  /** A page of the candidates of one goal (every goal: `None`) in one state, oldest found first. */
  def list(goal: Option[com.histmersion.domain.library.acquisition.CandidateService.ListGoal], state: com.histmersion.domain.library.acquisition.CandidateService.ListState, limit: com.histmersion.domain.library.acquisition.CandidateService.ListLimit, offset: com.histmersion.domain.library.acquisition.CandidateService.ListOffset): List[Source.Candidate]

  /** How many candidates there are in each state, in the states' order. */
  def counts(goal: Option[com.histmersion.domain.library.acquisition.CandidateService.CountsGoal]): List[CandidateCount]

  /** Set the state of these candidates; how many there were. */
  def mark(ids: List[com.histmersion.domain.library.acquisition.CandidateService.MarkIds], state: com.histmersion.domain.library.acquisition.CandidateService.MarkState): com.histmersion.domain.library.acquisition.CandidateService.MarkResult

  /** Set the state of a goal's candidates with these DOIs. */
  def markDois(goal: com.histmersion.domain.library.acquisition.CandidateService.MarkDoisGoal, dois: List[com.histmersion.domain.library.acquisition.CandidateService.MarkDoisDois], state: com.histmersion.domain.library.acquisition.CandidateService.MarkDoisState): com.histmersion.domain.library.acquisition.CandidateService.MarkDoisResult

object CandidateService:
  type WatchesGoal = WatchesGoal.T
  object WatchesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordWatchWatermark = RecordWatchWatermark.T
  object RecordWatchWatermark extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordWatchError = RecordWatchError.T
  object RecordWatchError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordWatchFound = RecordWatchFound.T
  object RecordWatchFound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpsertResult = UpsertResult.T
  object UpsertResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoisResult = DoisResult.T
  object DoisResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DoisGoal = DoisGoal.T
  object DoisGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListGoal = ListGoal.T
  object ListGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListState = ListState.T
  object ListState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListLimit = ListLimit.T
  object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListOffset = ListOffset.T
  object ListOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CountsGoal = CountsGoal.T
  object CountsGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkResult = MarkResult.T
  object MarkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkIds = MarkIds.T
  object MarkIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkState = MarkState.T
  object MarkState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkDoisResult = MarkDoisResult.T
  object MarkDoisResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkDoisGoal = MarkDoisGoal.T
  object MarkDoisGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkDoisDois = MarkDoisDois.T
  object MarkDoisDois extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkDoisState = MarkDoisState.T
  object MarkDoisState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): CandidateService = Implementation(db)

  final private class Implementation(db: Transactor) extends CandidateService:
    def watches(goal: com.histmersion.domain.library.acquisition.CandidateService.Implementation.WatchesGoal): List[CandidateWatch] = db.read(CandidateRepo.watches(goal))
    def recordWatch(watch: CandidateWatch, watermark: Option[com.histmersion.domain.library.acquisition.CandidateService.Implementation.RecordWatchWatermark], error: Option[com.histmersion.domain.library.acquisition.CandidateService.Implementation.RecordWatchError], found: com.histmersion.domain.library.acquisition.CandidateService.Implementation.RecordWatchFound): Unit =
      db.transact(CandidateRepo.recordWatch(watch, watermark, error, found))
    def upsert(candidate: Source.Candidate): com.histmersion.domain.library.acquisition.CandidateService.Implementation.UpsertResult = db.transact(CandidateRepo.upsert(candidate))
    def dois(goal: com.histmersion.domain.library.acquisition.CandidateService.Implementation.DoisGoal): Set[com.histmersion.domain.library.acquisition.CandidateService.Implementation.DoisResult] = db.read(CandidateRepo.dois(goal))
    def list(goal: Option[com.histmersion.domain.library.acquisition.CandidateService.Implementation.ListGoal], state: com.histmersion.domain.library.acquisition.CandidateService.Implementation.ListState, limit: com.histmersion.domain.library.acquisition.CandidateService.Implementation.ListLimit, offset: com.histmersion.domain.library.acquisition.CandidateService.Implementation.ListOffset): List[Source.Candidate] =
      db.read(CandidateRepo.list(goal, state, limit, offset))
    def counts(goal: Option[com.histmersion.domain.library.acquisition.CandidateService.Implementation.CountsGoal]): List[CandidateCount] = db.read(CandidateRepo.counts(goal))
    def mark(ids: List[com.histmersion.domain.library.acquisition.CandidateService.Implementation.MarkIds], state: com.histmersion.domain.library.acquisition.CandidateService.Implementation.MarkState): com.histmersion.domain.library.acquisition.CandidateService.Implementation.MarkResult =
      if ids.isEmpty then 0 else db.transact(CandidateRepo.mark(ids, state))
    def markDois(goal: com.histmersion.domain.library.acquisition.CandidateService.Implementation.MarkDoisGoal, dois: List[com.histmersion.domain.library.acquisition.CandidateService.Implementation.MarkDoisDois], state: com.histmersion.domain.library.acquisition.CandidateService.Implementation.MarkDoisState): com.histmersion.domain.library.acquisition.CandidateService.Implementation.MarkDoisResult =
      if dois.isEmpty then 0 else db.transact(CandidateRepo.markDois(goal, dois, state))

  object Implementation:
    type WatchesGoal = WatchesGoal.T
    object WatchesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordWatchWatermark = RecordWatchWatermark.T
    object RecordWatchWatermark extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordWatchError = RecordWatchError.T
    object RecordWatchError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordWatchFound = RecordWatchFound.T
    object RecordWatchFound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpsertResult = UpsertResult.T
    object UpsertResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DoisResult = DoisResult.T
    object DoisResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DoisGoal = DoisGoal.T
    object DoisGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListGoal = ListGoal.T
    object ListGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListState = ListState.T
    object ListState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListLimit = ListLimit.T
    object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListOffset = ListOffset.T
    object ListOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountsGoal = CountsGoal.T
    object CountsGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkResult = MarkResult.T
    object MarkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkIds = MarkIds.T
    object MarkIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkState = MarkState.T
    object MarkState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkDoisResult = MarkDoisResult.T
    object MarkDoisResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkDoisGoal = MarkDoisGoal.T
    object MarkDoisGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkDoisDois = MarkDoisDois.T
    object MarkDoisDois extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkDoisState = MarkDoisState.T
    object MarkDoisState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The candidates and watches in memory; `clock` stands in for the database's `now()`, ids count from 1. */
  final class InMemory(state: AtomicReference[AcquisitionState], clock: Clock) extends CandidateService:
    private val Stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
    private def now: com.histmersion.domain.library.acquisition.CandidateService.InMemory.NowResult = Stamp.format(clock.instant())
    private val Order = List(Candidates.State.New, Candidates.State.Seen, Candidates.State.Queued).map(_.wire)

    def watches(goal: com.histmersion.domain.library.acquisition.CandidateService.InMemory.WatchesGoal): List[CandidateWatch] =
      state.get.watches.values.filter(_.goal == goal).toList.sortBy(w => (w.kind, w.subject, w.index))

    def recordWatch(watch: CandidateWatch, watermark: Option[com.histmersion.domain.library.acquisition.CandidateService.InMemory.RecordWatchWatermark], error: Option[com.histmersion.domain.library.acquisition.CandidateService.InMemory.RecordWatchError], found: com.histmersion.domain.library.acquisition.CandidateService.InMemory.RecordWatchFound): Unit =
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

    def upsert(candidate: Source.Candidate): com.histmersion.domain.library.acquisition.CandidateService.InMemory.UpsertResult =
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

    def dois(goal: com.histmersion.domain.library.acquisition.CandidateService.InMemory.DoisGoal): Set[com.histmersion.domain.library.acquisition.CandidateService.InMemory.DoisResult] = state.get.candidates.filter(_.goal == goal).map(_.doi.value.toString).toSet

    def list(goal: Option[com.histmersion.domain.library.acquisition.CandidateService.InMemory.ListGoal], st: com.histmersion.domain.library.acquisition.CandidateService.InMemory.ListSt, limit: com.histmersion.domain.library.acquisition.CandidateService.InMemory.ListLimit, offset: com.histmersion.domain.library.acquisition.CandidateService.InMemory.ListOffset): List[Source.Candidate] =
      state.get.candidates
        .filter(c => goal.forall(_ == c.goal) && c.state == st)
        .sortBy(_.id)
        .slice(offset, offset + limit)

    def counts(goal: Option[com.histmersion.domain.library.acquisition.CandidateService.InMemory.CountsGoal]): List[CandidateCount] =
      state.get.candidates
        .filter(c => goal.forall(_ == c.goal))
        .groupBy(_.state)
        .toList
        .sortBy((s, _) => Order.indexOf(s) match { case -1 => Order.size; case i => i })
        .map((s, cs) => CandidateCount(s, cs.size))

    private def marked(pick: Source.Candidate => com.histmersion.domain.library.acquisition.CandidateService.InMemory.MarkedPickResult, to: com.histmersion.domain.library.acquisition.CandidateService.InMemory.MarkedTo): com.histmersion.domain.library.acquisition.CandidateService.InMemory.MarkedResult =
      var n = 0
      val _ = state.updateAndGet { st =>
        n = st.candidates.count(pick)
        st.modify(_.candidates).using(_.map(c => if pick(c) then c.modify(_.state).setTo(to) else c))
      }
      n

    def mark(ids: List[com.histmersion.domain.library.acquisition.CandidateService.InMemory.MarkIds], st: com.histmersion.domain.library.acquisition.CandidateService.InMemory.MarkSt): com.histmersion.domain.library.acquisition.CandidateService.InMemory.MarkResult = if ids.isEmpty then 0 else marked(c => ids.contains(c.id), st)

    def markDois(goal: com.histmersion.domain.library.acquisition.CandidateService.InMemory.MarkDoisGoal, dois: List[com.histmersion.domain.library.acquisition.CandidateService.InMemory.MarkDoisDois], st: com.histmersion.domain.library.acquisition.CandidateService.InMemory.MarkDoisSt): com.histmersion.domain.library.acquisition.CandidateService.InMemory.MarkDoisResult =
      if dois.isEmpty then 0 else marked(c => c.goal == goal && dois.contains(c.doi), st)

  object InMemory:
    type NowResult = NowResult.T
    object NowResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WatchesGoal = WatchesGoal.T
    object WatchesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordWatchWatermark = RecordWatchWatermark.T
    object RecordWatchWatermark extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordWatchError = RecordWatchError.T
    object RecordWatchError extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordWatchFound = RecordWatchFound.T
    object RecordWatchFound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpsertResult = UpsertResult.T
    object UpsertResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DoisResult = DoisResult.T
    object DoisResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DoisGoal = DoisGoal.T
    object DoisGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListGoal = ListGoal.T
    object ListGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListSt = ListSt.T
    object ListSt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListLimit = ListLimit.T
    object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListOffset = ListOffset.T
    object ListOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CountsGoal = CountsGoal.T
    object CountsGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkedResult = MarkedResult.T
    object MarkedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkedPickResult = MarkedPickResult.T
    object MarkedPickResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkedTo = MarkedTo.T
    object MarkedTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkResult = MarkResult.T
    object MarkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkIds = MarkIds.T
    object MarkIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkSt = MarkSt.T
    object MarkSt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkDoisResult = MarkDoisResult.T
    object MarkDoisResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkDoisGoal = MarkDoisGoal.T
    object MarkDoisGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkDoisDois = MarkDoisDois.T
    object MarkDoisDois extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkDoisSt = MarkDoisSt.T
    object MarkDoisSt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
