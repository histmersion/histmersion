package com.histmersion
package util.mcp

import chimp.protocol.ToolContent
import chimp.server.{tool, ToolResult}
import com.histmersion.api.Api
import com.histmersion.util.problem.Problem
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import ox.supervised
import sttp.tapir.server.netty.sync.NettySyncServer

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters.*
import com.histmersion.lint.{tolerated, LintRule}

/** The one size cap at the `/mcp` edge: every tool's arguments and answer, over the protocol, and the edge as the only
  * place the MCP server is built.
  */
class McpSizeCapSuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def limits(result: Int, arguments: Int) =
    McpConfig(McpConfig.ResultMaxChars(result), McpConfig.ArgumentsMaxChars(arguments))

  private def text(r: ToolResult[?]): String = r.content.collect { case t: ToolContent.Text => t.text }.mkString

  test("an answer within the cap is left as it is; a longer one is cut at a line, with a trailer"):
    val short = ToolResult.text("one\ntwo")
    assertEquals(McpEdge.answer(short, McpConfig.ResultMaxChars(100)), short)
    val long = ToolResult.text((1 to 2000).map(i => s"line $i").mkString("\n"))
    val cut = McpEdge.answer(long, McpConfig.ResultMaxChars(1000))
    assert(text(cut).length <= 1000, s"${text(cut).length}")
    assert(text(cut).contains("cut at") && text(cut).contains(McpEdge.ReadOn), text(cut).takeRight(300))
    assertEquals(McpEdge.answer(long, McpConfig.ResultMaxChars(0)), long, "0 = no cap")

  test("a refusal keeps its error flag and typed entries when its text is cut"):
    val refusal = McpOut.error(
      Api.Error(Problem.NotFound.Entity(Problem.TypeName("Citekey"), Problem.Raw("x"))),
      "hint " * 500
    )
    val cut = McpEdge.answer(refusal, McpConfig.ResultMaxChars(300))
    assert(cut.isError)
    assertEquals(cut.structuredContent, refusal.structuredContent)
    assert(text(cut).length <= 300)

  private val schema = RawJson.parse("""{"type":"object"}""").toOption.get

  /** A call to a tool that answers 200 000 characters, over a server held to `cap`. */
  private def call(cap: AtomicReference[McpConfig], arguments: String): String =
    val big = ChimpJson.rawTool(tool("big").description("answers a lot"), schema)(_ => ToolResult.text("x\n" * 100000))
    val server = McpEdge.server(List(big), () => cap.get)
    supervised {
      val b = NettySyncServer().host("127.0.0.1").port(0).addEndpoint(server.endpoint(List("mcp"))).start()
      try
        val req = HttpRequest
          .newBuilder(URI.create(s"http://127.0.0.1:${b.port}/mcp"))
          .header("content-type", "application/json")
          .header("accept", "application/json, text/event-stream")
          .POST(
            HttpRequest.BodyPublishers.ofString(
              s"""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"big","arguments":$arguments}}"""
            )
          )
          .build()
        HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString()).body
      finally b.stop()
    }

  test("over the protocol: a long answer is cut to the cap, and a reload of the cap applies at the next call"):
    val cap = AtomicReference(limits(5000, 10000))
    val body = call(cap, "{}")
    val answer = RawJson.parse(body.dropWhile(_ != '{')).toOption.flatMap(_.cursor.downField("result").focus)
    val shown = answer.flatMap(_.cursor.downField("content").focus).map(_.noSpaces.length).getOrElse(0)
    assert(shown > 1000 && shown < 9000, s"$shown: ${body.take(300)}")
    cap.set(limits(0, 10000))
    val whole = call(cap, "{}")
    assert(whole.length > 150000, s"no cap: ${whole.length}")

  test("over the protocol: arguments past the cap are refused before the tool runs"):
    val cap = AtomicReference(limits(5000, 50))
    val body = call(cap, s"""{"padding":"${"y" * 200}"}""")
    assert(body.contains("\"error\"") && body.contains("more than the server takes"), body.take(500))
    assert(!body.contains("\"result\""), "the tool did not run")

  test("the edge is the one place the MCP server is built"):
    val sources = Path.of(sys.props.getOrElse("histmersion.repoRoot", ".")).resolve("modules/server/src/main/scala")
    val builders = Files
      .walk(sources)
      .iterator
      .asScala
      .filter(_.toString.endsWith(".scala"))
      .filter(p => """(?<![.\w])McpServer\(""".r.findFirstIn(Files.readString(p, UTF_8)).isDefined)
      .map(_.getFileName.toString)
      .toList
    assertEquals(builders, List("McpEdge.scala"))
