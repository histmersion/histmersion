package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.automation.Activity
import com.histmersion.api.automation.Activity.Entry

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
    Entry(com.histmersion.api.automation.Activity.Entry.Key("run:r1"), com.histmersion.api.automation.Activity.Entry.Kind("run"), com.histmersion.api.automation.Activity.Entry.At("2026-10-30T12:00:00Z"), state = state, what = com.histmersion.api.automation.Activity.Entry.What("review-article"), job = Some("r1"))
  private def job(kind: String, state: String) =
    Entry(com.histmersion.api.automation.Activity.Entry.Key("job:j1"), com.histmersion.api.automation.Activity.Entry.Kind("job"), com.histmersion.api.automation.Activity.Entry.At("2026-10-30T12:00:00Z"), state = state, what = kind, job = Some("j1"))

  test("a row's first words say what is going on, or what it was and when"):
    assertEquals(ActivityView.when(run("running"), "12:40"), "Running")
    assertEquals(ActivityView.when(job("media-import", "done"), "12:40"), "Done · 12:40")
    assertEquals(ActivityView.when(job("media-import", "failed"), "11:02"), "Problem · 11:02")
    assertEquals(ActivityView.when(job("media-import", "paused"), "11:02"), "Paused · 11:02")
    assertEquals(ActivityView.when(Entry(com.histmersion.api.automation.Activity.Entry.Key("log:1"), com.histmersion.api.automation.Activity.Entry.Kind("log"), com.histmersion.api.automation.Activity.Entry.At("x")), "09:12"), "Note · 09:12")
    assertEquals(
      ActivityView.when(Entry(com.histmersion.api.automation.Activity.Entry.Key("decision:1"), com.histmersion.api.automation.Activity.Entry.Kind("decision"), com.histmersion.api.automation.Activity.Entry.At("x"), state = com.histmersion.api.automation.Activity.Entry.State("approved")), "09:12"),
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
    assertEquals(ActivityView.actions(Entry(com.histmersion.api.automation.Activity.Entry.Key("log:1"), com.histmersion.api.automation.Activity.Entry.Kind("log"), com.histmersion.api.automation.Activity.Entry.At("x"), job = Some(com.histmersion.api.SpatialHierarchyResult.Job.Id("r1")))), Nil)

  test("the page's goal rides in its query, kept from chip to chip"):
    assertEquals(ActivityView.search(Some(com.histmersion.api.automation.Goal.Slug("bronze age"))), "?goal=bronze%20age")
    assertEquals(ActivityView.search(None), "")
    assertEquals(ActivityView.param(com.histmersion.web.Pages.UrlQuery("?item=3&goal=bronze%20age"), com.histmersion.web.ActivityView.ParamName("goal")), Some("bronze age"))
    assertEquals(ActivityView.param(com.histmersion.web.Pages.UrlQuery("?goal="), com.histmersion.web.ActivityView.ParamName("goal")), None)
    assertEquals(ActivityView.param(com.histmersion.web.Pages.UrlQuery(""), com.histmersion.web.ActivityView.ParamName("goal")), None)
