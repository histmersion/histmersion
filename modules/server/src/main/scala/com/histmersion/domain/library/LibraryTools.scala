package com.histmersion.domain.library

import com.histmersion.api.aliases.AliasKinds

import com.histmersion.api.library.SearchMarkdown
import com.histmersion.api.library.SourcesMarkdown

import chimp.server.*
import com.histmersion.api.{Api, StrictJson, Write}
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.domain.library.citations.CitationsControllers
import com.histmersion.domain.library.findings.FindingsControllers
import com.histmersion.domain.library.labels.LabelsControllers
import com.histmersion.domain.library.search.SearchControllers
import com.histmersion.domain.library.sources.SourcesControllers
import com.histmersion.util.mcp.McpOut
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.api.aliases.Aliases
import com.histmersion.api.checks.FormatMarkdown
import com.histmersion.api.library.{Filter, PassagesMarkdown}
import com.histmersion.api.library.Rename

/** The library's MCP tools, in their place among `/mcp`'s: the search and its vocabulary, one source read whole or by
  * part and its passages, the map feed, the histogram, the citation graph, and the writes of one source. `exportNote`
  * renders a source as its Markdown note (the mirror's).
  */
object LibraryTools:
  type StrictDoc = StrictDoc.T
  object StrictDoc extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyExportNoteArg1 = ApplyExportNoteArg1.T
  object ApplyExportNoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyExportNoteResultPart2 = ApplyExportNoteResultPart2.T
  object ApplyExportNoteResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PassagesDoc = PassagesDoc.T
  object PassagesDoc extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PassagesMarkdownResult = PassagesMarkdownResult.T
  object PassagesMarkdownResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  import com.histmersion.util.mcp.ToolInputs.given

  /** The `strict` flag of every Markdown-writing tool. */
  val StrictDoc: com.histmersion.domain.library.LibraryTools.StrictDoc =
    "Refuse the write when its Markdown has a formatting ERROR (a table that will not render, unpaired **, " +
      "a broken link). Default false: the write goes through and the problems are listed under 'Formatting'."

  def apply(
      search: SearchControllers,
      labels: LabelsControllers,
      sources: SourcesControllers,
      findings: FindingsControllers,
      citations: CitationsControllers,
      exportNote: com.histmersion.domain.library.LibraryTools.ApplyExportNoteArg1 => Either[Api.Error, com.histmersion.domain.library.LibraryTools.ApplyExportNoteResultPart2]
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
          in.groupBy.map(_.trim).filter(_.nonEmpty) match
            case None =>
              val f = in.toFilter
              val r = search.sources(f)
              ToolResult.text(if in.verbose then SearchMarkdown.searchResults(r)
              else SearchMarkdown.briefResults(r))
            case Some(by) =>
              // A5/A26: MCP defaults depth:1, samples:0 unless asked, so a bare groupBy never overwhelms
              val depth = in.depth.orElse(Some(1))
              val samp = in.samples.orElse(Some(0))
              search.aggregate(Api.AggregateQuery.tree(by, in.toFilter, None, depth, in.asOf, samp)) match
                case Right(t) => ToolResult.text(SearchMarkdown.tree(t, "sources"))
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
          labels.facets(in.field, in.prefix, in.toFilter) match
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
          sources.markdown(in.citekey, in.fullText, in.section, in.heading, in.offset, in.chars) match
            case Right(md) => ToolResult.text(md)
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
          val unit = in.unit.getOrElse("sources")
          search.aggregate(Api.AggregateQuery.time(in.toFilter, Some(unit))) match
            case Right(hist) => ToolResult.text(SearchMarkdown.histogram(hist, unit))
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
          else ToolResult.text(SourcesMarkdown.neighbors(in.citekey, rows.toVector))
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
          ToolResult.text(SourcesMarkdown.contradictions(result, in.citekey))
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
          sources.put(in.citekey, in.source, in.strict) match
            case Right(sw) =>
              ToolResult.text(
                Aliases.note(AliasKinds.SourceRecords, in.citekey, sw.source.citekey.value)(
                  SourcesMarkdown.source(sw.source, sw.findings.toVector, false) + FormatMarkdown.notice(sw.formatting)
                )
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
          sources.patch(in.citekey, in.patch, in.strict) match
            case Right(sw) =>
              ToolResult.text(
                Aliases.note(AliasKinds.SourceRecords, in.citekey, sw.source.citekey.value)(
                  SourcesMarkdown.source(sw.source, sw.findings.toVector, false) + FormatMarkdown.notice(sw.formatting)
                )
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
            case Right(text) => ToolResult.text(text)
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
      query: Option[SearchInput.Query] = None,
      tags: List[SearchInput.Tags] = Nil,
      authors: List[SearchInput.Authors] = Nil,
      places: List[SearchInput.Places] = Nil,
      topics: List[SearchInput.Topics] = Nil,
      status: List[SearchInput.Status] = Nil,
      yearFrom: Option[SearchInput.YearFrom] = None,
      yearTo: Option[SearchInput.YearTo] = None,
      from: Option[SearchInput.From] = None,
      to: Option[SearchInput.To] = None,
      minLon: Option[SearchInput.MinLon] = None,
      minLat: Option[SearchInput.MinLat] = None,
      maxLon: Option[SearchInput.MaxLon] = None,
      maxLat: Option[SearchInput.MaxLat] = None,
      sort: Option[SearchInput.Sort] = None,
      limit: Option[SearchInput.Limit] = None,
      offset: Option[SearchInput.Offset] = None,
      title: Option[SearchInput.Title] = None,
      // ── the §1.6 nested grouping. Absent = the ordinary list of hits. ──
      groupBy: Option[SearchInput.GroupBy] = None,
      depth: Option[SearchInput.Depth] = None,
      asOf: Option[SearchInput.AsOf] = None,
      samples: Option[SearchInput.Samples] = None,
      verbose: SearchInput.Verbose = true
  ) derives KindlingsCodecAsObject:
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
    type Query = Query.T
    object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Authors = Authors.T
    object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Places = Places.T
    object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Topics = Topics.T
    object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type YearFrom = YearFrom.T
    object YearFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type YearTo = YearTo.T
    object YearTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type To = To.T
    object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinLon = MinLon.T
    object MinLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinLat = MinLat.T
    object MinLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxLon = MaxLon.T
    object MaxLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxLat = MaxLat.T
    object MaxLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Sort = Sort.T
    object Sort extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Offset = Offset.T
    object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GroupBy = GroupBy.T
    object GroupBy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Depth = Depth.T
    object Depth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AsOf = AsOf.T
    object AsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Samples = Samples.T
    object Samples extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Verbose = Verbose.T
    object Verbose extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
      field: FacetsInput.Field,
      prefix: Option[FacetsInput.Prefix] = None,
      query: Option[FacetsInput.Query] = None,
      tags: List[FacetsInput.Tags] = Nil,
      authors: List[FacetsInput.Authors] = Nil,
      places: List[FacetsInput.Places] = Nil,
      topics: List[FacetsInput.Topics] = Nil,
      status: List[FacetsInput.Status] = Nil,
      yearFrom: Option[FacetsInput.YearFrom] = None,
      yearTo: Option[FacetsInput.YearTo] = None,
      from: Option[FacetsInput.From] = None,
      to: Option[FacetsInput.To] = None,
      minLon: Option[FacetsInput.MinLon] = None,
      minLat: Option[FacetsInput.MinLat] = None,
      maxLon: Option[FacetsInput.MaxLon] = None,
      maxLat: Option[FacetsInput.MaxLat] = None,
      limit: Option[FacetsInput.Limit] = None
  ) derives KindlingsCodecAsObject:
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
    type Field = Field.T
    object Field extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Prefix = Prefix.T
    object Prefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Query = Query.T
    object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Authors = Authors.T
    object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Places = Places.T
    object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Topics = Topics.T
    object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type YearFrom = YearFrom.T
    object YearFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type YearTo = YearTo.T
    object YearTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type To = To.T
    object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinLon = MinLon.T
    object MinLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinLat = MinLat.T
    object MinLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxLon = MaxLon.T
    object MaxLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxLat = MaxLat.T
    object MaxLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[FacetsInput] = Filter.Docs
      .describe(KindlingsSchema.derived[FacetsInput].schema)
      .modify(_.field)(_.description("Dimension to enumerate: tags|authors|places|topics|status|provenance"))
      .modify(_.prefix)(_.description("Case-insensitive prefix match for autocompletion"))
      .modify(_.limit)(_.description("At most this many values"))

  final case class GetSourceInput(
      citekey: com.histmersion.domain.library.Citekey,
      fullText: GetSourceInput.FullText = false,
      section: Option[GetSourceInput.Section] = None,
      heading: Option[GetSourceInput.Heading] = None,
      offset: Option[GetSourceInput.Offset] = None,
      chars: Option[GetSourceInput.Chars] = None
  ) derives KindlingsCodecAsObject
  object GetSourceInput:
    type FullText = FullText.T
    object FullText extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Section = Section.T
    object Section extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Heading = Heading.T
    object Heading extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Offset = Offset.T
    object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Chars = Chars.T
    object Chars extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
  val PassagesDoc: com.histmersion.domain.library.LibraryTools.PassagesDoc =
    "Find a word, phrase or quotation inside ONE source's full text — `grep` over its body. Returns the " +
      "passages around each match (the match in **bold**), each with its `offset` into the body (pass it " +
      "to `get_source` as `offset` to read on) and the heading it sits under, then how many more there " +
      "are. `query` is matched LITERALLY (no stemming), case-insensitive, accents optional (`Hattusa` " +
      "finds `Ḫattuša`), curly quotes and dashes as plain ones; its words may span a line break. " +
      "`regex: true` makes it a Java regex (`^`/`$` per line). `limit` passages (default 10, max 50), " +
      "`context` characters around each match (default 600, max 3000). Use it to check what a source " +
      "says about a term instead of paging through `get_source` slices."

  /** `passages` as Markdown, with the same wording for an unknown citekey as `get_source`. */
  def passagesMarkdown(sources: SourcesControllers, in: PassagesInput): com.histmersion.domain.library.LibraryTools.PassagesMarkdownResult =
    sources.passages(Api.PassagesQuery(in.citekey, in.query, in.regex, in.limit, in.context)) match
      case Right(r)                                       => PassagesMarkdown.render(r)
      case Left(e) if e.code.contains(Write.CodeNotFound) =>
        s"_No source '${in.citekey}' — find a citekey with search._"
      case Left(e) => McpOut.refusal(e)

  final case class PassagesInput(
      citekey: com.histmersion.domain.library.Citekey,
      query: PassagesInput.Query,
      regex: PassagesInput.Regex = false,
      limit: Option[PassagesInput.Limit] = None,
      context: Option[PassagesInput.Context] = None
  ) derives KindlingsCodecAsObject
  object PassagesInput:
    type Query = Query.T
    object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Regex = Regex.T
    object Regex extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Context = Context.T
    object Context extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
      query: Option[GeoWhenInput.Query] = None,
      tags: List[GeoWhenInput.Tags] = Nil,
      authors: List[GeoWhenInput.Authors] = Nil,
      places: List[GeoWhenInput.Places] = Nil,
      topics: List[GeoWhenInput.Topics] = Nil,
      status: List[GeoWhenInput.Status] = Nil,
      yearFrom: Option[GeoWhenInput.YearFrom] = None,
      yearTo: Option[GeoWhenInput.YearTo] = None,
      from: Option[GeoWhenInput.From] = None,
      to: Option[GeoWhenInput.To] = None,
      minLon: Option[GeoWhenInput.MinLon] = None,
      minLat: Option[GeoWhenInput.MinLat] = None,
      maxLon: Option[GeoWhenInput.MaxLon] = None,
      maxLat: Option[GeoWhenInput.MaxLat] = None,
      limit: Option[GeoWhenInput.Limit] = None,
      /** @deprecated use `from` */
      periodStart: Option[com.histmersion.Year] = None,
      /** @deprecated use `to` */
      periodEnd: Option[com.histmersion.Year] = None,
      /** @deprecated use `places` */
      region: Option[GeoWhenInput.Region] = None,
      offset: Option[GeoWhenInput.Offset] = None
  ) derives KindlingsCodecAsObject:
    def toQuery: Api.FindingsQuery =
      val effectiveFrom = from.orElse(periodStart)
      val effectiveTo = to.orElse(periodEnd)
      val effectivePlaces = places ++ region.map(_.trim).filter(_.nonEmpty).toList
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
    type Query = Query.T
    object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Authors = Authors.T
    object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Places = Places.T
    object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Topics = Topics.T
    object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type YearFrom = YearFrom.T
    object YearFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type YearTo = YearTo.T
    object YearTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type To = To.T
    object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinLon = MinLon.T
    object MinLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinLat = MinLat.T
    object MinLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxLon = MaxLon.T
    object MaxLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxLat = MaxLat.T
    object MaxLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Region = Region.T
    object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Offset = Offset.T
    object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[GeoWhenInput] = Filter.Docs
      .describe(KindlingsSchema.derived[GeoWhenInput].schema)
      .modify(_.region)(_.description("Deprecated alias for a places entry"))
      .modify(_.periodStart)(_.description("Deprecated alias for from"))
      .modify(_.periodEnd)(_.description("Deprecated alias for to"))

  final case class NeighborsInput(citekey: com.histmersion.domain.library.Citekey, kinds: List[NeighborsInput.Kinds] = Nil) derives KindlingsCodecAsObject
  object NeighborsInput:
    type Kinds = Kinds.T
    object Kinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[NeighborsInput] = KindlingsSchema
      .derived[NeighborsInput]
      .schema
      .modify(_.citekey)(_.description("The source's citekey"))
      .modify(_.kinds)(
        _.description("Filter to these edge kinds only: references|challenges|refutes|updates|superseded_by")
      )

  final case class GraphInput(
      citekey: com.histmersion.domain.library.Citekey,
      depth: Option[GraphInput.Depth] = None
  ) derives KindlingsCodecAsObject
  object GraphInput:
    type Depth = Depth.T
    object Depth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[GraphInput] = KindlingsSchema
      .derived[GraphInput]
      .schema
      .modify(_.citekey)(_.description("Root source citekey to start BFS from"))
      .modify(_.depth)(_.description("BFS depth 0–4 (default 2)"))

  final case class ContradictionsInput(
      citekey: Option[com.histmersion.domain.library.Citekey] = None,
      since: Option[ContradictionsInput.Since] = None
  ) derives KindlingsCodecAsObject
  object ContradictionsInput:
    type Since = Since.T
    object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[ContradictionsInput] = KindlingsSchema
      .derived[ContradictionsInput]
      .schema
      .modify(_.citekey)(_.description("Show contradictions for this source only; omit for corpus-wide"))
      .modify(_.since)(_.description("Filter by the challenging source's publication year (signed year, BCE negative)"))

  // The write tools reuse the REST DTOs verbatim, so the MCP tool schema and the HTTP body are the
  // same shape — one contract, two transports.
  final case class PutSourceInput(
      citekey: com.histmersion.domain.library.Citekey,
      source: com.histmersion.api.library.Source.Put,
      strict: PutSourceInput.Strict = false
  )
  object PutSourceInput:
    type Strict = Strict.T
    object Strict extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[PutSourceInput] = KindlingsSchema
      .derived[PutSourceInput]
      .schema
      .modify(_.strict)(_.description(StrictDoc))
    given Codec.AsObject[PutSourceInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PutSourceInput]) // #813

  final case class PatchSourceInput(
      citekey: com.histmersion.domain.library.Citekey,
      patch: com.histmersion.api.library.Source.Patch,
      strict: PatchSourceInput.Strict = false
  )
  object PatchSourceInput:
    type Strict = Strict.T
    object Strict extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[PatchSourceInput] = KindlingsSchema
      .derived[PatchSourceInput]
      .schema
      .modify(_.strict)(_.description(StrictDoc))
    given Codec.AsObject[PatchSourceInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PatchSourceInput]) // #813

  final case class RenameSourceInput(citekey: com.histmersion.domain.library.Citekey, newCitekey: RenameSourceInput.NewCitekey) derives KindlingsCodecAsObject
  object RenameSourceInput:
    type NewCitekey = NewCitekey.T
    object NewCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[RenameSourceInput] = KindlingsSchema
      .derived[RenameSourceInput]
      .schema
      .modify(_.citekey)(_.description("The source's current citekey"))
      .modify(_.newCitekey)(_.description("The citekey it should have from now on"))

  final case class ExportSourceInput(citekey: com.histmersion.domain.library.Citekey) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema


  /** The MCP spelling of [[com.histmersion.api.library.Filter]] for `histogram` — now the FULL filter shape (A2).
    * `periodStart`/`periodEnd` and `region` are deprecated aliases (A1).
    */
  final case class HistogramInput(
      query: Option[HistogramInput.Query] = None,
      tags: List[HistogramInput.Tags] = Nil,
      authors: List[HistogramInput.Authors] = Nil,
      places: List[HistogramInput.Places] = Nil,
      topics: List[HistogramInput.Topics] = Nil,
      status: List[HistogramInput.Status] = Nil,
      yearFrom: Option[HistogramInput.YearFrom] = None,
      yearTo: Option[HistogramInput.YearTo] = None,
      from: Option[HistogramInput.From] = None,
      to: Option[HistogramInput.To] = None,
      minLon: Option[HistogramInput.MinLon] = None,
      minLat: Option[HistogramInput.MinLat] = None,
      maxLon: Option[HistogramInput.MaxLon] = None,
      maxLat: Option[HistogramInput.MaxLat] = None,
      sort: Option[HistogramInput.Sort] = None,
      limit: Option[HistogramInput.Limit] = None,
      unit: Option[HistogramInput.Unit] = None,
      /** @deprecated use `from` */
      periodStart: Option[com.histmersion.Year] = None,
      /** @deprecated use `to` */
      periodEnd: Option[com.histmersion.Year] = None,
      /** @deprecated use `places` */
      region: Option[HistogramInput.Region] = None
  ) derives KindlingsCodecAsObject:
    def toFilter: Filter =
      val effectiveFrom = from.orElse(periodStart)
      val effectiveTo = to.orElse(periodEnd)
      val effectivePlaces = places ++ region.map(_.trim).filter(_.nonEmpty).toList
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
    type Query = Query.T
    object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Tags = Tags.T
    object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Authors = Authors.T
    object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Places = Places.T
    object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Topics = Topics.T
    object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Status = Status.T
    object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type YearFrom = YearFrom.T
    object YearFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type YearTo = YearTo.T
    object YearTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type From = From.T
    object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type To = To.T
    object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinLon = MinLon.T
    object MinLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MinLat = MinLat.T
    object MinLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxLon = MaxLon.T
    object MaxLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MaxLat = MaxLat.T
    object MaxLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Sort = Sort.T
    object Sort extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Limit = Limit.T
    object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Unit = Unit.T
    object Unit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Region = Region.T
    object Region extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[HistogramInput] = Filter.Docs
      .describe(KindlingsSchema.derived[HistogramInput].schema)
      .modify(_.unit)(_.description("What to count: sources (default) | findings"))
