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



/** The approvals, as the engine's features reach them: what a run proposed for a person to decide — open until decided
  * (once), or recorded approved when it was applied at once — and the reads the lists, the digest, the autopilot and
  * the scheduler make of them.
  */
trait ApprovalService:

  def approvalIn(id: com.histmersion.domain.automation.Approval.Id)(using com.augustnagro.magnum.DbCon): Option[ApprovalInfo]

  def payload(id: com.histmersion.domain.automation.Approval.Id): Option[com.histmersion.domain.automation.agents.ApprovalService.Payload]



  def approval(id: com.histmersion.domain.automation.Approval.Id): Option[ApprovalInfo]

  /** Newest first, of one state or every one. */
  def approvals(state: Option[ApprovalState], limit: com.histmersion.domain.automation.approvals.ApprovalQuery.Limit): List[ApprovalInfo]

  /** One newest-first page of one state or every one; `before`/`upTo` are approval ids (Left: one that is none). */
  def approvalPage(
      state: Option[ApprovalState],
      limit: com.histmersion.domain.automation.approvals.ApprovalQuery.Limit,
      before: Option[com.histmersion.domain.automation.approvals.ApprovalQuery.Cursor] = None,
      upTo: Option[com.histmersion.domain.automation.approvals.ApprovalQuery.UpTo] = None
  ): Either[com.histmersion.domain.automation.approvals.ApprovalQuery.Cursor, (List[ApprovalInfo], Boolean)]

  /** One newest-first page of the approvals `q` matches. */
  def approvalPage(
      q: ApprovalQuery,
      limit: com.histmersion.domain.automation.approvals.ApprovalQuery.Limit,
      before: Option[com.histmersion.domain.automation.approvals.ApprovalQuery.Cursor],
      upTo: Option[com.histmersion.domain.automation.approvals.ApprovalQuery.UpTo]
  ): Either[com.histmersion.domain.automation.approvals.ApprovalQuery.Cursor, (List[ApprovalInfo], Boolean)]

  /** Per state (every filter but the state's), and the kind and decided-by facets (each without its own filter). */
  def approvalFacets(q: ApprovalQuery): (List[(com.histmersion.domain.automation.agents.ApprovalService.ApprovalFacetsResultPart1Part1, com.histmersion.domain.automation.agents.ApprovalService.Count)], List[(com.histmersion.domain.automation.agents.ApprovalService.ApprovalFacetsResultPart2Part1, List[(com.histmersion.domain.automation.agents.ApprovalService.ApprovalFacetsResultPart2Part2Part1, com.histmersion.domain.automation.agents.ApprovalService.Count)])])

  /** Every approval counted per state. */
  def approvalCounts(): List[(com.histmersion.domain.automation.Approval.Kind.Wire, com.histmersion.domain.automation.agents.ApprovalService.Count)]

  /** The approval step `seq` of run `jobId` waits on, opened unless it is there already. */
  def openForStep(
      jobId: com.histmersion.util.jobs.Job.Id,
      seq: com.histmersion.domain.automation.agents.ApprovalService.StepSeq,
      kind: ApprovalKind,
      subject: com.histmersion.domain.automation.approvals.ApprovalQuery.Subject,
      payload: com.histmersion.domain.automation.agents.ApprovalService.Payload,
      state: ApprovalState = ApprovalState.Open,
      result: Option[com.histmersion.domain.automation.agents.ApprovalService.OpenForStepResult] = None
  ): ApprovalInfo

  /** A proposal about a goal or an article that no run waits for. */
  def openSubject(
      jobId: Option[com.histmersion.util.jobs.Job.Id],
      kind: ApprovalKind,
      subject: com.histmersion.domain.automation.approvals.ApprovalQuery.Subject,
      payload: com.histmersion.domain.automation.agents.ApprovalService.Payload,
      state: ApprovalState = ApprovalState.Open,
      result: Option[com.histmersion.domain.automation.agents.ApprovalService.OpenSubjectResult] = None
  ): ApprovalInfo

  /** What run `jobId` opened (of `kind`), oldest first. */
  def approvalsOf(jobId: com.histmersion.util.jobs.Job.Id, kind: Option[ApprovalKind] = None): List[ApprovalInfo]

  /** Of `kind`, approved by the autopilot since `since`. */
  def autoApprovedSince(kind: ApprovalKind, since: Instant): List[ApprovalInfo]

  /** The open ones of these kinds, oldest first. */
  def openOfKinds(kinds: List[ApprovalKind], limit: com.histmersion.domain.automation.approvals.ApprovalQuery.Limit): List[ApprovalInfo]

  /** Every open one, light, oldest first. */
  def openLight(): List[ApprovalDigest.Open]
  def openCount(kind: ApprovalKind): com.histmersion.domain.automation.agents.ApprovalService.Count

  /** Open ones per subject. */
  def openBySubject(): Map[com.histmersion.domain.automation.approvals.ApprovalQuery.Subject, com.histmersion.domain.automation.agents.ApprovalService.Count]

  /** The approval step `seq` of run `jobId` opened. */
  def forStep(jobId: com.histmersion.util.jobs.Job.Id, seq: com.histmersion.domain.automation.agents.ApprovalService.StepSeq): Option[ApprovalInfo]

  /** A cancelled run's open step approvals rejected with `why`; how many. */
  def closeForRun(jobId: com.histmersion.util.jobs.Job.Id, why: com.histmersion.domain.automation.agents.AgentRepo.CloseReason): com.histmersion.domain.automation.agents.ApprovalService.Count

  /** Compare-and-set an open one to its decision; false when it was decided already. */
  def decide(id: com.histmersion.domain.automation.Approval.Id, to: ApprovalState, note: Option[com.histmersion.domain.automation.Approval.Note], result: Option[String]): Boolean

  /** After [[decide]]: the final state and what applying it did. */
  def settle(id: com.histmersion.domain.automation.Approval.Id, to: ApprovalState, result: com.histmersion.domain.automation.Approval.Outcome): Unit

object ApprovalService:
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Payload = Payload.T
  object Payload extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepSeq = StepSeq.T
  object StepSeq extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalFacetsResultPart1Part1 = ApprovalFacetsResultPart1Part1.T
  object ApprovalFacetsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalFacetsResultPart2Part1 = ApprovalFacetsResultPart2Part1.T
  object ApprovalFacetsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApprovalFacetsResultPart2Part2Part1 = ApprovalFacetsResultPart2Part2Part1.T
  object ApprovalFacetsResultPart2Part2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenForStepResult = OpenForStepResult.T
  object OpenForStepResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenSubjectResult = OpenSubjectResult.T
  object OpenSubjectResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor, clock: Clock): ApprovalService = Implementation(db, clock)

  final private class Implementation(db: Transactor, clock: Clock) extends ApprovalService:
    def approvalIn(id: com.histmersion.domain.automation.Approval.Id)(using com.augustnagro.magnum.DbCon): Option[ApprovalInfo] = AgentRepo.approval(id)
    private def now: Instant = Instant.now(clock)

    def payload(id: com.histmersion.domain.automation.Approval.Id): Option[com.histmersion.domain.automation.agents.ApprovalService.Payload] = db.read(AgentRepo.payload(id))

    def approval(id: com.histmersion.domain.automation.Approval.Id): Option[ApprovalInfo] = db.read(AgentRepo.approval(id))
    def approvals(state: Option[ApprovalState], limit: com.histmersion.domain.automation.approvals.ApprovalQuery.Limit): List[ApprovalInfo] =
      db.read(AgentRepo.approvals(state, limit))
    def approvalPage(
        state: Option[ApprovalState],
        limit: com.histmersion.domain.automation.approvals.ApprovalQuery.Limit,
        before: Option[com.histmersion.domain.automation.approvals.ApprovalQuery.Cursor],
        upTo: Option[com.histmersion.domain.automation.approvals.ApprovalQuery.UpTo]
    ): Either[com.histmersion.domain.automation.approvals.ApprovalQuery.Cursor, (List[ApprovalInfo], Boolean)] = db.read(AgentRepo.approvalPage(state, limit, before, upTo))
    def approvalPage(
        q: ApprovalQuery,
        limit: com.histmersion.domain.automation.approvals.ApprovalQuery.Limit,
        before: Option[com.histmersion.domain.automation.approvals.ApprovalQuery.Cursor],
        upTo: Option[com.histmersion.domain.automation.approvals.ApprovalQuery.UpTo]
    ): Either[com.histmersion.domain.automation.approvals.ApprovalQuery.Cursor, (List[ApprovalInfo], Boolean)] = db.read(AgentRepo.approvalPage(q, limit, before, upTo))
    def approvalFacets(q: ApprovalQuery): (List[(com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalFacetsResultPart1Part1, com.histmersion.domain.automation.agents.ApprovalService.Count)], List[(com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalFacetsResultPart2Part1, List[(com.histmersion.domain.automation.agents.ApprovalService.Implementation.ApprovalFacetsResultPart2Part2Part1, com.histmersion.domain.automation.agents.ApprovalService.Count)])]) =
      db.read(AgentRepo.approvalFacets(q))
    def approvalCounts(): List[(com.histmersion.domain.automation.Approval.Kind.Wire, com.histmersion.domain.automation.agents.ApprovalService.Count)] = db.read(AgentRepo.approvalCounts())
    def openForStep(
        jobId: com.histmersion.util.jobs.Job.Id,
        seq: com.histmersion.domain.automation.agents.ApprovalService.StepSeq,
        kind: ApprovalKind,
        subject: com.histmersion.domain.automation.approvals.ApprovalQuery.Subject,
        payload: com.histmersion.domain.automation.agents.ApprovalService.Payload,
        state: ApprovalState,
        result: Option[com.histmersion.domain.automation.agents.ApprovalService.Implementation.OpenForStepResult]
    ): ApprovalInfo = db.transact(AgentRepo.openForStep(jobId, seq, kind, subject, payload, state, result, now))
    def openSubject(
        jobId: Option[com.histmersion.util.jobs.Job.Id],
        kind: ApprovalKind,
        subject: com.histmersion.domain.automation.approvals.ApprovalQuery.Subject,
        payload: com.histmersion.domain.automation.agents.ApprovalService.Payload,
        state: ApprovalState,
        result: Option[com.histmersion.domain.automation.agents.ApprovalService.Implementation.OpenSubjectResult]
    ): ApprovalInfo = db.transact(AgentRepo.openSubject(jobId, kind, subject, payload, state, result, now))
    def approvalsOf(jobId: com.histmersion.util.jobs.Job.Id, kind: Option[ApprovalKind]): List[ApprovalInfo] =
      db.read(AgentRepo.approvalsOf(jobId, kind))
    def autoApprovedSince(kind: ApprovalKind, since: Instant): List[ApprovalInfo] =
      db.read(AgentRepo.autoApprovedSince(kind, since))
    def openOfKinds(kinds: List[ApprovalKind], limit: com.histmersion.domain.automation.approvals.ApprovalQuery.Limit): List[ApprovalInfo] =
      db.read(AgentRepo.openOfKinds(kinds, limit))
    def openLight(): List[ApprovalDigest.Open] = db.read(AgentRepo.openLight())
    def openCount(kind: ApprovalKind): com.histmersion.domain.automation.agents.ApprovalService.Count = db.read(AgentRepo.openCount(kind))
    def openBySubject(): Map[com.histmersion.domain.automation.approvals.ApprovalQuery.Subject, com.histmersion.domain.automation.agents.ApprovalService.Count] = db.read(AgentRepo.openBySubject())
    def forStep(jobId: com.histmersion.util.jobs.Job.Id, seq: com.histmersion.domain.automation.agents.ApprovalService.StepSeq): Option[ApprovalInfo] = db.read(AgentRepo.forStep(jobId, seq))
    def closeForRun(jobId: com.histmersion.util.jobs.Job.Id, why: com.histmersion.domain.automation.agents.AgentRepo.CloseReason): com.histmersion.domain.automation.agents.ApprovalService.Count = db.transact(AgentRepo.closeForRun(jobId, why))
    def decide(id: com.histmersion.domain.automation.Approval.Id, to: ApprovalState, note: Option[com.histmersion.domain.automation.Approval.Note], result: Option[String]): Boolean =
      db.transact(AgentRepo.decide(id, to, note, result))
    def settle(id: com.histmersion.domain.automation.Approval.Id, to: ApprovalState, result: com.histmersion.domain.automation.Approval.Outcome): Unit = db.transact(AgentRepo.settle(id, to, result))

  object Implementation:
    type ApprovalFacetsResultPart1Part1 = ApprovalFacetsResultPart1Part1.T
    object ApprovalFacetsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalFacetsResultPart2Part1 = ApprovalFacetsResultPart2Part1.T
    object ApprovalFacetsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalFacetsResultPart2Part2Part1 = ApprovalFacetsResultPart2Part2Part1.T
    object ApprovalFacetsResultPart2Part2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenForStepResult = OpenForStepResult.T
    object OpenForStepResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenSubjectResult = OpenSubjectResult.T
    object OpenSubjectResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The approvals in memory, with the runs they name (their goal and manager match a list's filters); `clock` stands
    * in for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[AutomationState], runs: JobRuns.InMemory, clock: Clock)
      extends ApprovalService:
    def approvalIn(id: com.histmersion.domain.automation.Approval.Id)(using com.augustnagro.magnum.DbCon): Option[ApprovalInfo] = approval(id)
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

    private def decidedAt(st: ApprovalState): Option[Instant] = Option.when(st != ApprovalState.Open)(now)


    def payload(id: com.histmersion.domain.automation.Approval.Id): Option[com.histmersion.domain.automation.agents.ApprovalService.Payload] = rows.find(_.id == id).map(_.proposal)

    def approval(id: com.histmersion.domain.automation.Approval.Id): Option[ApprovalInfo] = rows.find(_.id == id).map(info)
    def approvals(state: Option[ApprovalState], limit: com.histmersion.domain.automation.approvals.ApprovalQuery.Limit): List[ApprovalInfo] =
      newest(rows.filter(r => state.forall(_.wire == r.state))).take(math.max(1, limit)).toList.map(info)
    def approvalPage(
        state: Option[ApprovalState],
        limit: com.histmersion.domain.automation.approvals.ApprovalQuery.Limit,
        before: Option[com.histmersion.domain.automation.approvals.ApprovalQuery.Cursor],
        upTo: Option[com.histmersion.domain.automation.approvals.ApprovalQuery.UpTo]
    ): Either[com.histmersion.domain.automation.approvals.ApprovalQuery.Cursor, (List[ApprovalInfo], Boolean)] = approvalPage(ApprovalQuery(state), limit, before, upTo)

    private def decidedBy(r: ApprovalRecord): Option[com.histmersion.domain.automation.Approval.DecidedBy.Wire] =
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
        limit: com.histmersion.domain.automation.approvals.ApprovalQuery.Limit,
        before: Option[com.histmersion.domain.automation.approvals.ApprovalQuery.Cursor],
        upTo: Option[com.histmersion.domain.automation.approvals.ApprovalQuery.UpTo]
    ): Either[com.histmersion.domain.automation.approvals.ApprovalQuery.Cursor, (List[ApprovalInfo], Boolean)] =
      def cursor(c: com.histmersion.domain.automation.approvals.ApprovalQuery.Cursor): Either[com.histmersion.domain.automation.approvals.ApprovalQuery.Cursor, ApprovalRecord] =
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

    private def facet(values: Vector[com.histmersion.domain.automation.agents.ApprovalService.InMemory.FacetValues]): List[(com.histmersion.domain.automation.agents.ApprovalService.InMemory.FacetResultPart1, com.histmersion.domain.automation.agents.ApprovalService.Count)] =
      values.groupBy(identity).toList.map((v, n) => (v, n.size)).sortBy((v, n) => (-n, v)).take(ListFilters.FacetMax)
    def approvalFacets(q: ApprovalQuery): (List[(com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalFacetsResultPart1Part1, com.histmersion.domain.automation.agents.ApprovalService.Count)], List[(com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalFacetsResultPart2Part1, List[(com.histmersion.domain.automation.agents.ApprovalService.InMemory.ApprovalFacetsResultPart2Part2Part1, com.histmersion.domain.automation.agents.ApprovalService.Count)])]) =
      (
        facet(rows.filter(matches(q, "state")).map(_.state)),
        List(
          "kind" -> facet(rows.filter(matches(q, "kind")).map(_.kind)),
          "decidedBy" -> facet(rows.filter(matches(q, "decidedBy")).flatMap(decidedBy))
        )
      )
    def approvalCounts(): List[(com.histmersion.domain.automation.Approval.Kind.Wire, com.histmersion.domain.automation.agents.ApprovalService.Count)] = facet(rows.map(_.state))
    def openForStep(
        jobId: com.histmersion.util.jobs.Job.Id,
        seq: com.histmersion.domain.automation.agents.ApprovalService.StepSeq,
        kind: ApprovalKind,
        subject: com.histmersion.domain.automation.approvals.ApprovalQuery.Subject,
        payload: com.histmersion.domain.automation.agents.ApprovalService.Payload,
        state: ApprovalState,
        result: Option[com.histmersion.domain.automation.agents.ApprovalService.InMemory.OpenForStepResult]
    ): ApprovalInfo =
      val at = now
      val _ = AutomationState.insertApproval(this.state)(r => r.jobId.contains(jobId) && r.seq.contains(seq))(id =>
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
        jobId: Option[com.histmersion.util.jobs.Job.Id],
        kind: ApprovalKind,
        subject: com.histmersion.domain.automation.approvals.ApprovalQuery.Subject,
        payload: com.histmersion.domain.automation.agents.ApprovalService.Payload,
        state: ApprovalState,
        result: Option[com.histmersion.domain.automation.agents.ApprovalService.InMemory.OpenSubjectResult]
    ): ApprovalInfo =
      val at = now
      AutomationState.insertApproval(this.state)(_ => false)(id =>
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
    def approvalsOf(jobId: com.histmersion.util.jobs.Job.Id, kind: Option[ApprovalKind]): List[ApprovalInfo] =
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
    def openOfKinds(kinds: List[ApprovalKind], limit: com.histmersion.domain.automation.approvals.ApprovalQuery.Limit): List[ApprovalInfo] =
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
    def openCount(kind: ApprovalKind): com.histmersion.domain.automation.agents.ApprovalService.Count = rows.count(r => r.state == Open && r.kind == kind.wire)
    def openBySubject(): Map[com.histmersion.domain.automation.approvals.ApprovalQuery.Subject, com.histmersion.domain.automation.agents.ApprovalService.Count] =
      rows.filter(_.state == Open).flatMap(_.subject).groupBy(identity).view.mapValues(_.size).toMap
    def forStep(jobId: com.histmersion.util.jobs.Job.Id, seq: com.histmersion.domain.automation.agents.ApprovalService.StepSeq): Option[ApprovalInfo] =
      rows.find(r => r.jobId.contains(jobId) && r.seq.contains(seq)).map(info)

    /** Change the rows `which` selects by `f`; how many. */
    private def change(which: ApprovalRecord => Boolean)(f: ApprovalRecord => ApprovalRecord): com.histmersion.domain.automation.agents.ApprovalService.Count =
      state.getAndUpdate(_.modify(_.approvals).using(_.map(r => if which(r) then f(r) else r))).approvals.count(which)
    def closeForRun(jobId: com.histmersion.util.jobs.Job.Id, why: com.histmersion.domain.automation.agents.AgentRepo.CloseReason): com.histmersion.domain.automation.agents.ApprovalService.Count =
      val at = now
      change(r => r.jobId.contains(jobId) && r.seq.isDefined && r.state == Open)(
        _.modify(_.state)
          .setTo(ApprovalState.Rejected.wire)
          .modify(_.result)
          .setTo(Some(why))
          .modify(_.decidedAt)
          .setTo(Some(at))
      )
    def decide(id: com.histmersion.domain.automation.Approval.Id, to: ApprovalState, note: Option[com.histmersion.domain.automation.Approval.Note], result: Option[String]): Boolean =
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
    def settle(id: com.histmersion.domain.automation.Approval.Id, to: ApprovalState, result: com.histmersion.domain.automation.Approval.Outcome): Unit =
      val _ = change(_.id == id)(_.modify(_.state).setTo(to.wire).modify(_.result).setTo(Some(result)))

  object InMemory:

    type FacetResultPart1 = FacetResultPart1.T
    object FacetResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FacetValues = FacetValues.T
    object FacetValues extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalFacetsResultPart1Part1 = ApprovalFacetsResultPart1Part1.T
    object ApprovalFacetsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalFacetsResultPart2Part1 = ApprovalFacetsResultPart2Part1.T
    object ApprovalFacetsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ApprovalFacetsResultPart2Part2Part1 = ApprovalFacetsResultPart2Part2Part1.T
    object ApprovalFacetsResultPart2Part2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OpenForStepResult = _root_.com.histmersion.domain.automation.agents.ApprovalRecord.Result
    val OpenForStepResult: _root_.com.histmersion.domain.automation.agents.ApprovalRecord.Result.type = _root_.com.histmersion.domain.automation.agents.ApprovalRecord.Result
    type OpenSubjectResult = _root_.com.histmersion.domain.automation.agents.ApprovalRecord.Result
    val OpenSubjectResult: _root_.com.histmersion.domain.automation.agents.ApprovalRecord.Result.type = _root_.com.histmersion.domain.automation.agents.ApprovalRecord.Result
