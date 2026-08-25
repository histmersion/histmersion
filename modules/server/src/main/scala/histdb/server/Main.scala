package histdb.server

import chimp.server.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import sttp.tapir.server.netty.sync.NettySyncServer
import histdb.core.{Db, Markdown, SourceRepo, Version}

/** hist-db server — direct-style (Netty sync). Chimp MCP endpoint at `/mcp`; every tool returns
  * Markdown. Next: the REST API + static web UI (Leaflet map + deep-time timeline). */
object Main:

  // Circe Codec + tapir Schema both derived from each input by Kindlings → matching by construction.
  final case class PingInput() derives KindlingsCodecAsObject
  object PingInput:
    given Schema[PingInput] = KindlingsSchema.derived[PingInput].schema

  final case class SearchInput(query: Option[String] = None, limit: Int = 20) derives KindlingsCodecAsObject
  object SearchInput:
    given Schema[SearchInput] = KindlingsSchema.derived[SearchInput].schema

  final case class GetSourceInput(citekey: String, fullText: Boolean = false) derives KindlingsCodecAsObject
  object GetSourceInput:
    given Schema[GetSourceInput] = KindlingsSchema.derived[GetSourceInput].schema

  final case class GeoWhenInput(
      region: Option[String] = None,
      minLon: Option[Double] = None,
      minLat: Option[Double] = None,
      maxLon: Option[Double] = None,
      maxLat: Option[Double] = None,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None,
      limit: Int = 50
  ) derives KindlingsCodecAsObject
  object GeoWhenInput:
    given Schema[GeoWhenInput] = KindlingsSchema.derived[GeoWhenInput].schema

  final case class NeighborsInput(citekey: String) derives KindlingsCodecAsObject
  object NeighborsInput:
    given Schema[NeighborsInput] = KindlingsSchema.derived[NeighborsInput].schema

  private val ds = Db.fromConfig() // eager: config load must not run on an Ox virtual thread (StackValue)

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

  private val getSource =
    tool("get_source")
      .description("Fetch one source (frontmatter + findings, optionally full text) as Markdown")
      .input[GetSourceInput]
      .handle { in =>
        SourceRepo.get(ds, in.citekey) match
          case Some((s, fs)) => ToolResult.text(Markdown.source(s, fs, in.fullText))
          case None          => ToolResult.text(s"_No source '${in.citekey}'._")
      }

  private val geoWhen =
    tool("geo_when")
      .description("Findings at a place (region name or lon/lat bbox) and/or deep-time range, as Markdown")
      .input[GeoWhenInput]
      .handle { in =>
        val bbox = (in.minLon, in.minLat, in.maxLon, in.maxLat) match
          case (Some(a), Some(b), Some(c), Some(d)) => Some((a, b, c, d))
          case _                                    => None
        ToolResult.text(
          Markdown.geoResults(SourceRepo.geoWhen(ds, in.region, bbox, in.periodStart, in.periodEnd, in.limit))
        )
      }

  private val neighbors =
    tool("neighbors")
      .description("Typed references to/from a source (cites/refutes/challenges/updates/supersedes), as Markdown")
      .input[NeighborsInput]
      .handle(in => ToolResult.text(Markdown.neighbors(in.citekey, SourceRepo.neighbors(ds, in.citekey))))

  private val mcp =
    McpServer(tools = List(ping, search, getSource, geoWhen, neighbors)).endpoint(List("mcp"))

  def main(args: Array[String]): Unit =
    val _ = args
    // netty's pooled allocator mishandles ByteBuf refCnt under native-image → use unpooled buffers.
    // (Safe now: config no longer parses system properties, so these dotted keys don't reach sconfig.)
    System.setProperty("io.netty.allocator.type", "unpooled")
    System.setProperty("io.netty.leakDetection.level", "disabled")
    NettySyncServer()
      .port(8000)
      .addEndpoint(mcp)
      .addEndpoints(Http.endpoints)
      .startAndWait()
