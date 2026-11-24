package com.histmersion
package util.mcp

import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Text.sentences
import chimp.protocol.ToolContent
import chimp.server.{NoStructuredOutput, ToolResult}
import com.histmersion.api.Api
import com.histmersion.util.mcp.ChimpJson.structured
import com.histmersion.lint.{LintRule, tolerated}

/** The one way an MCP tool answers: Markdown, or a refusal. A refusal (a value the handler returned as `Left`, or an
  * argument the tool itself found missing) is a result the protocol flags as an error (`isError`), so a harness or a
  * retry policy that counts errors by the flag sees it. Its text is each problem's sentence in italics (`_No source
  * 'x'…_`), what the agent reads; its structured content is the `{"problems": [...], "code": ...}` body an HTTP answer
  * carries, the typed entries a caller branches on. No tool builds a refusal any other way.
  */
object McpOut:
  type Markdown = String

  /** A refusal's text: each problem's English sentence in italics, one line each. Pure. The embedded agents' tools,
    * which answer text alone, use it as it is; an MCP tool never does (it answers [[error]]).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def refusalText(e: Api.Error): Markdown = e.problems.sentences.map(s => s"_${s}_").mkString("\n")

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def text(md: Markdown): ToolResult[NoStructuredOutput] = ToolResult.text(md)

  /** A handler's error as the tool's answer: flagged as an error, with the typed entries as structured content. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def error(e: Api.Error): ToolResult[NoStructuredOutput] = refusalWith(Nil)(e)

  /** [[error]], followed by Markdown that helps the caller on (where to find a valid key, the schema to query). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def error(e: Api.Error, more: Markdown): ToolResult[NoStructuredOutput] = refusalWith(List(more))(e)

  private def refusalWith(more: List[Markdown])(e: Api.Error): ToolResult[NoStructuredOutput] =
    ToolResult(
      List(ToolContent.Text(text = (refusalText(e) :: more).mkString("\n\n"))),
      structuredContent = Some(e.structured),
      isError = true
    )

  /** A refusal the tool found itself (an argument missing or out of place), before any handler ran. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def refused(problems: Problems): ToolResult[NoStructuredOutput] = error(Api.Error(problems))

  /** An argument, `raw` as the caller wrote it, that is none of the values of `typeName` the tool knows (`known`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def unknown(typeName: Problem.TypeName, raw: String, known: Problem.Raw*): ToolResult[NoStructuredOutput] =
    refused(Problem.Invalid.Unknown.here(typeName, Problem.Raw(raw), known.toVector))

  /** A required argument, `field`, that the call left out. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def missing(field: Problem.Field): ToolResult[NoStructuredOutput] = refused(Problem.Invalid.Missing.here(field))

  /** A handler's answer as the tool's answer: its Markdown, or the refusal. */
  def of[A](r: Either[Api.Error, A])(md: A => Markdown): ToolResult[NoStructuredOutput] =
    r.fold(e => error(e), a => text(md(a)))

  /** [[of]] for a handler that answers Markdown already. */
  def ofText(r: Either[Api.Error, Markdown]): ToolResult[NoStructuredOutput] = of(r)(identity)
