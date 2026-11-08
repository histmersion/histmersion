package com.histmersion
package domain.automation.agents

import com.histmersion.domain.Problem
import com.histmersion.api.Paging
import com.histmersion.api.automation.{ApprovalDigest, ListFilters}
import com.histmersion.api.automation.Agents.{ApprovalInfo, ApprovalKind, ApprovalState, StepInfo}
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.domain.automation.AutomationState
import com.histmersion.util.database.{Jsonb, Transactor}
import com.histmersion.util.jobs.JobRuns
import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.domain.automation.Approval
import com.histmersion.util.jobs.Job
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Citekey
import com.histmersion.domain.automation.Run

/** The approvals, as the engine's features reach them: what a run proposed for a person to decide — open until decided
  * (once), or recorded approved when it was applied at once — and the reads the lists, the digest, the autopilot and
  * the scheduler make of them.
  */
trait ApprovalService:

  def approvalIn(id: Approval.Id)(using com.augustnagro.magnum.DbCon): Option[ApprovalInfo]

  def payload(id: Approval.Id): Option[ApprovalService.Payload]

  def approval(id: Approval.Id): Option[ApprovalInfo]

  /** Newest first, of one state or every one. */
  def approvals(state: Option[ApprovalState], limit: ApprovalQuery.Limit): List[ApprovalInfo]

  /** One newest-first page of one state or every one; `before`/`upTo` are approval ids (Left: one that is none). */
  def approvalPage(
      state: Option[ApprovalState],
      limit: ApprovalQuery.Limit,
      before: Option[ApprovalQuery.Cursor] = None,
      upTo: Option[ApprovalQuery.UpTo] = None
  ): Either[Problem, (List[ApprovalInfo], Boolean)]

  /** One newest-first page of the approvals `q` matches. */
  def approvalPage(
      q: ApprovalQuery,
      limit: ApprovalQuery.Limit,
      before: Option[ApprovalQuery.Cursor],
      upTo: Option[ApprovalQuery.UpTo]
  ): Either[Problem, (List[ApprovalInfo], Boolean)]

  /** Per state (every filter but the state's), and the kind and decided-by facets (each without its own filter). */
  def approvalFacets(
      q: ApprovalQuery
  ): (List[(Approval.State.Wire, RowCount)], List[(String, List[(String, RowCount)])])

  /** Every approval counted per state. */
  def approvalCounts(): List[(Approval.State.Wire, RowCount)]

  /** The approval step `seq` of run `jobId` waits on, opened unless it is there already. */
  def openForStep(
      jobId: Job.Id,
      seq: ApprovalService.StepSeq,
      kind: ApprovalKind,
      subject: ApprovalQuery.Subject,
      payload: ApprovalService.Payload,
      state: ApprovalState = ApprovalState.Open,
      result: Option[ApprovalService.OpenForStepResult] = None
  ): ApprovalInfo

  /** A proposal about a goal or an article that no run waits for. */
  def openSubject(
      jobId: Option[Job.Id],
      kind: ApprovalKind,
      subject: ApprovalQuery.Subject,
      payload: ApprovalService.Payload,
      state: ApprovalState = ApprovalState.Open,
      result: Option[ApprovalService.OpenSubjectResult] = None
  ): ApprovalInfo

  /** What run `jobId` opened (of `kind`), oldest first. */
  def approvalsOf(jobId: Job.Id, kind: Option[ApprovalKind] = None): List[ApprovalInfo]

  /** Of `kind`, approved by the autopilot since `since`. */
  def autoApprovedSince(kind: ApprovalKind, since: Instant): List[ApprovalInfo]

  /** The open ones of these kinds, oldest first. */
  def openOfKinds(kinds: List[ApprovalKind], limit: ApprovalQuery.Limit): List[ApprovalInfo]

  /** Every open one, light, oldest first. */
  def openLight(): List[ApprovalDigest.Open]
  def openCount(kind: ApprovalKind): RowCount

  /** Open ones per subject. */
  def openBySubject(): Map[ApprovalQuery.Subject, RowCount]

  /** The approval step `seq` of run `jobId` opened. */
  def forStep(jobId: Job.Id, seq: ApprovalService.StepSeq): Option[ApprovalInfo]

  /** A cancelled run's open step approvals rejected with `why`; how many. */
  def closeForRun(jobId: Job.Id, why: Problem): RowCount

  /** Compare-and-set an open one to its decision; false when it was decided already. */
  def decide(id: Approval.Id, to: ApprovalState, note: Option[Approval.Note], result: Option[String]): Boolean

  /** After [[decide]]: the final state and what applying it did. */
  def settle(id: Approval.Id, to: ApprovalState, result: Approval.Outcome): Unit

object ApprovalService:
  type Payload = Payload.T
  object Payload extends Newtype[String, Pure]
  type StepSeq = StepSeq.T
  object StepSeq extends Newtype[Int, Pure]
  type OpenForStepResult = OpenForStepResult.T
  object OpenForStepResult extends Newtype[String, Pure]
  type OpenSubjectResult = OpenSubjectResult.T
  object OpenSubjectResult extends Newtype[String, Pure]

  def apply(db: Transactor, clock: Clock): ApprovalService = Implementation(db, clock)

  final private class Implementation(db: Transactor, clock: Clock) extends ApprovalService:
    def approvalIn(id: Approval.Id)(using com.augustnagro.magnum.DbCon): Option[ApprovalInfo] =
      AgentRepo.approval(ApprovalInfo.Id(id.value))
    private def now: Instant = Instant.now(clock)

    def payload(id: Approval.Id): Option[Payload] =
      db.read(AgentRepo.payload(ApprovalInfo.Id(id.value))).map(p => Payload(p.value))

    def approval(id: Approval.Id): Option[ApprovalInfo] = db.read(AgentRepo.approval(ApprovalInfo.Id(id.value)))
    def approvals(state: Option[ApprovalState], limit: ApprovalQuery.Limit): List[ApprovalInfo] =
      db.read(AgentRepo.approvals(state, PageSize.assume(limit.value)))
    def approvalPage(
        state: Option[ApprovalState],
        limit: ApprovalQuery.Limit,
        before: Option[ApprovalQuery.Cursor],
        upTo: Option[ApprovalQuery.UpTo]
    ): Either[Problem, (List[ApprovalInfo], Boolean)] =
      db.read(
        AgentRepo.approvalPage(
          state,
          PageSize.assume(limit.value),
          before.map(c => AgentRepo.PageCursor(c.value)),
          upTo.map(c => AgentRepo.PageCursor(c.value))
        )
      ).left.map(Problem.fromApi)
    def approvalPage(
        q: ApprovalQuery,
        limit: ApprovalQuery.Limit,
        before: Option[ApprovalQuery.Cursor],
        upTo: Option[ApprovalQuery.UpTo]
    ): Either[Problem, (List[ApprovalInfo], Boolean)] =
      db.read(
        AgentRepo.approvalPage(
          q,
          PageSize.assume(limit.value),
          before.map(c => AgentRepo.PageCursor(c.value)),
          upTo.map(c => AgentRepo.PageCursor(c.value))
        )
      ).left.map(Problem.fromApi)
    def approvalFacets(
        q: ApprovalQuery
    ): (List[(Approval.State.Wire, RowCount)], List[(String, List[(String, RowCount)])]) =
      db.read(AgentRepo.approvalFacets(q))
    def approvalCounts(): List[(Approval.State.Wire, RowCount)] = db.read(AgentRepo.approvalCounts())
    def openForStep(
        jobId: Job.Id,
        seq: StepSeq,
        kind: ApprovalKind,
        subject: ApprovalQuery.Subject,
        payload: Payload,
        state: ApprovalState,
        result: Option[OpenForStepResult]
    ): ApprovalInfo =
      db.transact(
        AgentRepo.openForStep(
          jobId,
          StepInfo.Seq(seq.value),
          kind,
          ApprovalInfo.Subject(subject.value),
          Jsonb.JsonText(payload.value),
          state,
          result.map(r => ApprovalInfo.Result(r.value)),
          now
        )
      )
    def openSubject(
        jobId: Option[Job.Id],
        kind: ApprovalKind,
        subject: ApprovalQuery.Subject,
        payload: Payload,
        state: ApprovalState,
        result: Option[OpenSubjectResult]
    ): ApprovalInfo =
      db.transact(
        AgentRepo.openSubject(
          jobId,
          kind,
          ApprovalInfo.Subject(subject.value),
          Jsonb.JsonText(payload.value),
          state,
          result.map(r => ApprovalInfo.Result(r.value)),
          now
        )
      )
    def approvalsOf(jobId: Job.Id, kind: Option[ApprovalKind]): List[ApprovalInfo] =
      db.read(AgentRepo.approvalsOf(jobId, kind))
    def autoApprovedSince(kind: ApprovalKind, since: Instant): List[ApprovalInfo] =
      db.read(AgentRepo.autoApprovedSince(kind, since))
    def openOfKinds(kinds: List[ApprovalKind], limit: ApprovalQuery.Limit): List[ApprovalInfo] =
      db.read(AgentRepo.openOfKinds(kinds, PageSize.assume(limit.value)))
    def openLight(): List[ApprovalDigest.Open] = db.read(AgentRepo.openLight())
    def openCount(kind: ApprovalKind): RowCount = db.read(AgentRepo.openCount(kind))
    def openBySubject(): Map[ApprovalQuery.Subject, RowCount] =
      db.read(AgentRepo.openBySubject()).map((k, v) => ApprovalQuery.Subject(k.value) -> v)
    def forStep(jobId: Job.Id, seq: StepSeq): Option[ApprovalInfo] =
      db.read(AgentRepo.forStep(jobId, StepInfo.Seq(seq.value)))
    def closeForRun(jobId: Job.Id, why: Problem): RowCount =
      db.transact(AgentRepo.closeForRun(jobId, Problem.wire(why)))
    def decide(id: Approval.Id, to: ApprovalState, note: Option[Approval.Note], result: Option[String]): Boolean =
      db.transact(
        AgentRepo.decide(
          ApprovalInfo.Id(id.value),
          to,
          note.map(n => ApprovalInfo.Note(n.value)),
          result.map(ApprovalInfo.Result(_))
        )
      )
    def settle(id: Approval.Id, to: ApprovalState, result: Approval.Outcome): Unit =
      db.transact(AgentRepo.settle(ApprovalInfo.Id(id.value), to, ApprovalInfo.Result(result.value)))

  /** The approvals in memory, with the runs they name (their goal and manager match a list's filters); `clock` stands
    * in for the database's `now()`.
    */
  final class InMemory(state: AtomicReference[AutomationState], runs: JobRuns.InMemory, clock: Clock)
      extends ApprovalService:
    def approvalIn(id: Approval.Id)(using com.augustnagro.magnum.DbCon): Option[ApprovalInfo] = approval(id)
    private def now: Instant = Instant.now(clock)
    private def rows: Vector[ApprovalRecord] = state.get.approvals
    private val Open = ApprovalState.Open.wire.value
    private def info(r: ApprovalRecord): ApprovalInfo =
      AgentRepo.info(
        AgentRepo.ApprovalRow(
          ApprovalInfo.Id(r.id.value),
          r.jobId.map(j => AgentRepo.ApprovalJobId(j.value)),
          ApprovalInfo.Kind(r.kind.value),
          r.citekey.map(c => Citekey(c.value)),
          r.status.map(s => ApprovalInfo.Status(s.value)),
          r.current.map(c => ApprovalInfo.Current(c.value)),
          r.edgeKind.map(e => ApprovalInfo.EdgeKind(e.value)),
          r.target.map(t => ApprovalInfo.Target(t.value)),
          Jsonb.JsonText(r.proposal.value),
          ApprovalInfo.State(r.state.value),
          r.note.map(n => ApprovalInfo.Note(n.value)),
          r.result.map(x => ApprovalInfo.Result(x.value)),
          ApprovalInfo.CreatedAt(AutomationState.text(r.createdAt).value),
          r.decidedAt.map(d => ApprovalInfo.DecidedAt(AutomationState.text(d).value)),
          r.subject.map(s => ApprovalInfo.Subject(s.value)),
          r.seq.map(s => StepInfo.Seq(s.value))
        )
      )
    private def newest(rs: Vector[ApprovalRecord]): Vector[ApprovalRecord] =
      rs.sortBy[(Instant, Long)](r => (r.createdAt, r.id.value)).reverse

    /** Insert `make(id)` unless `conflicts` holds of a row there; the new row's id. */

    private def decidedAt(st: ApprovalState): Option[Instant] = Option.when(st != ApprovalState.Open)(now)

    def payload(id: Approval.Id): Option[Payload] =
      rows.find(_.id.value == id.value).map(r => Payload(r.proposal.value))

    def approval(id: Approval.Id): Option[ApprovalInfo] = rows.find(_.id.value == id.value).map(info)
    def approvals(state: Option[ApprovalState], limit: ApprovalQuery.Limit): List[ApprovalInfo] =
      newest(rows.filter(r => state.forall(_.wire.value == r.state.value)))
        .take(math.max(1, limit.value))
        .toList
        .map(info)
    def approvalPage(
        state: Option[ApprovalState],
        limit: ApprovalQuery.Limit,
        before: Option[ApprovalQuery.Cursor],
        upTo: Option[ApprovalQuery.UpTo]
    ): Either[Problem, (List[ApprovalInfo], Boolean)] =
      approvalPage(ApprovalQuery(state), limit, before, upTo)

    private def decidedBy(r: ApprovalRecord): Option[String] =
      if r.state.value == Open then None
      else if r.note.exists(_.value.startsWith(ListFilters.DecidedBy.AutopilotNote)) then
        Some(ListFilters.DecidedBy.Autopilot.wire.value)
      else if r.note.isEmpty && r.result.exists(_.value.startsWith(ListFilters.DecidedBy.AutoResult)) then
        Some(ListFilters.DecidedBy.Auto.wire.value)
      else Some(ListFilters.DecidedBy.Person.wire.value)
    private def contains(v: Option[String], t: String): Boolean = v.exists(_.toLowerCase.contains(t.toLowerCase))

    /** Whether `r` passes every filter of `q` but `except`'s. */
    private def matches(q: ApprovalQuery, except: String)(r: ApprovalRecord): Boolean =
      lazy val run = r.jobId.flatMap(j => runs.get(Run.Id.assume(j.value)))
      (except == "state" || q.state.forall(_.wire.value == r.state.value)) &&
      (except == "kind" || q.kinds.isEmpty || q.kinds.exists(_.wire.value == r.kind.value)) &&
      q.subject.forall(t =>
        contains(r.subject.map(_.value), t.value) || contains(r.citekey.map(_.value), t.value) || contains(
          r.target.map(_.value),
          t.value
        ) ||
          run.exists(j => contains(j.row.goal.map(_.value), t.value) || contains(j.row.managerId.map(_.value), t.value))
      ) &&
      q.goal.forall(g => r.subject.exists(_.value == g.value) || run.exists(_.row.goal.exists(_.value == g.value))) &&
      (except == "decidedBy" || q.decidedBy.isEmpty || decidedBy(r).exists(d => q.decidedBy.exists(_.wire.value == d)))

    def approvalPage(
        q: ApprovalQuery,
        limit: ApprovalQuery.Limit,
        before: Option[ApprovalQuery.Cursor],
        upTo: Option[ApprovalQuery.UpTo]
    ): Either[Problem, (List[ApprovalInfo], Boolean)] =
      def cursor(c: String): Either[Problem, ApprovalRecord] =
        c.trim.toLongOption
          .flatMap(id => rows.find(_.id.value == id))
          .toRight(Problem(s"No approval '$c' to page from"))
      for
        b <- before.fold(Right(None))(c => cursor(c.value).map(Some(_)))
        u <- upTo.fold(Right(None))(c => cursor(c.value).map(Some(_)))
      yield
        def key(r: ApprovalRecord): (Instant, Long) = (r.createdAt, r.id.value)
        val ordering = summon[Ordering[(Instant, Long)]]
        val all = newest(rows.filter(matches(q, "")))
        val bounded =
          all.filter(r => b.forall(c => ordering.lt(key(r), key(c))) && u.forall(c => ordering.gteq(key(r), key(c))))
        val (shown, more) =
          Paging.cut(bounded.take(math.max(1, limit.value) + 1), Paging.Limit(math.max(1, limit.value)))
        val older =
          if more || u.isEmpty then more
          else shown.lastOption.exists(last => all.exists(r => ordering.lt(key(r), key(last))))
        (shown.toList.map(info), older)

    private def facet(values: Vector[String]): List[(String, RowCount)] =
      values
        .groupBy(identity)
        .toList
        .map((v, n) => (v, n.size))
        .sortBy((v, n) => (-n, v))
        .take(ListFilters.FacetMax)
        .map((v, n) => (v, RowCount(n)))
    def approvalFacets(
        q: ApprovalQuery
    ): (List[(Approval.State.Wire, RowCount)], List[(String, List[(String, RowCount)])]) =
      (
        facet(rows.filter(matches(q, "state")).map(_.state.value)).map((s, n) => (Approval.State.Wire(s), n)),
        List(
          "kind" -> facet(rows.filter(matches(q, "kind")).map(_.kind.value)),
          "decidedBy" -> facet(rows.filter(matches(q, "decidedBy")).flatMap(decidedBy))
        )
      )
    def approvalCounts(): List[(Approval.State.Wire, RowCount)] =
      facet(rows.map(_.state.value)).map((s, n) => (Approval.State.Wire(s), n))
    def openForStep(
        jobId: Job.Id,
        seq: StepSeq,
        kind: ApprovalKind,
        subject: ApprovalQuery.Subject,
        payload: Payload,
        state: ApprovalState,
        result: Option[OpenForStepResult]
    ): ApprovalInfo =
      val at = now
      val _ =
        AutomationState.insertApproval(this.state)(r => r.jobId.contains(jobId) && r.seq.exists(_.value == seq.value))(
          id =>
            ApprovalRecord(
              ApprovalRecord.Id(id.value),
              Some(jobId),
              Some(ApprovalRecord.Seq(seq.value)),
              ApprovalRecord.Kind(kind.wire.value),
              None,
              None,
              None,
              None,
              None,
              Some(ApprovalRecord.Subject(subject.value)),
              ApprovalRecord.Proposal(payload.value),
              ApprovalRecord.State(state.wire.value),
              None,
              result.map(r => ApprovalRecord.Result(r.value)),
              at,
              decidedAt(state)
            )
        )
      forStep(jobId, seq).get
    def openSubject(
        jobId: Option[Job.Id],
        kind: ApprovalKind,
        subject: ApprovalQuery.Subject,
        payload: Payload,
        state: ApprovalState,
        result: Option[OpenSubjectResult]
    ): ApprovalInfo =
      val at = now
      AutomationState
        .insertApproval(this.state)(_ => false)(id =>
          ApprovalRecord(
            ApprovalRecord.Id(id.value),
            jobId,
            None,
            ApprovalRecord.Kind(kind.wire.value),
            None,
            None,
            None,
            None,
            None,
            Some(ApprovalRecord.Subject(subject.value)),
            ApprovalRecord.Proposal(payload.value),
            ApprovalRecord.State(state.wire.value),
            None,
            result.map(r => ApprovalRecord.Result(r.value)),
            at,
            decidedAt(state)
          )
        )
        .flatMap(approval)
        .get
    def approvalsOf(jobId: Job.Id, kind: Option[ApprovalKind]): List[ApprovalInfo] =
      rows
        .filter(r => r.jobId.contains(jobId) && kind.forall(_.wire.value == r.kind.value))
        .sortBy[Long](_.id.value)
        .toList
        .map(info)
    def autoApprovedSince(kind: ApprovalKind, since: Instant): List[ApprovalInfo] =
      rows
        .filter(r =>
          r.kind.value == kind.wire.value && r.note.exists(_.value.startsWith("autopilot: approved")) && r.decidedAt
            .exists(
              !_.isBefore(since)
            )
        )
        .sortBy[Long](_.id.value)
        .toList
        .map(info)
    def openOfKinds(kinds: List[ApprovalKind], limit: ApprovalQuery.Limit): List[ApprovalInfo] =
      rows
        .filter(r => r.state.value == Open && kinds.exists(_.wire.value == r.kind.value))
        .sortBy[Long](_.id.value)
        .take(math.max(1, limit.value))
        .toList
        .map(info)
    def openLight(): List[ApprovalDigest.Open] =
      rows
        .filter(_.state.value == Open)
        .sortBy[Long](_.id.value)
        .toList
        .map(r =>
          ApprovalDigest.Open(
            ApprovalDigest.Open.Id(r.id.value),
            ApprovalDigest.Open.Kind(r.kind.value),
            r.subject.map(s => ApprovalDigest.Open.Subject(s.value)),
            ApprovalDigest.Open.CreatedAt(AutomationState.text(r.createdAt).value)
          )
        )
    def openCount(kind: ApprovalKind): RowCount = RowCount(
      rows.count(r => r.state.value == Open && r.kind.value == kind.wire.value)
    )
    def openBySubject(): Map[ApprovalQuery.Subject, RowCount] =
      rows
        .filter(_.state.value == Open)
        .flatMap(_.subject)
        .groupBy(_.value)
        .map((s, rs) => ApprovalQuery.Subject(s) -> RowCount(rs.size))
    def forStep(jobId: Job.Id, seq: StepSeq): Option[ApprovalInfo] =
      rows.find(r => r.jobId.contains(jobId) && r.seq.exists(_.value == seq.value)).map(info)

    /** Change the rows `which` selects by `f`; how many. */
    private def change(which: ApprovalRecord => Boolean)(f: ApprovalRecord => ApprovalRecord): RowCount =
      RowCount(
        state.getAndUpdate(_.modify(_.approvals).using(_.map(r => if which(r) then f(r) else r))).approvals.count(which)
      )
    def closeForRun(jobId: Job.Id, why: Problem): RowCount =
      val at = now
      change(r => r.jobId.contains(jobId) && r.seq.isDefined && r.state.value == Open)(
        _.modify(_.state)
          .setTo(ApprovalRecord.State(ApprovalState.Rejected.wire.value))
          .modify(_.result)
          .setTo(Some(ApprovalRecord.Result(why.value)))
          .modify(_.decidedAt)
          .setTo(Some(at))
      )
    def decide(id: Approval.Id, to: ApprovalState, note: Option[Approval.Note], result: Option[String]): Boolean =
      val at = now
      change(r => r.id.value == id.value && r.state.value == Open)(
        _.modify(_.state)
          .setTo(ApprovalRecord.State(to.wire.value))
          .modify(_.note)
          .setTo(note.map(n => ApprovalRecord.Note(n.value)))
          .modify(_.result)
          .setTo(result.map(ApprovalRecord.Result(_)))
          .modify(_.decidedAt)
          .setTo(Some(at))
      ).value > 0
    def settle(id: Approval.Id, to: ApprovalState, result: Approval.Outcome): Unit =
      val _ = change(_.id.value == id.value)(
        _.modify(_.state)
          .setTo(ApprovalRecord.State(to.wire.value))
          .modify(_.result)
          .setTo(Some(ApprovalRecord.Result(result.value)))
      )
