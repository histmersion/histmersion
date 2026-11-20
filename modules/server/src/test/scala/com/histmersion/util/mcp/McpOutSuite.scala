package com.histmersion
package util.mcp
import com.histmersion.util.problem.Problem

import chimp.protocol.ToolContent
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.api.Api

/** [[McpOut]]: one shape for a tool's refusal — flagged as an error, the sentence in italics, the typed entries as
  * structured content — and no tool answering a refusal as plain text.
  */
class McpOutSuite extends munit.FunSuite:

  private val notFound = Api.Error(Problem.NotFound.Entity(Problem.TypeName("Citekey"), Problem.Raw("nosuch2020")))

  private def text(r: chimp.server.ToolResult[?]): String =
    r.content.collect { case t: ToolContent.Text => t.text }.mkString("\n")

  test("a refusal is flagged as an error, reads as the sentence in italics, and carries the typed entry"):
    val r = McpOut.error(notFound)
    assert(r.isError)
    assert(text(r).startsWith("_") && text(r).endsWith("_") && text(r).contains("nosuch2020"), text(r))
    val problems = r.structuredContent.flatMap(_.hcursor.downField("problems").focus).flatMap(_.asArray)
    assertEquals(problems.map(_.size), Some(1), r.structuredContent.toString)
    val entry = problems.flatMap(_.headOption).map(_.noSpaces).getOrElse("")
    assert(entry.contains("nosuch2020") && entry.contains("Entity"), entry)

  test("a refusal with a hint keeps the hint after the reason"):
    val r = McpOut.error(notFound, "Find a citekey with search.")
    assert(r.isError)
    assert(text(r).endsWith("\n\nFind a citekey with search."), text(r))

  test("an answer is not an error; a handler's Left and a tool's own refusals are"):
    assert(!McpOut.of(Right(3): Either[Api.Error, Int])(n => s"**$n**").isError)
    assertEquals(text(McpOut.of(Right(3): Either[Api.Error, Int])(n => s"**$n**")), "**3**")
    assert(McpOut.of(Left(notFound): Either[Api.Error, Int])(n => s"$n").isError)
    assert(McpOut.missing(Problem.Field("id")).isError)
    assert(McpOut.unknown(Problem.TypeName("QueueAction"), "frob", Problem.Raw("list")).isError)

  private val sources =
    Path.of(sys.props.getOrElse("histmersion.repoRoot", ".")).resolve("modules/server/src/main/scala")

  /** Every file that defines an MCP tool. */
  private def toolFiles: List[Path] =
    Files
      .walk(sources)
      .iterator
      .asScala
      .filter(p => p.toString.endsWith(".scala") && Files.readString(p, UTF_8).contains("tool(\""))
      .toList

  /** A refusal answered as plain text: an italic `_…` literal handed to `ToolResult.text`, the italic refusal text
    * (only the embedded agents' tools, which answer text alone, may use it), or the removed text-only helpers.
    */
  private val plainRefusal = """ToolResult\.text\(\s*s?"_|McpOut\.refusalText|McpOut\.refusal\(|McpOut\.markdown""".r

  /** Italic answers that are no refusal: an empty result said in words. */
  private val notRefusals = List("_No references recorded for")

  test("no MCP tool answers a refusal as plain text"):
    assert(toolFiles.size > 10, s"tool files found under $sources: ${toolFiles.size}")
    val found = toolFiles.flatMap { p =>
      val text = Files.readString(p, UTF_8)
      plainRefusal
        .findAllMatchIn(text)
        .filterNot(m => notRefusals.exists(n => text.startsWith(n, m.end - 1)))
        .map(m => s"$p: ${m.matched}")
    }
    assertEquals(found, Nil, "answer a refusal through McpOut.error / McpOut.of / McpOut.refused")
