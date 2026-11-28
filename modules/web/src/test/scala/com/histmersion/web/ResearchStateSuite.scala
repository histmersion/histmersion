package com.histmersion
package web

import com.histmersion.api.automation.queue.QueueItem

import com.histmersion.api.automation.Queue
import com.histmersion.api.research.OpenWork
import com.histmersion.api.research.OpenWork.{OpenItem, Terms, Verdict, Who}
import com.histmersion.api.automation.Creation

class ResearchStateSuite extends munit.FunSuite:
  private def acquisition(id: QueueItem.Id, text: QueueItem.Text, v: Verdict): OpenItem.Acquisition =
    OpenItem.Acquisition(id, text, v, None, Terms.Tries(1), None, None)
  private def task(actor: Who): OpenItem.Task =
    OpenItem.Task(QueueItem.Id(9), QueueItem.Text("Read the review\nsecond line"), actor, None, None)
  private val state = OpenWork.State(
    OpenWork.Totals(Terms.Count(2), Terms.Count(1)),
    Vector(
      acquisition(QueueItem.Id(1), QueueItem.Text("Anthony 2007, The Horse"), Verdict.Borrow),
      acquisition(QueueItem.Id(2), QueueItem.Text("Kristiansen 2005"), Verdict.NoIdentifier),
      task(Who.Agents)
    ),
    Terms.At("2026-11-23T12:00:00Z")
  )

  test("the text filter keeps the items whose line mentions the words, case-insensitively; the totals stay"):
    assertEquals(ResearchState.shown(state, "HORSE").items.map(ResearchState.key), Vector("acquisition-1"))
    assertEquals(ResearchState.shown(state, "  ").items.size, 3)
    assertEquals(ResearchState.shown(state, "horse").totals, state.totals)

  test("the acquisitions table lists the acquisitions only"):
    assertEquals(ResearchState.acquisitions(state).map(_.item), Vector(QueueItem.Id(1), QueueItem.Id(2)))

  test("a task's toggle hands it to the other side; a row shows the text's first line"):
    assertEquals(ResearchState.handedTo(task(Who.Owner)), Creation.Agents)
    assertEquals(ResearchState.handedTo(task(Who.Agents)), Creation.Person)
    assertEquals(ResearchState.firstLine(task(Who.Owner).text), "Read the review")
    assertEquals(ResearchState.key(task(Who.Owner)), "task-9")
