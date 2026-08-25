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
    tool("search")
      .description(
        "Search the corpus. The SOURCE (paper/book) is the unit of every result: each hit lists the " +
          "findings that made it match (claim, place, period) and why it matched, so you can judge " +
          "relevance without opening it. Combine free text (`query`) with any filters: `tags` (ALL " +
          "must be present), `authors`/`places` (any of), `topics` (all), `status`, publication years " +
          "(`yearFrom`/`yearTo`), deep-time overlap (`from`/`to`, signed years — BCE is negative, so " +
          "1200 BCE is -1200) and a lon/lat box. A source matches a period/box/place through its own " +
          "metadata OR any of its findings. Use the `facets` tool FIRST to discover the tag/author/" +
          "place/topic vocabulary instead of guessing terms; a filter with an unknown tag returns " +
          "nothing. Page with `limit`/`offset`; `sort` is relevance|cited|period|year|title.")
      .input[SearchInput]
      .handle(in => ToolResult.text(Markdown.searchResults(h.sources(in.toFilter))))

  private val facets =
    tool("facets")
      .description(
        "List the vocabulary of one dimension — field = tags|authors|places|topics|status — with the " +
          "number of DISTINCT sources each value would leave inside the CURRENT filter. Use this " +
          "before `search` to learn which tags/authors/places/topics actually exist (and how big each " +
          "is), and after a search to see how to narrow it further. `prefix` does case-insensitive " +
          "prefix matching for autocompletion; the other parameters are the same filter as `search`.")
      .input[FacetsInput]
      .handle { in =>
        h.facets(in.field, in.prefix, in.toFilter) match
          case Right(fs) => ToolResult.text(Markdown.facets(in.field, fs.toVector))
          case Left(e)   => ToolResult.text(s"_${e.message}_")
      }

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

  private val neighbors =
    tool("neighbors").description("Typed references to/from a source (cites/refutes/challenges/updates/supersedes), as Markdown")
      .input[NeighborsInput]
      .handle(in => ToolResult.text(Markdown.neighbors(in.citekey, h.neighbors(in.citekey).toVector)))

  val endpoint =
    McpServer(tools = List(ping, search, facets, getSource, geoWhen, neighbors)).endpoint(List("mcp"))

object McpTools:
  // Circe Codec + tapir Schema both derived from each input by Kindlings → matching by construction.
  // `withDefaults` makes the DECODER honour the Scala default of every field, which is what the
  // advertised JSON schema already promises (nothing but `field` is `required`): an LLM may send
  // `{"tags":["x"]}` and the omitted lists arrive as `Nil` instead of failing to decode.
  given hearth.kindlings.circederivation.Configuration =
    hearth.kindlings.circederivation.Configuration.default.withDefaults

  final case class PingInput() derives KindlingsCodecAsObject
  object PingInput:
    given Schema[PingInput] = KindlingsSchema.derived[PingInput].schema

  /** The MCP spelling of [[histdb.core.Filter]] — flat and all-optional, so a model can fill in only
    * what it knows. `Filter.of` is the single interpreter of this shape (REST and CLI share it). */
  final case class SearchInput(
      query: Option[String] = None,
      tags: List[String] = Nil,
      authors: List[String] = Nil,
      places: List[String] = Nil,
      topics: List[String] = Nil,
      status: List[String] = Nil,
      yearFrom: Option[Long] = None,
      yearTo: Option[Long] = None,
      from: Option[Long] = None,
      to: Option[Long] = None,
      minLon: Option[Double] = None,
      minLat: Option[Double] = None,
      maxLon: Option[Double] = None,
      maxLat: Option[Double] = None,
      sort: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives KindlingsCodecAsObject:
    def toFilter: Filter = Filter.of(query, tags, authors, places, topics, status, yearFrom, yearTo,
      from, to, minLon, minLat, maxLon, maxLat, sort, limit, offset)
  object SearchInput:
    given Schema[SearchInput] = KindlingsSchema.derived[SearchInput].schema

  final case class FacetsInput(
      field: String,
      prefix: Option[String] = None,
      query: Option[String] = None,
      tags: List[String] = Nil,
      authors: List[String] = Nil,
      places: List[String] = Nil,
      topics: List[String] = Nil,
      status: List[String] = Nil,
      yearFrom: Option[Long] = None,
      yearTo: Option[Long] = None,
      from: Option[Long] = None,
      to: Option[Long] = None,
      minLon: Option[Double] = None,
      minLat: Option[Double] = None,
      maxLon: Option[Double] = None,
      maxLat: Option[Double] = None,
      limit: Option[Int] = None
  ) derives KindlingsCodecAsObject:
    def toFilter: Filter = Filter.of(query, tags, authors, places, topics, status, yearFrom, yearTo,
      from, to, minLon, minLat, maxLon, maxLat, None, limit, None)
  object FacetsInput:
    given Schema[FacetsInput] = KindlingsSchema.derived[FacetsInput].schema

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
