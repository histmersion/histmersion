package com.histmersion
package api.research

import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, writeToString}
import com.histmersion.api.automation.{Agents, Goal, Pipeline, Queue}
import com.histmersion.api.library.Citekey
import com.histmersion.util.problem.Problem
import OpenWork.{NextStep, OpenItem, Terms, Verdict, When, Who}

import java.time.Instant
import com.histmersion.api.Provenance

class OpenWorkSuite extends munit.FunSuite:
  private val now = Instant.parse("2026-11-23T12:00:00Z")
  private val checked = Terms.At.of(now)
  private val at = Terms.At("2026-11-23T10:00:00Z")
  private val soon = Terms.At("2026-11-23T18:00:00Z")
  private val later = Terms.At("2026-11-30T10:00:00Z")
  private val noAnswer = Problem.NotFound.Entity(Problem.TypeName("Doi"), Problem.Raw("10.1/x"))

  private def acquisition(v: Verdict, creator: Option[Provenance] = None): OpenItem =
    OpenItem.Acquisition(
      Queue.QueueItem.Id(1),
      Queue.QueueItem.Text("Smith 2020, A work"),
      v,
      Some(at),
      Terms.Tries(2),
      creator,
      Some(Goal.Slug("bronze-age"))
    )

  private val verdicts: List[Verdict] = List(
    Verdict.Unchecked(at),
    Verdict.Converting(Some(Terms.Source("unpaywall"))),
    Verdict.Retrying(Terms.Failures(2), soon, Vector(noAnswer)),
    Verdict.Stuck(Terms.Failures(5), at, Vector(noAnswer)),
    Verdict.ManualDownload(Some(Terms.OaUrl("https://example.org/a.pdf"))),
    Verdict.Borrow,
    Verdict.NoFreeCopy(later),
    Verdict.NoIdentifier
  )

  private val decision = OpenItem.Decision(
    Agents.ApprovalInfo(
      Agents.ApprovalInfo.Id(7),
      None,
      Agents.ApprovalInfo.Kind("goal-plan"),
      Citekey("smith2020"),
      subject = Some(Agents.ApprovalInfo.Subject("bronze-age"))
    ),
    Who.Owner
  )
  private val task = OpenItem.Task(
    Queue.QueueItem.Id(2),
    Queue.QueueItem.Text("Read the review"),
    Who.Agents,
    Some(Provenance(Provenance.Unattributed, surface = Some(Provenance.Surface("rest")))),
    None
  )
  private val run = OpenItem.PausedRun(
    Agents.RunInfo.Id("0193a5f2-7c1e-7b3a-9f0e-2d4c6b8a1e3f"),
    Pipeline.Wire("enrich-source"),
    None,
    None,
    Who.Owner
  )

  test("a verdict says who acts, and the person's step"):
    assertEquals(
      verdicts.map(v => (v.who, v.step)),
      List(
        (Who.Agents, None),
        (Who.Agents, None),
        (Who.Agents, None),
        (Who.Owner, Some(NextStep.LookAtStuckProvider)),
        (Who.Owner, Some(NextStep.DownloadInBrowser)),
        (Who.Owner, Some(NextStep.Borrow)),
        (Who.Owner, Some(NextStep.BuyOrBorrow)),
        (Who.Owner, Some(NextStep.AddIdentifier))
      )
    )
    assertEquals(Verdict.NoFreeCopy(later).wording.take(39), "No free legal copy found: buy or borrow")
    assertEquals(NextStep.BuyOrBorrow.wording, "No free legal copy found: buy or borrow")

  test("an open item: who, step, next time, and an unattributed creator"):
    assertEquals(decision.step, Some(NextStep.Decide))
    assertEquals(run.step, Some(NextStep.ResumeRun))
    assertEquals(task.step, None)
    assertEquals(task.creatorNotRecorded, true)
    assertEquals(acquisition(Verdict.Borrow).creatorNotRecorded, false)
    assertEquals(acquisition(Verdict.Retrying(Terms.Failures(1), soon, Vector.empty)).next, Some(soon))
    assertEquals(task.line.endsWith("creator not recorded"), true)

  test("when the agents act: now, today, later"):
    assertEquals(When.of(None, now), When.Now)
    assertEquals(When.of(Some(at), now), When.Now)
    assertEquals(When.of(Some(soon), now), When.Today)
    assertEquals(When.of(Some(later), now), When.Later)
    assertEquals(When.of(Some(Terms.At("not a time")), now), When.Now)

  test("a filter word: known case-insensitively, else none"):
    assertEquals(Who.from(Terms.Who("Owner")), Some(Who.Owner))
    assertEquals(Who.from(Terms.Who(" agents ")), Some(Who.Agents))
    assertEquals(Who.from(Terms.Who("person")), None)
    assertEquals(OpenWork.Kind.from(Terms.Kind("run")), Some(OpenWork.Kind.Run))
    assertEquals(OpenWork.Kind.from(Terms.Kind("approval")), None)
    val scope = OpenWork.Scope(Some(Terms.Who("person")), None, Some(Terms.Kind("task")))
    assertEquals(scope.unknownWho, Some(Terms.Who("person")))
    assertEquals(scope.unknownKind, None)
    assertEquals(scope.parsed, OpenWork.Scope.Parsed(None, None, Some(OpenWork.Kind.Task)))
    assertEquals(Terms.Who.all.flatMap(Who.from), List(Who.Owner, Who.Agents))
    assertEquals(Terms.Kind.all.flatMap(OpenWork.Kind.from), OpenWork.Kind.values.toList)

  test("the state: totals over the kind asked for, items of the who asked for, grouped"):
    val items = Vector(decision, task, run) ++ verdicts.map(v => acquisition(v))
    val all = OpenWork.State.of(items, OpenWork.Scope.Parsed(None, None, None), checked)
    assertEquals(all.totals, OpenWork.Totals(Terms.Count(7), Terms.Count(4)))
    assertEquals(all.items.size, 11)
    val mine = OpenWork.State.of(items, OpenWork.Scope.Parsed(Some(Who.Owner), None, None), checked)
    assertEquals(mine.totals, all.totals)
    assertEquals(mine.items.forall(_.who == Who.Owner), true)
    assertEquals(
      all.owner.map(_._1),
      Vector(
        NextStep.Decide,
        NextStep.DownloadInBrowser,
        NextStep.Borrow,
        NextStep.BuyOrBorrow,
        NextStep.AddIdentifier,
        NextStep.LookAtStuckProvider,
        NextStep.ResumeRun
      )
    )
    assertEquals(all.agents.map((w, is) => (w, is.size)), Vector((When.Now, 3), (When.Today, 1)))
    val acq = OpenWork.State.of(items, OpenWork.Scope.Parsed(None, None, Some(OpenWork.Kind.Acquisition)), checked)
    assertEquals(acq.totals, OpenWork.Totals(Terms.Count(5), Terms.Count(3)))
    assertEquals(
      all.markdown.linesIterator.take(1).toList,
      List("### Research state: 7 wait for you · 4 the agents will handle")
    )
    assertEquals(all.markdown.contains("**No free legal copy found: buy or borrow**"), true)

  test("the state and a history read back as written"):
    val state = OpenWork.State.of(
      Vector(decision, task, run) ++ verdicts.map(v => acquisition(v)),
      OpenWork.Scope.Parsed(None, None, None),
      checked
    )
    assertEquals(readFromString[OpenWork.State](writeToString(state)), state)
    val history = OpenWork.History(
      Queue.QueueItem.Id(1),
      Queue.QueueItem.Text("Smith 2020"),
      Verdict.NoFreeCopy(later),
      Vector(
        OpenWork.Attempt(
          at,
          OpenWork.Trigger.Recheck,
          Terms.Generation("g1"),
          OpenWork.Outcome.NoCopy,
          Vector(OpenWork.Answer(Terms.Fetcher("unpaywall"), OpenWork.Said.NotHeld, noAnswer)),
          Some(OpenWork.Oa(None, None, None, OpenWork.Scripts.Allowed))
        ),
        OpenWork.Attempt(
          at,
          OpenWork.Trigger.OldCheck,
          Terms.Generation("legacy"),
          OpenWork.Outcome.OldCheck(Terms.LegacyNote("checked 2026-11-01: no copy"), Terms.Tries(3)),
          Vector.empty,
          None
        )
      )
    )
    assertEquals(readFromString[OpenWork.History](writeToString(history)), history)
    assertEquals(history.markdown.contains("unpaywall does not have it"), true)
    assertEquals(history.markdown.contains("an older check (3 tries): checked 2026-11-01: no copy"), true)
