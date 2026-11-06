package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.IngestBacklog.*
import com.histmersion.api.automation.Managers.*

import java.time.Instant
import com.histmersion.api.automation.ManagersMarkdown
import com.histmersion.api.library.RepairAcquire

/** The watchers' ingest backlog, pure: a sweep's allowance after the backlog, the drain's order and caps, where a pick
  * goes when it is not ingested, what a failed ingest leads to, the days to drain, and the repair's decision per
  * acquisition item.
  */
class IngestBacklogSuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-19T12:00:00Z")

  test(
    "a sweep's new finds get what the backlog leaves of the allowance: its own backlog under its cap, every backlog under the shared one"
  ):
    assertEquals(sweepAllowance(own = com.histmersion.domain.IngestBacklog.OwnAllowance(5), shared = None, ownBacklog = com.histmersion.domain.IngestBacklog.Lane.Backlog(0), allBacklog = com.histmersion.domain.IngestBacklog.TotalBacklog(0)), 5)
    assertEquals(
      sweepAllowance(com.histmersion.domain.IngestBacklog.OwnAllowance(5), None, ownBacklog = com.histmersion.domain.IngestBacklog.Lane.Backlog(3), allBacklog = com.histmersion.domain.IngestBacklog.TotalBacklog(40)),
      2,
      "no shared cap: only its own backlog counts"
    )
    assertEquals(sweepAllowance(com.histmersion.domain.IngestBacklog.OwnAllowance(5), Some(com.histmersion.domain.IngestBacklog.SharedAllowance(20)), ownBacklog = com.histmersion.domain.IngestBacklog.Lane.Backlog(0), allBacklog = com.histmersion.domain.IngestBacklog.TotalBacklog(18)), 2, "the shared cap after every backlog")
    assertEquals(
      sweepAllowance(com.histmersion.domain.IngestBacklog.OwnAllowance(5), Some(com.histmersion.domain.IngestBacklog.SharedAllowance(20)), ownBacklog = com.histmersion.domain.IngestBacklog.Lane.Backlog(5), allBacklog = com.histmersion.domain.IngestBacklog.TotalBacklog(5)),
      0,
      "its own backlog fills its cap: nothing new today"
    )
    assertEquals(sweepAllowance(com.histmersion.domain.IngestBacklog.OwnAllowance(5), Some(com.histmersion.domain.IngestBacklog.SharedAllowance(20)), ownBacklog = com.histmersion.domain.IngestBacklog.Lane.Backlog(9), allBacklog = com.histmersion.domain.IngestBacklog.TotalBacklog(96)), 0, "never negative")
    assertEquals(sweepAllowance(com.histmersion.domain.IngestBacklog.OwnAllowance(20), Some(com.histmersion.domain.IngestBacklog.SharedAllowance(20)), com.histmersion.domain.IngestBacklog.Lane.Backlog(0), com.histmersion.domain.IngestBacklog.TotalBacklog(0)), 20)
    assertEquals(sweepAllowance(com.histmersion.domain.IngestBacklog.OwnAllowance(0), Some(com.histmersion.domain.IngestBacklog.SharedAllowance(20)), com.histmersion.domain.IngestBacklog.Lane.Backlog(0), com.histmersion.domain.IngestBacklog.TotalBacklog(0)), 0)

  test(
    "the drain: due items only, highest priority then the oldest, within each watcher's own cap and the shared one, at most max"
  ):
    val es = List(
      Entry(com.histmersion.domain.IngestBacklog.Entry.Id(5), com.histmersion.domain.IngestBacklog.Entry.ManagerId("watch:a"), com.histmersion.domain.IngestBacklog.Entry.Priority(0), None),
      Entry(com.histmersion.domain.IngestBacklog.Entry.Id(1), com.histmersion.domain.IngestBacklog.Entry.ManagerId("watch:a"), com.histmersion.domain.IngestBacklog.Entry.Priority(0), None),
      Entry(com.histmersion.domain.IngestBacklog.Entry.Id(2), com.histmersion.domain.IngestBacklog.Entry.ManagerId("watch:b"), com.histmersion.domain.IngestBacklog.Entry.Priority(0), None),
      Entry(com.histmersion.domain.IngestBacklog.Entry.Id(3), com.histmersion.domain.IngestBacklog.Entry.ManagerId("watch:b"), com.histmersion.domain.IngestBacklog.Entry.Priority(7), None),
      Entry(com.histmersion.domain.IngestBacklog.Entry.Id(4), com.histmersion.domain.IngestBacklog.Entry.ManagerId("watch:c"), com.histmersion.domain.IngestBacklog.Entry.Priority(0), Some(now.plusSeconds(3600))),
      Entry(com.histmersion.domain.IngestBacklog.Entry.Id(6), com.histmersion.domain.IngestBacklog.Entry.ManagerId("watch:a"), com.histmersion.domain.IngestBacklog.Entry.Priority(0), None)
    )
    val left = Map("watch:a" -> 2, "watch:b" -> 5, "watch:c" -> 5)
    assertEquals(
      drain(es, left, None, now, com.histmersion.domain.IngestBacklog.DrainLimit(10)).map(_.id),
      List(3L, 1L, 2L, 5L),
      "priority 7 first; a's cap stops #6; c not due"
    )
    assertEquals(drain(es, left, Some(com.histmersion.domain.IngestBacklog.SharedAllowance(2)), now, com.histmersion.domain.IngestBacklog.DrainLimit(10)).map(_.id), List(3L, 1L), "the shared cap")
    assertEquals(drain(es, left, None, now, com.histmersion.domain.IngestBacklog.DrainLimit(1)).map(_.id), List(3L), "one tick's share")
    assertEquals(
      drain(es, left - "watch:b", None, now, com.histmersion.domain.IngestBacklog.DrainLimit(10)).map(_.id),
      List(1L, 5L),
      "a watcher missing (paused, auto-ingest off) drains nothing"
    )
    assertEquals(drain(es, left, Some(com.histmersion.domain.IngestBacklog.SharedAllowance(0)), now, com.histmersion.domain.IngestBacklog.DrainLimit(10)), Nil)
    assertEquals(drain(es, left, None, now.plusSeconds(7200), com.histmersion.domain.IngestBacklog.DrainLimit(10)).map(_.id), List(3L, 1L, 2L, 4L, 5L), "c is due later")

  test(
    "a pick not ingested now: nothing new when the corpus or the acquisition list has it, the open entry joined, a settled one respected"
  ):
    assertEquals(place(Some(com.histmersion.domain.library.Citekey("ck")), Some(com.histmersion.domain.IngestBacklog.Placement.JoinAcquire.Item(7)), Some(com.histmersion.domain.IngestBacklog.Placement.JoinBacklog.Item(9L)), None), Placement.InCorpus(com.histmersion.domain.library.Citekey("ck")))
    assertEquals(place(None, Some(com.histmersion.domain.IngestBacklog.Placement.JoinAcquire.Item(7)), Some(com.histmersion.domain.IngestBacklog.Placement.JoinBacklog.Item(9L)), None), Placement.JoinAcquire(com.histmersion.domain.IngestBacklog.Placement.JoinAcquire.Item(7)))
    assertEquals(place(None, None, Some(com.histmersion.domain.IngestBacklog.Placement.JoinBacklog.Item(9L)), Some(com.histmersion.domain.IngestBacklog.Placement.SettledBefore.Item(3L))), Placement.JoinBacklog(com.histmersion.domain.IngestBacklog.Placement.JoinBacklog.Item(9L)))
    assertEquals(place(None, None, None, Some(com.histmersion.domain.IngestBacklog.Placement.SettledBefore.Item(3L))), Placement.SettledBefore(com.histmersion.domain.IngestBacklog.Placement.SettledBefore.Item(3L)))
    assertEquals(place(None, None, None, None), Placement.New)

  test(
    "a failed ingest: no legal copy → the acquisition list at once; another failure → again after the backoff, the list after max-failures"
  ):
    assert(isNoLegalCopy(com.histmersion.domain.IngestBacklog.Problem("no legal open-access copy of 10.1/x: nothing found")))
    assert(!isNoLegalCopy(com.histmersion.domain.IngestBacklog.Problem("HTTP 503 from the converter")))
    onFailure(com.histmersion.domain.IngestBacklog.Problem("no legal open-access copy of 10.1/x"), com.histmersion.domain.IngestBacklog.Failure.Retry.Failures(0), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxFailures(3), com.histmersion.domain.ManagerPolicy.ReviewSettings.BackoffMinutes(15), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxBackoffMinutes(360), now) match
      case Failure.ToAcquire(why) => assert(why.startsWith(NoLegalCopy), why)
      case other                  => fail(s"expected the acquisition list, got $other")
    assertEquals(onFailure(com.histmersion.domain.IngestBacklog.Problem("timeout"), com.histmersion.domain.IngestBacklog.Failure.Retry.Failures(0), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxFailures(3), com.histmersion.domain.ManagerPolicy.ReviewSettings.BackoffMinutes(15), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxBackoffMinutes(360), now), Failure.Retry(now.plusSeconds(15 * 60), com.histmersion.domain.IngestBacklog.Failure.Retry.Failures(1), com.histmersion.domain.IngestBacklog.Failure.Why("timeout")))
    assertEquals(onFailure(com.histmersion.domain.IngestBacklog.Problem("timeout"), com.histmersion.domain.IngestBacklog.Failure.Retry.Failures(1), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxFailures(3), com.histmersion.domain.ManagerPolicy.ReviewSettings.BackoffMinutes(15), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxBackoffMinutes(360), now), Failure.Retry(now.plusSeconds(30 * 60), com.histmersion.domain.IngestBacklog.Failure.Retry.Failures(2), com.histmersion.domain.IngestBacklog.Failure.Why("timeout")))
    onFailure(com.histmersion.domain.IngestBacklog.Problem("timeout"), com.histmersion.domain.IngestBacklog.Failure.Retry.Failures(2), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxFailures(3), com.histmersion.domain.ManagerPolicy.ReviewSettings.BackoffMinutes(15), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxBackoffMinutes(360), now) match
      case Failure.ToAcquire(why) => assertEquals(why, "timeout (3 tries)")
      case other                  => fail(s"expected the acquisition list, got $other")
    assert(onFailure(com.histmersion.domain.IngestBacklog.Problem(""), com.histmersion.domain.IngestBacklog.Failure.Retry.Failures(0), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxFailures(3), com.histmersion.domain.ManagerPolicy.ReviewSettings.BackoffMinutes(15), com.histmersion.domain.ManagerPolicy.ReviewSettings.MaxBackoffMinutes(360), now).isInstanceOf[Failure.Retry])

  test("days to drain: 0 = today, the shared cap truncates a day, None when nothing can drain"):
    assertEquals(daysToDrain(Nil, None, None), Some(0))
    assertEquals(daysToDrain(List(Lane(com.histmersion.domain.IngestBacklog.Lane.Backlog(3), com.histmersion.domain.IngestBacklog.Lane.PerDay(5), com.histmersion.domain.IngestBacklog.Lane.LeftToday(5))), None, None), Some(0), "today's allowance takes it all")
    assertEquals(daysToDrain(List(Lane(com.histmersion.domain.IngestBacklog.Lane.Backlog(10), com.histmersion.domain.IngestBacklog.Lane.PerDay(5), com.histmersion.domain.IngestBacklog.Lane.LeftToday(0))), None, None), Some(2), "nothing left today, 5 a day")
    assertEquals(daysToDrain(List(Lane(com.histmersion.domain.IngestBacklog.Lane.Backlog(10), com.histmersion.domain.IngestBacklog.Lane.PerDay(5), com.histmersion.domain.IngestBacklog.Lane.LeftToday(2))), None, None), Some(2), "2 today, 5, then 3")
    // 96 works over 35 watchers of 5 a day each, 20 a day in all, today's 20 spent: ~5 more days
    val lanes = List.fill(26)(Lane(com.histmersion.domain.IngestBacklog.Lane.Backlog(3), com.histmersion.domain.IngestBacklog.Lane.PerDay(5), com.histmersion.domain.IngestBacklog.Lane.LeftToday(0))) ++ List.fill(9)(Lane(2, 5, 0))
    assertEquals(lanes.map(_.backlog).sum, 96)
    assertEquals(daysToDrain(lanes, Some(com.histmersion.domain.IngestBacklog.Lane.PerDay(20)), Some(com.histmersion.domain.IngestBacklog.Lane.LeftToday(0))), Some(5))
    assertEquals(daysToDrain(lanes, None, None), Some(1), "no shared cap: every watcher drains its 2–3 tomorrow")
    assertEquals(daysToDrain(List(Lane(com.histmersion.domain.IngestBacklog.Lane.Backlog(4), com.histmersion.domain.IngestBacklog.Lane.PerDay(0), com.histmersion.domain.IngestBacklog.Lane.LeftToday(0))), None, None), None, "a cap of 0 never drains")
    assertEquals(daysToDrain(List(Lane(com.histmersion.domain.IngestBacklog.Lane.Backlog(4), com.histmersion.domain.IngestBacklog.Lane.PerDay(5), com.histmersion.domain.IngestBacklog.Lane.LeftToday(5))), Some(com.histmersion.domain.IngestBacklog.Lane.PerDay(0)), Some(com.histmersion.domain.IngestBacklog.Lane.LeftToday(0))), None, "a shared cap of 0 never drains")

  test(
    "the repair: a legal open-access item moves (its watcher auto-ingests), one in the corpus is removed, the rest stay"
  ):
    val f = RepairFacts(com.histmersion.domain.IngestBacklog.RepairFacts.Item(7), com.histmersion.domain.library.Doi("10.1/x"), isOa = com.histmersion.domain.IngestBacklog.RepairFacts.IsOa(true), com.histmersion.domain.IngestBacklog.RepairFacts.Watcher("watch:g"), watcherExists = com.histmersion.domain.IngestBacklog.RepairFacts.WatcherExists(true), watcherAutoIngest = com.histmersion.domain.IngestBacklog.RepairFacts.WatcherAutoIngest(true), None)
    assertEquals(repairAction(f), RepairAction.Move)
    assertEquals(repairAction(f.modify(_.inCorpus).setTo(Some("ck"))), RepairAction.Remove(com.histmersion.domain.library.Citekey("ck")))
    assert(repairAction(f.modify(_.isOa).setTo(false)).isInstanceOf[RepairAction.Keep], "a closed work stays on the list")
    assert(
      repairAction(f.modify(_.isOa).setTo(false).modify(_.inCorpus).setTo(Some("ck"))).isInstanceOf[RepairAction.Keep],
      "closed: never touched"
    )
    assert(repairAction(f.modify(_.watcherAutoIngest).setTo(false)) match
      case RepairAction.Keep(why) => why.contains("does not auto-ingest")
      case _                      => false)
    assert(repairAction(f.modify(_.watcherExists).setTo(false)).isInstanceOf[RepairAction.Keep])

  test("the backlog's wire state and its few words"):
    val st =
      ItemState(oa = Some(OaWork(com.histmersion.api.library.Doi("10.1/x"), com.histmersion.api.library.Title("X"), Some(com.histmersion.api.catalogue.Year(2020)), List(com.histmersion.api.library.AuthorName("A")), None, List(com.histmersion.api.automation.Goal.Slug("g1"), com.histmersion.api.automation.Goal.Slug("g2")), com.histmersion.api.automation.Managers.OaWork.Why("fits"), Some(com.histmersion.api.automation.Managers.OaWork.FoundBy("run")))))
    assertEquals(io.circe.parser.decode[ItemState](io.circe.syntax.EncoderOps(st).asJson.noSpaces), Right(st))
    assertEquals(
      io.circe.parser.decode[ItemState]("""{"history":[]}"""),
      Right(ItemState()),
      "an old item's state still decodes"
    )
    assertEquals(ManagersMarkdown.backlog(com.histmersion.api.automation.Managers.ManagerInfo.Backlog(0), None), "0 legal open-access works in the ingest backlog")
    assertEquals(
      ManagersMarkdown.backlog(com.histmersion.api.automation.Managers.ManagerInfo.Backlog(96), Some(com.histmersion.api.automation.Managers.ManagerInfo.BacklogDays(5))),
      "96 legal open-access works in the ingest backlog (~5 more days at the current allowance)"
    )
    assertEquals(
      ManagersMarkdown.backlog(com.histmersion.api.automation.Managers.ManagerInfo.Backlog(1), Some(com.histmersion.api.automation.Managers.ManagerInfo.BacklogDays(0))),
      "1 legal open-access work in the ingest backlog (drained today at the current allowance)"
    )
    assert(ManagersMarkdown.backlog(com.histmersion.api.automation.Managers.ManagerInfo.Backlog(3), None).contains("not draining"))
    val info = ManagerInfo(com.histmersion.api.automation.Managers.ManagerInfo.Id("watch:g"), com.histmersion.api.automation.Managers.ManagerInfo.Kind("watch"), backlog = 4, backlogDays = Some(1))
    assert(
      ManagersMarkdown.line(info).contains("4 legal open-access works in the ingest backlog (~1 more day"),
      ManagersMarkdown.line(info)
    )
    assert(!ManagersMarkdown.line(info.modify(_.backlog).setTo(0)).contains("backlog"))
    val r = RepairAcquire.RepairResult(
      dryRun = com.histmersion.api.library.RepairAcquire.RepairResult.DryRun(true),
      moved = com.histmersion.api.library.RepairAcquire.RepairResult.Moved(1),
      kept = 1,
      untouched = 5,
      rows = List(
        RepairAcquire.RepairRow(com.histmersion.api.library.RepairAcquire.RepairRow.Item(7), com.histmersion.api.library.Doi("10.1/x"), List(com.histmersion.api.library.RepairAcquire.RepairRow.Goals("g")), com.histmersion.api.library.RepairAcquire.RepairRow.Watcher("watch:g"), RepairAcquire.Move, com.histmersion.api.library.RepairAcquire.RepairRow.Detail("legal open access")),
        RepairAcquire.RepairRow(com.histmersion.api.library.RepairAcquire.RepairRow.Item(8), com.histmersion.api.library.Doi("10.1/y"), List(com.histmersion.api.library.RepairAcquire.RepairRow.Goals("g")), com.histmersion.api.library.RepairAcquire.RepairRow.Watcher("watch:g"), RepairAcquire.Keep, com.histmersion.api.library.RepairAcquire.RepairRow.Detail("closed"))
      ),
      backlog = 1,
      backlogDays = Some(1)
    )
    val md = RepairAcquire.markdown(r)
    assert(
      md.contains("dry run: nothing changed") && md.contains("would move 1 legal open-access work") &&
        md.contains("#7 https://doi.org/10.1/x · goal g · would move to watch:g") && md.contains(
          "#8 https://doi.org/10.1/y · goal g · keep: closed"
        ) &&
        md.contains("5 open acquisition items not created by a watcher's sweep: untouched"),
      md
    )
