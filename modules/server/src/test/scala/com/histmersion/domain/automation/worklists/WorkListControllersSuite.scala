package com.histmersion
package domain.automation.worklists

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.automation.Queue.{Kind, QueueAdd, QueuePatch}
import com.histmersion.util.data.maintenance.WriteGate

import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.automation.Queue

/** The work lists' controllers over the in-memory kernel: the list parsed from its word, the inputs checked, a log item
  * dated by the clock it is given.
  */
class WorkListControllersSuite extends munit.FunSuite:
  private val clock = Clock.fixed(Instant.parse("2026-10-30T23:59:59Z"), ZoneOffset.UTC)
  private def controllers() =
    WorkListControllers(QueueService.InMemory(AtomicReference(WorkListState.empty), clock), WriteGate(), clock)

  test("a log item with no topic joins the entry of the clock's UTC day; another list's stays without one"):
    val c = controllers()
    assertEquals(
      c.add(WorkListControllers.ListName("log"), QueueAdd(Queue.QueueItem.Text("ran the lint"))).map(_.topic),
      Right(Some("2026-10-30"))
    )
    assertEquals(
      c.add(
        WorkListControllers.ListName("log"),
        QueueAdd(Queue.QueueItem.Text("named"), topic = Some(Queue.QueueItem.Topic("Later")))
      ).map(_.topic),
      Right(Some("Later"))
    )
    assertEquals(
      c.add(WorkListControllers.ListName("tasks"), QueueAdd(Queue.QueueItem.Text("read Cline"))).map(_.topic),
      Right(None)
    )

  test("an unknown list, an empty text, a bad status or instant are refused with today's messages"):
    val c = controllers()
    assertEquals(
      c.add(WorkListControllers.ListName("notes"), QueueAdd(Queue.QueueItem.Text("x")))
        .left
        .map(_.problems.head)
        .left
        .map(_.sentence),
      Left("unknown queue list 'notes' — one of: acquire, tasks, log")
    )
    assertEquals(
      c.add(WorkListControllers.ListName("tasks"), QueueAdd(Queue.QueueItem.Text("  ")))
        .left
        .map(_.problems.head)
        .left
        .map(_.sentence),
      Left("queue item text is empty")
    )
    assert(
      c.add(
        WorkListControllers.ListName("tasks"),
        QueueAdd(Queue.QueueItem.Text("x"), status = Some(Queue.QueueItem.Status("maybe")))
      ).left
        .exists(_.problems.sentences.mkString("; ").contains("unknown queue item status 'maybe'"))
    )
    assert(
      c.add(
        WorkListControllers.ListName("tasks"),
        QueueAdd(Queue.QueueItem.Text("x"), createdAt = Some(Queue.QueueItem.CreatedAt("yesterday")))
      ).left
        .exists(_.problems.sentences.mkString("; ").contains("'yesterday' is not a queue item creation time"))
    )
    assertEquals(
      c.patch(QueueItemId(42), QueuePatch(text = Some(Queue.QueueItem.Text(" "))))
        .left
        .map(_.problems.head)
        .left
        .map(_.sentence),
      Left("text is required")
    )
    assertEquals(
      c.delete(QueueItemId(42)).left.map(e => (e.problems.sentences.mkString("; "), e.code)),
      Left(("no queue item id '42'", Some("not_found")))
    )

  test("an import counts the items and the notes apart; the export renders what was imported"):
    val c = controllers()
    val file = Queue.QueueItem.Text("# TASKS\n\n## Backlog\n- [ ] read Cline\n- [x] read Kuhrt\n")
    val r =
      c.importText(Kind.Label("tasks"), Queue.QueueImport(file))
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((r.list, r.items, r.replaced), ("tasks", 2, 0))
    assertEquals(c.exportText(Kind.Label("tasks")).map(_.value.contains("- [x] read Kuhrt")), Right(true))
    assertEquals(c.files().map(_._1), Kind.values.toList.map(_.file))
