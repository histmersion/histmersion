package com.histmersion.api.automation.agents

import com.histmersion.api.*

object AgentStateMarkdown:

  def agentStateList(entries: List[AgentStateEntry]): String =
    if entries.isEmpty then "_No agent state entries._"
    else
      val body = entries
        .map { e =>
          val short = if e.value.length > 80 then e.value.take(77) + "…" else e.value
          s"- `${e.key}` = $short"
        }
        .mkString("\n")
      s"### ${entries.size} key(s)\n\n$body"

  def agentState(e: AgentStateEntry): String =
    val upd = e.updatedAt.map(u => s"  \n_Updated: ${u}_").getOrElse("")
    s"# `${e.key}`\n\n```json\n${e.value}\n```$upd"
