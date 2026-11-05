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
  def watches(goal: String): List[CandidateWatch]

  /** A watch read: its new watermark (none on a failed read, which keeps the old one), what it found, or why it failed.
    */
  def recordWatch(watch: CandidateWatch, watermark: Option[String], error: Option[String], found: Int): Unit

  /** Store a candidate: a new one (`true`), or one found again. */
  def upsert(candidate: Source.Candidate): Boolean

  /** The DOIs a goal has candidates for, in any state. */
  def dois(goal: String): Set[String]

  /** A page of the candidates of one goal (every goal: `None`) in one state, oldest found first. */
  def list(goal: Option[String], state: String, limit: Int, offset: Int): List[Source.Candidate]

  /** How many candidates there are in each state, in the states' order. */
  def counts(goal: Option[String]): List[CandidateCount]

  /** Set the state of these candidates; how many there were. */
  def mark(ids: List[Long], state: String): Int

  /** Set the state of a goal's candidates with these DOIs. */
  def markDois(goal: String, dois: List[String], state: String): Int

object CandidateService:
  def apply(db: Transactor): CandidateService = Implementation(db)

  final private class Implementation(db: Transactor) extends CandidateService:
    def watches(goal: String): List[CandidateWatch] = db.read(CandidateRepo.watches(goal))
    def recordWatch(watch: CandidateWatch, watermark: Option[String], error: Option[String], found: Int): Unit =
      db.transact(CandidateRepo.recordWatch(watch, watermark, error, found))
    def upsert(candidate: Source.Candidate): Boolean = db.transact(CandidateRepo.upsert(candidate))
    def dois(goal: String): Set[String] = db.read(CandidateRepo.dois(goal))
    def list(goal: Option[String], state: String, limit: Int, offset: Int): List[Source.Candidate] =
      db.read(CandidateRepo.list(goal, state, limit, offset))
    def counts(goal: Option[String]): List[CandidateCount] = db.read(CandidateRepo.counts(goal))
    def mark(ids: List[Long], state: String): Int =
      if ids.isEmpty then 0 else db.transact(CandidateRepo.mark(ids, state))
    def markDois(goal: String, dois: List[String], state: String): Int =
      if dois.isEmpty then 0 else db.transact(CandidateRepo.markDois(goal, dois, state))

  /** The candidates and watches in memory; `clock` stands in for the database's `now()`, ids count from 1. */
  final class InMemory(state: AtomicReference[AcquisitionState], clock: Clock) extends CandidateService:
    private val Stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
    private def now: String = Stamp.format(clock.instant())
    private val Order = List(Candidates.State.New, Candidates.State.Seen, Candidates.State.Queued).map(_.wire)

    def watches(goal: String): List[CandidateWatch] =
      state.get.watches.values.filter(_.goal == goal).toList.sortBy(w => (w.kind, w.subject, w.index))

    def recordWatch(watch: CandidateWatch, watermark: Option[String], error: Option[String], found: Int): Unit =
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

    def dois(goal: String): Set[String] = state.get.candidates.filter(_.goal == goal).map(_.doi.value.toString).toSet

    def list(goal: Option[String], st: String, limit: Int, offset: Int): List[Source.Candidate] =
      state.get.candidates
        .filter(c => goal.forall(_ == c.goal) && c.state == st)
        .sortBy(_.id)
        .slice(offset, offset + limit)

    def counts(goal: Option[String]): List[CandidateCount] =
      state.get.candidates
        .filter(c => goal.forall(_ == c.goal))
        .groupBy(_.state)
        .toList
        .sortBy((s, _) => Order.indexOf(s) match { case -1 => Order.size; case i => i })
        .map((s, cs) => CandidateCount(s, cs.size))

    private def marked(pick: Source.Candidate => Boolean, to: String): Int =
      var n = 0
      val _ = state.updateAndGet { st =>
        n = st.candidates.count(pick)
        st.modify(_.candidates).using(_.map(c => if pick(c) then c.modify(_.state).setTo(to) else c))
      }
      n

    def mark(ids: List[Long], st: String): Int = if ids.isEmpty then 0 else marked(c => ids.contains(c.id), st)

    def markDois(goal: String, dois: List[String], st: String): Int =
      if dois.isEmpty then 0 else marked(c => c.goal == goal && dois.contains(c.doi), st)
