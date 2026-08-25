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
      .handle(in => ToolResult.text(Markdown.geoResults(h.findings(in.toQuery).toVector)))

  private val histogram =
    tool("histogram")
      .description("When is the evidence? Counts of sources (or findings) per adaptive time bucket " +
        "across a deep-time window, optionally narrowed by region/bbox — returns a Markdown table + bar chart")
      .input[HistogramInput]
      .handle { in =>
        val unit = in.unit.getOrElse("sources")
        h.aggregate(Api.AggregateQuery.time(in.toFilter, Some(unit))) match
          case Right(hist) => ToolResult.text(Markdown.histogram(hist, unit))
          case Left(e)     => ToolResult.text(s"_${e.message}_")
      }

  private val neighbors =
    tool("neighbors").description("Typed references to/from a source (cites/refutes/challenges/updates/supersedes), as Markdown")
      .input[NeighborsInput]
      .handle(in => ToolResult.text(Markdown.neighbors(in.citekey, h.neighbors(in.citekey).toVector)))

  // ── writes (plan §1.9a) ─────────────────────────────────────────────────────────────────────
  private val PatchFirst =
    "To add or correct findings/tags/edges/notes on a source that already exists, use patch_source; " +
      "never re-upload the whole source."

  private val putSource =
    tool("put_source")
      .description(
        "Create or REPLACE an entire source (metadata, summary, body, notes, topics, regions, tags, " +
          "period, findings, edges) under one citekey. Idempotent, and destructive: anything you omit " +
          "is erased and every finding is renumbered from #0. Use it only to CREATE a source or to " +
          s"deliberately re-import one. $PatchFirst")
      .input[PutSourceInput]
      .handle { in =>
        h.putSource(in.citekey, in.source) match
          case Right(sw) => ToolResult.text(Markdown.source(sw.source, sw.findings.toVector, false))
          case Left(e)   => ToolResult.text(s"_${e.message}_")
      }

  private val patchSource =
    tool("patch_source")
      .description(
        "Selectively enrich an existing source: send ONLY the parts you are changing; everything else " +
          "is left exactly as it is. Parts: metadata{title,authors,year,venue,doi,status}, summary, " +
          "body, notes (your compiled article about the source), topics, regions, period{start,end} " +
          "(signed years, BCE negative), tags{add,remove,replace}, findings{add,remove(ids),replace}, " +
          "edges{add,remove}. Findings keep stable ids '<citekey>#<n>', so one can be removed or " +
          "re-added individually. Pass ifUpdatedAt with the updatedAt you last read to be told — " +
          "instead of silently clobbering — when someone else changed the source meanwhile. " +
          s"$PatchFirst")
      .input[PatchSourceInput]
      .handle { in =>
        h.patchSource(in.citekey, in.patch) match
          case Right(sw) => ToolResult.text(Markdown.source(sw.source, sw.findings.toVector, false))
          case Left(e)   => ToolResult.text(s"_${e.message}_")
      }

  private val exportSource =
    tool("export_source")
      .description(
        "Render a source as the Markdown note it would be on disk: YAML frontmatter (citekey, doi, " +
          "title, authors, year, venue, status, topics, regions, tags, period_start/period_end, " +
          "summary, findings) plus the notes body. This is the git-friendly mirror of the database " +
          s"and it round-trips back in through seed. $PatchFirst")
      .input[ExportSourceInput]
      .handle { in =>
        h.exportSource(in.citekey) match
          case Right(text) => ToolResult.text(text)
          case Left(e)     => ToolResult.text(s"_${e.message}_")
      }

  val endpoint = McpServer(tools =
    List(ping, search, facets, getSource, geoWhen, histogram, neighbors, putSource, patchSource, exportSource)
      ++ OpsTools(h.ops))  // stream 2.8: lint · queue · query
    .endpoint(List("mcp"))

object McpTools:
  /** Honour Scala default values when DECODING a tool's arguments — without it Kindlings requires
    * every non-Option field, so `get_source {"citekey":"x"}` was rejected for a missing `fullText`.
    * (`histdb.core` carries the same given for the wire DTOs.) */
  given hearth.kindlings.circederivation.Configuration =
    hearth.kindlings.circederivation.Configuration.default.withDefaults

  // Circe Codec + tapir Schema both derived from each input by Kindlings → matching by construction.
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

  /** `region` is the tool's spelling of `Filter.places` (a case-insensitive region-name substring);
    * everything else is the shared filter shape. */
  final case class GeoWhenInput(
      region: Option[String] = None,
      minLon: Option[Double] = None,
      minLat: Option[Double] = None,
      maxLon: Option[Double] = None,
      maxLat: Option[Double] = None,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None,
      limit: Option[Int] = None
  ) derives KindlingsCodecAsObject:
    def toQuery: Api.FindingsQuery = Api.FindingsQuery(
      Filter.of(from = periodStart, to = periodEnd, minLon = minLon, minLat = minLat,
        maxLon = maxLon, maxLat = maxLat, limit = limit),
      region)
  object GeoWhenInput:
    given Schema[GeoWhenInput] = KindlingsSchema.derived[GeoWhenInput].schema

  final case class NeighborsInput(citekey: String) derives KindlingsCodecAsObject
  object NeighborsInput:
    given Schema[NeighborsInput] = KindlingsSchema.derived[NeighborsInput].schema

  // The write tools reuse the REST DTOs verbatim, so the MCP tool schema and the HTTP body are the
  // same shape — one contract, two transports.
  final case class PutSourceInput(citekey: String, source: SourcePut) derives KindlingsCodecAsObject
  object PutSourceInput:
    given Schema[PutSourceInput] = KindlingsSchema.derived[PutSourceInput].schema

  final case class PatchSourceInput(citekey: String, patch: SourcePatch) derives KindlingsCodecAsObject
  object PatchSourceInput:
    given Schema[PatchSourceInput] = KindlingsSchema.derived[PatchSourceInput].schema

  final case class ExportSourceInput(citekey: String) derives KindlingsCodecAsObject
  object ExportSourceInput:
    given Schema[ExportSourceInput] = KindlingsSchema.derived[ExportSourceInput].schema
  final case class HistogramInput(
      region: Option[String] = None,
      minLon: Option[Double] = None,
      minLat: Option[Double] = None,
      maxLon: Option[Double] = None,
      maxLat: Option[Double] = None,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None,
      unit: Option[String] = None
  ) derives KindlingsCodecAsObject:
    /** `region` is the tool's spelling of `Filter.places`, as in `geo_when`. */
    def toFilter: Filter = Filter.of(places = region.toList, from = periodStart, to = periodEnd,
      minLon = minLon, minLat = minLat, maxLon = maxLon, maxLat = maxLat)
  object HistogramInput:
    given Schema[HistogramInput] = KindlingsSchema.derived[HistogramInput].schema
