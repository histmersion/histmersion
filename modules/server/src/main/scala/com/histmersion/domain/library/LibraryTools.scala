package com.histmersion
package domain.library
import com.histmersion.util.mcp.ChimpJson.given

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.aliases.AliasKinds

import com.histmersion.api.library.SearchMarkdown
import com.histmersion.api.library.SourcesMarkdown

import chimp.server.*
import com.histmersion.api.{Api, StrictJson, Write}
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.domain.library.citations.{CitationsControllers, CitationService}
import com.histmersion.domain.library.findings.FindingsControllers
import com.histmersion.domain.library.labels.LabelsControllers
import com.histmersion.domain.library.search.SearchControllers
import com.histmersion.domain.library.sources.{SourcesControllers, SourceService}
import com.histmersion.domain.library.search.FilterSql
import com.histmersion.util.data.mirror.Frontmatter
import com.histmersion.util.mcp.McpOut
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.aliases.Aliases
import com.histmersion.api.checks.FormatMarkdown
import com.histmersion.api.library.{Citekey as ApiCitekey, Filter, PassagesMarkdown}
import com.histmersion.api.library.Rename
import com.histmersion.api.library.Facet
import com.histmersion.api.library.Histogram
import com.histmersion.api.catalogue.Year
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.library.Sort
import io.github.iltotore.iron.constraint.any.Pure

/** The library's MCP tools, in their place among `/mcp`'s: the search and its vocabulary, one source read whole or by
  * part and its passages, the map feed, the histogram, the citation graph, and the writes of one source. `exportNote`
  * renders a source as its Markdown note (the mirror's).
  */
object LibraryTools:
  type StrictDoc = StrictDocValue
  type StrictDocValue = String
  object StrictDocValue:
    inline def apply(v: String): String = v
  type PassagesDoc = PassagesDocValue
  type PassagesDocValue = String
  object PassagesDocValue:
    inline def apply(v: String): String = v

  import com.histmersion.util.mcp.ToolInputs.given

  /** The `strict` flag of every Markdown-writing tool. */
  val StrictDoc: StrictDoc =
    "Refuse the write when its Markdown has a formatting ERROR (a table that will not render, unpaired **, " +
      "a broken link). Default false: the write goes through and the problems are listed under 'Formatting'."

  def apply(
      search: SearchControllers,
      labels: LabelsControllers,
      sources: SourcesControllers,
      findings: FindingsControllers,
      citations: CitationsControllers,
      exportNote: Citekey => Either[Api.Error, Frontmatter.NoteBody]
  ) =
    val searchTool =
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
            "nothing. Page with `limit`/`offset`; `sort` is relevance|cited|period|year|oldest|title. " +
            "USE `groupBy` TO SEE **WHEN** AND **WHERE** THE EVIDENCE SITS BEFORE READING SOURCES: " +
            "groupBy = time | region | time,region | region,time returns a nested tree of counts " +
            "instead of a list — adaptive time buckets, and/or the gazetteer from continents down — " +
            "with a few sample sources under each leaf. `depth` (1-3) is how deep it nests, `asOf` (a " +
            "signed year) is when the region hierarchy is read at (`time,region` reads each bucket at " +
            "its own midpoint, so the same site moves from Hittite Empire to Phrygia on its own). " +
            "Counts are NON-ADDITIVE: a source is counted in every bucket its period overlaps and in " +
            "every place that covers it, so children never sum to their parent — the tree says how " +
            "many are double-counted. Narrow `from`/`to` or `places` to one bucket and ask again to " +
            "drill into it. `verbose` (default true): set to false for a compact listing — citekey · " +
            "title · year · status · period · one why-line, no snippets/matches/summaries. " +
            "Use when scanning a large result set before drilling in. FTS note: an AND is evaluated " +
            "within one chunk, so `sea peoples pottery` matches only when all three words appear in " +
            "the same ~4 000-char section of one source."
        )
        .input[SearchInput]
        .handle { in =>
          in.groupBy.map(b => Api.AggregateQuery.By(b.value.trim)).filter(_.value.nonEmpty) match
            case None =>
              val f = in.toFilter
              val r = search.sources(f)
              ToolResult.text(if in.verbose.value then SearchMarkdown.searchResults(r)
              else SearchMarkdown.briefResults(r))
            case Some(by) =>
              // A5/A26: MCP defaults depth:1, samples:0 unless asked, so a bare groupBy never overwhelms
              val depth = in.depth.orElse(Some(Api.AggregateQuery.Depth(1)))
              val samp = in.samples.orElse(Some(Api.AggregateQuery.TreeSamples(0)))
              search.aggregate(Api.AggregateQuery.tree(by, in.toFilter, None, depth, in.asOf, samp)) match
                case Right(t) => ToolResult.text(SearchMarkdown.tree(t, SearchMarkdown.TreeUnit("sources")))
                case Left(e)  => McpOut.error(e)
        }

    val facets =
      tool("facets")
        .description(
          "List the vocabulary of one dimension — field = tags|authors|places|topics|status — with the " +
            "number of DISTINCT sources each value would leave inside the CURRENT filter. Use this " +
            "before `search` to learn which tags/authors/places/topics actually exist (and how big each " +
            "is), and after a search to see how to narrow it further. `prefix` does case-insensitive " +
            "prefix matching for autocompletion; the other parameters are the same filter as `search`."
        )
        .input[FacetsInput]
        .handle { in =>
          labels
            .facets(
              Facet.Field.Wire(in.field.value),
              in.prefix.map(p => FilterSql.FacetPrefix(p.value)),
              in.toFilter
            )
            .left
            .map(Api.Error(_)) match
            case Right(fs) => ToolResult.text(SearchMarkdown.facets(in.field, fs.toVector))
            case Left(e)   => McpOut.error(e)
        }

    val getSource =
      tool("get_source")
        .description(
          "Fetch one source by citekey. `section` selects what to return: summary (abstract), findings, " +
            "notes (the agent's article about this source), toc (table of contents: headings with byte " +
            "offsets into the body), body (the full converted text, sliceable), or all. Default returns " +
            "summary + findings + notes. `heading` returns only the body chunks under one heading " +
            "(case-insensitive prefix match). `offset`/`chars` slice the body: chars characters starting " +
            "at offset (0-based), with a trailing '…N more chars — pass offset=M' when truncated. " +
            "`fullText: true` is a shorthand for section=body without offset — capped at the configured " +
            "limit (default 60 000 chars). Bodies are large (mean 450 KB, max 5.7 MB) — to find a word or " +
            "a quotation inside one source use `passages`; otherwise prefer `search` snippets or a " +
            "`section`/`heading` slice over the full body. " +
            "Use after `search` to read what a source says — not to discover sources."
        )
        .input[GetSourceInput]
        .handle { in =>
          sources
            .markdown(
              in.citekey,
              SourcesControllers.MarkdownFullText(in.fullText.value),
              in.section,
              in.heading,
              in.offset,
              in.chars
            )
            .left
            .map(Api.Error(_)) match
            case Right(md) => ToolResult.text(md.value)
            case Left(e)   => ToolResult.text(s"_No source '${in.citekey}' — find a citekey with search._")
        }

    val passages =
      tool("passages")
        .description(PassagesDoc)
        .input[PassagesInput]
        .handle(in => ToolResult.text(passagesMarkdown(sources, in)))

    val geoWhen =
      tool("geo_when")
        .description(
          "Findings at a place and/or deep-time range, as Markdown. Takes the SAME filters as `search`: " +
            "`query` (free text), `tags` (ALL required), `authors`/`places` (any of), `topics`, `status`, " +
            "`yearFrom`/`yearTo` (publication years), `from`/`to` (signed years, BCE negative), bbox. " +
            "`region` is a deprecated alias for a `places` entry; `periodStart`/`periodEnd` are deprecated " +
            "aliases for `from`/`to`. A page at a time: `limit`, and `offset` from the footer's " +
            "`next: offset=N`. Use when you need to see individual findings rather than sources — " +
            "not when a source list with `search` would suffice."
        )
        .input[GeoWhenInput]
        .handle(in => ToolResult.text(SearchMarkdown.geoPage(findings.page(in.toQuery))))

    val histogram =
      tool("histogram")
        .description(
          "When is the evidence? Counts of sources (or findings) per adaptive time bucket across a " +
            "deep-time window. Takes the SAME filters as `search`: `query`, `tags` (ALL required), " +
            "`authors`/`places` (any of), `topics`, `status`, `yearFrom`/`yearTo`, `from`/`to` " +
            "(signed years, BCE negative), bbox. `unit` is `sources` (default) or `findings`. " +
            "`region` is a deprecated alias for a `places` entry; `periodStart`/`periodEnd` are " +
            "deprecated aliases for `from`/`to`. Returns a Markdown table with bar chart. " +
            "Use when you need to see the temporal shape of the evidence — not when individual " +
            "sources or findings are needed."
        )
        .input[HistogramInput]
        .handle { in =>
          val unit = in.unit.getOrElse(Api.AggregateQuery.Unit("sources"))
          search.aggregate(Api.AggregateQuery.time(in.toFilter, Some(unit))) match
            case Right(hist) => ToolResult.text(SearchMarkdown.histogram(hist, Histogram.Unit(unit.value)))
            case Left(e)     => McpOut.error(e)
        }

    val neighbors =
      tool("neighbors")
        .description(
          "Typed edges to/from a source: references, challenges, refutes, updates, superseded_by. " +
            "The output has two sections: **Sources** (edges where the target resolves to a real source, " +
            "with its status) and **Topics** (unresolved `references` targets collapsed to a list of " +
            "article slugs). Use `kinds` to filter by edge kind. An empty result is not evidence of " +
            "agreement — disagreement edges are extremely sparse in the corpus. Use `contradictions` " +
            "for a focused disagreement view, not this."
        )
        .input[NeighborsInput]
        .handle { in =>
          val rows = citations.neighbors(in.citekey, in.kinds)
          if rows.isEmpty then
            ToolResult.text(
              s"_No references recorded for **${in.citekey}**._ " +
                "An empty result is not evidence of agreement — disagreement edges are sparse."
            )
          else ToolResult.text(SourcesMarkdown.neighbors(ApiCitekey(in.citekey.value), rows.toVector))
        }

    val sourceGraph =
      tool("graph")
        .description(
          "Source dependency GRAPH: BFS traversal of the citation/relationship network around a " +
            "source, returning a D3-ready node+link structure. Each node carries citekey, title, " +
            "year, status and BFS depth. Links carry the edge kind. Use `depth` to control how " +
            "far to walk (0–4, default 2). Edge kinds include: references, challenges, refutes, " +
            "updates, superseded_by, supersedes, confirms, cites, foundational_for, " +
            "compilation_of, review_of, verifies."
        )
        .input[GraphInput]
        .handle { in =>
          val g = citations.graph(in.citekey, in.depth)
          ToolResult.text(SourcesMarkdown.graph(g))
        }

    val contradictions =
      tool("contradictions")
        .description(
          "Disagreement edges and contested statuses in the corpus. With a `citekey`: its " +
            "challenges/refutes/updates/superseded_by edges in both directions, with the other side's " +
            "year and status. Without: corpus-wide — every source that has such an edge OR whose status " +
            "is contested|superseded|debunked|retracted. `since` filters on the challenging source's " +
            "publication year. Edge kinds: references, challenges, refutes, updates, superseded_by. " +
            "An empty result is not evidence of agreement — disagreement edges are extremely sparse."
        )
        .input[ContradictionsInput]
        .handle { in =>
          val result = citations.contradictions(in.citekey, in.since)
          ToolResult.text(SourcesMarkdown.contradictions(result, in.citekey.map(c => ApiCitekey(c.value))))
        }

    // ── writes (plan §1.9a) ─────────────────────────────────────────────────────────────────────
    val PatchFirst =
      "To add or correct findings/tags/edges/notes on a source that already exists, use patch_source; " +
        "never re-upload the whole source."

    val putSource =
      tool("put_source")
        .description(
          "Create or REPLACE an entire source (metadata, summary, body, notes, topics, regions, tags, " +
            "period, findings, edges) under one citekey. Idempotent, and destructive: anything you omit " +
            "is erased and every finding is renumbered from #0. Edges are FROM this source, each " +
            "{dst,kind} (a src naming another source, or a self-edge, is refused). An unknown key is " +
            "refused, naming it. Use it only to CREATE a source or to " +
            s"deliberately re-import one. $PatchFirst"
        )
        .input[PutSourceInput]
        .handle { in =>
          sources.put(in.citekey, in.source, SourcesControllers.PutStrict(in.strict.value)) match
            case Right(sw) =>
              ToolResult.text(
                Aliases
                  .note(AliasKinds.SourceRecords, Aliases.Id(in.citekey.value), Aliases.Id(sw.source.citekey.value))(
                    Aliases.Note(
                      SourcesMarkdown.source(sw.source, sw.findings.toVector, false) + FormatMarkdown
                        .notice(sw.formatting)
                    )
                  )
                  .value
              )
            case Left(e) => McpOut.error(e)
        }

    val patchSource =
      tool("patch_source")
        .description(
          "Selectively enrich an existing source: send ONLY the parts you are changing; everything else " +
            "is left exactly as it is. Parts: metadata{title,authors,year,venue,doi,status,provenance," +
            "calibreId} (null CLEARS year/venue/doi/provenance/calibreId), summary, " +
            "body, notes (your compiled article about the source), topics, regions, period{start,end} " +
            "(signed years, BCE negative), tags{add,remove,replace}, findings{add,remove(ids),replace}, " +
            "edges{add,remove} — edges FROM this source, each {dst,kind}: to record a --refutes--> b, " +
            "patch a (a src naming another source is refused). Findings keep stable ids '<citekey>#<n>', so one can be removed or " +
            "re-added individually. Pass ifUpdatedAt with the updatedAt you last read to be told — " +
            "instead of silently clobbering — when someone else changed the source meanwhile. An " +
            "unknown or misplaced key (e.g. status outside metadata) is refused, naming it and where it " +
            "belongs. The answer lists formatting problems in the summary and notes; `strict: true` " +
            s"refuses a patch whose summary or notes have a formatting error. $PatchFirst"
        )
        .input[PatchSourceInput]
        .handle { in =>
          sources.patch(in.citekey, in.patch, SourcesControllers.PatchStrict(in.strict.value)) match
            case Right(sw) =>
              ToolResult.text(
                Aliases
                  .note(AliasKinds.SourceRecords, Aliases.Id(in.citekey.value), Aliases.Id(sw.source.citekey.value))(
                    Aliases.Note(
                      SourcesMarkdown.source(sw.source, sw.findings.toVector, false) + FormatMarkdown
                        .notice(sw.formatting)
                    )
                  )
                  .value
              )
            case Left(e) => McpOut.error(e)
        }

    val renameSource =
      tool("rename_source")
        .description(
          "Give a source a NEW citekey — to correct a misleading one (e.g. a year that is not the " +
            "publication year: page2024_history for a 1959 book → page1959_history). One transaction: " +
            "the findings are renumbered '<new>#<n>', every table that stores the citekey follows, and " +
            "every [[old]] link (labels and anchors kept, short forms like [[beckman2025]] included) " +
            "is rewritten in article bodies — each rewrite recorded as an article version — and in " +
            s"source notes/summaries. ${Aliases.rule(AliasKinds.SourceRecords)} (get_source, neighbors, graph, " +
            "export_source, /source/<old> and [[old]] included.) Refused when the " +
            "new citekey is taken (by a source, or as an old citekey of another source) or is not " +
            "link-shaped. Use it only to fix an identifier; to change metadata (year, title) use " +
            "patch_source."
        )
        .input[RenameSourceInput]
        .handle { in =>
          sources.rename(in.citekey, Rename.Request(in.newCitekey)) match
            case Right(r) => ToolResult.text(SourcesMarkdown.sourceRenamed(r))
            case Left(e)  => McpOut.error(e)
        }

    val exportSource =
      tool("export_source")
        .description(
          "Render a source as the Markdown note it would be on disk: YAML frontmatter (citekey, doi, " +
            "title, authors, year, venue, status, topics, regions, tags, period_start/period_end, " +
            "summary, findings) plus the notes body. This is the git-friendly mirror of the database " +
            s"and it round-trips back in through seed. $PatchFirst"
        )
        .input[ExportSourceInput]
        .handle { in =>
          exportNote(in.citekey) match
            case Right(text) => ToolResult.text(text.value)
            case Left(e)     => McpOut.error(e)
        }
    List(
      searchTool,
      facets,
      getSource,
      passages,
      geoWhen,
      histogram,
      neighbors,
      sourceGraph,
      contradictions,
      putSource,
      patchSource,
      renameSource,
      exportSource
    )

  /** The MCP spelling of [[com.histmersion.api.library.Filter]] — flat and all-optional, so a model can fill in only
    * what it knows. `Filter.of` is the single interpreter of this shape (REST and CLI share it).
    */
  final case class SearchInput(
      query: Option[Filter.Q] = None,
      tags: List[Filter.Tags] = Nil,
      authors: List[Filter.Authors] = Nil,
      places: List[Filter.Places] = Nil,
      topics: List[Filter.Topics] = Nil,
      status: List[Filter.Status] = Nil,
      yearFrom: Option[Year] = None,
      yearTo: Option[Year] = None,
      from: Option[Year] = None,
      to: Option[Year] = None,
      minLon: Option[Longitude] = None,
      minLat: Option[Latitude] = None,
      maxLon: Option[Longitude] = None,
      maxLat: Option[Latitude] = None,
      sort: Option[Sort.Wire] = None,
      limit: Option[Filter.Limit] = None,
      offset: Option[Filter.Offset] = None,
      title: Option[Filter.Title] = None,
      // ── the §1.6 nested grouping. Absent = the ordinary list of hits. ──
      groupBy: Option[Api.AggregateQuery.By] = None,
      depth: Option[Api.AggregateQuery.Depth] = None,
      asOf: Option[Api.AggregateQuery.TreeAsOf] = None,
      samples: Option[Api.AggregateQuery.TreeSamples] = None,
      verbose: SearchInput.Verbose = SearchInput.Verbose(true)
  ) derives JsonCodec:
    def toFilter: Filter = Filter.of(
      query,
      tags,
      authors,
      places,
      topics,
      status,
      yearFrom,
      yearTo,
      from,
      to,
      minLon,
      minLat,
      maxLon,
      maxLat,
      sort,
      limit,
      offset,
      title
    )
  object SearchInput:
    // the filter's fields are described once, in FilterDocs (the REST parameters and the CLI flags read it too)
    type Verbose = Verbose.T
    object Verbose extends Newtype[Boolean, Pure]

    given Schema[SearchInput] = Filter.Docs
      .describe(KindlingsSchema.derived[SearchInput].schema)
      .modify(_.groupBy)(_.description("Group into a tree instead of listing: time|region|time,region|region,time"))
      .modify(_.depth)(_.description("Nesting levels for groupBy (1-3; default 1 for MCP)"))
      .modify(_.asOf)(_.description("Signed year the gazetteer hierarchy is read at for groupBy=region"))
      .modify(_.samples)(_.description("Sample sources per leaf (0-5; default 0 for MCP)"))
      .modify(_.verbose)(
        _.description(
          "false = compact listing: citekey/title/year/status/period/one-why-line, no snippets/matches/summaries"
        )
      )

  final case class FacetsInput(
      field: Facet.FieldText,
      prefix: Option[Api.Facets.Prefix] = None,
      query: Option[Filter.Q] = None,
      tags: List[Filter.Tags] = Nil,
      authors: List[Filter.Authors] = Nil,
      places: List[Filter.Places] = Nil,
      topics: List[Filter.Topics] = Nil,
      status: List[Filter.Status] = Nil,
      yearFrom: Option[Year] = None,
      yearTo: Option[Year] = None,
      from: Option[Year] = None,
      to: Option[Year] = None,
      minLon: Option[Longitude] = None,
      minLat: Option[Latitude] = None,
      maxLon: Option[Longitude] = None,
      maxLat: Option[Latitude] = None,
      limit: Option[Filter.Limit] = None
  ) derives JsonCodec:
    def toFilter: Filter = Filter.of(
      query,
      tags,
      authors,
      places,
      topics,
      status,
      yearFrom,
      yearTo,
      from,
      to,
      minLon,
      minLat,
      maxLon,
      maxLat,
      None,
      limit,
      None
    )
  object FacetsInput:

    given Schema[FacetsInput] = Filter.Docs
      .describe(KindlingsSchema.derived[FacetsInput].schema)
      .modify(_.field)(_.description("Dimension to enumerate: tags|authors|places|topics|status|provenance"))
      .modify(_.prefix)(_.description("Case-insensitive prefix match for autocompletion"))
      .modify(_.limit)(_.description("At most this many values"))

  final case class GetSourceInput(
      citekey: Citekey,
      fullText: GetSourceInput.FullText = GetSourceInput.FullText(false),
      section: Option[com.histmersion.api.library.Source.Section.Wire] = None,
      heading: Option[SourceService.Heading] = None,
      offset: Option[GetSourceInput.Offset] = None,
      chars: Option[SourcesControllers.Chars] = None
  ) derives JsonCodec
  object GetSourceInput:
    type FullText = FullText.T
    object FullText extends Newtype[Boolean, Pure]
    type Offset = com.histmersion.Offset
    val Offset: com.histmersion.Offset.type = com.histmersion.Offset

    given Schema[GetSourceInput] = KindlingsSchema
      .derived[GetSourceInput]
      .schema
      .modify(_.citekey)(_.description("The source's citekey (e.g. haak2015_massive)"))
      .modify(_.fullText)(
        _.description("Include the full converted text (shorthand for section=body; capped at the configured limit)")
      )
      .modify(_.section)(
        _.description("What to return: summary|findings|notes|toc|body|all (default: summary+findings+notes)")
      )
      .modify(_.heading)(
        _.description("Return only chunks under this heading (case-insensitive prefix match on source_chunks.heading)")
      )
      .modify(_.offset)(_.description("Body slice start (0-based character offset)"))
      .modify(_.chars)(_.description("Body slice length in characters (default: configured full-text-chars limit)"))

  /** What `passages` does — shared by the MCP tool and the agents' toolbox. */
  val PassagesDoc: PassagesDoc =
    "Find a word, phrase or quotation inside ONE source's full text — `grep` over its body. Returns the " +
      "passages around each match (the match in **bold**), each with its `offset` into the body (pass it " +
      "to `get_source` as `offset` to read on) and the heading it sits under, then how many more there " +
      "are. `query` is matched LITERALLY (no stemming), case-insensitive, accents optional (`Hattusa` " +
      "finds `Ḫattuša`), curly quotes and dashes as plain ones; its words may span a line break. " +
      "`regex: true` makes it a Java regex (`^`/`$` per line). `limit` passages (default 10, max 50), " +
      "`context` characters around each match (default 600, max 3000). Use it to check what a source " +
      "says about a term instead of paging through `get_source` slices."

  /** `passages` as Markdown, with the same wording for an unknown citekey as `get_source`. */
  def passagesMarkdown(sources: SourcesControllers, in: PassagesInput): String =
    sources.passages(Api.PassagesQuery(ApiCitekey(in.citekey.value), in.query, in.regex, in.limit, in.context)) match
      case Right(r)                                                       => PassagesMarkdown.render(r)
      case Left(e) if e.code.contains(Api.Error.Code(Write.CodeNotFound)) =>
        s"_No source '${in.citekey}' — find a citekey with search._"
      case Left(e) => McpOut.refusal(e)

  final case class PassagesInput(
      citekey: Citekey,
      query: Api.PassagesQuery.Q,
      regex: Api.PassagesQuery.Regex = Api.PassagesQuery.Regex(false),
      limit: Option[Api.PassagesQuery.Limit] = None,
      context: Option[Api.PassagesQuery.Context] = None
  ) derives JsonCodec
  object PassagesInput:

    given Schema[PassagesInput] = KindlingsSchema
      .derived[PassagesInput]
      .schema
      .modify(_.citekey)(_.description("The source's citekey (an old, renamed one works too)"))
      .modify(_.query)(
        _.description("The text to find, matched literally (case- and accent-insensitive); a regex when regex=true")
      )
      .modify(_.regex)(_.description("Treat query as a Java regular expression (default false)"))
      .modify(_.limit)(_.description("Passages to show (default 10, max 50)"))
      .modify(_.context)(_.description("Characters of context around each match (default 600, max 3000)"))

  /** The MCP spelling of [[com.histmersion.api.library.Filter]] for `geo_when` — now the FULL filter shape (A2), so
    * `tags`/`authors`/`status`/`topics` work. `region` is an alias for a `places` entry (A1). `periodStart`/`periodEnd`
    * are deprecated aliases for `from`/`to` — kept for one release.
    */
  final case class GeoWhenInput(
      query: Option[Filter.Q] = None,
      tags: List[Filter.Tags] = Nil,
      authors: List[Filter.Authors] = Nil,
      places: List[Filter.Places] = Nil,
      topics: List[Filter.Topics] = Nil,
      status: List[Filter.Status] = Nil,
      yearFrom: Option[Year] = None,
      yearTo: Option[Year] = None,
      from: Option[Year] = None,
      to: Option[Year] = None,
      minLon: Option[Longitude] = None,
      minLat: Option[Latitude] = None,
      maxLon: Option[Longitude] = None,
      maxLat: Option[Latitude] = None,
      limit: Option[Filter.Limit] = None,
      /** @deprecated use `from` */
      periodStart: Option[Year] = None,
      /** @deprecated use `to` */
      periodEnd: Option[Year] = None,
      /** @deprecated use `places` */
      region: Option[Filter.Places] = None,
      offset: Option[Filter.Offset] = None
  ) derives JsonCodec:
    def toQuery: Api.FindingsQuery =
      val effectiveFrom = from.orElse(periodStart)
      val effectiveTo = to.orElse(periodEnd)
      val effectivePlaces = places ++ region.map(r => Filter.Places(r.value.trim)).filter(_.value.nonEmpty).toList
      Api.FindingsQuery(
        Filter.of(
          query,
          tags,
          authors,
          effectivePlaces,
          topics,
          status,
          yearFrom,
          yearTo,
          effectiveFrom,
          effectiveTo,
          minLon,
          minLat,
          maxLon,
          maxLat,
          None,
          limit,
          offset
        ),
        None
      ) // region is folded into places, not passed separately
  object GeoWhenInput:

    given Schema[GeoWhenInput] = Filter.Docs
      .describe(KindlingsSchema.derived[GeoWhenInput].schema)
      .modify(_.region)(_.description("Deprecated alias for a places entry"))
      .modify(_.periodStart)(_.description("Deprecated alias for from"))
      .modify(_.periodEnd)(_.description("Deprecated alias for to"))

  final case class NeighborsInput(citekey: Citekey, kinds: List[NeighborsInput.Kinds] = Nil) derives JsonCodec
  object NeighborsInput:
    type Kinds = Edge.Kind.Wire
    val Kinds: Edge.Kind.Wire.type = Edge.Kind.Wire

    given Schema[NeighborsInput] = KindlingsSchema
      .derived[NeighborsInput]
      .schema
      .modify(_.citekey)(_.description("The source's citekey"))
      .modify(_.kinds)(
        _.description("Filter to these edge kinds only: references|challenges|refutes|updates|superseded_by")
      )

  final case class GraphInput(
      citekey: Citekey,
      depth: Option[CitationService.Depth] = None
  ) derives JsonCodec
  object GraphInput:

    given Schema[GraphInput] = KindlingsSchema
      .derived[GraphInput]
      .schema
      .modify(_.citekey)(_.description("Root source citekey to start BFS from"))
      .modify(_.depth)(_.description("BFS depth 0–4 (default 2)"))

  final case class ContradictionsInput(
      citekey: Option[Citekey] = None,
      since: Option[CitationService.Since] = None
  ) derives JsonCodec
  object ContradictionsInput:

    given Schema[ContradictionsInput] = KindlingsSchema
      .derived[ContradictionsInput]
      .schema
      .modify(_.citekey)(_.description("Show contradictions for this source only; omit for corpus-wide"))
      .modify(_.since)(_.description("Filter by the challenging source's publication year (signed year, BCE negative)"))

  // The write tools reuse the REST DTOs verbatim, so the MCP tool schema and the HTTP body are the
  // same shape — one contract, two transports.
  final case class PutSourceInput(
      citekey: Citekey,
      source: com.histmersion.api.library.Source.Put,
      strict: PutSourceInput.Strict = PutSourceInput.Strict(false)
  ) derives StrictJson
  object PutSourceInput:
    type Strict = Strict.T
    object Strict extends Newtype[Boolean, Pure]

    given Schema[PutSourceInput] = KindlingsSchema
      .derived[PutSourceInput]
      .schema
      .modify(_.strict)(_.description(StrictDoc))

  final case class PatchSourceInput(
      citekey: Citekey,
      patch: com.histmersion.api.library.Source.Patch,
      strict: PatchSourceInput.Strict = PatchSourceInput.Strict(false)
  ) derives StrictJson
  object PatchSourceInput:
    type Strict = Strict.T
    object Strict extends Newtype[Boolean, Pure]

    given Schema[PatchSourceInput] = KindlingsSchema
      .derived[PatchSourceInput]
      .schema
      .modify(_.strict)(_.description(StrictDoc))

  final case class RenameSourceInput(citekey: Citekey, newCitekey: com.histmersion.api.library.Citekey)
      derives JsonCodec
  object RenameSourceInput:

    given Schema[RenameSourceInput] = KindlingsSchema
      .derived[RenameSourceInput]
      .schema
      .modify(_.citekey)(_.description("The source's current citekey"))
      .modify(_.newCitekey)(_.description("The citekey it should have from now on"))

  final case class ExportSourceInput(citekey: Citekey)
      derives JsonCodec,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  /** The MCP spelling of [[com.histmersion.api.library.Filter]] for `histogram` — now the FULL filter shape (A2).
    * `periodStart`/`periodEnd` and `region` are deprecated aliases (A1).
    */
  final case class HistogramInput(
      query: Option[Filter.Q] = None,
      tags: List[Filter.Tags] = Nil,
      authors: List[Filter.Authors] = Nil,
      places: List[Filter.Places] = Nil,
      topics: List[Filter.Topics] = Nil,
      status: List[Filter.Status] = Nil,
      yearFrom: Option[Year] = None,
      yearTo: Option[Year] = None,
      from: Option[Year] = None,
      to: Option[Year] = None,
      minLon: Option[Longitude] = None,
      minLat: Option[Latitude] = None,
      maxLon: Option[Longitude] = None,
      maxLat: Option[Latitude] = None,
      sort: Option[Sort.Wire] = None,
      limit: Option[Filter.Limit] = None,
      unit: Option[Api.AggregateQuery.Unit] = None,
      /** @deprecated use `from` */
      periodStart: Option[Year] = None,
      /** @deprecated use `to` */
      periodEnd: Option[Year] = None,
      /** @deprecated use `places` */
      region: Option[Filter.Places] = None
  ) derives JsonCodec:
    def toFilter: Filter =
      val effectiveFrom = from.orElse(periodStart)
      val effectiveTo = to.orElse(periodEnd)
      val effectivePlaces = places ++ region.map(r => Filter.Places(r.value.trim)).filter(_.value.nonEmpty).toList
      Filter.of(
        query,
        tags,
        authors,
        effectivePlaces,
        topics,
        status,
        yearFrom,
        yearTo,
        effectiveFrom,
        effectiveTo,
        minLon,
        minLat,
        maxLon,
        maxLat,
        sort,
        limit,
        None
      )
  object HistogramInput:

    given Schema[HistogramInput] = Filter.Docs
      .describe(KindlingsSchema.derived[HistogramInput].schema)
      .modify(_.unit)(_.description("What to count: sources (default) | findings"))
