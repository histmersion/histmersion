package com.histmersion
package api.log

import com.histmersion.api.log.LogEvent.What

/** The structured log as Markdown: one line per event, its time, its step and its trace. */
object LogMarkdown:

  def page(p: LogPage, older: LogPage.Cursor => String): String =
    val origin = p.origin match
      case LogPage.Origin.Database => ""
      case LogPage.Origin.File     => "_Read from the log file: the database could not answer._\n\n"
    val lines =
      if p.events.isEmpty then "_No events._"
      else p.events.map(line).mkString("\n")
    origin + lines + p.next.fold("")(c => s"\n\n${older(c)}")

  def line(e: LogEvent): String =
    val at = e.at.value.take(23).replace('T', ' ')
    val label = e.label.fold("")(l => s" [${l.value}]")
    val subjects = if e.subjects.isEmpty then "" else e.subjects.map(_.ref).mkString(" (", ", ", ")")
    s"- $at · ${step(e.what)}$subjects · trace `${e.trace.value}`$label"

  def step(what: What): String = what match
    case r: What.RequestAnswered =>
      val client = r.client.fold("")(c => s" from ${c.value}")
      val search = r.search.fold("")(s => s" searching “${s.value}”")
      s"${r.method.value} ${r.operation.value} → ${r.status.value} in ${r.ms.value} ms$client$search"
    case c: What.RequestsCounted =>
      s"${c.operation.value}: ${c.count.value} request(s), ${c.failed.value} failed"
    case p: What.ProviderCalled =>
      val status = p.status.fold("unreachable")(s => s"${s.value}")
      s"${p.upstream.value} (${p.host.value}) → $status in ${p.ms.value} ms"
    case w: What.Written =>
      val rows = w.rows.fold("")(n => s" ${n.value} row(s)")
      val keys = if w.keys.isEmpty then "" else w.keys.map(_.value).mkString(": ", ", ", "")
      s"${w.change.toString.toLowerCase} ${w.table.value}$rows$keys"
    case p: What.ProblemRaised => s"problem ${p.problem.value}${p.id.fold("")(i => s" (id `${i.value}`)")}"
    case t: What.ToolCalled    => s"MCP tool `${t.tool.value}`"
    case w: What.WritesCounted =>
      s"${w.count.value} ${w.change.toString.toLowerCase} statement(s) on ${w.table.value}, counted"
    case l: What.Lost =>
      l.loss match
        case LogEvent.Loss.QueueFull       => s"${l.count.value} event(s) lost: the log's queue was full"
        case LogEvent.Loss.DatabaseBacklog =>
          s"${l.count.value} event(s) kept in the log file only: the database stayed unreachable"
