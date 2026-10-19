package com.histmersion.core

import com.histmersion.core.IngestBacklog.*
import com.histmersion.core.Managers.*

import java.time.Instant

/** The watchers' ingest backlog, pure: a sweep's allowance after the backlog, the drain's order and caps,
  * where a pick goes when it is not ingested, what a failed ingest leads to, the days to drain, and the
  * repair's decision per acquisition item. */
class IngestBacklogSuite extends munit.FunSuite:

  private val now = Instant.parse("2026-10-19T12:00:00Z")

  test("a sweep's new finds get what the backlog leaves of the allowance: its own backlog under its cap, every backlog under the shared one"):
    assertEquals(sweepAllowance(own = 5, shared = None, ownBacklog = 0, allBacklog = 0), 5)
    assertEquals(sweepAllowance(5, None, ownBacklog = 3, allBacklog = 40), 2, "no shared cap: only its own backlog counts")
    assertEquals(sweepAllowance(5, Some(20), ownBacklog = 0, allBacklog = 18), 2, "the shared cap after every backlog")
    assertEquals(sweepAllowance(5, Some(20), ownBacklog = 5, allBacklog = 5), 0, "its own backlog fills its cap: nothing new today")
    assertEquals(sweepAllowance(5, Some(20), ownBacklog = 9, allBacklog = 96), 0, "never negative")
    assertEquals(sweepAllowance(20, Some(20), 0, 0), 20)
    assertEquals(sweepAllowance(0, Some(20), 0, 0), 0)

  test("the drain: due items only, highest priority then the oldest, within each watcher's own cap and the shared one, at most max"):
    val es = List(
      Entry(5, "watch:a", 0, None), Entry(1, "watch:a", 0, None), Entry(2, "watch:b", 0, None),
      Entry(3, "watch:b", 7, None), Entry(4, "watch:c", 0, Some(now.plusSeconds(3600))), Entry(6, "watch:a", 0, None))
    val left = Map("watch:a" -> 2, "watch:b" -> 5, "watch:c" -> 5)
    assertEquals(drain(es, left, None, now, 10).map(_.id), List(3L, 1L, 2L, 5L), "priority 7 first; a's cap stops #6; c not due")
    assertEquals(drain(es, left, Some(2), now, 10).map(_.id), List(3L, 1L), "the shared cap")
    assertEquals(drain(es, left, None, now, 1).map(_.id), List(3L), "one tick's share")
    assertEquals(drain(es, left - "watch:b", None, now, 10).map(_.id), List(1L, 5L), "a watcher missing (paused, auto-ingest off) drains nothing")
    assertEquals(drain(es, left, Some(0), now, 10), Nil)
    assertEquals(drain(es, left, None, now.plusSeconds(7200), 10).map(_.id), List(3L, 1L, 2L, 4L, 5L), "c is due later")

  test("a pick not ingested now: nothing new when the corpus or the acquisition list has it, the open entry joined, a settled one respected"):
    assertEquals(place(Some("ck"), Some(7), Some(9L), None), Placement.InCorpus("ck"))
    assertEquals(place(None, Some(7), Some(9L), None), Placement.JoinAcquire(7))
    assertEquals(place(None, None, Some(9L), Some(3L)), Placement.JoinBacklog(9L))
    assertEquals(place(None, None, None, Some(3L)), Placement.SettledBefore(3L))
    assertEquals(place(None, None, None, None), Placement.New)

  test("a failed ingest: no legal copy → the acquisition list at once; another failure → again after the backoff, the list after max-failures"):
    assert(isNoLegalCopy("no legal open-access copy of 10.1/x: nothing found"))
    assert(!isNoLegalCopy("HTTP 503 from the converter"))
    onFailure("no legal open-access copy of 10.1/x", 0, 3, 15, 360, now) match
      case Failure.ToAcquire(why) => assert(why.startsWith(NoLegalCopy), why)
      case other                  => fail(s"expected the acquisition list, got $other")
    assertEquals(onFailure("timeout", 0, 3, 15, 360, now), Failure.Retry(now.plusSeconds(15 * 60), 1, "timeout"))
    assertEquals(onFailure("timeout", 1, 3, 15, 360, now), Failure.Retry(now.plusSeconds(30 * 60), 2, "timeout"))
    onFailure("timeout", 2, 3, 15, 360, now) match
      case Failure.ToAcquire(why) => assertEquals(why, "timeout (3 tries)")
      case other                  => fail(s"expected the acquisition list, got $other")
    assert(onFailure("", 0, 3, 15, 360, now).isInstanceOf[Failure.Retry])

  test("days to drain: 0 = today, the shared cap truncates a day, None when nothing can drain"):
    assertEquals(daysToDrain(Nil, None, None), Some(0))
    assertEquals(daysToDrain(List(Lane(3, 5, 5)), None, None), Some(0), "today's allowance takes it all")
    assertEquals(daysToDrain(List(Lane(10, 5, 0)), None, None), Some(2), "nothing left today, 5 a day")
    assertEquals(daysToDrain(List(Lane(10, 5, 2)), None, None), Some(2), "2 today, 5, then 3")
    // 96 works over 35 watchers of 5 a day each, 20 a day in all, today's 20 spent: ~5 more days
    val lanes = List.fill(26)(Lane(3, 5, 0)) ++ List.fill(9)(Lane(2, 5, 0))
    assertEquals(lanes.map(_.backlog).sum, 96)
    assertEquals(daysToDrain(lanes, Some(20), Some(0)), Some(5))
    assertEquals(daysToDrain(lanes, None, None), Some(1), "no shared cap: every watcher drains its 2–3 tomorrow")
    assertEquals(daysToDrain(List(Lane(4, 0, 0)), None, None), None, "a cap of 0 never drains")
    assertEquals(daysToDrain(List(Lane(4, 5, 5)), Some(0), Some(0)), None, "a shared cap of 0 never drains")

  test("the repair: a legal open-access item moves (its watcher auto-ingests), one in the corpus is removed, the rest stay"):
    val f = RepairFacts(7, "10.1/x", isOa = true, "watch:g", watcherExists = true, watcherAutoIngest = true, None)
    assertEquals(repairAction(f), RepairAction.Move)
    assertEquals(repairAction(f.copy(inCorpus = Some("ck"))), RepairAction.Remove("ck"))
    assert(repairAction(f.copy(isOa = false)).isInstanceOf[RepairAction.Keep], "a closed work stays on the list")
    assert(repairAction(f.copy(isOa = false, inCorpus = Some("ck"))).isInstanceOf[RepairAction.Keep], "closed: never touched")
    assert(repairAction(f.copy(watcherAutoIngest = false)) match
      case RepairAction.Keep(why) => why.contains("does not auto-ingest")
      case _                      => false)
    assert(repairAction(f.copy(watcherExists = false)).isInstanceOf[RepairAction.Keep])

  test("the backlog's wire state and its few words"):
    val st = ItemState(oa = Some(OaWork("10.1/x", "X", Some(2020), List("A"), None, List("g1", "g2"), "fits", Some("run"))))
    assertEquals(io.circe.parser.decode[ItemState](io.circe.syntax.EncoderOps(st).asJson.noSpaces), Right(st))
    assertEquals(io.circe.parser.decode[ItemState]("""{"history":[]}"""), Right(ItemState()), "an old item's state still decodes")
    assertEquals(ManagersMarkdown.backlog(0, None), "0 legal open-access works in the ingest backlog")
    assertEquals(ManagersMarkdown.backlog(96, Some(5)), "96 legal open-access works in the ingest backlog (~5 more days at the current allowance)")
    assertEquals(ManagersMarkdown.backlog(1, Some(0)), "1 legal open-access work in the ingest backlog (drained today at the current allowance)")
    assert(ManagersMarkdown.backlog(3, None).contains("not draining"))
    val info = ManagerInfo("watch:g", "watch", backlog = 4, backlogDays = Some(1))
    assert(ManagersMarkdown.line(info).contains("4 legal open-access works in the ingest backlog (~1 more day"), ManagersMarkdown.line(info))
    assert(!ManagersMarkdown.line(info.copy(backlog = 0)).contains("backlog"))
    val r = RepairAcquire.RepairResult(dryRun = true, moved = 1, kept = 1, untouched = 5,
      rows = List(RepairAcquire.RepairRow(7, "10.1/x", List("g"), "watch:g", RepairAcquire.Move, "legal open access"),
        RepairAcquire.RepairRow(8, "10.1/y", List("g"), "watch:g", RepairAcquire.Keep, "closed")), backlog = 1, backlogDays = Some(1))
    val md = RepairAcquire.markdown(r)
    assert(md.contains("dry run: nothing changed") && md.contains("would move 1 legal open-access work") &&
      md.contains("#7 https://doi.org/10.1/x · goal g · would move to watch:g") && md.contains("#8 https://doi.org/10.1/y · goal g · keep: closed") &&
      md.contains("5 open acquisition items not created by a watcher's sweep: untouched"), md)
