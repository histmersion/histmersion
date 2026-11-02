package com.histmersion.util.mcp

import chimp.server.{NoStructuredOutput, ToolResult}
import com.histmersion.api.Api

/** The one way an MCP tool answers (#1946): Markdown, and a refusal (a value the handler returned as `Left`) as its
  * message in italics — `_No source 'x'…_`. Every tool used to spell the refusal itself
  * (`ToolResult.text(s"_${e.message}_")`, 40-odd times, four shapes). A refusal stays a text result, not an MCP error:
  * the agent reads why and tries again, as it always has.
  */
object McpOut:

  /** A refusal's text: the reason in italics. Pure. */
  def refusal(message: String): String = s"_${message}_"

  /** [[refusal]] of a handler's error. */
  def refusal(e: Api.Error): String = refusal(e.message)

  /** A handler's answer: its Markdown, or the refusal. Pure. */
  def markdown[A](r: Either[Api.Error, A])(md: A => String): String = r.fold(refusal, md)

  def text(md: String): ToolResult[NoStructuredOutput] = ToolResult.text(md)

  /** A handler's error as the tool's answer. */
  def error(e: Api.Error): ToolResult[NoStructuredOutput] = ToolResult.text(refusal(e))

  /** A handler's answer as the tool's answer. */
  def of[A](r: Either[Api.Error, A])(md: A => String): ToolResult[NoStructuredOutput] = ToolResult.text(markdown(r)(md))
