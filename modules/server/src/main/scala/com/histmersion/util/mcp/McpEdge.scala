package com.histmersion
package util.mcp

import chimp.protocol.ToolContent
import chimp.server.{McpServer, ServerContext, ServerTool, ToolResult}
import com.histmersion.util.log.McpToolTracing
import sttp.shared.Identity

/** The `/mcp` edge: the one place an MCP server is built, and so the one place every tool's call passes. Each tool's
  * arguments and answer are held to the size cap of `histmersion.mcp` ([[McpConfig]]), read at each call so a config
  * reload applies at once: arguments longer than `argumentsMaxChars` are refused before the tool runs (an
  * invalid-params error of the protocol), and an answer longer than `resultMaxChars` is cut at a line with a trailer
  * saying how much was cut and how to read on.
  */
object McpEdge:
  type Tool = ServerTool[?, ?, Identity, ServerContext[Identity]]

  /** What a cut answer's trailer tells the caller to do. */
  val ReadOn: String = "ask for less: the tool's own paging (offset, chars, limit, before) or a narrower filter"

  def server(tools: List[Tool], limits: () => McpConfig): McpServer[Identity] =
    McpServer(tools = tools.map(t => McpToolTracing.traced(capped(t, limits))))

  /** `tool`, with its arguments and its answer held to `limits()`. */
  def capped[I, O](
      tool: ServerTool[I, O, Identity, ServerContext[Identity]],
      limits: () => McpConfig
  ): ServerTool[I, O, Identity, ServerContext[Identity]] =
    ServerTool(
      tool.name,
      tool.description,
      tool.inputSchema,
      ChimpJson.bounded(tool.inputDecoder, chars => limits().argumentsMaxChars.fits(chars)),
      tool.outputSchema,
      tool.annotations,
      (input, context, headers) => answer(tool.logic(input, context, headers), limits().resultMaxChars)
    )

  /** `result` with its text held to `most` characters, all its text blocks counted together; the error flag and the
    * structured content stay. Pure.
    */
  def answer[O](result: ToolResult[O], most: McpConfig.ResultMaxChars): ToolResult[O] =
    val texts = result.content.collect { case t: ToolContent.Text => t.text }
    if most.fits(texts.map(_.length).sum) then result
    else
      ToolResult(
        ToolContent.Text(text = most.cut(texts.mkString("\n\n"), ReadOn)) ::
          result.content.filterNot(_.isInstanceOf[ToolContent.Text]),
        result.structuredContent,
        result.isError
      )
