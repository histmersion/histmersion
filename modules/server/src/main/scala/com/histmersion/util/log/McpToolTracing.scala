package com.histmersion
package util.log

import chimp.server.{ServerContext, ServerTool}
import com.histmersion.api.log.LogEvent
import sttp.shared.Identity

object McpToolTracing:
  def traced[I, O, C <: ServerContext[Identity]](t: ServerTool[I, O, Identity, C]): ServerTool[I, O, Identity, C] =
    ServerTool(
      t.name,
      t.description,
      t.inputSchema,
      t.inputDecoder,
      t.outputSchema,
      t.annotations,
      (input, context, headers) =>
        TraceScope.record(LogEvent.What.ToolCalled(LogEvent.Tool.named(t.name)))
        t.logic(input, context, headers)
    )
