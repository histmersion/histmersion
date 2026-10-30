package com.histmersion.web

import com.histmersion.api.Activity
import com.histmersion.api.Activity.Entry

/** The activity list's pure parts: the chips, a row's first words and its controls, the page's query. */
class ActivityViewSuite extends munit.FunSuite:

  test("Activity's chips are the merged list's filters, in the mockup's order; the detailed lists are not chips"):
    assertEquals(
      ActivityTab.values.toList.filter(_.isChip).map(_.label),
      List("Everything", "Running now", "Agent work", "Background jobs", "Your actions", "Problems")
    )
    assertEquals(ActivityTab.values.toList.filter(_.isChip).flatMap(_.show), Activity.Show.values.toList)
    assertEquals(ActivityTab.values.toList.filterNot(_.isChip).map(_.slug), List("runs", "all-jobs", "worklog"))

  private def run(state: String) =
    Entry("run:r1", "run", "2026-10-30T12:00:00Z", state = state, what = "review-article", job = Some("r1"))
  private def job(kind: String, state: String) =
    Entry("job:j1", "job", "2026-10-30T12:00:00Z", state = state, what = kind, job = Some("j1"))

  test("a row's first words say what is going on, or what it was and when"):
    assertEquals(ActivityView.when(run("running"), "12:40"), "Running")
    assertEquals(ActivityView.when(job("media-import", "done"), "12:40"), "Done · 12:40")
    assertEquals(ActivityView.when(job("media-import", "failed"), "11:02"), "Problem · 11:02")
    assertEquals(ActivityView.when(job("media-import", "paused"), "11:02"), "Paused · 11:02")
    assertEquals(ActivityView.when(Entry("log:1", "log", "x"), "09:12"), "Note · 09:12")
    assertEquals(
      ActivityView.when(Entry("decision:1", "decision", "x", state = "approved"), "09:12"),
      "You decided · 09:12"
    )
    assert(ActivityView.problem(job("media-import", "failed")) && !ActivityView.problem(run("running")))

  test("Pause and Cancel in place: agent runs and the long background jobs, by their state; nothing else"):
    assertEquals(ActivityView.actions(run("running")), List("pause", "cancel"))
    assertEquals(ActivityView.actions(run("paused")), List("resume", "cancel"))
    assertEquals(ActivityView.actions(run("waiting")), List("cancel"))
    assertEquals(ActivityView.actions(run("done")), Nil)
    assertEquals(ActivityView.actions(job("media-import", "running")), List("pause", "cancel"))
    assertEquals(ActivityView.actions(job("basemap-fetch", "paused")), List("resume", "cancel"))
    assertEquals(ActivityView.actions(job("ingest", "running")), Nil, "an OCR runs as one piece")
    assertEquals(ActivityView.actions(Entry("log:1", "log", "x", job = Some("r1"))), Nil)

  test("the page's goal rides in its query, kept from chip to chip"):
    assertEquals(ActivityView.search(Some("bronze age")), "?goal=bronze%20age")
    assertEquals(ActivityView.search(None), "")
    assertEquals(ActivityView.param("?item=3&goal=bronze%20age", "goal"), Some("bronze age"))
    assertEquals(ActivityView.param("?goal=", "goal"), None)
    assertEquals(ActivityView.param("", "goal"), None)
