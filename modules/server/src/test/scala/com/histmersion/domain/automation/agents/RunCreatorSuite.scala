package com.histmersion
package domain.automation.agents

import com.histmersion.api.automation.agents.ModelRef

import com.histmersion.api.automation.ProviderKind
import com.histmersion.domain.Creator
import com.histmersion.domain.automation.workflows.WorkflowDefinition

class RunCreatorSuite extends munit.FunSuite:

  private val planner = Creator.Definition.Name("goal-planner")

  test("a run's own writes: code when no step calls a model, its one definition, or a model of several"):
    assertEquals(WorkflowDefinition.runAgentOf(Nil), Creator.Agent.Code)
    assertEquals(WorkflowDefinition.runAgentOf(List(planner, planner)), Creator.Agent.RunModels(Some(planner)))
    assertEquals(
      WorkflowDefinition.runAgentOf(
        List(Creator.Definition.Name("enrich-classify"), Creator.Definition.Name("enrich-notes"))
      ),
      Creator.Agent.RunModels(None)
    )

  test("a step's answer names its definition, the provider and the model the chain picked"):
    assertEquals(
      RunCtx.modelAgent(AgentDef.Name("article-fixer"), ModelRef(ProviderKind.ClaudeCode, ModelRef.Model("opus"))),
      Some(
        Creator.Agent.Model(
          Creator.Definition.Name("article-fixer"),
          Creator.Provider.Name("claude-code"),
          Creator.LanguageModel.Name("opus")
        )
      )
    )
    assertEquals(
      RunCtx
        .modelAgent(AgentDef.Name("article-fixer"), ModelRef(ProviderKind.Local, ModelRef.Model("qwen2.5-coder:32b"))),
      Some(
        Creator.Agent.Model(
          Creator.Definition.Name("article-fixer"),
          Creator.Provider.Name("local"),
          Creator.LanguageModel.Name("qwen2.5-coder:32b")
        )
      )
    )

  test("a name that does not pass its rule is no model: the write falls back to the run's, never a guess"):
    assertEquals(
      RunCtx.modelAgent(AgentDef.Name("Not A Definition"), ModelRef(ProviderKind.Anthropic, ModelRef.Model("x"))),
      None
    )
