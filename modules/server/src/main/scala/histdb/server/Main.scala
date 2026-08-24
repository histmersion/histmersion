package histdb.server

import chimp.server.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import sttp.tapir.server.netty.sync.NettySyncServer
import histdb.core.{Db, Markdown, SourceRepo, Version}

/** hist-db server — direct-style (Netty sync). Chimp MCP endpoint at `/mcp`. Tools return Markdown.
  * Next: get_source / geo_when / neighbors, the REST API, and the static web UI. */
object Main:

  // Both circe Codec and tapir Schema derived from the same model by Kindlings → matching by construction.
  final case class PingInput() derives KindlingsCodecAsObject
  object PingInput:
    given Schema[PingInput] = KindlingsSchema.derived[PingInput].schema

  final case class SearchInput(query: Option[String] = None, limit: Int = 20) derives KindlingsCodecAsObject
  object SearchInput:
    given Schema[SearchInput] = KindlingsSchema.derived[SearchInput].schema

  private lazy val ds = Db.fromConfig()

  private val ping =
    tool("ping")
      .description("Liveness check for the hist-db MCP server")
      .input[PingInput]
      .handle(_ => ToolResult.text(s"${Version.name} ${Version.version} — ok"))

  private val search =
    tool("search")
      .description("Search the corpus by text; returns matching sources as Markdown")
      .input[SearchInput]
      .handle(in => ToolResult.text(Markdown.searchResults(SourceRepo.search(ds, in.query, in.limit))))

  private val mcp = McpServer(tools = List(ping, search)).endpoint(List("mcp"))

  def main(args: Array[String]): Unit =
    val _ = args
    NettySyncServer()
      .port(8000)
      .addEndpoint(mcp)
      .startAndWait()
