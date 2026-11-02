package com.histmersion.api

import com.histmersion.domain.automation.Pipeline
import com.histmersion.api.automation.Agents
import com.histmersion.api.exchange.{InitPlan, MergeApplyJob}
import com.histmersion.api.instance.UiNotices
import com.histmersion.api.jobs.JobState
import com.histmersion.api.library.{AcquireChecks, Ingest}
import com.histmersion.api.maintenance.Vacuum
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.automation.Manager
import com.histmersion.domain.automation.Run
import com.histmersion.domain.library.Source

/** The label dictionary covers every identifier the UI shows: a pipeline, an approval kind or a job kind added without
  * wording fails here, not in front of a reader.
  */
class WordingSuite extends munit.FunSuite:

  /** A label a person reads: sentence case, words, never the identifier it stands for. */
  private def readable(label: String, wire: String): Unit =
    assert(label.nonEmpty && label.head.isUpper, s"'$label' ($wire) is not sentence case")
    assert(label != wire, s"'$wire' is shown as itself")
    assert(
      !label.matches(""".*\b[a-z0-9]+[-_][a-z0-9]+([-_][a-z0-9]+)*\b.*""") || label.contains("-hour") || label.contains(
        "-day"
      ),
      s"'$label' ($wire) still reads as an identifier"
    )

  test("every pipeline has wording"):
    Pipeline.values.foreach(p => readable(Wording.pipeline(p), p.wire))
    assertEquals(Wording.pipeline(Pipeline.ReviewArticle), "Review an article")
    assertEquals(Wording.pipeline(Pipeline.EnrichSource), "Enrich a source")
    assertEquals(Wording.pipeline(Pipeline.AcquisitionSweep), "Look for copies to fetch")

  test("every approval kind has wording"):
    Approval.Kind.values.foreach(k => readable(Wording.approvalKind(k), k.wire))
    assertEquals(Wording.approvalKind(Approval.Kind.ArticlePublish), "Make an article readable")
    assertEquals(Wording.approvalKind(Approval.Kind.StatusChange), "Change a source's status")
    assertEquals(Wording.approvalKind(Approval.Kind.Edge), "Link two sources")

  test("every job kind has wording, and the kinds are distinct"):
    RegisteredJobKinds.all.kinds.foreach(k => readable(Wording.jobKind(k), k.value))
    assertEquals(RegisteredJobKinds.all.kinds.map(_.value).distinct.length, RegisteredJobKinds.all.kinds.length)
    assertEquals(
      Wording.jobKind(com.histmersion.api.basemap.BasemapJobKinds.BasemapFetch.kind),
      "Download the background map"
    )
    assertEquals(
      Wording.jobKind(com.histmersion.api.catalogue.EventsJobKinds.EventsImport.kind),
      "Import events"
    )
    assertEquals(Wording.jobKind(com.histmersion.api.media.ImportJobKinds.MediaImport.kind), "Fetch pictures")
    // the constants the api module names are registered kinds
    assertEquals(
      List(
        Ingest.KindIngest,
        Ingest.KindReocr,
        InitPlan.JobKind,
        Vacuum.JobKind,
        Agents.JobKind,
        MergeApplyJob.JobKind,
        AcquireChecks.RecheckJob.JobKind
      ).filterNot(k => RegisteredJobKinds.all.parse(k).isDefined),
      Nil
    )

  test("every state, status and manager kind has wording"):
    Run.State.values.foreach(s => readable(Wording.runState(s), s.wire))
    Approval.State.values.foreach(s => readable(Wording.approvalState(s), s.wire))
    JobState.values.foreach(s => assert(Wording.jobState(s).head.isUpper, s.toString))
    Manager.Kind.values.foreach(k => readable(Wording.managerKind(k), k.wire))
    Source.Status.values.foreach(s => assert(Wording.status(s).head.isUpper, s.toString))
    assertEquals(Wording.status(Source.Status.Unassessed), "Not assessed")
    assertEquals(Wording.runState(Run.State.WaitingApproval), "Waiting for a decision")

  test("allowance windows read as the limit they are"):
    assertEquals(Wording.allowanceWindow("seven_day"), "This week's limit")
    assertEquals(Wording.allowanceWindow("five_hour"), "5-hour limit")
    assertEquals(Wording.allowanceWindow("10080m"), "This week's limit")
    assertEquals(Wording.allowanceWindow("codex/300m"), "5-hour limit")
    assertEquals(Wording.allowanceWindow("seven_day_opus"), "This week's limit (Opus)")
    assertEquals(Wording.allowanceWindow("90m"), "90-minute limit")

  test("the scheduler, the agents' state and pools are plain words"):
    assertEquals(Wording.strategies("type-balanced"), "Balance the kinds of work")
    assertEquals(Wording.strategies("goal-first"), "One goal at a time")
    assertEquals(Wording.pool("claude-plan"), "Claude plan")
    assertEquals(Wording.pool("chatgpt-plan"), "ChatGPT plan")
    assertEquals(Wording.agentsState(autopilotOn = true, draining = false), "Agents: working")
    assertEquals(
      Wording.agentsState(autopilotOn = true, draining = true),
      "Agents: finishing current work, then pausing"
    )
    assertEquals(Wording.Drain, "Finish current work, then pause")
    val glance = UiNotices.AgentsGlance(running = 3, queued = 12, on = true)
    assertEquals(glance.wording, "Agents working · 3 runs")
    assertEquals((glance.copy(running = 1)).wording, "Agents working · 1 run")
    assertEquals((glance.copy(running = 0)).wording, "Agents working · 12 waiting")
    assertEquals((glance.copy(running = 0, queued = 0)).wording, "Agents on · nothing to do")
    assertEquals((glance.copy(draining = true)).wording, "Agents finishing current work · 3 runs")
    assertEquals((glance.copy(draining = true, running = 0)).wording, "Agents paused")
    assertEquals((glance.copy(on = false)).wording, "Agents paused")
    assertEquals(Wording.decisions(0), None)
    assertEquals(Wording.decisions(1), Some("1 needs your decision"))
    assertEquals(Wording.decisions(2), Some("2 need your decision"))
    assertEquals(Wording.decisionsShort(2), "2 need you")
    assertEquals(Wording.manager("watch:bronze-age-collapse"), "Watch manager · Bronze age collapse")
    assertEquals(Wording.manager("review"), "Review manager")
    assertEquals(Wording.plugin("ai-anthropic"), "AI provider: Anthropic")
    assertEquals(Wording.plugin("keepassxc"), "KeePassXC")
    assertEquals(Wording.plugin("oa-core"), "Open access: CORE")

  test("an identifier the dictionary does not know is shown as words, never raw"):
    assertEquals(UiLabels.label("some-new_kind"), "Some new kind")
    assertEquals(UiLabels.label("review-article"), "Review an article")
    assertEquals(Wording.humanize("watch:pie-origins"), "Watch: pie origins")
    assertEquals(Wording.heading("disputed-grand-theories"), "Disputed grand theories")
    assertEquals(Wording.heading("Indo-European origins"), "Indo-European origins")
    assertEquals(Wording.value("bronze_age_collapse"), "bronze age collapse")
    UiLabels.known.foreach((wire, label) => readable(label, wire))
