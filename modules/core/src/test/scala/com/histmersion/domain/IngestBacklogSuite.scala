package com.histmersion
package domain
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.IngestBacklog.*
import com.histmersion.api.problem.StoredShapes
import com.histmersion.util.problem.Text.sentence
import com.histmersion.api.automation.Managers.*

import java.time.Instant
import com.histmersion.api.automation.ManagersMarkdown
import com.histmersion.api.library.RepairAcquire
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Doi
import com.histmersion.api.catalogue.Year
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.Managers
import com.histmersion.lint.{tolerated, LintRule}

/** The watchers' ingest backlog, pure: a sweep's allowance after the backlog, the drain's order and caps, where a pick
  * goes when it is not ingested, what a failed ingest leads to, the days to drain, and the repair's decision per
  * acquisition item.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
@tolerated(LintRule.SilentInterp, "pending G35 (a vocabulary renders through its wire string)")
class IngestBacklogSuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-19T12:00:00Z")

  test(
    "a sweep's new finds get what the backlog leaves of the allowance: its own backlog under its cap, every backlog under the shared one"
  ):
    assertEquals(
      sweepAllowance(
        own = IngestBacklog.OwnAllowance(5),
        shared = None,
        ownBacklog = IngestBacklog.Lane.Backlog(0),
        allBacklog = IngestBacklog.TotalBacklog(0)
      ),
      5
    )
    assertEquals(
      sweepAllowance(
        IngestBacklog.OwnAllowance(5),
        None,
        ownBacklog = IngestBacklog.Lane.Backlog(3),
        allBacklog = IngestBacklog.TotalBacklog(40)
      ),
      2,
      "no shared cap: only its own backlog counts"
    )
    assertEquals(
      sweepAllowance(
        IngestBacklog.OwnAllowance(5),
        Some(IngestBacklog.SharedAllowance(20)),
        ownBacklog = IngestBacklog.Lane.Backlog(0),
        allBacklog = IngestBacklog.TotalBacklog(18)
      ),
      2,
      "the shared cap after every backlog"
    )
    assertEquals(
      sweepAllowance(
        IngestBacklog.OwnAllowance(5),
        Some(IngestBacklog.SharedAllowance(20)),
        ownBacklog = IngestBacklog.Lane.Backlog(5),
        allBacklog = IngestBacklog.TotalBacklog(5)
      ),
      0,
      "its own backlog fills its cap: nothing new today"
    )
    assertEquals(
      sweepAllowance(
        IngestBacklog.OwnAllowance(5),
        Some(IngestBacklog.SharedAllowance(20)),
        ownBacklog = IngestBacklog.Lane.Backlog(9),
        allBacklog = IngestBacklog.TotalBacklog(96)
      ),
      0,
      "never negative"
    )
    assertEquals(
      sweepAllowance(
        IngestBacklog.OwnAllowance(20),
        Some(IngestBacklog.SharedAllowance(20)),
        IngestBacklog.Lane.Backlog(0),
        IngestBacklog.TotalBacklog(0)
      ),
      20
    )
    assertEquals(
      sweepAllowance(
        IngestBacklog.OwnAllowance(0),
        Some(IngestBacklog.SharedAllowance(20)),
        IngestBacklog.Lane.Backlog(0),
        IngestBacklog.TotalBacklog(0)
      ),
      0
    )

  test(
    "the drain: due items only, highest priority then the oldest, within each watcher's own cap and the shared one, at most max"
  ):
    val es = List(
      Entry(IngestBacklog.Entry.Id(5), IngestBacklog.Entry.ManagerId("watch:a"), IngestBacklog.Entry.Priority(0), None),
      Entry(IngestBacklog.Entry.Id(1), IngestBacklog.Entry.ManagerId("watch:a"), IngestBacklog.Entry.Priority(0), None),
      Entry(IngestBacklog.Entry.Id(2), IngestBacklog.Entry.ManagerId("watch:b"), IngestBacklog.Entry.Priority(0), None),
      Entry(IngestBacklog.Entry.Id(3), IngestBacklog.Entry.ManagerId("watch:b"), IngestBacklog.Entry.Priority(7), None),
      Entry(
        IngestBacklog.Entry.Id(4),
        IngestBacklog.Entry.ManagerId("watch:c"),
        IngestBacklog.Entry.Priority(0),
        Some(now.plusSeconds(3600))
      ),
      Entry(IngestBacklog.Entry.Id(6), IngestBacklog.Entry.ManagerId("watch:a"), IngestBacklog.Entry.Priority(0), None)
    )
    val left = Map(
      Entry.ManagerId("watch:a") -> Lane.LeftToday(2),
      Entry.ManagerId("watch:b") -> Lane.LeftToday(5),
      Entry.ManagerId("watch:c") -> Lane.LeftToday(5)
    )
    assertEquals(
      drain(es, left, None, now, IngestBacklog.DrainLimit(10)).map(_.id),
      List(3L, 1L, 2L, 5L),
      "priority 7 first; a's cap stops #6; c not due"
    )
    assertEquals(
      drain(es, left, Some(IngestBacklog.SharedAllowance(2)), now, IngestBacklog.DrainLimit(10)).map(_.id),
      List(3L, 1L),
      "the shared cap"
    )
    assertEquals(drain(es, left, None, now, IngestBacklog.DrainLimit(1)).map(_.id), List(3L), "one tick's share")
    assertEquals(
      drain(es, left - Entry.ManagerId("watch:b"), None, now, IngestBacklog.DrainLimit(10)).map(_.id),
      List(1L, 5L),
      "a watcher missing (paused, auto-ingest off) drains nothing"
    )
    assertEquals(drain(es, left, Some(IngestBacklog.SharedAllowance(0)), now, IngestBacklog.DrainLimit(10)), Nil)
    assertEquals(
      drain(es, left, None, now.plusSeconds(7200), IngestBacklog.DrainLimit(10)).map(_.id),
      List(3L, 1L, 2L, 4L, 5L),
      "c is due later"
    )

  test(
    "a pick not ingested now: nothing new when the corpus or the acquisition list has it, the open entry joined, a settled one respected"
  ):
    assertEquals(
      place(
        Some(Citekey("ck")),
        Some(IngestBacklog.Placement.JoinAcquire.Item(7)),
        Some(IngestBacklog.Placement.JoinBacklog.Item(9L)),
        None
      ),
      Placement.InCorpus(Citekey("ck"))
    )
    assertEquals(
      place(
        None,
        Some(IngestBacklog.Placement.JoinAcquire.Item(7)),
        Some(IngestBacklog.Placement.JoinBacklog.Item(9L)),
        None
      ),
      Placement.JoinAcquire(IngestBacklog.Placement.JoinAcquire.Item(7))
    )
    assertEquals(
      place(
        None,
        None,
        Some(IngestBacklog.Placement.JoinBacklog.Item(9L)),
        Some(IngestBacklog.Placement.SettledBefore.Item(3L))
      ),
      Placement.JoinBacklog(IngestBacklog.Placement.JoinBacklog.Item(9L))
    )
    assertEquals(
      place(None, None, None, Some(IngestBacklog.Placement.SettledBefore.Item(3L))),
      Placement.SettledBefore(IngestBacklog.Placement.SettledBefore.Item(3L))
    )
    assertEquals(place(None, None, None, None), Placement.New)

  test(
    "a refusal an earlier version stored ('no legal open-access copy of …') reads back as it was and still means no legal copy"
  ):
    val stored = scala.io.Source
      .fromResource("stored-texts/no-legal-copy.txt")(using scala.io.Codec.UTF8)
      .getLines()
      .filter(_.nonEmpty)
      .toList
    assert(stored.nonEmpty)
    stored.foreach { text =>
      val read = StoredShapes.read(text)
      assertEquals(read, Problem.Stored.Unrecognised(Problem.Stored.Text(text)))
      assertEquals(read.sentence, text, "its text, whole")
      assert(isNoLegalCopy(read), text)
      assertEquals(AcquireRecheck.failure(read), AcquireRecheck.Outcome.NoCopy, text)
      onFailure(
        read,
        IngestBacklog.Failure.Retry.Failures(0),
        ManagerPolicy.ReviewSettings.MaxFailures(3),
        ManagerPolicy.ReviewSettings.BackoffMinutes(15),
        ManagerPolicy.ReviewSettings.MaxBackoffMinutes(360),
        now
      ) match
        case Failure.ToAcquire(why) => assertEquals(why.said, text)
        case other                  => fail("expected the acquisition list", clues(other))
    }
    assertEquals(AcquireRecheck.answers(StoredShapes.read(stored(1))), Vector.empty, "a stored text is never parsed")

  test(
    "a failed ingest: no legal copy → the acquisition list at once; another failure → again after the backoff, the list after max-failures"
  ):
    assert(isNoLegalCopy(Problem.NotFound.Copy(Problem.Raw("10.1/x"), Vector.empty)))
    assert(!isNoLegalCopy(Problem.Unclassified(Legacy.Text("HTTP 503 from the converter"))))
    onFailure(
      Problem.NotFound.Copy(Problem.Raw("10.1/x"), Vector.empty),
      IngestBacklog.Failure.Retry.Failures(0),
      ManagerPolicy.ReviewSettings.MaxFailures(3),
      ManagerPolicy.ReviewSettings.BackoffMinutes(15),
      ManagerPolicy.ReviewSettings.MaxBackoffMinutes(360),
      now
    ) match
      case Failure.ToAcquire(why) => assertEquals(why.said, "no legal open-access PDF found for 10.1/x")
      case other                  => fail(s"expected the acquisition list, got $other")
    assertEquals(
      onFailure(
        Problem.Unclassified(Legacy.Text("timeout")),
        IngestBacklog.Failure.Retry.Failures(0),
        ManagerPolicy.ReviewSettings.MaxFailures(3),
        ManagerPolicy.ReviewSettings.BackoffMinutes(15),
        ManagerPolicy.ReviewSettings.MaxBackoffMinutes(360),
        now
      ),
      Failure.Retry(
        now.plusSeconds(15 * 60),
        IngestBacklog.Failure.Retry.Failures(1),
        IngestBacklog.Rationale.Failed(Problem.Unclassified(Legacy.Text("timeout")))
      )
    )
    assertEquals(
      onFailure(
        Problem.Unclassified(Legacy.Text("timeout")),
        IngestBacklog.Failure.Retry.Failures(1),
        ManagerPolicy.ReviewSettings.MaxFailures(3),
        ManagerPolicy.ReviewSettings.BackoffMinutes(15),
        ManagerPolicy.ReviewSettings.MaxBackoffMinutes(360),
        now
      ),
      Failure.Retry(
        now.plusSeconds(30 * 60),
        IngestBacklog.Failure.Retry.Failures(2),
        IngestBacklog.Rationale.Failed(Problem.Unclassified(Legacy.Text("timeout")))
      )
    )
    onFailure(
      Problem.Unclassified(Legacy.Text("timeout")),
      IngestBacklog.Failure.Retry.Failures(2),
      ManagerPolicy.ReviewSettings.MaxFailures(3),
      ManagerPolicy.ReviewSettings.BackoffMinutes(15),
      ManagerPolicy.ReviewSettings.MaxBackoffMinutes(360),
      now
    ) match
      case Failure.ToAcquire(why) => assertEquals(why.said, "timeout (3 tries)")
      case other                  => fail(s"expected the acquisition list, got $other")
    assert(
      onFailure(
        Problem.Unclassified(Legacy.Text("")),
        IngestBacklog.Failure.Retry.Failures(0),
        ManagerPolicy.ReviewSettings.MaxFailures(3),
        ManagerPolicy.ReviewSettings.BackoffMinutes(15),
        ManagerPolicy.ReviewSettings.MaxBackoffMinutes(360),
        now
      ).isInstanceOf[Failure.Retry]
    )

  test("days to drain: 0 = today, the shared cap truncates a day, None when nothing can drain"):
    assertEquals(daysToDrain(Nil, None, None), Some(0))
    assertEquals(
      daysToDrain(
        List(Lane(IngestBacklog.Lane.Backlog(3), IngestBacklog.Lane.PerDay(5), IngestBacklog.Lane.LeftToday(5))),
        None,
        None
      ),
      Some(0),
      "today's allowance takes it all"
    )
    assertEquals(
      daysToDrain(
        List(Lane(IngestBacklog.Lane.Backlog(10), IngestBacklog.Lane.PerDay(5), IngestBacklog.Lane.LeftToday(0))),
        None,
        None
      ),
      Some(2),
      "nothing left today, 5 a day"
    )
    assertEquals(
      daysToDrain(
        List(Lane(IngestBacklog.Lane.Backlog(10), IngestBacklog.Lane.PerDay(5), IngestBacklog.Lane.LeftToday(2))),
        None,
        None
      ),
      Some(2),
      "2 today, 5, then 3"
    )
    // 96 works over 35 watchers of 5 a day each, 20 a day in all, today's 20 spent: ~5 more days
    val lanes = List.fill(26)(
      Lane(IngestBacklog.Lane.Backlog(3), IngestBacklog.Lane.PerDay(5), IngestBacklog.Lane.LeftToday(0))
    ) ++ List.fill(9)(
      Lane(IngestBacklog.Lane.Backlog(2), IngestBacklog.Lane.PerDay(5), IngestBacklog.Lane.LeftToday(0))
    )
    assertEquals(lanes.map(_.backlog.value).sum, 96)
    assertEquals(
      daysToDrain(lanes, Some(IngestBacklog.Lane.PerDay(20)), Some(IngestBacklog.Lane.LeftToday(0))),
      Some(5)
    )
    assertEquals(daysToDrain(lanes, None, None), Some(1), "no shared cap: every watcher drains its 2–3 tomorrow")
    assertEquals(
      daysToDrain(
        List(Lane(IngestBacklog.Lane.Backlog(4), IngestBacklog.Lane.PerDay(0), IngestBacklog.Lane.LeftToday(0))),
        None,
        None
      ),
      None,
      "a cap of 0 never drains"
    )
    assertEquals(
      daysToDrain(
        List(Lane(IngestBacklog.Lane.Backlog(4), IngestBacklog.Lane.PerDay(5), IngestBacklog.Lane.LeftToday(5))),
        Some(IngestBacklog.Lane.PerDay(0)),
        Some(IngestBacklog.Lane.LeftToday(0))
      ),
      None,
      "a shared cap of 0 never drains"
    )

  test(
    "the repair: a legal open-access item moves (its watcher auto-ingests), one in the corpus is removed, the rest stay"
  ):
    val f = RepairFacts(
      IngestBacklog.RepairFacts.Item(7),
      Doi("10.1/x"),
      isOa = IngestBacklog.RepairFacts.IsOa(true),
      IngestBacklog.RepairFacts.Watcher("watch:g"),
      watcherExists = IngestBacklog.RepairFacts.WatcherExists(true),
      watcherAutoIngest = IngestBacklog.RepairFacts.WatcherAutoIngest(true),
      None
    )
    assertEquals(repairAction(f), RepairAction.Move)
    assertEquals(repairAction(f.modify(_.inCorpus).setTo(Some(Citekey("ck")))), RepairAction.Remove(Citekey("ck")))
    assert(
      repairAction(f.modify(_.isOa).setTo(IngestBacklog.RepairFacts.IsOa(false))).isInstanceOf[RepairAction.Keep],
      "a closed work stays on the list"
    )
    assert(
      repairAction(
        f.modify(_.isOa).setTo(IngestBacklog.RepairFacts.IsOa(false)).modify(_.inCorpus).setTo(Some(Citekey("ck")))
      ).isInstanceOf[RepairAction.Keep],
      "closed: never touched"
    )
    assert(repairAction(f.modify(_.watcherAutoIngest).setTo(IngestBacklog.RepairFacts.WatcherAutoIngest(false))) match
      case RepairAction.Keep(why) => why.said.contains("does not auto-ingest")
      case _                      => false)
    assert(
      repairAction(f.modify(_.watcherExists).setTo(IngestBacklog.RepairFacts.WatcherExists(false)))
        .isInstanceOf[RepairAction.Keep]
    )

  test("the backlog's wire state and its few words"):
    val st =
      ItemState(oa =
        Some(
          OaWork(
            api.library.Doi("10.1/x"),
            api.library.Title("X"),
            Some(Year(2020)),
            List(api.library.AuthorName("A")),
            None,
            List(Goal.Slug("g1"), Goal.Slug("g2")),
            Managers.Rationale("fits"),
            Some(Managers.OaWork.FoundBy("run"))
          )
        )
      )
    assertEquals(RawJson.decode[ItemState]((st).asJson.noSpaces), Right(st))
    assertEquals(
      RawJson.decode[ItemState]("""{"history":[]}"""),
      Right(ItemState()),
      "an old item's state still decodes"
    )
    assertEquals(
      ManagersMarkdown.backlog(Managers.ManagerInfo.Backlog(0), None),
      "0 legal open-access works in the ingest backlog"
    )
    assertEquals(
      ManagersMarkdown.backlog(Managers.ManagerInfo.Backlog(96), Some(Managers.ManagerInfo.BacklogDays(5))),
      "96 legal open-access works in the ingest backlog (~5 more days at the current allowance)"
    )
    assertEquals(
      ManagersMarkdown.backlog(Managers.ManagerInfo.Backlog(1), Some(Managers.ManagerInfo.BacklogDays(0))),
      "1 legal open-access work in the ingest backlog (drained today at the current allowance)"
    )
    assert(ManagersMarkdown.backlog(Managers.ManagerInfo.Backlog(3), None).contains("not draining"))
    val info = ManagerInfo(
      Managers.ManagerInfo.Id("watch:g"),
      Managers.ManagerInfo.Kind("watch"),
      backlog = Managers.ManagerInfo.Backlog(4),
      backlogDays = Some(Managers.ManagerInfo.BacklogDays(1))
    )
    assert(
      ManagersMarkdown.line(info).contains("4 legal open-access works in the ingest backlog (~1 more day"),
      ManagersMarkdown.line(info)
    )
    assert(!ManagersMarkdown.line(info.modify(_.backlog).setTo(Managers.ManagerInfo.Backlog(0))).contains("backlog"))
    val r = RepairAcquire.RepairResult(
      dryRun = RepairAcquire.RepairResult.DryRun.DryRun,
      moved = RepairAcquire.RepairResult.Moved(1),
      kept = RepairAcquire.RepairResult.Kept(1),
      untouched = RepairAcquire.RepairResult.Untouched(5),
      rows = List(
        RepairAcquire.RepairRow(
          RepairAcquire.RepairRow.Item(7),
          api.library.Doi("10.1/x"),
          List(RepairAcquire.RepairRow.Goals("g")),
          RepairAcquire.RepairRow.Watcher("watch:g"),
          RepairAcquire.RepairRow.Action(RepairAcquire.Move),
          RepairAcquire.RepairRow.Detail("legal open access")
        ),
        RepairAcquire.RepairRow(
          RepairAcquire.RepairRow.Item(8),
          api.library.Doi("10.1/y"),
          List(RepairAcquire.RepairRow.Goals("g")),
          RepairAcquire.RepairRow.Watcher("watch:g"),
          RepairAcquire.RepairRow.Action(RepairAcquire.Keep),
          RepairAcquire.RepairRow.Detail("closed")
        )
      ),
      backlog = Managers.ManagerInfo.Backlog(1),
      backlogDays = Some(Managers.ManagerInfo.BacklogDays(1))
    )
    val md = RepairAcquire.markdown(r)
    assert(
      md.value.contains("dry run: nothing changed") && md.value.contains("would move 1 legal open-access work") &&
        md.value.contains("#7 https://doi.org/10.1/x · goal g · would move to watch:g") && md.value.contains(
          "#8 https://doi.org/10.1/y · goal g · keep: closed"
        ) &&
        md.value.contains("5 open acquisition items not created by a watcher's sweep: untouched"),
      md.value
    )
