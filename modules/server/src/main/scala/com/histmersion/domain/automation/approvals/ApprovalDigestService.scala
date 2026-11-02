package com.histmersion.domain.automation.approvals

import com.histmersion.domain.automation.agents.*

import com.histmersion.api.ApiError
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.{ApprovalKind, Decision, DecisionRequest}
import com.histmersion.api.automation.ApprovalDigest.*
import com.histmersion.api.automation.Queue.QueueAdd

import java.time.Instant
import javax.sql.DataSource
import com.histmersion.api.automation.ApprovalDigest

/** The approval digest (#3762 proposal 3): everything waiting for a person in one view ([[digest]]), ONE worklog line a
  * UTC day after `agents.autopilot.digest-hour` ([[tick]], from the managers' tick), and a whole group decided at once
  * ([[decideAll]]) — each approval through [[AgentsHandlers.decide]], the very decision a person's click takes (a
  * waiting run is woken, a plan applied).
  */
final class ApprovalDigestService(
    ds: DataSource,
    cfg: => AgentsConfig,
    agents: AgentsHandlers
):

  /** Does code decide an approval of kind `k` now (the autopilot's checks)? */
  private def checked(k: String): Boolean =
    ApprovalKind.parse(k).exists(kk => agents.autoApprovals.Kinds.contains(kk) && cfg.approvals.checked(kk))

  /** The digest as of `now`. */
  def digest(now: Instant = Instant.now(agents.clock)): Digest =
    val open = agents.approvalStore.openLight()
    val wait = agents.focus.goalPlansWait()
    Digest(
      now.toString,
      open.size,
      ApprovalDigest.group(open, checked, wait.fold(Set.empty)(_ => Set(ApprovalKind.GoalPlan.wire))),
      agents.managerStore.forPerson(),
      agents.autopilotRow.lastDigest(),
      wait
    )

  /** From the managers' tick: once a UTC day (the tick's), after `digest-hour` (on the tick's time or on the digest's
    * clock, when it has one: [[AgentsHandlers.digestClock]]), the digest's line goes to the worklog.
    */
  def tick(now: Instant): List[String] =
    val hour = cfg.autopilot.digestHour
    val day = ManagerPolicy.dayStart(now)
    def pastHour(t: Instant) = !t.isBefore(ManagerPolicy.dayStart(t).plusSeconds(math.min(23, hour).toLong * 3600L))
    val due = hour >= 0 && (pastHour(now) || agents.digestClock.exists(c => pastHour(Instant.now(c)))) &&
      agents.autopilotRow.lastDigest().flatMap(ManagerPolicy.parseInstant).forall(_.isBefore(day))
    if !due then Nil
    else
      val line = ApprovalDigest.line(digest(now))
      agents.autopilotRow.digested(now)
      val _ = agents.workLists.add("log", QueueAdd(line.take(1000)))
      scribe.info(line)
      List("approval digest written")

  /** Decide every open approval `b` matches (a kind, a subject, ids), oldest first; a refused one does not stop the
    * rest.
    */
  def decideAll(b: BulkDecide): Either[ApiError, BulkResult] =
    for
      d <- Decision.parse(b.decision).toRight(ApiError(s"unknown decision '${b.decision}' (approve or reject)"))
      _ <- Either.cond(
        b.kind.exists(_.trim.nonEmpty) || b.subject.exists(_.trim.nonEmpty) || b.ids.nonEmpty,
        (),
        ApiError("give a kind, a subject or ids: a bulk decision never takes every open approval")
      )
      _ <- b.kind
        .map(_.trim)
        .filter(_.nonEmpty)
        .fold[Either[ApiError, Unit]](Right(()))(k =>
          ApprovalKind
            .parse(k)
            .toRight(ApiError(s"unknown approval kind '$k' (${ApprovalKind.values.map(_.wire).mkString(", ")})"))
            .map(_ => ())
        )
    yield
      val open = agents.approvalStore.openLight()
      val ms = ApprovalDigest.matching(open, b)
      if b.dryRun then BulkResult(d.wire, dryRun = true, ms.size, ms.map(_.id), Nil, open.size)
      else
        val by = b.by.map(_.trim).filter(_.nonEmpty).getOrElse("person")
        val note = Some((b.note.map(_.trim).filter(_.nonEmpty).getOrElse("") + s" (decided in bulk by $by)").trim)
        val done = ms.map(o => o.id -> agents.decide(o.id, d.wire, DecisionRequest(note)))
        val ok = done.collect { case (id, Right(_)) => id }
        val bad = done.collect { case (id, Left(e)) => BulkFailed(id, e.message.take(300)) }
        val left = agents.approvalStore.openLight().size
        if ok.nonEmpty then
          val what = List(
            b.kind.map(k => s"kind $k"),
            b.subject.map(s => s"about $s"),
            Option.when(b.ids.nonEmpty)(s"${b.ids.size} ids")
          ).flatten.mkString(", ")
          val line = s"approvals ${if d == Decision.Approve then "approved" else "rejected"} in bulk by $by ($what): " +
            s"${ok.size} of ${ms.size}" + (if bad.nonEmpty then s", ${bad.size} refused" else "") + s"; $left open left"
          val _ = agents.workLists.add("log", QueueAdd(line.take(1000)))
          scribe.info(line)
        agents.managers.nudge()
        BulkResult(d.wire, dryRun = false, ms.size, ok, bad, left)
