package com.histmersion
package util.log

import chimp.server.{ServerContext, ServerTool}
import com.histmersion.api.log.LogEvent
import sttp.shared.Identity

/** An MCP tool whose every call is recorded in its request's trace under the tool's name: the request itself is one
  * `POST /mcp` whatever tool it calls, and its body is never read for the log.
  */
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
        Tracing.record(LogEvent.What.ToolCalled(LogEvent.Tool(t.name)))
        t.logic(input, context, headers)
    )
