package com.histmersion
package domain.automation.workflows

import com.histmersion.api.automation.Pipeline
import com.histmersion.domain.Creator

class InTreeWorkflowSuite extends munit.FunSuite:

  test("every in-tree process is a model-free workflow of the engine or the library, each named once"):
    val creators = InTreeWorkflow.values.toList.map(_.creator)
    creators.foreach {
      case Creator.New.Automated(plugin, _, agent, surface) =>
        assertEquals(surface, Creator.Surface.Internal)
        assert(Set("engine", "library").contains(s"$plugin"), s"$plugin")
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
