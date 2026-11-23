package com.histmersion
package server.views

import com.histmersion.api.Api
import com.histmersion.api.automation.Agents.{ApprovalInfo, ApprovalState, RunInfo}
import com.histmersion.api.automation.{Goal, Pipeline, Queue}
import com.histmersion.api.library.Citekey
import com.histmersion.api.research.OpenWork
import com.histmersion.api.research.OpenWork.{OpenItem, Terms, Who}
import com.histmersion.domain.Actor
import com.histmersion.domain.automation.agents.{AgentsControllers, Engine}
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.domain.automation.worklists.QueueScope
import com.histmersion.domain.library.acquisition.{AcquireAttempt, AcquireVerdict}
import com.histmersion.domain.library.workflows.AcquireRecheckService
import com.histmersion.util.problem.Problem
import io.scalaland.chimney.dsl.*

import java.time.Instant
import com.histmersion.api.automation.Creation

final class OpenWorkView(agents: AgentsControllers, recheck: AcquireRecheckService):

  def state(p: OpenWork.Scope.Parsed): OpenWork.State =
    val now = Instant.now(agents.clock)
    val wanted = p.kind
    val items =
      (if wanted.forall(_ == OpenWork.Kind.Decision) then decisions else Vector.empty) ++
        (if wanted.forall(_ == OpenWork.Kind.Acquisition) then acquisitions(now) else Vector.empty) ++
        (if wanted.forall(_ == OpenWork.Kind.Task) then tasks else Vector.empty) ++
        (if wanted.forall(_ == OpenWork.Kind.Run) then runs(now) else Vector.empty)
    OpenWork.State.of(p.goal.fold(items)(g => items.filter(concerns(_, g))), p, Terms.At.of(now))

  def acquisition(id: Queue.QueueItem.Id): Either[Api.Error, OpenWork.History] =
    val judged = recheck.verdict(id, Instant.now(agents.clock))
    Queue
      .fulfillable(id, judged.map(_.item))
      .left
      .map(Api.Error(_))
      .map(item =>
        judged.fold(OpenWork.History(item.id, item.text, OpenWork.Verdict.NoIdentifier, Vector.empty))(j =>
          OpenWork.History(j.item.id, j.item.text, verdictOf(j.verdict), j.attempts.toVector.map(attemptOf))
        )
      )

  private def concerns(item: OpenItem, goal: Goal.Slug): Boolean = item match
    case OpenItem.Decision(a, _) => a.subject.exists(s => s"$s" == s"$goal")
    case a: OpenItem.Acquisition => a.goal.contains(goal)
    case t: OpenItem.Task        => t.goal.contains(goal)
    case _: OpenItem.PausedRun   => false

  private def who(actor: Actor): Who = actor match
    case Actor.Person => Who.Owner
    case Actor.Agents => Who.Agents

  private def decisions: Vector[OpenItem] =
    agents.approvalStore
      .approvals(Some(ApprovalState.Open), ApprovalQuery.Limit(1000))
      .toVector
      .map((a: ApprovalInfo) => OpenItem.Decision(a, who(agents.autoApprovals.decides(a))))

  private def acquisitions(now: Instant): Vector[OpenItem] =
    recheck
      .verdicts(now)
      .toVector
      .map(j =>
        OpenItem.Acquisition(
          j.item.id,
          j.item.text,
          verdictOf(j.verdict),
          j.attempts.headOption.map(a => Terms.At.of(a.at)),
          AcquireAttempt.Tries.of(j.attempts).transformInto[Terms.Tries],
          j.item.creator,
          j.item.goal
        )
      )

  private def tasks: Vector[OpenItem] =
    agents.queues
      .list(Queue.Kind.Tasks, QueueScope.Open)
      .toVector
      .map(i =>
        OpenItem.Task(
          i.id,
          i.text,
          if i.actor.contains(Creation.Agents) then Who.Agents else Who.Owner,
          i.creator,
          i.goal
        )
      )

  private def runs(now: Instant): Vector[OpenItem] =
    agents.engine
      .pausedWaiting(now)
      .toVector
      .map((r: Engine.Paused) =>
        OpenItem.PausedRun(
          r.run.transformInto[RunInfo.Id],
          r.pipeline.transformInto[Pipeline.Wire],
          r.cause,
          r.resumes.map(Terms.At.of),
          who(r.actor)
        )
      )

  private def verdictOf(v: AcquireVerdict): OpenWork.Verdict = v match
    case AcquireVerdict.Unchecked(due)     => OpenWork.Verdict.Unchecked(Terms.At.of(due))
    case AcquireVerdict.Converting(source) =>
      OpenWork.Verdict.Converting(source.map(_.transformInto[Terms.Source]))
    case AcquireVerdict.Retrying(f, next, last) =>
      OpenWork.Verdict.Retrying(f.transformInto[Terms.Failures], Terms.At.of(next), last.fold(Vector.empty)(_.toVector))
    case AcquireVerdict.Stuck(f, since, last) =>
      OpenWork.Verdict.Stuck(f.transformInto[Terms.Failures], Terms.At.of(since), last.fold(Vector.empty)(_.toVector))
    case AcquireVerdict.ManualDownload(url) => OpenWork.Verdict.ManualDownload(url.map(_.transformInto[Terms.OaUrl]))
    case AcquireVerdict.Borrow              => OpenWork.Verdict.Borrow
    case AcquireVerdict.NoFreeCopy(recheck) => OpenWork.Verdict.NoFreeCopy(Terms.At.of(recheck))
    case AcquireVerdict.NoIdentifier        => OpenWork.Verdict.NoIdentifier

  private def attemptOf(a: AcquireAttempt): OpenWork.Attempt =
    OpenWork.Attempt(
      Terms.At.of(a.at),
      triggerOf(a.trigger),
      a.generation.transformInto[Terms.Generation],
      outcomeOf(a.outcome),
      a.answers.map(x => OpenWork.Answer(x.fetcher.transformInto[Terms.Fetcher], saidOf(x.said), x.why)),
      a.oa.map(o =>
        OpenWork.Oa(
          o.status.map(_.transformInto[Terms.OaStatus]),
          o.url.map(_.transformInto[Terms.OaUrl]),
          o.licence.map(_.transformInto[Terms.OaLicence]),
          o.scripts match
            case AcquireAttempt.Oa.Scripts.Allowed => OpenWork.Scripts.Allowed
            case AcquireAttempt.Oa.Scripts.Refused => OpenWork.Scripts.Refused
        )
      )
    )

  private def triggerOf(t: AcquireAttempt.Trigger): OpenWork.Trigger = t match
    case AcquireAttempt.Trigger.PersonIngest   => OpenWork.Trigger.PersonIngest
    case AcquireAttempt.Trigger.WatcherBacklog => OpenWork.Trigger.WatcherBacklog
    case AcquireAttempt.Trigger.Recheck        => OpenWork.Trigger.Recheck
    case AcquireAttempt.Trigger.Legacy         => OpenWork.Trigger.OldCheck

  private def outcomeOf(o: AcquireAttempt.Outcome): OpenWork.Outcome = o match
    case AcquireAttempt.Outcome.Closed(ck)      => OpenWork.Outcome.Closed(ck.transformInto[Citekey])
    case AcquireAttempt.Outcome.Fetched(source) => OpenWork.Outcome.Fetched(source.transformInto[Terms.Source])
    case AcquireAttempt.Outcome.NoCopy          => OpenWork.Outcome.NoCopy
    case AcquireAttempt.Outcome.Failed(why)     => OpenWork.Outcome.Failed(why.toVector)
    case AcquireAttempt.Outcome.Legacy(note, n) =>
      OpenWork.Outcome.OldCheck(note.transformInto[Terms.LegacyNote], n.transformInto[Terms.Tries])

  private def saidOf(s: Problem.NotFound.Said): OpenWork.Said = s match
    case Problem.NotFound.Said.Miss(Problem.NotFound.Absence.NoIdentifier)      => OpenWork.Said.NoIdentifier
    case Problem.NotFound.Said.Miss(Problem.NotFound.Absence.NotHeld)           => OpenWork.Said.NotHeld
    case Problem.NotFound.Said.Miss(Problem.NotFound.Absence.Unanswered)        => OpenWork.Said.Unanswered
    case Problem.NotFound.Said.Miss(Problem.NotFound.Absence.RateLimited)       => OpenWork.Said.RateLimited
    case Problem.NotFound.Said.Miss(Problem.NotFound.Absence.Unconfigured)      => OpenWork.Said.Unconfigured
    case Problem.NotFound.Said.Refused(Problem.NotFound.Refusal.BotChallenge)   => OpenWork.Said.BotChallenge
    case Problem.NotFound.Said.Refused(Problem.NotFound.Refusal.LendingOnly)    => OpenWork.Said.LendingOnly
    case Problem.NotFound.Said.Refused(Problem.NotFound.Refusal.NoEntitlement)  => OpenWork.Said.NoEntitlement
    case Problem.NotFound.Said.Refused(Problem.NotFound.Refusal.LicenceForbids) => OpenWork.Said.LicenceForbids
