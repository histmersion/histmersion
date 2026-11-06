package com.histmersion.api.automation

object AgentStateMarkdown:

  type AgentStateListResult = AgentStateListResult.T
  object AgentStateListResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AgentStateResult = AgentStateResult.T
  object AgentStateResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def agentStateList(entries: List[AgentStateEntry]): com.histmersion.api.automation.AgentStateMarkdown.AgentStateListResult =
    if entries.isEmpty then _root_.com.histmersion.api.automation.AgentStateMarkdown.AgentStateListResult("_No agent state entries._")
    else
      val body = entries
        .map { e =>
          val short = if e.value.length > 80 then e.value.take(77) + "…" else e.value
          s"- `${e.key}` = $short"
        }
        .mkString("\n")
      _root_.com.histmersion.api.automation.AgentStateMarkdown.AgentStateListResult(s"### ${entries.size} key(s)\n\n$body")

  def agentState(e: AgentStateEntry): com.histmersion.api.automation.AgentStateMarkdown.AgentStateResult =
    val upd = e.updatedAt.map(u => s"  \n_Updated: ${u}_").getOrElse("")
    _root_.com.histmersion.api.automation.AgentStateMarkdown.AgentStateResult(s"# `${e.key}`\n\n```json\n${e.value}\n```$upd")
