package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.automation.Pipeline
import com.histmersion.api.automation.Agents
import com.histmersion.api.exchange.{InitPlan, MergeApplyJob}
import com.histmersion.api.instance.UiNotices
import com.histmersion.api.jobs.JobState
import com.histmersion.api.library.Ingest
import com.histmersion.api.maintenance.Vacuum
import com.histmersion.api.automation.Approval
import com.histmersion.api.automation.Manager
import com.histmersion.api.automation.Run
import com.histmersion.api.library.Source
import com.histmersion.api.library.Acquire
import com.histmersion.api.basemap.BasemapJobKinds
import com.histmersion.api.catalogue.EventsJobKinds
import com.histmersion.api.media.ImportJobKinds
import com.histmersion.api.jobs.JobKinds
import com.histmersion.api.automation.PlanQuota
import com.histmersion.api.automation.AgentScheduler
import com.histmersion.api.automation.AgentMode
import com.histmersion.lint.{tolerated, LintRule}

/** The label dictionary covers every identifier the UI shows: a pipeline, an approval kind or a job kind added without
  * wording fails here, not in front of a reader.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
    Pipeline.values.foreach(p => readable(Wording.pipeline(p), p.wire.value))
    assertEquals(Wording.pipeline(Pipeline.ReviewArticle), "Review an article")
    assertEquals(Wording.pipeline(Pipeline.EnrichSource), "Enrich a source")
    assertEquals(Wording.pipeline(Pipeline.AcquisitionSweep), "Look for copies to fetch")

  test("every approval kind has wording"):
    Approval.Kind.values.foreach(k => readable(Wording.approvalKind(k), k.wire.value))
    assertEquals(Wording.approvalKind(Approval.Kind.ArticlePublish), "Make an article readable")
    assertEquals(Wording.approvalKind(Approval.Kind.StatusChange), "Change a source's status")
    assertEquals(Wording.approvalKind(Approval.Kind.Edge), "Link two sources")

  test("every job kind has wording, and the kinds are distinct"):
    RegisteredJobKinds.all.kinds.foreach(k => readable(Wording.jobKind(k), k.value))
    assertEquals(RegisteredJobKinds.all.kinds.map(_.value).distinct.length, RegisteredJobKinds.all.kinds.length)
    assertEquals(
      Wording.jobKind(BasemapJobKinds.BasemapFetch.kind),
      "Download the background map"
    )
    assertEquals(
      Wording.jobKind(EventsJobKinds.EventsImport.kind),
      "Import events"
    )
    assertEquals(Wording.jobKind(ImportJobKinds.MediaImport.kind), "Fetch pictures")
    // the constants the api module names are registered kinds
    assertEquals(
      List(
        Ingest.KindIngest,
        Ingest.KindReocr,
        InitPlan.JobKind,
        Vacuum.JobKind,
        Agents.JobKind,
        MergeApplyJob.JobKind,
        Acquire.Checks.RecheckJob.JobKind
      ).filterNot(k => RegisteredJobKinds.all.parse(JobKinds.Wire(k)).isDefined),
      Nil
    )

  test("every state, status and manager kind has wording"):
    Run.State.values.foreach(s => readable(Wording.runState(s), s.wire.value))
    Approval.State.values.foreach(s => readable(Wording.approvalState(s), s.wire.value))
    JobState.values.foreach(s => assert(Wording.jobState(s).head.isUpper, s.toString))
    Manager.Kind.values.foreach(k => readable(Wording.managerKind(k), k.wire.value))
    Source.Status.values.foreach(s => assert(Wording.status(s).head.isUpper, s.toString))
    assertEquals(Wording.status(Source.Status.Unassessed), "Not assessed")
    assertEquals(Wording.runState(Run.State.WaitingApproval), "Waiting for a decision")

  test("allowance windows read as the limit they are"):
    assertEquals(Wording.allowanceWindow(PlanQuota.WindowStatus.Kind("seven_day")), "This week's limit")
    assertEquals(Wording.allowanceWindow(PlanQuota.WindowStatus.Kind("five_hour")), "5-hour limit")
    assertEquals(Wording.allowanceWindow(PlanQuota.WindowStatus.Kind("10080m")), "This week's limit")
    assertEquals(Wording.allowanceWindow(PlanQuota.WindowStatus.Kind("codex/300m")), "5-hour limit")
    assertEquals(Wording.allowanceWindow(PlanQuota.WindowStatus.Kind("seven_day_opus")), "This week's limit (Opus)")
    assertEquals(Wording.allowanceWindow(PlanQuota.WindowStatus.Kind("90m")), "90-minute limit")

  test("the scheduler, the agents' state and pools are plain words"):
    assertEquals(Wording.strategies(AgentScheduler.StrategyLine.Name("type-balanced")), "Balance the kinds of work")
    assertEquals(Wording.strategies(AgentScheduler.StrategyLine.Name("goal-first")), "One goal at a time")
    assertEquals(Wording.pool(AgentScheduler.PoolUse.Pool("claude-plan")), "Claude plan")
    assertEquals(Wording.pool(AgentScheduler.PoolUse.Pool("chatgpt-plan")), "ChatGPT plan")
    assertEquals(
      Wording.agentsState(autopilotOn = AgentMode.Step.Autopilot.On(true), draining = AgentMode.Step.Drain.On(false)),
      "Agents: working"
    )
    assertEquals(
      Wording.agentsState(autopilotOn = AgentMode.Step.Autopilot.On(true), draining = AgentMode.Step.Drain.On(true)),
      "Agents: finishing current work, then pausing"
    )
    assertEquals(Wording.Drain, "Finish current work, then pause")
    val glance = UiNotices.AgentsGlance(
      running = UiNotices.AgentsGlance.Running(3),
      queued = UiNotices.AgentsGlance.Queued(12),
      on = UiNotices.AgentsGlance.On(true)
    )
    assertEquals(glance.wording, "Agents working · 3 runs")
    assertEquals((glance.modify(_.running).setTo(UiNotices.AgentsGlance.Running(1))).wording, "Agents working · 1 run")
    assertEquals(
      (glance.modify(_.running).setTo(UiNotices.AgentsGlance.Running(0))).wording,
      "Agents working · 12 waiting"
    )
    assertEquals(
      (glance
        .modify(_.running)
        .setTo(UiNotices.AgentsGlance.Running(0))
        .modify(_.queued)
        .setTo(UiNotices.AgentsGlance.Queued(0)))
        .wording,
      "Agents on · nothing to do"
    )
    assertEquals(
      (glance.modify(_.draining).setTo(UiNotices.AgentsGlance.Draining(true))).wording,
      "Agents finishing current work · 3 runs"
    )
    assertEquals(
      (glance
        .modify(_.draining)
        .setTo(UiNotices.AgentsGlance.Draining(true))
        .modify(_.running)
        .setTo(UiNotices.AgentsGlance.Running(0)))
        .wording,
      "Agents paused"
    )
    assertEquals((glance.modify(_.on).setTo(UiNotices.AgentsGlance.On(false))).wording, "Agents paused")
    assertEquals(Wording.decisions(Wording.Decisions.Open(0)), None)
    assertEquals(Wording.decisions(Wording.Decisions.Open(1)), Some("1 needs your decision"))
    assertEquals(Wording.decisions(Wording.Decisions.Open(2)), Some("2 need your decision"))
    assertEquals(Wording.decisionsShort(Wording.Decisions.Open(2)), "2 need you")
    assertEquals(Wording.manager(Wording.ManagerId("watch:bronze-age-collapse")), "Watch manager · Bronze age collapse")
    assertEquals(Wording.manager(Wording.ManagerId("review")), "Review manager")
    assertEquals(Wording.plugin(Wording.PluginId("ai-anthropic")), "AI provider: Anthropic")
    assertEquals(Wording.plugin(Wording.PluginId("keepassxc")), "KeePassXC")
    assertEquals(Wording.plugin(Wording.PluginId("oa-core")), "Open access: CORE")

  test("an identifier the dictionary does not know is shown as words, never raw"):
    assertEquals(UiLabels.label(UiLabels.Key("some-new_kind")), "Some new kind")
    assertEquals(UiLabels.label(UiLabels.Key("review-article")), "Review an article")
    assertEquals(Wording.humanize(Wording.Id("watch:pie-origins")), "Watch: pie origins")
    assertEquals(Wording.heading("disputed-grand-theories"), "Disputed grand theories")
    assertEquals(Wording.heading("Indo-European origins"), "Indo-European origins")
    assertEquals(Wording.value("bronze_age_collapse"), "bronze age collapse")
    UiLabels.known.foreach((wire, label) => readable(label.value, wire.value))
