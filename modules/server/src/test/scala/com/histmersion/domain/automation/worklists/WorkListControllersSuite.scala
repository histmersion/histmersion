package com.histmersion
package domain.automation.worklists

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.automation.Queue.{Kind, QueueAdd, QueuePatch}
import com.histmersion.util.data.maintenance.WriteGate

import java.time.{Clock, Instant, ZoneOffset}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.automation.Queue
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{LintRule, tolerated}

/** The work lists' controllers over the in-memory kernel: the list parsed from its word, the inputs checked, a log item
  * dated by the clock it is given.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class WorkListControllersSuite extends munit.FunSuite:
  private val clock = Clock.fixed(Instant.parse("2026-10-30T23:59:59Z"), ZoneOffset.UTC)
  private def controllers() =
    WorkListControllers(QueueService.InMemory(AtomicReference(WorkListState.empty), clock), WriteGate(), clock)

  test("a log item with no topic joins the entry of the clock's UTC day; another list's stays without one"):
    val c = controllers()
    assertEquals(
      c.addOverRest(WorkListControllers.ListName("log"), QueueAdd(Queue.QueueItem.Text("ran the lint"))).map(_.topic),
      Right(Some("2026-10-30"))
    )
    assertEquals(
      c.addOverRest(
        WorkListControllers.ListName("log"),
        QueueAdd(Queue.QueueItem.Text("named"), topic = Some(Queue.QueueItem.Topic("Later")))
      ).map(_.topic),
      Right(Some("Later"))
    )
    assertEquals(
      c.addOverRest(WorkListControllers.ListName("tasks"), QueueAdd(Queue.QueueItem.Text("read Cline"))).map(_.topic),
      Right(None)
    )

  test("an unknown list, an empty text, a bad status or instant are refused with today's messages"):
    val c = controllers()
    assertEquals(
      c.addOverRest(WorkListControllers.ListName("notes"), QueueAdd(Queue.QueueItem.Text("x")))
        .left
        .map(_.problems.head)
        .left
        .map(_.sentence),
      Left("unknown queue list 'notes' — one of: acquire, tasks, log")
    )
    assertEquals(
      c.addOverRest(WorkListControllers.ListName("tasks"), QueueAdd(Queue.QueueItem.Text("  ")))
        .left
        .map(_.problems.head)
        .left
        .map(_.sentence),
      Left("queue item text is empty")
    )
    assert(
      c.addOverRest(
        WorkListControllers.ListName("tasks"),
        QueueAdd(Queue.QueueItem.Text("x"), status = Some(Queue.QueueItem.Status("maybe")))
      ).left
        .exists(_.problems.sentences.mkString("; ").contains("unknown queue item status 'maybe'"))
    )
    assert(
      c.addOverRest(
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

  test("a REST add records its surface; an MCP add needs createdBy and records the workflow; a partial one is refused"):
    val c = controllers()
    val byCli = c.addOverRest(
      WorkListControllers.ListName("tasks"),
      QueueAdd(Queue.QueueItem.Text("from the cli"), via = Some(Creation.Cli))
    )
    assertEquals(byCli.map(i => (i.creator.flatMap(_.surface), i.actor)), Right((Some("cli"), Some("person"))))
    val byWeb = c.addOverRest(
      WorkListControllers.ListName("tasks"),
      QueueAdd(Queue.QueueItem.Text("web"), via = Some(Creation.Web))
    )
    assertEquals(byWeb.map(_.creator.flatMap(_.surface)), Right(Some("web")))
    val plain = c.addOverRest(WorkListControllers.ListName("tasks"), QueueAdd(Queue.QueueItem.Text("plain")))
    assertEquals(plain.map(_.creator.flatMap(_.surface)), Right(Some("rest")))
    assert(c.addOverMcp(WorkListControllers.ListName("tasks"), QueueAdd(Queue.QueueItem.Text("anonymous"))).isLeft)
    val named = Creation.By(Some(Creation.Plugin("research")), Some(Creation.Workflow("trailhead")))
    val byAgent = c.addOverMcp(
      WorkListControllers.ListName("tasks"),
      QueueAdd(Queue.QueueItem.Text("from an agent"), createdBy = Some(named))
    )
    assertEquals(
      byAgent.map(i => (i.creator.flatMap(_.workflow), i.creator.flatMap(_.agent).map(_.kind), i.actor)),
      Right((Some("trailhead"), Some("code"), Some("agents")))
    )
    val partial = Creation.By(Some(Creation.Plugin("research")), None)
    assert(
      c.addOverRest(
        WorkListControllers.ListName("tasks"),
        QueueAdd(Queue.QueueItem.Text("x"), createdBy = Some(partial))
      ).isLeft
    )
    val added = plain.toOption.get
    assert(
      c.patch(QueueItemId(7), QueuePatch(actor = Some(Creation.Actor("robot")))).left.exists(_.code.isEmpty),
      "an actor that is neither is refused before the item is looked up"
    )
    assertEquals(added.actor, Some(Creation.Person))

  test("an import counts the items and the notes apart; the export renders what was imported"):
    val c = controllers()
    val file = Queue.QueueItem.Text("# TASKS\n\n## Backlog\n- [ ] read Cline\n- [x] read Kuhrt\n")
    val r =
      c.importText(Kind.Label("tasks"), Queue.QueueImport(file))
        .fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals((r.list, r.items, r.replaced), ("tasks", 2, 0))
    assertEquals(c.exportText(Kind.Label("tasks")).map(_.value.contains("- [x] read Kuhrt")), Right(true))
    assertEquals(c.files().map(_._1), Kind.values.toList.map(_.file))
