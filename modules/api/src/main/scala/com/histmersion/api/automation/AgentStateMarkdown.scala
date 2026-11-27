package com.histmersion
package api.automation

import com.histmersion.lint.{tolerated, LintRule}
object AgentStateMarkdown:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def agentStateList(entries: List[AgentStateEntry]): String =
    if entries.isEmpty then ("_No agent state entries._")
    else
      val body = entries
        .map { e =>
          val short = if e.value.value.length > 80 then e.value.value.take(77) + "…" else e.value.value
          s"- `${e.key}` = $short"
        }
        .mkString("\n")
      (s"### ${entries.size} key(s)\n\n$body")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def agentState(e: AgentStateEntry): String =
    val upd = e.updatedAt.map(u => s"  \n_Updated: ${u}_").getOrElse("")
    (s"# `${e.key}`\n\n```json\n${e.value}\n```$upd")
