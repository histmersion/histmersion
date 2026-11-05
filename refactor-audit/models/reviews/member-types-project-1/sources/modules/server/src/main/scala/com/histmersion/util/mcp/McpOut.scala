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
  type RefusalResult = RefusalResult.T
  object RefusalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefusalMessage = RefusalMessage.T
  object RefusalMessage extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownResult = MarkdownResult.T
  object MarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkdownMdResult = MarkdownMdResult.T
  object MarkdownMdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextMd = TextMd.T
  object TextMd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfMdResult = OfMdResult.T
  object OfMdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def refusal(message: com.histmersion.util.mcp.McpOut.RefusalMessage): com.histmersion.util.mcp.McpOut.RefusalResult = s"_${message}_"

  /** [[refusal]] of a handler's error. */
  def refusal(e: Api.Error): com.histmersion.util.mcp.McpOut.RefusalResult = refusal(e.message)

  /** A handler's answer: its Markdown, or the refusal. Pure. */
  def markdown[A](r: Either[Api.Error, A])(md: A => com.histmersion.util.mcp.McpOut.MarkdownMdResult): com.histmersion.util.mcp.McpOut.MarkdownResult = r.fold(refusal, md)

  def text(md: com.histmersion.util.mcp.McpOut.TextMd): ToolResult[NoStructuredOutput] = ToolResult.text(md)

  /** A handler's error as the tool's answer. */
  def error(e: Api.Error): ToolResult[NoStructuredOutput] = ToolResult.text(refusal(e))

  /** A handler's answer as the tool's answer. */
  def of[A](r: Either[Api.Error, A])(md: A => com.histmersion.util.mcp.McpOut.OfMdResult): ToolResult[NoStructuredOutput] = ToolResult.text(markdown(r)(md))
