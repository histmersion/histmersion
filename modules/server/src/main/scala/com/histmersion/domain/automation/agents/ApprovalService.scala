package com.histmersion.domain.automation.agents

import com.histmersion.api.Paging
import com.histmersion.api.automation.{ApprovalDigest, ListFilters}
import com.histmersion.api.automation.Agents.{ApprovalInfo, ApprovalKind, ApprovalState}
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.domain.automation.AutomationState
import com.histmersion.util.database.Transactor
import com.histmersion.util.jobs.JobRuns
import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.domain.library.Edge
import com.histmersion.domain.library.Source

/** The approvals, as the engine's features reach them: what a run proposed for a person to decide — open until decided
  * (once), or recorded approved when it was applied at once — and the reads the lists, the digest, the autopilot and
  * the scheduler make of them.
  */
trait ApprovalService:
  /** A proposal about a source from its parts and its payload; not doubled while an identical one is open. */
  def record(
      jobId: Option[String],
      kind: ApprovalKind,
      citekey: String,
      status: Option[Source.Status],
      current: Option[Source.Status],
      edgeKind: Option[Edge.Kind],
      target: Option[String],
      payload: String,
      state: ApprovalState,
      result: Option[String]
  ): Option[ApprovalInfo]
  def payload(id: Long): Option[String]

  /** Was this change of a source ever proposed (in any state)? */
  def proposedBefore(
      kind: ApprovalKind,
      citekey: String,
      status: Option[Source.Status],
      edgeKind: Option[Edge.Kind],
      target: Option[String]
  ): Boolean
  def approval(id: Long): Option[ApprovalInfo]

  /** Newest first, of one state or every one. */
  def approvals(state: Option[ApprovalState], limit: Int): List[ApprovalInfo]

  /** One newest-first page of one state or every one; `before`/`upTo` are approval ids (Left: one that is none). */
  def approvalPage(
      state: Option[ApprovalState],
      limit: Int,
      before: Option[String] = None,
      upTo: Option[String] = None
  ): Either[String, (List[ApprovalInfo], Boolean)]

  /** One newest-first page of the approvals `q` matches. */
  def approvalPage(
      q: ApprovalQuery,
      limit: Int,
      before: Option[String],
      upTo: Option[String]
  ): Either[String, (List[ApprovalInfo], Boolean)]

  /** Per state (every filter but the state's), and the kind and decided-by facets (each without its own filter). */
  def approvalFacets(q: ApprovalQuery): (List[(String, Int)], List[(String, List[(String, Int)])])

  /** Every approval counted per state. */
  def approvalCounts(): List[(String, Int)]

  /** The approval step `seq` of run `jobId` waits on, opened unless it is there already. */
  def openForStep(
      jobId: String,
      seq: Int,
      kind: ApprovalKind,
      subject: String,
      payload: String,
      state: ApprovalState = ApprovalState.Open,
      result: Option[String] = None
  ): ApprovalInfo

  /** A proposal about a goal or an article that no run waits for. */
  def openSubject(
      jobId: Option[String],
      kind: ApprovalKind,
      subject: String,
      payload: String,
      state: ApprovalState = ApprovalState.Open,
      result: Option[String] = None
  ): ApprovalInfo

  /** What run `jobId` opened (of `kind`), oldest first. */
  def approvalsOf(jobId: String, kind: Option[ApprovalKind] = None): List[ApprovalInfo]

  /** Of `kind`, approved by the autopilot since `since`. */
  def autoApprovedSince(kind: ApprovalKind, since: Instant): List[ApprovalInfo]

  /** The open ones of these kinds, oldest first. */
  def openOfKinds(kinds: List[ApprovalKind], limit: Int): List[ApprovalInfo]

  /** Every open one, light, oldest first. */
  def openLight(): List[ApprovalDigest.Open]
  def openCount(kind: ApprovalKind): Int

  /** Open ones per subject. */
  def openBySubject(): Map[String, Int]

  /** The approval step `seq` of run `jobId` opened. */
  def forStep(jobId: String, seq: Int): Option[ApprovalInfo]

  /** A cancelled run's open step approvals rejected with `why`; how many. */
  def closeForRun(jobId: String, why: String): Int

  /** Compare-and-set an open one to its decision; false when it was decided already. */
  def decide(id: Long, to: ApprovalState, note: Option[String], result: Option[String]): Boolean

  /** After [[decide]]: the final state and what applying it did. */
  def settle(id: Long, to: ApprovalState, result: String): Unit

object ApprovalService:
  def apply(db: Transactor, clock: Clock): ApprovalService = Implementation(db, clock)

  final private class Implementation(db: Transactor, clock: Clock) extends ApprovalService:
    private def now: Instant = Instant.now(clock)
    def record(
        jobId: Option[String],
        kind: ApprovalKind,
        citekey: String,
        status: Option[Source.Status],
        current: Option[Source.Status],
        edgeKind: Option[Edge.Kind],
        target: Option[String],
        payload: String,
        state: ApprovalState,
        result: Option[String]
    ): Option[ApprovalInfo] =
      db.transact(
        AgentRepo.record(jobId, kind, citekey, status, current, edgeKind, target, payload, state, result, now)
      )
    def payload(id: Long): Option[String] = db.read(AgentRepo.payload(id))
    def proposedBefore(
        kind: ApprovalKind,
        citekey: String,
        status: Option[Source.Status],
        edgeKind: Option[Edge.Kind],
        target: Option[String]
    ): Boolean = db.read(AgentRepo.proposedBefore(kind, citekey, status, edgeKind, target))
    def approval(id: Long): Option[ApprovalInfo] = db.read(AgentRepo.approval(id))
    def approvals(state: Option[ApprovalState], limit: Int): List[ApprovalInfo] =
      db.read(AgentRepo.approvals(state, limit))
    def approvalPage(
        state: Option[ApprovalState],
        limit: Int,
        before: Option[String],
        upTo: Option[String]
    ): Either[String, (List[ApprovalInfo], Boolean)] = db.read(AgentRepo.approvalPage(state, limit, before, upTo))
    def approvalPage(
        q: ApprovalQuery,
        limit: Int,
        before: Option[String],
        upTo: Option[String]
    ): Either[String, (List[ApprovalInfo], Boolean)] = db.read(AgentRepo.approvalPage(q, limit, before, upTo))
    def approvalFacets(q: ApprovalQuery): (List[(String, Int)], List[(String, List[(String, Int)])]) =
      db.read(AgentRepo.approvalFacets(q))
    def approvalCounts(): List[(String, Int)] = db.read(AgentRepo.approvalCounts())
    def openForStep(
        jobId: String,
        seq: Int,
        kind: ApprovalKind,
        subject: String,
        payload: String,
        state: ApprovalState,
        result: Option[String]
    ): ApprovalInfo = db.transact(AgentRepo.openForStep(jobId, seq, kind, subject, payload, state, result, now))
    def openSubject(
        jobId: Option[String],
        kind: ApprovalKind,
        subject: String,
        payload: String,
        state: ApprovalState,
        result: Option[String]
    ): ApprovalInfo = db.transact(AgentRepo.openSubject(jobId, kind, subject, payload, state, result, now))
    def approvalsOf(jobId: String, kind: Option[ApprovalKind]): List[ApprovalInfo] =
      db.read(AgentRepo.approvalsOf(jobId, kind))
    def autoApprovedSince(kind: ApprovalKind, since: Instant): List[ApprovalInfo] =
      db.read(AgentRepo.autoApprovedSince(kind, since))
    def openOfKinds(kinds: List[ApprovalKind], limit: Int): List[ApprovalInfo] =
      db.read(AgentRepo.openOfKinds(kinds, limit))
    def openLight(): List[ApprovalDigest.Open] = db.read(AgentRepo.openLight())
    def openCount(kind: ApprovalKind): Int = db.read(AgentRepo.openCount(kind))
    def openBySubject(): Map[String, Int] = db.read(AgentRepo.openBySubject())
    def forStep(jobId: String, seq: Int): Option[ApprovalInfo] = db.read(AgentRepo.forStep(jobId, seq))
    def closeForRun(jobId: String, why: String): Int = db.transact(AgentRepo.closeForRun(jobId, why))
    def decide(id: Long, to: ApprovalState, note: Option[String], result: Option[String]): Boolean =
      db.transact(AgentRepo.decide(id, to, note, result))
    def settle(id: Long, to: ApprovalState, result: String): Unit = db.transact(AgentRepo.settle(id, to, result))

  /** The approvals in memory, with the runs they name (their goal and manager match a list's filters); `clock` stands
    * in for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[AutomationState], runs: JobRuns.InMemory, clock: Clock)
      extends ApprovalService:
    private def now: Instant = Instant.now(clock)
    private def rows: Vector[ApprovalRecord] = state.get.approvals
    private val Open = ApprovalState.Open.wire
    private def info(r: ApprovalRecord): ApprovalInfo =
      AgentRepo.info(
        AgentRepo.ApprovalRow(
          r.id,
          r.jobId,
          r.kind,
          r.citekey,
          r.status,
          r.current,
          r.edgeKind,
          r.target,
          r.proposal,
          r.state,
          r.note,
          r.result,
          AutomationState.text(r.createdAt),
          r.decidedAt.map(AutomationState.text),
          r.subject,
          r.seq
        )
      )
    private def newest(rs: Vector[ApprovalRecord]): Vector[ApprovalRecord] =
      rs.sortBy(r => (r.createdAt, r.id)).reverse

    /** Insert `make(id)` unless `conflicts` holds of a row there; the new row's id. */
    private def insert(conflicts: ApprovalRecord => Boolean)(make: Long => ApprovalRecord): Option[Long] =
      var made: Option[Long] = None
      val _ = state.updateAndGet { s =>
        if s.approvals.exists(conflicts) then { made = None; s }
        else
          val id = s.lastApprovalId + 1
          made = Some(id)
          s.modify(_.approvals).using(_ :+ make(id)).modify(_.lastApprovalId).setTo(id)
      }
      made
    private def decidedAt(st: ApprovalState): Option[Instant] = Option.when(st != ApprovalState.Open)(now)

    def record(
        jobId: Option[String],
        kind: ApprovalKind,
        citekey: String,
        status: Option[Source.Status],
        current: Option[Source.Status],
        edgeKind: Option[Edge.Kind],
        target: Option[String],
        payload: String,
        state: ApprovalState,
        result: Option[String]
    ): Option[ApprovalInfo] =
      val (st, ek) = (status.map(_.wire), edgeKind.map(Edge.Kind.wireOf))
      def same(r: ApprovalRecord) =
        state == ApprovalState.Open && r.state == Open && r.kind == kind.wire && r.citekey.contains(citekey) &&
          r.status.getOrElse("") == st.getOrElse("") && r.edgeKind.getOrElse("") == ek.getOrElse("") &&
          r.target.getOrElse("") == target.getOrElse("")
      val at = now
      insert(same)(id =>
        ApprovalRecord(
          id,
          jobId,
          None,
          kind.wire,
          Some(citekey),
          st,
          current.map(_.wire),
          ek,
          target,
          None,
          payload,
          state.wire,
          None,
          result,
          at,
          decidedAt(state)
        )
      ).flatMap(approval)
    def payload(id: Long): Option[String] = rows.find(_.id == id).map(_.proposal)
    def proposedBefore(
        kind: ApprovalKind,
        citekey: String,
        status: Option[Source.Status],
        edgeKind: Option[Edge.Kind],
        target: Option[String]
    ): Boolean =
      rows.exists(r =>
        r.kind == kind.wire && r.citekey.contains(citekey) && r.status.getOrElse("") == status.fold("")(_.wire) &&
          r.edgeKind.getOrElse("") == edgeKind.fold("")(Edge.Kind.wireOf) && r.target.getOrElse("") == target
            .getOrElse("")
      )
    def approval(id: Long): Option[ApprovalInfo] = rows.find(_.id == id).map(info)
    def approvals(state: Option[ApprovalState], limit: Int): List[ApprovalInfo] =
      newest(rows.filter(r => state.forall(_.wire == r.state))).take(math.max(1, limit)).toList.map(info)
    def approvalPage(
        state: Option[ApprovalState],
        limit: Int,
        before: Option[String],
        upTo: Option[String]
    ): Either[String, (List[ApprovalInfo], Boolean)] = approvalPage(ApprovalQuery(state), limit, before, upTo)

    private def decidedBy(r: ApprovalRecord): Option[String] =
      if r.state == Open then None
      else if r.note.exists(_.startsWith(ListFilters.DecidedBy.AutopilotNote)) then
        Some(ListFilters.DecidedBy.Autopilot.wire)
      else if r.note.isEmpty && r.result.exists(_.startsWith(ListFilters.DecidedBy.AutoResult)) then
        Some(ListFilters.DecidedBy.Auto.wire)
      else Some(ListFilters.DecidedBy.Person.wire)
    private def contains(v: Option[String], t: String): Boolean = v.exists(_.toLowerCase.contains(t.toLowerCase))

    /** Whether `r` passes every filter of `q` but `except`'s. */
    private def matches(q: ApprovalQuery, except: String)(r: ApprovalRecord): Boolean =
      lazy val run = r.jobId.flatMap(runs.get)
      (except == "state" || q.state.forall(_.wire == r.state)) &&
      (except == "kind" || q.kinds.isEmpty || q.kinds.exists(_.wire == r.kind)) &&
      q.subject.forall(t =>
        contains(r.subject, t) || contains(r.citekey, t) || contains(r.target, t) ||
          run.exists(j => contains(j.row.goal, t) || contains(j.row.managerId, t))
      ) &&
      q.goal.forall(g => r.subject.contains(g) || run.exists(_.row.goal.contains(g))) &&
      (except == "decidedBy" || q.decidedBy.isEmpty || decidedBy(r).exists(d => q.decidedBy.exists(_.wire == d)))

    def approvalPage(
        q: ApprovalQuery,
        limit: Int,
        before: Option[String],
        upTo: Option[String]
    ): Either[String, (List[ApprovalInfo], Boolean)] =
      def cursor(c: String): Either[String, ApprovalRecord] =
        c.trim.toLongOption.flatMap(id => rows.find(_.id == id)).toRight(s"No approval '$c' to page from")
      for
        b <- before.fold(Right(None))(cursor(_).map(Some(_)))
        u <- upTo.fold(Right(None))(cursor(_).map(Some(_)))
      yield
        def key(r: ApprovalRecord) = (r.createdAt, r.id)
        val ordering = summon[Ordering[(Instant, Long)]]
        val all = newest(rows.filter(matches(q, "")))
        val bounded =
          all.filter(r => b.forall(c => ordering.lt(key(r), key(c))) && u.forall(c => ordering.gteq(key(r), key(c))))
        val (shown, more) = Paging.cut(bounded.take(math.max(1, limit) + 1), math.max(1, limit))
        val older =
          if more || u.isEmpty then more
          else shown.lastOption.exists(last => all.exists(r => ordering.lt(key(r), key(last))))
        (shown.toList.map(info), older)

    private def facet(values: Vector[String]): List[(String, Int)] =
      values.groupBy(identity).toList.map((v, n) => (v, n.size)).sortBy((v, n) => (-n, v)).take(ListFilters.FacetMax)
    def approvalFacets(q: ApprovalQuery): (List[(String, Int)], List[(String, List[(String, Int)])]) =
      (
        facet(rows.filter(matches(q, "state")).map(_.state)),
        List(
          "kind" -> facet(rows.filter(matches(q, "kind")).map(_.kind)),
          "decidedBy" -> facet(rows.filter(matches(q, "decidedBy")).flatMap(decidedBy))
        )
      )
    def approvalCounts(): List[(String, Int)] = facet(rows.map(_.state))
    def openForStep(
        jobId: String,
        seq: Int,
        kind: ApprovalKind,
        subject: String,
        payload: String,
        state: ApprovalState,
        result: Option[String]
    ): ApprovalInfo =
      val at = now
      val _ = insert(r => r.jobId.contains(jobId) && r.seq.contains(seq))(id =>
        ApprovalRecord(
          id,
          Some(jobId),
          Some(seq),
          kind.wire,
          None,
          None,
          None,
          None,
          None,
          Some(subject),
          payload,
          state.wire,
          None,
          result,
          at,
          decidedAt(state)
        )
      )
      forStep(jobId, seq).get
    def openSubject(
        jobId: Option[String],
        kind: ApprovalKind,
        subject: String,
        payload: String,
        state: ApprovalState,
        result: Option[String]
    ): ApprovalInfo =
      val at = now
      insert(_ => false)(id =>
        ApprovalRecord(
          id,
          jobId,
          None,
          kind.wire,
          None,
          None,
          None,
          None,
          None,
          Some(subject),
          payload,
          state.wire,
          None,
          result,
          at,
          decidedAt(state)
        )
      ).flatMap(approval).get
    def approvalsOf(jobId: String, kind: Option[ApprovalKind]): List[ApprovalInfo] =
      rows.filter(r => r.jobId.contains(jobId) && kind.forall(_.wire == r.kind)).sortBy(_.id).toList.map(info)
    def autoApprovedSince(kind: ApprovalKind, since: Instant): List[ApprovalInfo] =
      rows
        .filter(r =>
          r.kind == kind.wire && r.note.exists(_.startsWith("autopilot: approved")) && r.decidedAt.exists(
            !_.isBefore(since)
          )
        )
        .sortBy(_.id)
        .toList
        .map(info)
    def openOfKinds(kinds: List[ApprovalKind], limit: Int): List[ApprovalInfo] =
      rows
        .filter(r => r.state == Open && kinds.exists(_.wire == r.kind))
        .sortBy(_.id)
        .take(math.max(1, limit))
        .toList
        .map(info)
    def openLight(): List[ApprovalDigest.Open] =
      rows
        .filter(_.state == Open)
        .sortBy(_.id)
        .toList
        .map(r => ApprovalDigest.Open(r.id, r.kind, r.subject, AutomationState.text(r.createdAt)))
    def openCount(kind: ApprovalKind): Int = rows.count(r => r.state == Open && r.kind == kind.wire)
    def openBySubject(): Map[String, Int] =
      rows.filter(_.state == Open).flatMap(_.subject).groupBy(identity).view.mapValues(_.size).toMap
    def forStep(jobId: String, seq: Int): Option[ApprovalInfo] =
      rows.find(r => r.jobId.contains(jobId) && r.seq.contains(seq)).map(info)

    /** Change the rows `which` selects by `f`; how many. */
    private def change(which: ApprovalRecord => Boolean)(f: ApprovalRecord => ApprovalRecord): Int =
      state.getAndUpdate(_.modify(_.approvals).using(_.map(r => if which(r) then f(r) else r))).approvals.count(which)
    def closeForRun(jobId: String, why: String): Int =
      val at = now
      change(r => r.jobId.contains(jobId) && r.seq.isDefined && r.state == Open)(
        _.modify(_.state)
          .setTo(ApprovalState.Rejected.wire)
          .modify(_.result)
          .setTo(Some(why))
          .modify(_.decidedAt)
          .setTo(Some(at))
      )
    def decide(id: Long, to: ApprovalState, note: Option[String], result: Option[String]): Boolean =
      val at = now
      change(r => r.id == id && r.state == Open)(
        _.modify(_.state)
          .setTo(to.wire)
          .modify(_.note)
          .setTo(note)
          .modify(_.result)
          .setTo(result)
          .modify(_.decidedAt)
          .setTo(Some(at))
      ) > 0
    def settle(id: Long, to: ApprovalState, result: String): Unit =
      val _ = change(_.id == id)(_.modify(_.state).setTo(to.wire).modify(_.result).setTo(Some(result)))
