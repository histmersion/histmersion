package com.histmersion.server.agents

import com.histmersion.api.Queue.QueueAdd
import com.histmersion.server.Handlers
import io.circe.syntax.*

/** What every agent pipeline does the same way (#1946) — each had its own copy:
  *  - [[log]]: a run ends with one line in the worklog (`queue log`), written through the run's
  *    write gate, so a dry run records the write it would have made instead;
  *  - [[isCitekey]]: whether a name a model wrote is a source in the corpus (an old, renamed citekey
  *    resolves too), read without the body. */
private[agents] object Pipelines:

  /** Add `line` to the worklog; answers the line. */
  def log(h: Handlers, ctx: RunCtx, line: String): Either[String, String] =
    val entry = QueueAdd(line)
    ctx.write("log line", entry.asJson)(h.ops.queueAdd("log", entry).left.map(_.message).map(_ => line))(Right(line))

  def isCitekey(h: Handlers)(ck: String): Boolean = h.getSource(ck, body = false).isRight
