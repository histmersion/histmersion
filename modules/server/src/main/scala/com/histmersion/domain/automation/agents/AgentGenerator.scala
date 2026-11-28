package com.histmersion
package domain.automation.agents
import com.histmersion.util.json.JsonCodec.given
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Text.sentence

import com.histmersion.server.agents.{ClaudeCode, Codex, Opencode}
import com.histmersion.lint.{tolerated, LintRule}

/** The per-runtime trees, as pure functions from the canonical definitions to `relative path → text`
  * (docs/research-agent-runtimes.md §2.2). Nothing here touches the disk; [[AgentTrees]] writes.
  */
object AgentGenerator:
  import AgentDefinitions.render
