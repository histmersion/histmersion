package com.histmersion
package web

import com.histmersion.api.automation.activity.Show
import com.histmersion.api.automation.activity.Entry

import com.histmersion.api.Api.JobControl
import com.histmersion.api.automation.Activity
import com.histmersion.api.SpatialHierarchyResult
import com.histmersion.api.automation.Goal

/** The activity list's pure parts: the chips, a row's first words and its controls, the page's query. */
class ActivityViewSuite extends munit.FunSuite:

  test("Activity's chips are the merged list's filters, in the mockup's order; the detailed lists are not chips"):
    assertEquals(
      ActivityTab.values.toList.filter(_.isChip).map(_.label),
      List("Everything", "Running now", "Agent work", "Background jobs", "Your actions", "Problems")
    )
    assertEquals(ActivityTab.values.toList.filter(_.isChip).flatMap(_.show), Show.values.toList)
    assertEquals(ActivityTab.values.toList.filterNot(_.isChip).map(_.slug), List("runs", "all-jobs", "worklog"))

  private def run(state: Entry.State) =
    Entry(
      Entry.Key("run:r1"),
      Entry.Kind("run"),
      Entry.At("2026-10-30T12:00:00Z"),
      state = state,
      what = Entry.What("review-article"),
      job = Some(SpatialHierarchyResult.Job.Id("r1"))
    )
  private def job(kind: Entry.What, state: Entry.State) =
    Entry(
      Entry.Key("job:j1"),
      Entry.Kind("job"),
      Entry.At("2026-10-30T12:00:00Z"),
      state = state,
      what = kind,
      job = Some(SpatialHierarchyResult.Job.Id("j1"))
    )

  test("a row's first words say what is going on, or what it was and when"):
    assertEquals(ActivityView.when(run(Entry.State("running")), "12:40"), "Running")
    assertEquals(ActivityView.when(job(Entry.What("media-import"), Entry.State("done")), "12:40"), "Done · 12:40")
    assertEquals(ActivityView.when(job(Entry.What("media-import"), Entry.State("failed")), "11:02"), "Problem · 11:02")
    assertEquals(ActivityView.when(job(Entry.What("media-import"), Entry.State("paused")), "11:02"), "Paused · 11:02")
    assertEquals(
      ActivityView.when(Entry(Entry.Key("log:1"), Entry.Kind("log"), Entry.At("x")), "09:12"),
      "Note · 09:12"
    )
    assertEquals(
      ActivityView.when(
        Entry(Entry.Key("decision:1"), Entry.Kind("decision"), Entry.At("x"), state = Entry.State("approved")),
        "09:12"
      ),
      "You decided · 09:12"
    )
    assert(
      ActivityView.problem(job(Entry.What("media-import"), Entry.State("failed"))) &&
        !ActivityView.problem(run(Entry.State("running")))
    )

  test("Pause and Cancel in place: agent runs and the long background jobs, by their state; nothing else"):
    assertEquals(
      ActivityView.actions(run(Entry.State("running"))),
      List(JobControl.Action("pause"), JobControl.Action("cancel"))
    )
    assertEquals(
      ActivityView.actions(run(Entry.State("paused"))),
      List(JobControl.Action("resume"), JobControl.Action("cancel"))
    )
    assertEquals(ActivityView.actions(run(Entry.State("waiting"))), List(JobControl.Action("cancel")))
    assertEquals(ActivityView.actions(run(Entry.State("done"))), Nil)
    assertEquals(
      ActivityView.actions(job(Entry.What("media-import"), Entry.State("running"))),
      List(JobControl.Action("pause"), JobControl.Action("cancel"))
    )
    assertEquals(
      ActivityView.actions(job(Entry.What("basemap-fetch"), Entry.State("paused"))),
      List(JobControl.Action("resume"), JobControl.Action("cancel"))
    )
    assertEquals(
      ActivityView.actions(job(Entry.What("ingest"), Entry.State("running"))),
      Nil,
      "an OCR runs as one piece"
    )
    assertEquals(
      ActivityView.actions(
        Entry(Entry.Key("log:1"), Entry.Kind("log"), Entry.At("x"), job = Some(SpatialHierarchyResult.Job.Id("r1")))
      ),
      Nil
    )

  test("the page's goal rides in its query, kept from chip to chip"):
    assertEquals(ActivityView.search(Some(Goal.Slug("bronze age"))), Pages.UrlQuery("?goal=bronze%20age"))
    assertEquals(ActivityView.search(None), Pages.UrlQuery(""))
    assertEquals(
      ActivityView.param(Pages.UrlQuery("?item=3&goal=bronze%20age"), ActivityView.ParamName("goal")),
      Some(ActivityView.ParamText("bronze age"))
    )
    assertEquals(ActivityView.param(Pages.UrlQuery("?goal="), ActivityView.ParamName("goal")), None)
    assertEquals(ActivityView.param(Pages.UrlQuery(""), ActivityView.ParamName("goal")), None)
