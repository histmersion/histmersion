package histdb.server

import chimp.server.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import sttp.tapir.server.netty.sync.NettySyncServer
import histdb.core.Version

/** hist-db server entrypoint — direct-style (Netty sync). Phase-1 skeleton: a Chimp MCP endpoint at
  * `/mcp` exposing a `ping` tool. Next: the read tools (search/get_source/geo_when/neighbors) backed
  * by Magnum/PostGIS, the REST API, and the static web UI. */
object Main:

  // Both the circe Codec (for JSON) and the tapir Schema (for the MCP tool's JSON Schema) are derived
  // from PingInput by Kindlings → they match by construction. `KindlingsCodecAsObject[A] <: Codec.AsObject[A]`
  // is `derives`-able directly; the tapir Schema is materialised from `KindlingsSchema.derived`.
  final case class PingInput() derives KindlingsCodecAsObject
  object PingInput:
    given Schema[PingInput] = KindlingsSchema.derived[PingInput].schema

  private val ping =
    tool("ping")
      .description("Liveness check for the hist-db MCP server")
      .input[PingInput]
      .handle(_ => ToolResult.text(s"${Version.name} ${Version.version} — ok"))

  private val mcp = McpServer(tools = List(ping)).endpoint(List("mcp"))

  def main(args: Array[String]): Unit =
    val _ = args
    NettySyncServer()
      .port(8000)
      .addEndpoint(mcp)
      .startAndWait()
