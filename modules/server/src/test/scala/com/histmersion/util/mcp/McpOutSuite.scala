package com.histmersion
package util.mcp

import com.histmersion.api.Problem
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import com.histmersion.api.Api

/** [[McpOut]] (#1946): one shape for a tool's refusal, and no tool spelling it by hand. */
class McpOutSuite extends munit.FunSuite:

  test("a refusal is the reason in italics; an answer is its Markdown"):
    assertEquals(McpOut.refusal(Api.Error(Problem("No source 'x'"))), "_No source 'x'_")
    assertEquals(
      McpOut.markdown(Left(Api.Error(Problem("nope"))): Either[Api.Error, Int])(_.toString),
      "_nope_"
    )
    assertEquals(McpOut.markdown(Right(3): Either[Api.Error, Int])(n => s"**$n**"), "**3**")

  test("no MCP tool spells a refusal itself"):
    val root = Path.of(sys.props.getOrElse("histmersion.repoRoot", ".")).resolve("modules/server/src/main/scala")
    val offenders = Files
      .walk(root)
      .iterator
      .asScala
      .filter(_.toString.endsWith(".scala"))
      .filterNot(_.getFileName.toString == "McpOut.scala")
      .flatMap(p =>
        Files.readAllLines(p).asScala.zipWithIndex.collect {
          case (l, i) if l.contains("s\"_${e.message}_") => s"${p.getFileName}:${i + 1}: ${l.trim}"
        }
      )
      .toList
    assertEquals(offenders, Nil, "answer through McpOut.error / McpOut.of / McpOut.refusal")
