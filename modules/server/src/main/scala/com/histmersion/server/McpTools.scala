package com.histmersion.server

import chimp.server.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import io.circe.Codec
import com.histmersion.core.*

/** MCP tools (Chimp). Every tool returns Markdown and delegates to the same [[Handlers]] as REST. */
final class McpTools(h: Handlers, acquire: com.histmersion.server.acquire.AcquireHandlers):
  import McpTools.*

  private val ping =
    tool("ping").description("Liveness check for the Histmersion MCP server").input[PingInput]
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
          "nothing. Page with `limit`/`offset`; `sort` is relevance|cited|period|year|title. " +
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
          "the same ~4 000-char section of one source.")
      .input[SearchInput]
      .handle { in =>
        in.groupBy.map(_.trim).filter(_.nonEmpty) match
          case None =>
            val f = in.toFilter
            val r = h.sources(f)
            ToolResult.text(
              if in.verbose then Markdown.searchResults(r, f.offset)
              else Markdown.briefResults(r, f.offset))
          case Some(by) =>
            // A5/A26: MCP defaults depth:1, samples:0 unless asked, so a bare groupBy never overwhelms
            val depth   = in.depth.orElse(Some(1))
            val samp    = in.samples.orElse(Some(0))
            h.aggregate(Api.AggregateQuery.tree(by, in.toFilter, None, depth, in.asOf, samp)) match
              case Right(t) => ToolResult.text(Markdown.tree(t, "sources"))
              case Left(e)  => ToolResult.text(s"_${e.message}_")
      }

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
    tool("get_source").description(
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
        "Use after `search` to read what a source says — not to discover sources.")
      .input[GetSourceInput]
      .handle { in =>
        h.sourceMarkdown(in.citekey, in.fullText, in.section, in.heading, in.offset, in.chars) match
          case Right(md) => ToolResult.text(md)
          case Left(e)   => ToolResult.text(s"_No source '${in.citekey}' — find a citekey with search._")
      }

  private val passages =
    tool("passages").description(PassagesDoc)
      .input[PassagesInput]
      .handle(in => ToolResult.text(passagesMarkdown(h, in)))

  private val geoWhen =
    tool("geo_when").description(
      "Findings at a place and/or deep-time range, as Markdown. Takes the SAME filters as `search`: " +
        "`query` (free text), `tags` (ALL required), `authors`/`places` (any of), `topics`, `status`, " +
        "`yearFrom`/`yearTo` (publication years), `from`/`to` (signed years, BCE negative), bbox. " +
        "`region` is a deprecated alias for a `places` entry; `periodStart`/`periodEnd` are deprecated " +
        "aliases for `from`/`to`. Use when you need to see individual findings rather than sources — " +
        "not when a source list with `search` would suffice.")
      .input[GeoWhenInput]
      .handle(in => ToolResult.text(Markdown.geoResults(h.findings(in.toQuery).toVector)))

  private val histogram =
    tool("histogram")
      .description(
        "When is the evidence? Counts of sources (or findings) per adaptive time bucket across a " +
          "deep-time window. Takes the SAME filters as `search`: `query`, `tags` (ALL required), " +
          "`authors`/`places` (any of), `topics`, `status`, `yearFrom`/`yearTo`, `from`/`to` " +
          "(signed years, BCE negative), bbox. `unit` is `sources` (default) or `findings`. " +
          "`region` is a deprecated alias for a `places` entry; `periodStart`/`periodEnd` are " +
          "deprecated aliases for `from`/`to`. Returns a Markdown table with bar chart. " +
          "Use when you need to see the temporal shape of the evidence — not when individual " +
          "sources or findings are needed.")
      .input[HistogramInput]
      .handle { in =>
        val unit = in.unit.getOrElse("sources")
        h.aggregate(Api.AggregateQuery.time(in.toFilter, Some(unit))) match
          case Right(hist) => ToolResult.text(Markdown.histogram(hist, unit))
          case Left(e)     => ToolResult.text(s"_${e.message}_")
      }

  private val neighbors =
    tool("neighbors").description(
      "Typed edges to/from a source: references, challenges, refutes, updates, superseded_by. " +
        "The output has two sections: **Sources** (edges where the target resolves to a real source, " +
        "with its status) and **Topics** (unresolved `references` targets collapsed to a list of " +
        "article slugs). Use `kinds` to filter by edge kind. An empty result is not evidence of " +
        "agreement — disagreement edges are extremely sparse in the corpus. Use `contradictions` " +
        "for a focused disagreement view, not this.")
      .input[NeighborsInput]
      .handle { in =>
        val rows = h.neighbors(in.citekey, in.kinds)
        if rows.isEmpty then
          ToolResult.text(s"_No references recorded for **${in.citekey}**._ " +
            "An empty result is not evidence of agreement — disagreement edges are sparse.")
        else ToolResult.text(Markdown.neighbors(in.citekey, rows.toVector))
      }

  private val sourceGraph =
    tool("graph")
      .description(
        "Source dependency GRAPH: BFS traversal of the citation/relationship network around a " +
          "source, returning a D3-ready node+link structure. Each node carries citekey, title, " +
          "year, status and BFS depth. Links carry the edge kind. Use `depth` to control how " +
          "far to walk (0–4, default 2). Edge kinds include: references, challenges, refutes, " +
          "updates, superseded_by, supersedes, confirms, cites, foundational_for, " +
          "compilation_of, review_of, verifies.")
      .input[GraphInput]
      .handle { in =>
        val g = h.graph(in.citekey, in.depth)
        ToolResult.text(Markdown.graph(g))
      }

  private val contradictions =
    tool("contradictions").description(
      "Disagreement edges and contested statuses in the corpus. With a `citekey`: its " +
        "challenges/refutes/updates/superseded_by edges in both directions, with the other side's " +
        "year and status. Without: corpus-wide — every source that has such an edge OR whose status " +
        "is contested|superseded|debunked|retracted. `since` filters on the challenging source's " +
        "publication year. Edge kinds: references, challenges, refutes, updates, superseded_by. " +
        "An empty result is not evidence of agreement — disagreement edges are extremely sparse.")
      .input[ContradictionsInput]
      .handle { in =>
        val result = h.contradictions(in.citekey, in.since)
        ToolResult.text(Markdown.contradictions(result, in.citekey))
      }

  // ── writes (plan §1.9a) ─────────────────────────────────────────────────────────────────────
  private val PatchFirst =
    "To add or correct findings/tags/edges/notes on a source that already exists, use patch_source; " +
      "never re-upload the whole source."

  private val putSource =
    tool("put_source")
      .description(
        "Create or REPLACE an entire source (metadata, summary, body, notes, topics, regions, tags, " +
          "period, findings, edges) under one citekey. Idempotent, and destructive: anything you omit " +
          "is erased and every finding is renumbered from #0. Edges are FROM this source, each " +
          "{dst,kind} (a src naming another source, or a self-edge, is refused). An unknown key is " +
          "refused, naming it. Use it only to CREATE a source or to " +
          s"deliberately re-import one. $PatchFirst")
      .input[PutSourceInput]
      .handle { in =>
        h.putSource(in.citekey, in.source, in.strict) match
          case Right(sw) => ToolResult.text(Markdown.source(sw.source, sw.findings.toVector, false) + FormatMarkdown.notice(sw.formatting))
          case Left(e)   => ToolResult.text(s"_${e.message}_")
      }

  private val patchSource =
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
          s"refuses a patch whose summary or notes have a formatting error. $PatchFirst")
      .input[PatchSourceInput]
      .handle { in =>
        h.patchSource(in.citekey, in.patch, in.strict) match
          case Right(sw) => ToolResult.text(Markdown.source(sw.source, sw.findings.toVector, false) + FormatMarkdown.notice(sw.formatting))
          case Left(e)   => ToolResult.text(s"_${e.message}_")
      }

  private val renameSource =
    tool("rename_source")
      .description(
        "Give a source a NEW citekey — to correct a misleading one (e.g. a year that is not the " +
          "publication year: page2024_history for a 1959 book → page1959_history). One transaction: " +
          "the findings are renumbered '<new>#<n>', every table that stores the citekey follows, and " +
          "every [[old]] link (labels and anchors kept, short forms like [[beckman2025]] included) " +
          "is rewritten in article bodies — each rewrite recorded as an article version — and in " +
          "source notes/summaries. The OLD citekey becomes an alias: get_source, neighbors, graph, " +
          "export_source, /source/<old> and [[old]] keep resolving to the source. Refused when the " +
          "new citekey is taken (by a source, or as an old citekey of another source) or is not " +
          "link-shaped. Use it only to fix an identifier; to change metadata (year, title) use " +
          "patch_source.")
      .input[RenameSourceInput]
      .handle { in =>
        h.renameSource(in.citekey, RenameRequest(in.newCitekey)) match
          case Right(r) => ToolResult.text(Markdown.sourceRenamed(r))
          case Left(e)  => ToolResult.text(s"_${e.message}_")
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

  // ── ingest, as a JOB (plan §1.8) ────────────────────────────────────────────────────────────
  private val ingest =
    tool("ingest")
      .description(
        "OCR a PDF/EPUB into a source and keep its figures. Give EITHER `path` (a path the SERVER " +
          "can see) OR `base64` (the document itself, for smaller files) plus a `citekey`. This " +
          "returns immediately with a job id, because converting a long scan takes minutes: poll " +
          "`job_status` until it is done|failed. `quality` runs the slow, accurate OCR path (use it " +
          "for image-only scans); `force` accepts a file already stored under another citekey. When " +
          "the citekey already exists only its body and figures are replaced — findings, tags and " +
          "notes are left alone — so re-OCR never costs enrichment. Optional title/authors/year/doi " +
          "are used ONLY when the source is being created. `acquireItem` names the acquisition-queue " +
          "item this document fulfils: the job ticks it off (`ingested as [[citekey]]`) when it " +
          "succeeds, and leaves it open when it fails. Without it, the job ticks off every open " +
          "acquisition item that names the source's DOI, and lists items with a similar title in its " +
          "progress as possible matches — those are never closed for you.")
      .input[IngestInput]
      .handle { in =>
        h.ingest.fromTool(in.citekey, in.path, in.base64, in.filename, in.quality, in.force,
          in.title, in.authors, in.year, in.doi, in.acquireItem) match
          case Right(a) => ToolResult.text(
            s"queued job `${a.jobId}` for **${a.citekey}** — poll `job_status` with that id")
          case Left(e) => ToolResult.text(s"_${e.message}_")
      }

  private val jobStatus =
    tool("job_status")
      .description(
        "Where an OCR job got to: queued|running|done|failed, with its progress line or the reason " +
          "it failed. Give `id` for one job, or omit it (optionally with `status`) to list the " +
          "recent ones. A job that says 'interrupted' was killed by a server restart — re-run the " +
          "same ingest, it is idempotent.")
      .input[JobStatusInput]
      .handle { in =>
        in.id.map(_.trim).filter(_.nonEmpty) match
          case Some(id) =>
            h.ingest.job(id) match
              case Right(j) => ToolResult.text(IngestMarkdown.job(j))
              case Left(e)  => ToolResult.text(s"_${e.message}_")
          case None => ToolResult.text(IngestMarkdown.jobs(h.ingest.jobs(in.status, in.limit).toVector))
      }

  private val assets =
    tool("assets")
      .description(
        "The figures stored with a source — name, mime, pixel size and the URL each is served at. " +
          "The Markdown body links them as `assets/<name>`; `get_source` rewrites those to absolute " +
          "URLs you can fetch. Use to check what figures are available — not to read the source.")
      .input[AssetsInput]
      .handle { in =>
        val rows = h.ingest.assets(in.citekey)
        if rows.isEmpty && h.getSource(in.citekey).isLeft then
          ToolResult.text(s"_No source '${in.citekey}' — find a citekey with search._")
        else ToolResult.text(IngestMarkdown.assets(in.citekey, rows.toVector))
      }

  private val patchAssetTool =
    tool("patch_asset")
      .description(
        "Update metadata on an asset — kind (figure/photo/map/plan/scan), caption, license, " +
          "geom (lat/lon) and period (periodStart/periodEnd as signed years).")
      .input[PatchAssetInput]
      .handle { in =>
        h.patchAsset(in.citekey, in.name, AssetPatch(
          kind = in.kind, caption = in.caption, license = in.license,
          lat = in.lat, lon = in.lon, periodStart = in.periodStart, periodEnd = in.periodEnd
        )) match
          case Right(d) => ToolResult.text(Markdown.assetDetail(in.citekey, d))
          case Left(e)  => ToolResult.text(s"_${e.message}_")
      }

  private val assetLinksTool =
    tool("asset_links")
      .description(
        "Search/show ASSET LINKS — semantic relationships between assets and entities. " +
          "Filter by citekey, asset name, target_type (event/finding/place/source/article), target_id. " +
          "With `create` fields, creates a new link instead.")
      .input[AssetLinksInput]
      .handle { in =>
        in.relation match
          case Some(rel) =>
            val put = AssetLinkPut(
              citekey = in.citekey.getOrElse(""),
              assetName = in.asset.getOrElse(""),
              relation = rel,
              targetType = in.targetType.getOrElse(""),
              targetId = in.targetId.getOrElse(""),
              xywh = in.xywh,
              notes = in.notes)
            h.createAssetLink(put) match
              case Right(l) => ToolResult.text(Markdown.assetLink(l))
              case Left(e)  => ToolResult.text(s"_${e.message}_")
          case None =>
            ToolResult.text(Markdown.assetLinkList(
              h.listAssetLinks(in.citekey, in.asset, in.targetType, in.targetId)))
      }

  private val deleteAssetLinkTool =
    tool("delete_asset_link")
      .description("Delete an asset link by id.")
      .input[DeleteAssetLinkInput]
      .handle { in =>
        h.deleteAssetLink(in.id) match
          case Right(d) => ToolResult.text(s"Deleted asset link #${d.id}.")
          case Left(e)  => ToolResult.text(s"_${e.message}_")
      }

  /** Backup is here because an agent asked to "save the state before a big reseed" should be able to
    * do it; RESTORE deliberately is NOT — it drops the database, which is not a decision to leave to
    * a tool call (`histmersion restore` asks a human to confirm). */
  private val backup =
    tool("backup")
      .description(
        "Take a full pg_dump backup of the Histmersion database, server-side, and return the file name, " +
          "size and SHA-256 checksum. Use it before a bulk reseed, a mass patch, or any other " +
          "operation you may need to undo. Restoring is NOT available as a tool: it destroys data, " +
          "and is done by a human with `histmersion restore <file> --force`.")
      .input[BackupInput]
      .handle { _ =>
        h.backup() match
          case Right(r) => ToolResult.text(Markdown.backupStatus(r))
          case Left(e)  => ToolResult.text(s"_${e.message}_")
      }

  // ── articles: the compiled wiki (plan §5.2 stream 2.7) ──────────────────────────────────────
  // Shared preamble: the ONE place the writing rules are named, so all four tools point an agent at
  // the same document instead of paraphrasing it differently.
  private val ArticleRules =
    "Articles are the COMPILED KNOWLEDGE of this corpus — the concept/myth/culture/region/timeline/" +
      "report pages a reader reads instead of the papers. Write them by the rules in " +
      "docs/writing-guide.md: exhaust the filter (every source it returned is used or explicitly " +
      "excluded), surface disagreements with years, EVERY PARAGRAPH CITES at least one source as " +
      "[[citekey]] (a paragraph with no citekey is deleted, not rewritten), plain technical language " +
      "with no filler, specialist terms defined in one clause on first use, and the sections " +
      "question / what the sources say / disagreements / what is unresolved. Record the article's FILTER " +
      "(`filter`) and a REASON for each of its sources the body does not cite (`reasons`): the page then " +
      "generates the Not used list, its counts and the Method note — never write those by hand."
  private val LinkRules =
    "Links are Obsidian wikilinks in the body: [[citekey]] cites a source (it renders as a link to " +
      "that source and appears in the article's 'Sources cited' footer), [[other_slug]] points at " +
      "another article (and shows up in its backlinks). Nothing else maintains the link graph."
  private val FormatRules =
    "The Markdown is CommonMark plus pipe tables (a blank line before a table, a `|---|` row with one " +
      "cell per column); every write answers with a 'Formatting' list of what will not display as " +
      "written (line, column, excerpt) — fix the errors; `strict: true` refuses a write that has one."
  private val PatchFirstArticle =
    "To extend or correct an article that already exists, use patch_article — with `append` to add a " +
      "section, or `body` to rewrite it. NEVER re-upload a whole article with put_article: that " +
      "erases anything you did not resend."

  private val listArticles =
    tool("list_articles")
      .description(
        "List/search the compiled articles. `kind` filters to concept|myth|culture|region|timeline|" +
          "report, `tag` to the articles citing a source with that tag, `title` to a title " +
          "substring, `query` is full text over the article's title, summary and body (websearch " +
          "syntax: \"phrase\", -not, or) plus a title substring. Start here before compiling " +
          "anything: an article on the topic may already exist, in which case you extend it with " +
          s"patch_article rather than starting a new one. $ArticleRules")
      .input[ListArticlesInput]
      .handle(in => ToolResult.text(ArticleMarkdown.list(h.listArticles(in.toQuery))))

  private val getArticle =
    tool("get_article")
      .description(
        "Fetch one compiled article by slug. `parts` (default: all) selects which sections to " +
          "return: body (the Markdown prose), summary (the one-paragraph lede), cited (sources it " +
          "cites with title/year/status — teasers truncated to 200 chars, markdown stripped), " +
          "backlinks (other articles and source notes linking TO it — same teaser format). " +
          "Pass a comma-separated list to get several parts (e.g. `parts: \"body,cited\"`). `coverage` is the " +
          "generated Not used list and Method note (from the article's recorded filter; see article_coverage). " +
          "Kinds: concept, myth, culture, region, timeline, report, index (the wiki's MOC pages). " +
          s"$LinkRules Read the article before patching it. $PatchFirstArticle")
      .input[GetArticleInput]
      .handle { in =>
        h.getArticle(in.slug) match
          case Right(v) =>
            val parts = in.parts.map(_.split(',').map(_.trim.toLowerCase).toList).getOrElse(Nil)
            ToolResult.text(ArticleMarkdown.article(v, parts))
          case Left(e) => ToolResult.text(s"_No article '${in.slug}' — find a slug with list_articles._")
      }

  private val putArticle =
    tool("put_article")
      .description(
        "Create or REPLACE a whole article under one slug (lower-case, underscores: sea_peoples). " +
          "Idempotent and destructive — everything you omit is erased — so use it to CREATE an " +
          "article or to deliberately rewrite one from scratch. `kind` is concept|myth|culture|" +
          "region|timeline|report, `body` is the Markdown (open it with a '# Title' heading), " +
          "`summary` is the one-paragraph lede (omit it and the first paragraph is used), `period` " +
          "is signed years, BCE negative. " +
          s"$ArticleRules $LinkRules $FormatRules $PatchFirstArticle")
      .input[PutArticleInput]
      .handle { in =>
        h.putArticle(in.slug, in.article, in.strict) match
          case Right(v) => ToolResult.text(ArticleMarkdown.article(v))
          case Left(e)  => ToolResult.text(s"_${e.message}_")
      }

  private val patchArticle =
    tool("patch_article")
      .description(
        "Extend or correct an existing article: send ONLY what changes, everything else is left " +
          "exactly as it is. `append` adds Markdown — at the end by default, or under a named " +
          "`section` heading (e.g. `section:\"## Disagreements\"`) so the new content lands in the " +
          "right place without a full body rewrite. `body` rewrites the prose, `title`, `summary`, " +
          "`kind`, `regions`, `period{start,end}` and `tags{add,remove,replace}` touch just " +
          "themselves. `filter` replaces the filter the article is compiled from ({} clears it); `reasons` " +
          "[{citekey, reason}] sets why each named source of that filter is left out (an empty reason removes " +
          "one) — the page generates the Not used list and the Method note from the two. Pass ifUpdatedAt with the updatedAt you last read to be told — instead of " +
          s"silently clobbering — when someone else changed the article meanwhile. $ArticleRules " +
          s"$LinkRules $FormatRules")
      .input[PatchArticleInput]
      .handle { in =>
        h.patchArticle(in.slug, in.patch, in.strict) match
          case Right(v) => ToolResult.text(ArticleMarkdown.article(v))
          case Left(e)  => ToolResult.text(s"_${e.message}_")
      }

  private val checkEdits =
    tool("check_edits")
      .description(McpTools.CheckEditsDoc)
      .input[CheckEditsInput]
      .handle(in => ToolResult.text(McpTools.checkEdits(h, in)))

  private val articleCoverage =
    tool("article_coverage")
      .description(McpTools.ArticleCoverageDoc)
      .input[ArticleCoverageInput]
      .handle(in => ToolResult.text(McpTools.articleCoverageMarkdown(h, in)))

  private val exportArticle =
    tool("export_article")
      .description(
        "Render an article as the Markdown note it would be on disk at wiki/<kind>/<slug>.md: YAML " +
          "frontmatter (type, slug, title, summary, regions, tags, period_start/period_end, and the filter " +
          "and reasons it is compiled from) plus the body, then the generated Not used and Method sections " +
          "between markers an import drops. This is the git-friendly mirror of the database and it round-trips back in through " +
          "seed.")
      .input[ExportArticleInput]
      .handle { in =>
        h.exportArticle(in.slug) match
          case Right(text) => ToolResult.text(text)
          case Left(e)     => ToolResult.text(s"_${e.message}_")
      }

  private val articleVersions =
    tool("article_versions")
      .description(
        "List the edit history of one article (newest first): version id, date, word count and the " +
          "first 200 chars of the summary at that point. Use it to see how an article changed over " +
          "time, or to find the version id of a body you want to recover with `article_version`.")
      .input[ArticleVersionsInput]
      .handle { in =>
        h.articleVersions(in.slug) match
          case Right(vs) => ToolResult.text(ArticleMarkdown.versions(in.slug, vs))
          case Left(e)   => ToolResult.text(s"_${e.message}_")
      }

  private val articleVersion =
    tool("article_version")
      .description(
        "Fetch the full body of one historical version of an article. Use it to read what the article " +
          "said before a rewrite, or to recover text that a bad patch overwrote.")
      .input[ArticleVersionInput]
      .handle { in =>
        h.articleVersion(in.slug, in.versionId) match
          case Right(v) => ToolResult.text(ArticleMarkdown.version(v))
          case Left(e)  => ToolResult.text(s"_${e.message}_")
      }

  // DELETE /api/articles/{slug} is deliberately NOT an MCP tool: dropping compiled knowledge is a
  // human decision, made with `histmersion article delete`, not something a model should reach for while
  // tidying. Every other article operation is on both surfaces.
  // POST /api/sources/{ck}/delete (#969) is deliberately NOT an MCP tool either, for the same reason:
  // removing a source, its findings and its stored original is a person's decision, made with
  // `histmersion delete <citekey> --apply` after reading the dry run.
  /** The gazetteer (plan §1.6a). One tool with two modes so a model does not have to pick between
    * four near-identical ones: no `id` → search, `id` → show that place with its hierarchy. */
  private val places =
    tool("places")
      .description(
        "The GAZETTEER: places with geometry, alternate/period names and a temporally scoped " +
          "hierarchy (continent > country > polity > site). Without `id` it SEARCHES by name — use " +
          "it to turn a name a human wrote ('Anatolia', 'Hatti') into a place **id**, then pass that " +
          "id as `places` to `search`/`geo_when`: an id matches every finding inside the place's " +
          "shape AND everything below it in the hierarchy, which a bare name never does. With `id` " +
          "it SHOWS one place: what it is, when it was valid, what it is part of and what it " +
          "contains, as an indented tree. `asOf` is a signed year (BCE negative) and selects the " +
          "hierarchy as it stood then — the same site is under 'Hittite Empire' at -1400 and under " +
          "'Phrygia' at -700. Counts are non-additive: a finding on a border belongs to every place " +
          "that covers it.")
      .input[PlacesInput]
      .handle { in =>
        in.id.map(_.trim).filter(_.nonEmpty) match
          case Some(id) =>
            h.place(id, in.asOf) match
              case Right(d) =>
                val tree = h.placeTree(id, in.asOf, None).getOrElse(Nil)
                ToolResult.text(Markdown.place(d, tree.toVector))
              case Left(e) => ToolResult.text(s"_${e.message}_")
          case None =>
            ToolResult.text(Markdown.places(h.places(in.name.orElse(in.query), in.asOf, in.limit).toVector, in.asOf))
      }


  /** A person's names for a place (#987): list, add or remove — the names a re-seed never deletes. */
  private val placeNames =
    tool("place_names")
      .description(
        "Names ADDED BY HAND to gazetteer places — the names every re-seed keeps (an import replaces a " +
          "place's other names). Without `name` it LISTS them (of `placeId`, or of every place). With " +
          "`placeId` and `name` it ADDS that name (`lang`: a language tag such as en, grc, pl; omit when " +
          "unknown) — use it when a well-known name finds no place in `places` (\"Lake Kinneret\" for the " +
          "Sea of Galilee), so the geocoder and `places=` filters know it from then on. With `remove: true` " +
          "it removes a name added by hand (a name the seed gives cannot be removed). Find the place id " +
          "with `places` first.")
      .input[PlaceNamesInput]
      .handle { in =>
        val id = in.placeId.map(_.trim).filter(_.nonEmpty)
        (id, in.name.map(_.trim).filter(_.nonEmpty)) match
          case (_, None) => ToolResult.text(Markdown.manualPlaceNames(h.manualPlaceNames(id)))
          case (None, Some(_)) => ToolResult.text("_`placeId` is required to add or remove a name — find it with `places`_")
          case (Some(pid), Some(n)) =>
            val r =
              if in.remove.getOrElse(false) then h.removePlaceName(pid, PlaceNameRemove(n, in.lang))
              else h.addPlaceName(pid, PlaceNameAdd(n, in.lang))
            r.fold(e => ToolResult.text(s"_${e.message}_"), x => ToolResult.text(Markdown.placeNameChanged(x)))
      }

  /** A place of a person's own (#987). */
  private val addPlaceTool =
    tool("add_place")
      .description(
        "Add a POINT place to the gazetteer that it does not have — only after `places` found nothing " +
          "under any of its names (otherwise give the existing place a name with `place_names`). " +
          "`source` is required and must say where the coordinates come from (a gazetteer or Wikidata " +
          "id, a map, a publication): never estimate a point. `kind`: site | region | feature | polity; " +
          "`names` are further names; `periodStart`/`periodEnd` are signed years (BCE negative) when the " +
          "place existed only then. The place is this collection's own (`local_<label>` unless `id` is " +
          "given), carried in a bundle and never touched by a re-seed. Refused when the id is taken.")
      .input[PlaceAdd]
      .handle(in => h.addPlace(in).fold(e => ToolResult.text(s"_${e.message}_"), p => ToolResult.text(Markdown.placeAdded(p))))

  /** Named periods (#719) — the same two modes as `places`: no `id` → search, `id` → show. */
  private val periods =
    tool("periods")
      .description(
        "NAMED PERIODS: PeriodO definitions of historical/archaeological periods (Bronze Age, Late " +
          "Minoan IB, Old Babylonian, Yamnaya…), each with its span in signed years (BCE negative), " +
          "its region and the published authority that states the range. Without `id` it SEARCHES " +
          "by label (`query`), exact `region`, and/or overlap with `from`..`to`; with `id` it SHOWS " +
          "one period with what it is part of and how it is divided. Use it to turn a period name in " +
          "a question into the years to pass as `from`/`to` to `search`/`geo_when` — and cite the " +
          "authority, since two authorities date the same name differently.")
      .input[PeriodsInput]
      .handle { in =>
        in.id.map(_.trim).filter(_.nonEmpty) match
          case Some(id) =>
            h.bootstrap.period(id) match
              case Right(d) => ToolResult.text(PeriodsMarkdown.detail(d))
              case Left(e)  => ToolResult.text(s"_${e.message}_")
          case None =>
            ToolResult.text(PeriodsMarkdown.list(
              h.periods(in.query.map(_.trim).filter(_.nonEmpty), in.region, in.from, in.to).toVector))
      }

  private val events =
    tool("events")
      .description(
        "EVENTS database: CIDOC-CRM compatible historical events with four-point time-spans, " +
          "compositional hierarchy (parent_id), causal/temporal edges (Allen interval algebra) " +
          "and participant roles. Without `id` it LISTS events; with `id` it SHOWS one event " +
          "including its datings, edges, participants and scale (local|regional, stated or derived). " +
          "Use `kind` to filter (event|battle|" +
          "migration|construction|destruction|founding|treaty|reign|period) and `historicity` " +
          "(attested|inferred|contested|legendary|mythic).")
      .input[EventsInput]
      .handle { in =>
        in.id.map(_.trim).filter(_.nonEmpty) match
          case Some(id) =>
            h.getEvent(id) match
              case Right(d) => ToolResult.text(Markdown.event(d))
              case Left(e)  => ToolResult.text(s"_${e.message}_")
          case None =>
            val q = Api.EventQuery(
              Filter.of(from = in.from, to = in.to, limit = in.limit, offset = in.offset, title = in.title),
              in.query, in.kind, in.historicity, in.placeId)
            ToolResult.text(Markdown.eventList(h.listEvents(q)))
      }

  private val patchEventTool =
    tool("patch_event")
      .description(
        "Update fields of one EVENT without replacing it: label, kind, historicity, description, " +
          "citekey (its primary source), parentId (the event it is part of), placeId (a gazetteer place " +
          "id — where it happened), its own geometry (GeoJSON `geometry`, or a point as `lat`/`lon`), and " +
          "`scale`: local (a battle — one place) | regional (an era, a migration — across a region) | " +
          "auto (forget the stated scale; it is derived from the kind, then the place's kind). The map's " +
          "playback draws a local event as a point and label at its site, a regional one as a label at " +
          "the region's centre with its outline. Returns the event as `events` with `id` shows it.")
      .input[PatchEventInput]
      .handle { in =>
        EventGeometry.of(in.geometry, in.lat, in.lon) match
          case Left(msg) => ToolResult.text(s"_${msg}_")
          case Right(geom) =>
            val p = EventPatch(label = in.label, kind = in.kind, historicity = in.historicity,
              geometry = geom, placeId = in.placeId, parentId = in.parentId,
              description = in.description, citekey = in.citekey, scale = in.scale)
            h.patchEvent(in.id, p) match
              case Right(d) => ToolResult.text(Markdown.event(d))
              case Left(e)  => ToolResult.text(s"_${e.message}_")
      }

  private val attestations =
    tool("attestations")
      .description(
        "ATTESTATIONS: granular source references linking entities (findings, events, places, edges) " +
          "to specific passages in a source. W3C Web Annotation selectors: TextQuoteSelector (quote/" +
          "prefix/suffix), TextPositionSelector (charStart/charEnd), chunk ordinals, page labels, " +
          "IIIF canvas+xywh, CTS URNs. Without `id` it LISTS attestations filtered by citekey/" +
          "targetType/targetId; with `id` it SHOWS one attestation.")
      .input[AttestationsInput]
      .handle { in =>
        in.id match
          case Some(id) =>
            h.getAttestation(id) match
              case Right(a) => ToolResult.text(Markdown.attestation(a))
              case Left(e)  => ToolResult.text(s"_${e.message}_")
          case None =>
            val q = Api.AttestationQuery(in.citekey, in.targetType, in.targetId, in.limit, in.offset)
            ToolResult.text(Markdown.attestationList(h.listAttestations(q)))
      }

  private val putAttestation =
    tool("put_attestation")
      .description(
        "Create a new attestation linking an entity to a specific passage in a source. Returns the " +
          "created attestation with its generated id. Target types: finding|event|event_dating|place|edge.")
      .input[AttestationPutInput]
      .handle { in =>
        val put = AttestationPut(in.citekey, in.targetType, in.targetId,
          in.quote, in.prefix, in.suffix, in.charStart, in.charEnd, in.chunkOrd,
          in.page, in.canvas, in.xywh, in.ctsUrn, in.note)
        h.createAttestation(put) match
          case Right(a) => ToolResult.text(Markdown.attestation(a))
          case Left(e)  => ToolResult.text(s"_${e.message}_")
      }

  private val deleteAttestationTool =
    tool("delete_attestation")
      .description("Delete an attestation by its id.")
      .input[DeleteAttestationInput]
      .handle { in =>
        h.deleteAttestation(in.id) match
          case Right(d) => ToolResult.text(s"Deleted attestation #${d.id}.")
          case Left(e)  => ToolResult.text(s"_${e.message}_")
      }

  private val goalsToolRead =
    tool("goals")
      .description(
        "Research GOALS: the project's research programs and topics. Without `slug` it LISTS goals " +
          "filtered by status (planned|seeding|compiled|reviewed|done); with `slug` it SHOWS one goal with " +
          "its sub-goals, links (sources, articles, queries, queue items), schedules and approved plan.")
      .input[GoalsInput]
      .handle { in =>
        in.slug.map(_.trim).filter(_.nonEmpty) match
          case Some(slug) =>
            h.agents.goalView(slug) match
              case Right(v) => ToolResult.text(AgentsMarkdown.goalView(v))
              case Left(e)  => ToolResult.text(s"_${e.message}_")
          case None =>
            val q = Api.GoalQuery(in.status, in.limit, in.offset)
            ToolResult.text(Markdown.goalList(h.listGoals(q)))
      }

  private val putGoalTool =
    tool("put_goal")
      .description(
        "Create or replace a research goal. A goal tracks a research program: slug (kebab-case id), " +
          "title, status (planned|seeding|compiled|reviewed|done), notes (progress), tags, regions, " +
          "period.")
      .input[GoalPutInput]
      .handle { in =>
        val put = GoalPut(in.title, in.status, in.notes, in.tags, in.regions,
          in.periodStart, in.periodEnd, in.parentSlug)
        h.putGoal(in.slug, put) match
          case Right(g) => ToolResult.text(Markdown.goal(g))
          case Left(e)  => ToolResult.text(s"_${e.message}_")
      }

  private val patchGoalTool =
    tool("patch_goal")
      .description("Update specific fields of a goal without replacing the whole row.")
      .input[GoalPatchInput]
      .handle { in =>
        val p = GoalPatch(in.title, in.status, in.notes, in.tags, in.regions,
          in.periodStart, in.periodEnd, in.parentSlug, in.budgetUsd)
        h.patchGoal(in.slug, p) match
          case Right(g) => ToolResult.text(Markdown.goal(g))
          case Left(e)  => ToolResult.text(s"_${e.message}_")
      }

  private val agentStateTool =
    tool("agent_state")
      .description(
        "Agent STATE: key-value JSONB store that survives across sessions. Without `key` it LISTS " +
          "all keys; with `key` it SHOWS one entry; with `key` + `value` it SETS (upserts).")
      .input[AgentStateInput]
      .handle { in =>
        in.key.map(_.trim).filter(_.nonEmpty) match
          case Some(key) =>
            in.value.map(_.trim).filter(_.nonEmpty) match
              case Some(v) =>
                h.agentStateSet(key, AgentStatePut(v)) match
                  case Right(e) => ToolResult.text(Markdown.agentState(e))
                  case Left(e)  => ToolResult.text(s"_${e.message}_")
              case None =>
                h.agentStateGet(key) match
                  case Right(e) => ToolResult.text(Markdown.agentState(e))
                  case Left(e)  => ToolResult.text(s"_${e.message}_")
          case None =>
            ToolResult.text(Markdown.agentStateList(h.agentStateList()))
      }

  private val deleteAgentStateTool =
    tool("delete_agent_state")
      .description("Delete an agent state key.")
      .input[DeleteAgentStateInput]
      .handle { in =>
        h.agentStateDelete(in.key) match
          case Right(d) => ToolResult.text(s"Deleted agent state key '${d.key}'.")
          case Left(e)  => ToolResult.text(s"_${e.message}_")
      }

  private val mergePlanTool =
    tool("merge_plan")
      .description(
        "Plan the import of another Histmersion collection's bundle into this one (read-only: it " +
          "decides nothing and writes nothing). Lists EVERY issue in one run — two citekeys for one " +
          "work, one citekey for two works, differing DOIs/years/statuses, records changed on both " +
          "sides, articles that diverged — each with a stable id (`S-3c1e7a2b`), the evidence, and the " +
          "decisions it allows (left = this collection, right = the bundle). `bundle` is the sha256 of " +
          "a bundle uploaded to the server (the user runs `histmersion merge plan <file>`, which " +
          "uploads it and prints the sha256). `resolve` maps issue ids to decisions " +
          "(`keep-left`, `keep-right`, `both`, `map:<citekey>`, …) to see what would remain; `editions` " +
          "is merge|separate. Present the plan to the user; scholarly decisions (statuses, editions, " +
          "articles) are theirs — never resolve them on their behalf.")
      .input[MergePlanInput]
      .handle { in =>
        val editions = in.editions.map(_.trim).filter(_.nonEmpty)
        editions.map(e => EditionsPolicy.values.find(_.toString.equalsIgnoreCase(e)).toRight(e)) match
          case Some(Left(e)) => ToolResult.text(s"_editions '$e' is not merge or separate_")
          case other =>
            val res = MergeResolutions(in.resolve.toList.sortBy(_._1).map((k, v) => MergeResolution(k, v)),
              MergePolicy(other.flatMap(_.toOption)))
            h.bundles.plan(MergePlanRequest(in.bundle, res)) match
              case Right(p) => ToolResult.text(MergeMarkdown.plan(p, in.all))
              case Left(e)  => ToolResult.text(s"_${e.message}_")
      }

  private val mergesTool =
    tool("merges")
      .description(
        "What this collection took in from other Histmersion collections (read-only). `action`: " +
          "`list` (default) — the merges, newest first, with the bundle, where it came from, the plan " +
          "and the backup taken; `show` with `id` (a merge id or its first characters) — one merge's " +
          "report (sources merged, new, renamed, articles merged three-way…), its resolutions and the " +
          "decisions it remembers; `ledger` — the merge ledger of one record (`uid`: a uid, here or " +
          "merged away, or a citekey) or of one merge (`id`): which record of the other collection " +
          "each one here came from, and the redirects of merged-away uids. Applying a merge is the " +
          "user's (`histmersion merge apply`, or the /merge screen) — never an agent's.")
      .input[MergesInput]
      .handle { in =>
        MergesAction.parse(in.action) match
          case None => ToolResult.text(s"_action '${in.action.getOrElse("")}' is not list, show or ledger_")
          case Some(MergesAction.List) => ToolResult.text(MergeMarkdown.merges(h.bundles.merges(in.limit)))
          case Some(MergesAction.Show) =>
            in.id.fold(ToolResult.text("_show needs `id` (a merge id from `list`)_"))(id =>
              h.bundles.mergeDetail(id).fold(e => ToolResult.text(s"_${e.message}_"), d => ToolResult.text(MergeMarkdown.detail(d))))
          case Some(MergesAction.Ledger) =>
            h.bundles.mergeLedger(in.uid, in.id, in.limit)
              .fold(e => ToolResult.text(s"_${e.message}_"), l => ToolResult.text(MergeMarkdown.ledger(l)))
      }

  private val renameLabel =
    tool("rename_label")
      .description(
        "Give an article a new slug, an event a new id, a goal a new slug, or a gazetteer place a new " +
          "id (`kind`: article | event | goal | place). One transaction: what points at it follows (an " +
          "article's versions, links and every [[old]] in other articles — each rewrite recorded as a " +
          "version; an event's attestations and asset links; a goal's sub-goals, links and schedules; a " +
          "place's names, parents, footprints, events, attestations and finding assignments), and the OLD " +
          "label keeps resolving as an alias (get_article / events / goals / places show the record " +
          "under its new one, a places filter on the old id still matches; a write to the old label is " +
          "refused with where it went). Refused when the new label " +
          "is taken or is an old label of another record. For a source's citekey use rename_source.")
      .input[RenameLabelInput]
      .handle { in =>
        val req = LabelRename(in.to)
        val r = in.kind.trim.toLowerCase(java.util.Locale.ROOT) match
          case "article" => Right(h.renameArticle(in.from, req))
          case "event"   => Right(h.renameEvent(in.from, req))
          case "goal"    => Right(h.renameGoal(in.from, req))
          case "place"   => Right(h.renamePlace(in.from, req))
          case other     => Left(s"_kind '$other' is not article, event, goal or place_")
        r.fold(ToolResult.text, _.fold(e => ToolResult.text(s"_${e.message}_"), x => ToolResult.text(MergeMarkdown.renamed(x))))
      }

  // Declared LAST: every tool above must be initialised before the list is built.
  val server = McpServer(tools =
    List(ping, search, facets, getSource, passages, geoWhen, histogram, neighbors, sourceGraph, contradictions,
      putSource, patchSource, renameSource, exportSource,
      backup, listArticles, getArticle, putArticle, patchArticle, checkEdits, articleCoverage, exportArticle,
      articleVersions, articleVersion, places, placeNames, addPlaceTool, periods, events, patchEventTool,
      attestations, putAttestation, deleteAttestationTool,
      goalsToolRead, putGoalTool, patchGoalTool,
      agentStateTool, deleteAgentStateTool,
      ingest, jobStatus, assets, patchAssetTool, assetLinksTool, deleteAssetLinkTool, mergePlanTool, mergesTool,
      renameLabel)
      ++ OpsTools(h.ops, h)  // stream 2.8: lint · queue · query
      ++ com.histmersion.server.agents.AgentsTools(h.agents)  // embedded agents: start and follow runs
      // stream 2.6: discover / refs / assess / followups / ingest_doi / archive
      ++ com.histmersion.server.acquire.AcquireMcp(acquire).tools)

  val endpoint = server.endpoint(List("mcp"))

  /** Every tool `/mcp` advertises — what a `{{tool:<name>}}` placeholder in a definition may name. */
  def toolNames: Set[String] = server.tools.map(_.name).toSet

object McpTools:
  /** Honour Scala default values when DECODING a tool's arguments — without it Kindlings requires
    * every non-Option field, so `get_source {"citekey":"x"}` was rejected for a missing `fullText`.
    * (`com.histmersion.core` carries the same given for the wire DTOs.) */
  given hearth.kindlings.circederivation.Configuration =
    hearth.kindlings.circederivation.Configuration.default.withDefaults.withStrictDecoding

  // Circe Codec + tapir Schema both derived from each input by Kindlings → matching by construction.
  final case class PingInput() derives KindlingsCodecAsObject
  object PingInput:
    given Schema[PingInput] = KindlingsSchema.derived[PingInput].schema

  /** The MCP spelling of [[com.histmersion.core.Filter]] — flat and all-optional, so a model can fill in only
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
      offset: Option[Int] = None,
      title: Option[String] = None,
      // ── the §1.6 nested grouping. Absent = the ordinary list of hits. ──
      groupBy: Option[String] = None,
      depth: Option[Int] = None,
      asOf: Option[Long] = None,
      samples: Option[Int] = None,
      verbose: Boolean = true
  ) derives KindlingsCodecAsObject:
    def toFilter: Filter = Filter.of(query, tags, authors, places, topics, status, yearFrom, yearTo,
      from, to, minLon, minLat, maxLon, maxLat, sort, limit, offset, title)
  object SearchInput:
    given Schema[SearchInput] = KindlingsSchema.derived[SearchInput].schema
      .modify(_.query)(_.description("Full-text query (websearch syntax: bare words AND-ed, \"phrase\", -exclude, or). Evaluated within one ~4000-char chunk."))
      .modify(_.tags)(_.description("ALL required — every tag must be present on the source or one of its findings"))
      .modify(_.authors)(_.description("ANY matches — each is a case-insensitive substring match on the source's author list"))
      .modify(_.places)(_.description("ANY matches — a place id from `places` matches via geometry + hierarchy; a name matches text (ILIKE)"))
      .modify(_.topics)(_.description("ALL required — every topic must be present on the source"))
      .modify(_.status)(_.description("ANY matches — foundational|current|contested|superseded|debunked|retracted|unassessed"))
      .modify(_.yearFrom)(_.description("Earliest publication year (inclusive)"))
      .modify(_.yearTo)(_.description("Latest publication year (inclusive)"))
      .modify(_.from)(_.description("Deep-time start, signed year (BCE negative, e.g. -1200 for 1200 BCE)"))
      .modify(_.to)(_.description("Deep-time end, signed year (BCE negative)"))
      .modify(_.sort)(_.description("Result ordering: relevance|cited|period|year|title"))
      .modify(_.limit)(_.description("Max results per page (default: configured page size, capped at 500)"))
      .modify(_.offset)(_.description("Skip this many results (for paging; pass the 'next: offset=N' value from the footer)"))
      .modify(_.title)(_.description("Case-insensitive substring of the source's title"))
      .modify(_.groupBy)(_.description("Group into a tree instead of listing: time|region|time,region|region,time"))
      .modify(_.depth)(_.description("Nesting levels for groupBy (1-3; default 1 for MCP)"))
      .modify(_.asOf)(_.description("Signed year the gazetteer hierarchy is read at for groupBy=region"))
      .modify(_.samples)(_.description("Sample sources per leaf (0-5; default 0 for MCP)"))
      .modify(_.verbose)(_.description("false = compact listing: citekey/title/year/status/period/one-why-line, no snippets/matches/summaries"))

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
      .modify(_.field)(_.description("Dimension to enumerate: tags|authors|places|topics|status|provenance"))
      .modify(_.prefix)(_.description("Case-insensitive prefix match for autocompletion"))
      .modify(_.query)(_.description("Free-text filter (same as search's query)"))
      .modify(_.tags)(_.description("ALL required"))
      .modify(_.authors)(_.description("ANY; case-insensitive substring"))
      .modify(_.places)(_.description("ANY; a place id from `places` matches via geometry + hierarchy, a name matches text"))
      .modify(_.topics)(_.description("ALL required"))
      .modify(_.status)(_.description("ANY; foundational|current|contested|superseded|debunked|retracted|unassessed"))

  final case class GetSourceInput(
      citekey: String,
      fullText: Boolean = false,
      section: Option[String] = None,
      heading: Option[String] = None,
      offset: Option[Int] = None,
      chars: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object GetSourceInput:
    given Schema[GetSourceInput] = KindlingsSchema.derived[GetSourceInput].schema
      .modify(_.citekey)(_.description("The source's citekey (e.g. haak2015_massive)"))
      .modify(_.fullText)(_.description("Include the full converted text (shorthand for section=body; capped at the configured limit)"))
      .modify(_.section)(_.description("What to return: summary|findings|notes|toc|body|all (default: summary+findings+notes)"))
      .modify(_.heading)(_.description("Return only chunks under this heading (case-insensitive prefix match on source_chunks.heading)"))
      .modify(_.offset)(_.description("Body slice start (0-based character offset)"))
      .modify(_.chars)(_.description("Body slice length in characters (default: configured full-text-chars limit)"))

  /** What `passages` does — shared by the MCP tool and the agents' toolbox. */
  val PassagesDoc: String =
    "Find a word, phrase or quotation inside ONE source's full text — `grep` over its body. Returns the " +
      "passages around each match (the match in **bold**), each with its `offset` into the body (pass it " +
      "to `get_source` as `offset` to read on) and the heading it sits under, then how many more there " +
      "are. `query` is matched LITERALLY (no stemming), case-insensitive, accents optional (`Hattusa` " +
      "finds `Ḫattuša`), curly quotes and dashes as plain ones; its words may span a line break. " +
      "`regex: true` makes it a Java regex (`^`/`$` per line). `limit` passages (default 10, max 50), " +
      "`context` characters around each match (default 600, max 3000). Use it to check what a source " +
      "says about a term instead of paging through `get_source` slices."

  /** `passages` as Markdown, with the same wording for an unknown citekey as `get_source`. */
  def passagesMarkdown(h: Handlers, in: PassagesInput): String =
    h.passages(Api.PassagesQuery(in.citekey, in.query, in.regex, in.limit, in.context)) match
      case Right(r) => PassagesMarkdown.render(r)
      case Left(e) if e.code.contains(Write.CodeNotFound) =>
        s"_No source '${in.citekey}' — find a citekey with search._"
      case Left(e) => s"_${e.message}_"

  final case class PassagesInput(
      citekey: String,
      query: String,
      regex: Boolean = false,
      limit: Option[Int] = None,
      context: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object PassagesInput:
    given Schema[PassagesInput] = KindlingsSchema.derived[PassagesInput].schema
      .modify(_.citekey)(_.description("The source's citekey (an old, renamed one works too)"))
      .modify(_.query)(_.description("The text to find, matched literally (case- and accent-insensitive); a regex when regex=true"))
      .modify(_.regex)(_.description("Treat query as a Java regular expression (default false)"))
      .modify(_.limit)(_.description("Passages to show (default 10, max 50)"))
      .modify(_.context)(_.description("Characters of context around each match (default 600, max 3000)"))

  /** The MCP spelling of [[com.histmersion.core.Filter]] for `geo_when` — now the FULL filter shape (A2),
    * so `tags`/`authors`/`status`/`topics` work. `region` is an alias for a `places` entry (A1).
    * `periodStart`/`periodEnd` are deprecated aliases for `from`/`to` — kept for one release. */
  final case class GeoWhenInput(
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
      limit: Option[Int] = None,
      /** @deprecated use `from` */
      periodStart: Option[Long] = None,
      /** @deprecated use `to` */
      periodEnd: Option[Long] = None,
      /** @deprecated use `places` */
      region: Option[String] = None
  ) derives KindlingsCodecAsObject:
    def toQuery: Api.FindingsQuery =
      val effectiveFrom = from.orElse(periodStart)
      val effectiveTo   = to.orElse(periodEnd)
      val effectivePlaces = places ++ region.map(_.trim).filter(_.nonEmpty).toList
      Api.FindingsQuery(
        Filter.of(query, tags, authors, effectivePlaces, topics, status, yearFrom, yearTo,
          effectiveFrom, effectiveTo, minLon, minLat, maxLon, maxLat, None, limit, None),
        None) // region is folded into places, not passed separately
  object GeoWhenInput:
    given Schema[GeoWhenInput] = KindlingsSchema.derived[GeoWhenInput].schema
      .modify(_.from)(_.description("Deep-time start, signed year (BCE negative)"))
      .modify(_.to)(_.description("Deep-time end, signed year (BCE negative)"))
      .modify(_.tags)(_.description("ALL required"))
      .modify(_.authors)(_.description("ANY; case-insensitive substring"))
      .modify(_.places)(_.description("ANY; a place id from `places` matches via geometry + hierarchy, a name matches text"))
      .modify(_.region)(_.description("Deprecated alias for a places entry"))
      .modify(_.periodStart)(_.description("Deprecated alias for from"))
      .modify(_.periodEnd)(_.description("Deprecated alias for to"))

  final case class NeighborsInput(citekey: String, kinds: List[String] = Nil) derives KindlingsCodecAsObject
  object NeighborsInput:
    given Schema[NeighborsInput] = KindlingsSchema.derived[NeighborsInput].schema
      .modify(_.citekey)(_.description("The source's citekey"))
      .modify(_.kinds)(_.description("Filter to these edge kinds only: references|challenges|refutes|updates|superseded_by"))

  final case class GraphInput(
      citekey: String,
      depth: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object GraphInput:
    given Schema[GraphInput] = KindlingsSchema.derived[GraphInput].schema
      .modify(_.citekey)(_.description("Root source citekey to start BFS from"))
      .modify(_.depth)(_.description("BFS depth 0–4 (default 2)"))

  final case class ContradictionsInput(
      citekey: Option[String] = None,
      since: Option[Long] = None
  ) derives KindlingsCodecAsObject
  object ContradictionsInput:
    given Schema[ContradictionsInput] = KindlingsSchema.derived[ContradictionsInput].schema
      .modify(_.citekey)(_.description("Show contradictions for this source only; omit for corpus-wide"))
      .modify(_.since)(_.description("Filter by the challenging source's publication year (signed year, BCE negative)"))

  // The write tools reuse the REST DTOs verbatim, so the MCP tool schema and the HTTP body are the
  // same shape — one contract, two transports.
  final case class PutSourceInput(citekey: String, source: SourcePut, strict: Boolean = false)
  object PutSourceInput:
    given Schema[PutSourceInput] = KindlingsSchema.derived[PutSourceInput].schema
      .modify(_.strict)(_.description(StrictDoc))
    given Codec.AsObject[PutSourceInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PutSourceInput]) // #813

  final case class PatchSourceInput(citekey: String, patch: SourcePatch, strict: Boolean = false)
  object PatchSourceInput:
    given Schema[PatchSourceInput] = KindlingsSchema.derived[PatchSourceInput].schema
      .modify(_.strict)(_.description(StrictDoc))
    given Codec.AsObject[PatchSourceInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PatchSourceInput]) // #813

  final case class RenameSourceInput(citekey: String, newCitekey: String) derives KindlingsCodecAsObject
  object RenameSourceInput:
    given Schema[RenameSourceInput] = KindlingsSchema.derived[RenameSourceInput].schema
      .modify(_.citekey)(_.description("The source's current citekey"))
      .modify(_.newCitekey)(_.description("The citekey it should have from now on"))

  final case class ExportSourceInput(citekey: String) derives KindlingsCodecAsObject
  object ExportSourceInput:
    given Schema[ExportSourceInput] = KindlingsSchema.derived[ExportSourceInput].schema
  /** The MCP spelling of [[com.histmersion.core.Filter]] for `histogram` — now the FULL filter shape (A2).
    * `periodStart`/`periodEnd` and `region` are deprecated aliases (A1). */
  final case class HistogramInput(
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
      unit: Option[String] = None,
      /** @deprecated use `from` */
      periodStart: Option[Long] = None,
      /** @deprecated use `to` */
      periodEnd: Option[Long] = None,
      /** @deprecated use `places` */
      region: Option[String] = None
  ) derives KindlingsCodecAsObject:
    def toFilter: Filter =
      val effectiveFrom = from.orElse(periodStart)
      val effectiveTo   = to.orElse(periodEnd)
      val effectivePlaces = places ++ region.map(_.trim).filter(_.nonEmpty).toList
      Filter.of(query, tags, authors, effectivePlaces, topics, status, yearFrom, yearTo,
        effectiveFrom, effectiveTo, minLon, minLat, maxLon, maxLat, sort, limit, None)
  object HistogramInput:
    given Schema[HistogramInput] = KindlingsSchema.derived[HistogramInput].schema
      .modify(_.from)(_.description("Deep-time start, signed year (BCE negative)"))
      .modify(_.to)(_.description("Deep-time end, signed year (BCE negative)"))
      .modify(_.unit)(_.description("What to count: sources (default) | findings"))
      .modify(_.tags)(_.description("ALL required"))
      .modify(_.places)(_.description("ANY; a place id or name"))

  final case class BackupInput() derives KindlingsCodecAsObject
  object BackupInput:
    given Schema[BackupInput] = KindlingsSchema.derived[BackupInput].schema
  // ── article tool inputs (plan §5.2 stream 2.7). The write tools reuse the REST DTOs verbatim, so
  //    the MCP schema and the HTTP body are the same shape — one contract, two transports.
  final case class ListArticlesInput(
      kind: Option[String] = None,
      query: Option[String] = None,
      tag: Option[String] = None,
      title: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives KindlingsCodecAsObject:
    /** `tag` is the shared Filter's (a source tag: articles CITING such a source); `query` is the
      * article's own full text — see [[Api.ArticleQuery]]. */
    def toQuery: Api.ArticleQuery =
      Api.ArticleQuery(Filter.of(tags = tag.toList, limit = limit, offset = offset, title = title), kind, query)
  object ListArticlesInput:
    given Schema[ListArticlesInput] = KindlingsSchema.derived[ListArticlesInput].schema
      .modify(_.kind)(_.description("Article kind: concept|myth|culture|region|timeline|report|index"))
      .modify(_.query)(_.description("Full-text search over title, summary and body"))
      .modify(_.tag)(_.description("Only articles citing a source that carries this tag"))
      .modify(_.title)(_.description("Case-insensitive substring of the article's title"))

  final case class GetArticleInput(slug: String, parts: Option[String] = None) derives KindlingsCodecAsObject
  object GetArticleInput:
    given Schema[GetArticleInput] = KindlingsSchema.derived[GetArticleInput].schema
      .modify(_.slug)(_.description("The article's slug (lowercase, e.g. steppe_hypothesis)"))
      .modify(_.parts)(_.description("Comma-separated parts to return: body,summary,coverage,cited,backlinks (default: all)"))

  /** `article_coverage` as `/mcp` and an agent step both describe it. */
  val ArticleCoverageDoc: String =
    "The coverage of one article, computed by code from the filter it records: how many sources the filter " +
      "returns, which of them the body cites, every other one with its stored reason (or 'not yet assessed'), " +
      "the status breakdown, the disagreement edges (challenges, refutes, updates, superseded_by) touching the " +
      "cited sources, the further searches, and the DRIFT — sources neither cited nor given a reason, reasons " +
      "that went stale. Then the Not used list and Method note exactly as the page generates them. An article " +
      "that records no filter has none (set it with patch_article `filter`)."

  /** `article_coverage`: the one handler, rendered. */
  def articleCoverageMarkdown(h: Handlers, in: ArticleCoverageInput): String =
    h.articleCoverage(in.slug).fold(e => s"_${e.message}_", ArticleCoverage.markdown)

  final case class ArticleCoverageInput(slug: String) derives KindlingsCodecAsObject
  object ArticleCoverageInput:
    given Schema[ArticleCoverageInput] = KindlingsSchema.derived[ArticleCoverageInput].schema
      .modify(_.slug)(_.description("The article's slug"))

  /** `check_edits` as `/mcp` and an agent step both describe it. */
  val CheckEditsDoc: String =
    "Dry-run a change to an article and see what the review checks would say — nothing is written. `edits` " +
      "are applied in order to the CURRENT stored body: each is {op: replace|insertAfter|insertBefore|delete, " +
      "anchor: text copied exactly from the body (it must occur once; whitespace is normalised), text}. Or pass " +
      "`body`, a whole draft, instead (the article need not exist yet). The answer: each edit's fate (applies, " +
      "or why not), then the problems of the result — links to nothing, uncited paragraphs, a Sources cited " +
      "heading, banned filler, formatting errors it introduces — and whether one is fatal. These are exactly the " +
      "checks code runs on a review fixer's answer."

  /** `check_edits`: the one handler, rendered. */
  def checkEdits(h: Handlers, in: CheckEditsInput): String =
    h.checkArticle(in.slug, ArticleEdits.ArticleCheckRequest(in.edits, in.body, in.kind))
      .fold(e => s"_${e.message}_", ArticleEdits.markdown)

  final case class CheckEditsInput(slug: String, edits: List[ArticleEdits.EditAnswer] = Nil, body: Option[String] = None,
      kind: Option[String] = None) derives KindlingsCodecAsObject
  object CheckEditsInput:
    given Schema[CheckEditsInput] = KindlingsSchema.derived[CheckEditsInput].schema
      .modify(_.slug)(_.description("The article's slug"))
      .modify(_.edits)(_.description("Edits to the current stored body, applied in order"))
      .modify(_.body)(_.description("Instead of edits: a whole draft body"))
      .modify(_.kind)(_.description("Check the section set of this kind instead of the article's (index = none, for a fragment to be appended)"))

  /** The `strict` flag of every Markdown-writing tool. */
  private val StrictDoc =
    "Refuse the write when its Markdown has a formatting ERROR (a table that will not render, unpaired **, " +
      "a broken link). Default false: the write goes through and the problems are listed under 'Formatting'."

  final case class PutArticleInput(slug: String, article: ArticlePut, strict: Boolean = false)
  object PutArticleInput:
    given Schema[PutArticleInput] = KindlingsSchema.derived[PutArticleInput].schema
      .modify(_.strict)(_.description(StrictDoc))
    given Codec.AsObject[PutArticleInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PutArticleInput]) // #813

  final case class PatchArticleInput(slug: String, patch: ArticlePatch, strict: Boolean = false)
  object PatchArticleInput:
    given Schema[PatchArticleInput] = KindlingsSchema.derived[PatchArticleInput].schema
      .modify(_.strict)(_.description(StrictDoc))
    given Codec.AsObject[PatchArticleInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PatchArticleInput]) // #813

  final case class ExportArticleInput(slug: String) derives KindlingsCodecAsObject
  object ExportArticleInput:
    given Schema[ExportArticleInput] = KindlingsSchema.derived[ExportArticleInput].schema

  final case class ArticleVersionsInput(slug: String) derives KindlingsCodecAsObject
  object ArticleVersionsInput:
    given Schema[ArticleVersionsInput] = KindlingsSchema.derived[ArticleVersionsInput].schema
      .modify(_.slug)(_.description("The article's slug (lowercase, e.g. steppe_hypothesis)"))

  final case class ArticleVersionInput(slug: String, versionId: Long) derives KindlingsCodecAsObject
  object ArticleVersionInput:
    given Schema[ArticleVersionInput] = KindlingsSchema.derived[ArticleVersionInput].schema
      .modify(_.slug)(_.description("The article's slug"))
      .modify(_.versionId)(_.description("The version id from article_versions"))
  /** Input of the `places` tool. Declared LAST in this file on purpose: native-image metadata indexes
    * the derived lazies per compilation unit, so new `derives KindlingsCodecAsObject` types go at the
    * end rather than in the middle. */
  final case class PlacesInput(
      query: Option[String] = None,
      name: Option[String] = None,
      id: Option[String] = None,
      asOf: Option[Long] = None,
      limit: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object PlacesInput:
    given Schema[PlacesInput] = KindlingsSchema.derived[PlacesInput].schema
      .modify(_.query)(_.description("Name or label substring (case-insensitive); `name` is an alias"))
      .modify(_.name)(_.description("Alias of `query` — a place NAME to look up (not full-text search)"))
      .modify(_.id)(_.description("Place id to look up directly"))
      .modify(_.asOf)(_.description("Signed year the gazetteer is read at (omit for all rows)"))
  // ── ingest / jobs / assets (plan §1.8). Declared LAST, for the same reason `PlacesInput` was. ──
  final case class IngestInput(
      citekey: String,
      path: Option[String] = None,
      base64: Option[String] = None,
      filename: Option[String] = None,
      quality: Boolean = false,
      force: Boolean = false,
      title: Option[String] = None,
      authors: List[String] = Nil,
      year: Option[Int] = None,
      doi: Option[String] = None,
      provenance: Option[String] = None,
      acquireItem: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object IngestInput:
    given Schema[IngestInput] = KindlingsSchema.derived[IngestInput].schema
      .modify(_.citekey)(_.description("Citekey to store the source under"))
      .modify(_.path)(_.description("Server-side file path (alternative to base64)"))
      .modify(_.base64)(_.description("Base64-encoded document (alternative to path)"))
      .modify(_.quality)(_.description("Use the slow, accurate OCR path (docling)"))
      .modify(_.force)(_.description("Accept a file already stored under another citekey"))
      .modify(_.provenance)(_.description("How the source was acquired: manual|external|ill|author|scan|calibre|archive|oa"))
      .modify(_.acquireItem)(_.description(
        "Id of the acquisition-queue item this document fulfils; ticked off when the OCR job succeeds"))

  final case class JobStatusInput(
      id: Option[String] = None,
      status: Option[String] = None,
      limit: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object JobStatusInput:
    given Schema[JobStatusInput] = KindlingsSchema.derived[JobStatusInput].schema
      .modify(_.id)(_.description("Job id to check (omit for a listing)"))
      .modify(_.status)(_.description("Filter listing: queued|running|done|failed"))

  final case class AssetsInput(citekey: String) derives KindlingsCodecAsObject
  object AssetsInput:
    given Schema[AssetsInput] = KindlingsSchema.derived[AssetsInput].schema

  final case class EventsInput(
      query: Option[String] = None,
      id: Option[String] = None,
      kind: Option[String] = None,
      historicity: Option[String] = None,
      placeId: Option[String] = None,
      from: Option[Long] = None,
      to: Option[Long] = None,
      title: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object EventsInput:
    given Schema[EventsInput] = KindlingsSchema.derived[EventsInput].schema
      .modify(_.query)(_.description("Text search over event label and description"))
      .modify(_.id)(_.description("Event id to look up directly (omit for listing)"))
      .modify(_.kind)(_.description("Filter: event|battle|migration|construction|destruction|founding|treaty|reign|period"))
      .modify(_.historicity)(_.description("Filter: attested|inferred|contested|legendary|mythic"))
      .modify(_.placeId)(_.description("Filter by place id"))
      .modify(_.from)(_.description("Overlap: events whose outer span reaches at or after this signed year"))
      .modify(_.to)(_.description("Overlap: events whose outer span reaches at or before this signed year"))
      .modify(_.title)(_.description("Case-insensitive substring of the event's label"))

  final case class PatchEventInput(
      id: String,
      label: Option[String] = None,
      kind: Option[String] = None,
      historicity: Option[String] = None,
      scale: Option[String] = None,
      placeId: Option[String] = None,
      geometry: Option[String] = None,
      lat: Option[Double] = None,
      lon: Option[Double] = None,
      parentId: Option[String] = None,
      description: Option[String] = None,
      citekey: Option[String] = None
  )
  object PatchEventInput:
    given Schema[PatchEventInput] = KindlingsSchema.derived[PatchEventInput].schema
      .modify(_.id)(_.description("Event id"))
      .modify(_.scale)(_.description("local | regional | auto (clear: derived from kind, then place kind)"))
      .modify(_.placeId)(_.description("Gazetteer place id where the event happened (see `places`)"))
      .modify(_.geometry)(_.description("The event's own geometry as GeoJSON (Point or Polygon)"))
      .modify(_.lat)(_.description("The event's own point, latitude (with lon; instead of geometry)"))
      .modify(_.lon)(_.description("The event's own point, longitude (with lat)"))
      .modify(_.historicity)(_.description("attested|inferred|contested|legendary|mythic"))
    given Codec.AsObject[PatchEventInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PatchEventInput]) // #813

  final case class AttestationsInput(
      id: Option[Long] = None,
      citekey: Option[String] = None,
      targetType: Option[String] = None,
      targetId: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object AttestationsInput:
    given Schema[AttestationsInput] = KindlingsSchema.derived[AttestationsInput].schema
      .modify(_.id)(_.description("Attestation id to look up directly (omit for listing)"))
      .modify(_.citekey)(_.description("Filter by source citekey"))
      .modify(_.targetType)(_.description("Filter: finding|event|event_dating|place|edge"))
      .modify(_.targetId)(_.description("Filter by target entity id"))

  final case class AttestationPutInput(
      citekey: String,
      targetType: String,
      targetId: String,
      quote: Option[String] = None,
      prefix: Option[String] = None,
      suffix: Option[String] = None,
      charStart: Option[Int] = None,
      charEnd: Option[Int] = None,
      chunkOrd: Option[Int] = None,
      page: Option[String] = None,
      canvas: Option[String] = None,
      xywh: Option[String] = None,
      ctsUrn: Option[String] = None,
      note: Option[String] = None
  )
  object AttestationPutInput:
    given Schema[AttestationPutInput] = KindlingsSchema.derived[AttestationPutInput].schema
      .modify(_.citekey)(_.description("Source citekey this attestation references"))
      .modify(_.targetType)(_.description("Entity type: finding|event|event_dating|place|edge"))
      .modify(_.targetId)(_.description("Id of the target entity"))
      .modify(_.quote)(_.description("TextQuoteSelector: the exact quoted passage"))
      .modify(_.prefix)(_.description("TextQuoteSelector: text immediately before the quote"))
      .modify(_.suffix)(_.description("TextQuoteSelector: text immediately after the quote"))
      .modify(_.charStart)(_.description("TextPositionSelector: character offset start in OCR body"))
      .modify(_.charEnd)(_.description("TextPositionSelector: character offset end in OCR body"))
      .modify(_.chunkOrd)(_.description("source_chunks ordinal (existing FTS chunk index)"))
      .modify(_.page)(_.description("Page/folio label: '42', 'f.12r', 'pl.VII'"))
      .modify(_.canvas)(_.description("IIIF canvas URI"))
      .modify(_.xywh)(_.description("IIIF region 'x,y,w,h'"))
      .modify(_.ctsUrn)(_.description("CTS URN for classical texts"))
    given Codec.AsObject[AttestationPutInput] = StrictJson.codec(KindlingsCodecAsObject.derived[AttestationPutInput]) // #813

  final case class DeleteAttestationInput(id: Long) derives KindlingsCodecAsObject
  object DeleteAttestationInput:
    given Schema[DeleteAttestationInput] = KindlingsSchema.derived[DeleteAttestationInput].schema
      .modify(_.id)(_.description("Id of the attestation to delete"))

  final case class GoalsInput(
      slug: Option[String] = None,
      status: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object GoalsInput:
    given Schema[GoalsInput] = KindlingsSchema.derived[GoalsInput].schema
      .modify(_.slug)(_.description("Goal slug to look up directly (omit for listing)"))
      .modify(_.status)(_.description("Filter: planned|seeding|compiled|reviewed|done"))

  final case class GoalPutInput(
      slug: String,
      title: String,
      status: String = "planned",
      notes: Option[String] = None,
      tags: List[String] = Nil,
      regions: List[String] = Nil,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None,
      parentSlug: Option[String] = None
  )
  object GoalPutInput:
    given Schema[GoalPutInput] = KindlingsSchema.derived[GoalPutInput].schema
      .modify(_.slug)(_.description("Kebab-case identifier for the goal"))
      .modify(_.status)(_.description("planned|seeding|compiled|reviewed|done"))
    given Codec.AsObject[GoalPutInput] = StrictJson.codec(KindlingsCodecAsObject.derived[GoalPutInput]) // #813

  final case class GoalPatchInput(
      slug: String,
      title: Option[String] = None,
      status: Option[String] = None,
      notes: Option[String] = None,
      tags: Option[List[String]] = None,
      regions: Option[List[String]] = None,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None,
      parentSlug: Option[String] = None,
      budgetUsd: Option[Double] = None
  )
  object GoalPatchInput:
    given Schema[GoalPatchInput] = KindlingsSchema.derived[GoalPatchInput].schema
      .modify(_.slug)(_.description("Goal slug to patch"))
      .modify(_.budgetUsd)(_.description("Cost cap (USD) of the agent runs started for this goal; 0 clears it"))
    given Codec.AsObject[GoalPatchInput] = StrictJson.codec(KindlingsCodecAsObject.derived[GoalPatchInput]) // #813

  final case class AgentStateInput(
      key: Option[String] = None,
      value: Option[String] = None
  ) derives KindlingsCodecAsObject
  object AgentStateInput:
    given Schema[AgentStateInput] = KindlingsSchema.derived[AgentStateInput].schema
      .modify(_.key)(_.description("State key to get or set (omit for listing all keys)"))
      .modify(_.value)(_.description("JSON string value to set (omit to just read the key)"))

  final case class DeleteAgentStateInput(key: String) derives KindlingsCodecAsObject
  object DeleteAgentStateInput:
    given Schema[DeleteAgentStateInput] = KindlingsSchema.derived[DeleteAgentStateInput].schema
      .modify(_.key)(_.description("State key to delete"))

  // ── media associations (plan §1.6) ──────────────────────────────────────────────────────────

  final case class PatchAssetInput(
      citekey: String,
      name: String,
      kind: Option[String] = None,
      caption: Option[String] = None,
      license: Option[String] = None,
      lat: Option[Double] = None,
      lon: Option[Double] = None,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None
  )
  object PatchAssetInput:
    given Schema[PatchAssetInput] = KindlingsSchema.derived[PatchAssetInput].schema
      .modify(_.citekey)(_.description("Source citekey"))
      .modify(_.name)(_.description("Asset file name"))
      .modify(_.kind)(_.description("Asset kind: figure, photo, map, plan, scan"))
    given Codec.AsObject[PatchAssetInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PatchAssetInput]) // #813

  final case class AssetLinksInput(
      citekey: Option[String] = None,
      asset: Option[String] = None,
      targetType: Option[String] = None,
      targetId: Option[String] = None,
      relation: Option[String] = None,
      xywh: Option[String] = None,
      notes: Option[String] = None
  ) derives KindlingsCodecAsObject
  object AssetLinksInput:
    given Schema[AssetLinksInput] = KindlingsSchema.derived[AssetLinksInput].schema
      .modify(_.citekey)(_.description("Filter by citekey (or required for create)"))
      .modify(_.asset)(_.description("Filter by asset name (or required for create)"))
      .modify(_.targetType)(_.description("Filter by target type: event, finding, place, source, article"))
      .modify(_.targetId)(_.description("Filter by target id"))
      .modify(_.relation)(_.description("Set to create a link: depicts, taken_at, illustrates, page_of"))

  final case class DeleteAssetLinkInput(id: Long) derives KindlingsCodecAsObject
  object DeleteAssetLinkInput:
    given Schema[DeleteAssetLinkInput] = KindlingsSchema.derived[DeleteAssetLinkInput].schema
      .modify(_.id)(_.description("Asset link id to delete"))

  /** Input of the `merge_plan` tool (docs/research-merge-import.md §6, M3). */
  final case class MergePlanInput(
      bundle: String,
      resolve: Map[String, String] = Map.empty,
      editions: Option[String] = None,
      all: Boolean = false
  )
  object MergePlanInput:
    given Schema[MergePlanInput] = KindlingsSchema.derived[MergePlanInput].schema
      .modify(_.bundle)(_.description("sha256 of a bundle uploaded to the server (`histmersion merge plan <file>` prints it)"))
      .modify(_.resolve)(_.description("Issue id → decision, e.g. {\"S-3c1e7a2b\": \"map:guterbock1997_hittite\"}"))
      .modify(_.editions)(_.description("merge | separate: answers every year-gap (edition) question at once"))
      .modify(_.all)(_.description("List every default, info line and match, not only the first few"))
    given Codec.AsObject[MergePlanInput] = StrictJson.codec(KindlingsCodecAsObject.derived[MergePlanInput])

  /** What the `merges` tool does. */
  enum MergesAction:
    case List, Show, Ledger
  object MergesAction:
    def parse(raw: Option[String]): Option[MergesAction] =
      raw.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty) match
        case None    => Some(List)
        case Some(a) => values.find(_.toString.equalsIgnoreCase(a))

  /** Input of the `merges` tool (docs/research-merge-import.md §6.1, M7). */
  final case class MergesInput(
      action: Option[String] = None,
      id: Option[String] = None,
      uid: Option[String] = None,
      limit: Option[Int] = None
  )
  object MergesInput:
    given Schema[MergesInput] = KindlingsSchema.derived[MergesInput].schema
      .modify(_.action)(_.description("list (default) | show | ledger"))
      .modify(_.id)(_.description("A merge id, or its first characters (show; ledger of one merge)"))
      .modify(_.uid)(_.description("ledger: the record — a uid (here, or merged away) or a citekey"))
      .modify(_.limit)(_.description("How many merges / ledger rows (default 50 / 200)"))
    given Codec.AsObject[MergesInput] = StrictJson.codec(KindlingsCodecAsObject.derived[MergesInput])

  /** Input of the `rename_label` tool (V042, V053, V055). */
  final case class RenameLabelInput(kind: String, from: String, to: String)
  object RenameLabelInput:
    given Schema[RenameLabelInput] = KindlingsSchema.derived[RenameLabelInput].schema
      .modify(_.kind)(_.description("article | event | goal | place"))
      .modify(_.from)(_.description("The current slug (article, goal) or id (event, place)"))
      .modify(_.to)(_.description("The new one: lower-case letters, digits, '_', '.' and '-'"))
    given Codec.AsObject[RenameLabelInput] = StrictJson.codec(KindlingsCodecAsObject.derived[RenameLabelInput])

  /** Input of the `periods` tool (#719). Declared last, like `PlacesInput`. */
  final case class PeriodsInput(
      query: Option[String] = None,
      id: Option[String] = None,
      region: Option[String] = None,
      from: Option[Long] = None,
      to: Option[Long] = None
  ) derives KindlingsCodecAsObject
  object PeriodsInput:
    given Schema[PeriodsInput] = KindlingsSchema.derived[PeriodsInput].schema
      .modify(_.query)(_.description("Label substring (case-insensitive), e.g. 'Minoan'"))
      .modify(_.id)(_.description("Period id (a PeriodO id such as p0ds9qjq7b2) to show"))
      .modify(_.region)(_.description("Exact region, case-insensitive (e.g. 'Crete', 'Levant')"))
      .modify(_.from)(_.description("Signed year (BCE negative): periods overlapping from..to"))
      .modify(_.to)(_.description("Signed year (BCE negative): periods overlapping from..to"))

  /** Input of the `place_names` tool (#987). Declared last, like `PlacesInput`. */
  final case class PlaceNamesInput(
      placeId: Option[String] = None,
      name: Option[String] = None,
      lang: Option[String] = None,
      remove: Option[Boolean] = None
  )
  object PlaceNamesInput:
    given Schema[PlaceNamesInput] = KindlingsSchema.derived[PlaceNamesInput].schema
      .modify(_.placeId)(_.description("The place (an id from `places`); required with `name`"))
      .modify(_.name)(_.description("The name to add (or remove); omit to list the names added by hand"))
      .modify(_.lang)(_.description("Language tag of the name: en, grc, la, pl …; omit when unknown"))
      .modify(_.remove)(_.description("true: remove this name (only a name added by hand)"))
    given Codec.AsObject[PlaceNamesInput] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceNamesInput])
