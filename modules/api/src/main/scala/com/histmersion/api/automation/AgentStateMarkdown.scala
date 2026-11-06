package com.histmersion.api.automation

object AgentStateMarkdown:


  def agentStateList(entries: List[AgentStateEntry]): com.histmersion.api.Markdown.Text =
    if entries.isEmpty then _root_.com.histmersion.api.Markdown.Text("_No agent state entries._")
    else
      val body = entries
        .map { e =>
          val short = if e.value.length > 80 then e.value.take(77) + "…" else e.value
          s"- `${e.key}` = $short"
        }
        .mkString("\n")
      _root_.com.histmersion.api.Markdown.Text(s"### ${entries.size} key(s)\n\n$body")

  def agentState(e: AgentStateEntry): com.histmersion.api.Markdown.Text =
    val upd = e.updatedAt.map(u => s"  \n_Updated: ${u}_").getOrElse("")
    _root_.com.histmersion.api.Markdown.Text(s"# `${e.key}`\n\n```json\n${e.value}\n```$upd")
