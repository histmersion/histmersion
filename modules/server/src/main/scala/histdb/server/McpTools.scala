package histdb.server

import chimp.server.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import histdb.core.*

/** MCP tools (Chimp). Every tool returns Markdown and delegates to the same [[Handlers]] as REST. */
final class McpTools(h: Handlers):
  import McpTools.*

  private val ping =
    tool("ping").description("Liveness check for the hist-db MCP server").input[PingInput]
      .handle(_ => ToolResult.text(s"${h.version.name} ${h.version.version} — ok"))

  private val search =
    tool("search").description("Search the corpus by text; returns matching sources as Markdown")
      .input[SearchInput]
      .handle(in => ToolResult.text(Markdown.searchResults(h.search(in.query, in.limit).toVector)))

  private val getSource =
    tool("get_source").description("Fetch one source (frontmatter + findings, optionally full text) as Markdown")
      .input[GetSourceInput]
      .handle { in =>
        h.getSource(in.citekey) match
          case Right(sw) => ToolResult.text(Markdown.source(sw.source, sw.findings.toVector, in.fullText))
          case Left(e)   => ToolResult.text(s"_${e.message}_")
      }

  private val geoWhen =
    tool("geo_when").description("Findings at a place (region name or lon/lat bbox) and/or deep-time range, as Markdown")
      .input[GeoWhenInput]
      .handle { in =>
        val q = Api.FindingsQuery(in.region, in.minLon, in.minLat, in.maxLon, in.maxLat, in.periodStart, in.periodEnd, in.limit)
        ToolResult.text(Markdown.geoResults(h.findings(q).toVector))
      }

  private val histogram =
    tool("histogram")
      .description("When is the evidence? Counts of sources (or findings) per adaptive time bucket " +
        "across a deep-time window, optionally narrowed by region/bbox — returns a Markdown table + bar chart")
      .input[HistogramInput]
      .handle { in =>
        val unit = in.unit.getOrElse("sources")
        val q = Api.AggregateQuery(Some("time"), Some(unit), in.region,
          in.minLon, in.minLat, in.maxLon, in.maxLat, in.periodStart, in.periodEnd)
        h.aggregate(q) match
          case Right(hist) => ToolResult.text(Markdown.histogram(hist, unit))
          case Left(e)     => ToolResult.text(s"_${e.message}_")
      }

  private val neighbors =
    tool("neighbors").description("Typed references to/from a source (cites/refutes/challenges/updates/supersedes), as Markdown")
      .input[NeighborsInput]
      .handle(in => ToolResult.text(Markdown.neighbors(in.citekey, h.neighbors(in.citekey).toVector)))

  val endpoint = McpServer(tools = List(ping, search, getSource, geoWhen, histogram, neighbors)).endpoint(List("mcp"))

object McpTools:
  // Circe Codec + tapir Schema both derived from each input by Kindlings → matching by construction.
  final case class PingInput() derives KindlingsCodecAsObject
  object PingInput:
    given Schema[PingInput] = KindlingsSchema.derived[PingInput].schema

  final case class SearchInput(query: Option[String] = None, limit: Option[Int] = None) derives KindlingsCodecAsObject
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
      limit: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object GeoWhenInput:
    given Schema[GeoWhenInput] = KindlingsSchema.derived[GeoWhenInput].schema

  final case class NeighborsInput(citekey: String) derives KindlingsCodecAsObject
  object NeighborsInput:
    given Schema[NeighborsInput] = KindlingsSchema.derived[NeighborsInput].schema

  final case class HistogramInput(
      region: Option[String] = None,
      minLon: Option[Double] = None,
      minLat: Option[Double] = None,
      maxLon: Option[Double] = None,
      maxLat: Option[Double] = None,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None,
      unit: Option[String] = None
  ) derives KindlingsCodecAsObject
  object HistogramInput:
    given Schema[HistogramInput] = KindlingsSchema.derived[HistogramInput].schema
