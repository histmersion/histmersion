package com.histmersion
package domain.automation.approvals

import io.scalaland.chimney.dsl.*

import com.histmersion.domain.automation.agents.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.{ApprovalKind, Decision, DecisionRequest}
import com.histmersion.api.automation.ApprovalDigest.*
import com.histmersion.api.automation.Queue.{QueueAdd, QueueItem}
import com.histmersion.domain.automation.Approval as DomainApproval

import java.time.Instant
import javax.sql.DataSource
import com.histmersion.api.automation.ApprovalDigest
import com.histmersion.api.Api
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.automation.worklists.WorkListControllers

/** The approval digest (#3762 proposal 3): everything waiting for a person in one view ([[digest]]), ONE worklog line a
  * UTC day after `agents.autopilot.digest-hour` ([[tick]], from the managers' tick), and a whole group decided at once
  * ([[decideAll]]) — each approval through [[AgentsControllers.decide]], the very decision a person's click takes (a
  * waiting run is woken, a plan applied).
  */
final class ApprovalDigestService(
    ds: DataSource,
    cfg: => AgentsConfig,
    agents: AgentsControllers
):

  /** Does code decide an approval of kind `k` now (the autopilot's checks)? */
  private def checked(k: ApprovalKind.Wire): Boolean =
    ApprovalKind
      .parse(k)
      .exists(kk =>
        agents.autoApprovals.Kinds.contains(kk) && cfg.approvals
          .checked(kk.transformInto[Approval.Kind])
      )

  /** The digest as of `now`. */
  def digest(now: Instant = Instant.now(agents.clock)): Digest =
    val open = agents.approvalStore.openLight()
    val wait = agents.focus.goalPlansWait()
    Digest(
      Digest.At(now.toString),
      Digest.Total(open.size),
      ApprovalDigest.group(open, checked, wait.fold(Set.empty)(_ => Set(ApprovalKind.GoalPlan.wire))),
      Digest.HeldForPerson(agents.managerStore.forPerson().value),
      agents.autopilotRow.lastDigest().map(d => Digest.LastWritten(d.value)),
      wait.map(w => Digest.GoalPlansWait(w.value))
    )

  /** From the managers' tick: once a UTC day (the tick's), after `digest-hour` (on the tick's time or on the digest's
    * clock, when it has one: [[AgentsControllers.digestClock]]), the digest's line goes to the worklog.
    */
  def tick(now: Instant): List[String] =
    val hour = cfg.autopilot.digestHour.value
    val day = ManagerPolicy.dayStart(now)
    def pastHour(t: Instant) = !t.isBefore(ManagerPolicy.dayStart(t).plusSeconds(math.min(23, hour).toLong * 3600L))
    val due = hour >= 0 && (pastHour(now) || agents.digestClock.exists(c => pastHour(Instant.now(c)))) &&
      agents.autopilotRow
        .lastDigest()
        .flatMap(d => ManagerPolicy.parseInstant(ManagerPolicy.InstantText(d.value)))
        .forall(_.isBefore(day))
    if !due then Nil
    else
      val line = ApprovalDigest.line(digest(now))
      agents.autopilotRow.digested(now)
      val _ = agents.workLists.add(WorkListControllers.ListName("log"), QueueAdd(QueueItem.Text(line.value.take(1000))))
      scribe.info(line.value)
      List("approval digest written")

  /** Decide every open approval `b` matches (a kind, a subject, ids), oldest first; a refused one does not stop the
    * rest.
    */
  def decideAll(b: BulkDecide): Either[Api.Error, BulkResult] =
    for
      d <- Decision
        .parse(Decision.Wire(b.decision.value))
        .toRight(Api.Error(Problem(s"unknown decision '${b.decision}' (approve or reject)")))
      _ <- Either.cond(
        b.kind.exists(_.value.trim.nonEmpty) || b.subject.exists(_.value.trim.nonEmpty) || b.ids.nonEmpty,
        (),
        Api.Error(Problem("give a kind, a subject or ids: a bulk decision never takes every open approval"))
      )
      _ <- b.kind
        .map(_.value.trim)
        .filter(_.nonEmpty)
        .fold[Either[Api.Error, Unit]](Right(()))(k =>
          ApprovalKind
            .parse(ApprovalKind.Wire(k))
            .toRight(
              Api.Error(
                Problem(s"unknown approval kind '$k' (${ApprovalKind.values.map(_.wire).mkString(", ")})")
              )
            )
            .map(_ => ())
        )
    yield
      val open = agents.approvalStore.openLight()
      val ms = ApprovalDigest.matching(open, b)
      if b.dryRun.value then
        BulkResult(
          BulkResult.Decision(d.wire.value),
          dryRun = BulkResult.DryRun(true),
          BulkResult.Matched(ms.size),
          ms.map(o => BulkResult.Decided(o.id.value)),
          Nil,
          BulkResult.Left(open.size)
        )
      else
        val by = b.by.map(_.value.trim).filter(_.nonEmpty).getOrElse("person")
        val note = Some(
          DecisionRequest.Note(
            (b.note.map(_.value.trim).filter(_.nonEmpty).getOrElse("") + s" (decided in bulk by $by)").trim
          )
        )
        val done = ms.map(o =>
          o.id -> agents.decide(
            DomainApproval.Id(o.id.value),
            DomainApproval.Outcome(d.wire.value),
            DecisionRequest(note)
          )
        )
        val ok = done.collect { case (id, Right(_)) => id }
        val bad = done.collect { case (id, Left(e)) =>
          BulkFailed(BulkFailed.Id(id.value), Problem(e.message.value.take(300)))
        }
        val left = agents.approvalStore.openLight().size
        if ok.nonEmpty then
          val what = List(
            b.kind.map(k => s"kind $k"),
            b.subject.map(s => s"about $s"),
            Option.when(b.ids.nonEmpty)(s"${b.ids.size} ids")
          ).flatten.mkString(", ")
          val line = s"approvals ${if d == Decision.Approve then "approved" else "rejected"} in bulk by $by ($what): " +
            s"${ok.size} of ${ms.size}" + (if bad.nonEmpty then s", ${bad.size} refused" else "") + s"; $left open left"
          val _ = agents.workLists.add(
            WorkListControllers.ListName("log"),
            QueueAdd(QueueItem.Text(line.take(1000)))
          )
          scribe.info(line)
        agents.managers.nudge()
        BulkResult(
          BulkResult.Decision(d.wire.value),
          dryRun = BulkResult.DryRun(false),
          BulkResult.Matched(ms.size),
          ok.map(id => BulkResult.Decided(id.value)),
          bad,
          BulkResult.Left(left)
        )
