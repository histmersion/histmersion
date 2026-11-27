package com.histmersion
package domain.automation.workflows

import com.histmersion.api.automation.Pipeline
import com.histmersion.domain.Creator
import com.histmersion.plugin.Plugin
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class InTreeWorkflowSuite extends munit.FunSuite:

  test("every in-tree process is a model-free workflow of an in-tree contributor, each named once"):
    val creators = InTreeWorkflow.values.toList.map(_.creator)
    creators.foreach {
      case Creator.New.Automated(plugin, _, agent, surface) =>
        assertEquals(surface, Creator.Surface.Internal)
        assert(Set("engine", "library", "catalogue").contains(s"$plugin"), s"$plugin")
        assertEquals(agent, Creator.Agent.Code)
      case other => fail("not automated: " + other.toString)
    }
    val names = InTreeWorkflow.values.toList.map(w => s"${w.workflow}")
    assertEquals(names.distinct, names)

  test("no in-tree process takes the name of a registered workflow, so the two never read as one"):
    val pipelines = Pipeline.values.toList.map(p => s"${p.wire}").toSet
    InTreeWorkflow.values.foreach(w => assert(!pipelines(s"${w.workflow}"), s"${w.workflow} is a pipeline's name"))

  test("a workflow's identifier is a creator's workflow name as it stands"):
    List(WorkflowId("enrich-source") -> "enrich-source", WorkflowId("plan-goal") -> "plan-goal").foreach { (id, raw) =>
      assertEquals(s"${id.creatorName}", raw)
    }

  test("an import names the plugin that registered what it reads, else the in-tree contributor, never the starter"):
    val owned = InTreeWorkflow.imported(
      Some(Plugin.Info.Id("events-gvp")),
      InTreeWorkflow.Catalogue,
      InTreeWorkflow.EventImporting
    )
    assertEquals(owned, Creator.code(Creator.Plugin.Name("events-gvp"), Creator.Workflow.Name("event-import")))
    val inTree =
      InTreeWorkflow.imported(Option.empty[Plugin.Info.Id], InTreeWorkflow.Media, InTreeWorkflow.MediaImporting)
    assertEquals(inTree, Creator.code(Creator.Plugin.Name("media"), Creator.Workflow.Name("media-import")))
    val malformed =
      InTreeWorkflow.imported(
        Some(Plugin.Info.Id("Not A Name")),
        InTreeWorkflow.Catalogue,
        InTreeWorkflow.GazetteerImporting
      )
    assertEquals(malformed, Creator.code(Creator.Plugin.Name("catalogue"), Creator.Workflow.Name("gazetteer-import")))
