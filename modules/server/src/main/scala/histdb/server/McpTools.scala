package histdb.server

import chimp.server.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import histdb.core.*

/** MCP tools (Chimp). Every tool returns Markdown and delegates to the same [[Handlers]] as REST. */
final class McpTools(h: Handlers, acquire: histdb.server.acquire.AcquireHandlers):
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
        "limit (default 60 000 chars). Bodies are large (mean 450 KB, max 5.7 MB) — prefer `search` " +
        "snippets, a `section` or `heading` slice, or `query` with `substring()` over the full body. " +
        "Use after `search` to read what a source says — not to discover sources.")
      .input[GetSourceInput]
      .handle { in =>
        h.sourceMarkdown(in.citekey, in.fullText, in.section, in.heading, in.offset, in.chars) match
          case Right(md) => ToolResult.text(md)
          case Left(e)   => ToolResult.text(s"_No source '${in.citekey}' — find a citekey with search._")
      }

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
          "are used ONLY when the source is being created.")
      .input[IngestInput]
      .handle { in =>
        h.ingest.fromTool(in.citekey, in.path, in.base64, in.filename, in.quality, in.force,
          in.title, in.authors, in.year, in.doi) match
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

  /** Backup is here because an agent asked to "save the state before a big reseed" should be able to
    * do it; RESTORE deliberately is NOT — it drops the database, which is not a decision to leave to
    * a tool call (`histdb restore` asks a human to confirm). */
  private val backup =
    tool("backup")
      .description(
        "Take a full pg_dump backup of the hist-db database, server-side, and return the file name, " +
          "size and SHA-256 checksum. Use it before a bulk reseed, a mass patch, or any other " +
          "operation you may need to undo. Restoring is NOT available as a tool: it destroys data, " +
          "and is done by a human with `histdb restore <file> --force`.")
      .input[BackupInput]
      .handle { _ =>
        h.backup() match
          case Right(r) => ToolResult.text(Markdown.backup(r))
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
      "with no filler, specialist terms defined in one clause on first use, and the six sections " +
      "question / what the sources say / disagreements / what is unresolved / sources used / not used."
  private val LinkRules =
    "Links are Obsidian wikilinks in the body: [[citekey]] cites a source (it renders as a link to " +
      "that source and appears in the article's 'Sources cited' footer), [[other_slug]] points at " +
      "another article (and shows up in its backlinks). Nothing else maintains the link graph."
  private val PatchFirstArticle =
    "To extend or correct an article that already exists, use patch_article — with `append` to add a " +
      "section, or `body` to rewrite it. NEVER re-upload a whole article with put_article: that " +
      "erases anything you did not resend."

  private val listArticles =
    tool("list_articles")
      .description(
        "List/search the compiled articles. `kind` filters to concept|myth|culture|region|timeline|" +
          "report, `tag` to one tag, `query` is full text over title, summary and body (websearch " +
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
          "Pass a comma-separated list to get several parts (e.g. `parts: \"body,cited\"`). " +
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
          s"$ArticleRules $LinkRules $PatchFirstArticle")
      .input[PutArticleInput]
      .handle { in =>
        h.putArticle(in.slug, in.article) match
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
          "themselves. Pass ifUpdatedAt with the updatedAt you last read to be told — instead of " +
          s"silently clobbering — when someone else changed the article meanwhile. $ArticleRules " +
          s"$LinkRules")
      .input[PatchArticleInput]
      .handle { in =>
        h.patchArticle(in.slug, in.patch) match
          case Right(v) => ToolResult.text(ArticleMarkdown.article(v))
          case Left(e)  => ToolResult.text(s"_${e.message}_")
      }

  private val exportArticle =
    tool("export_article")
      .description(
        "Render an article as the Markdown note it would be on disk at wiki/<kind>/<slug>.md: YAML " +
          "frontmatter (type, slug, title, summary, regions, tags, period_start/period_end) plus the " +
          "body. This is the git-friendly mirror of the database and it round-trips back in through " +
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
  // human decision, made with `histdb article delete`, not something a model should reach for while
  // tidying. Every other article operation is on both surfaces.
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


  private val events =
    tool("events")
      .description(
        "EVENTS database: CIDOC-CRM compatible historical events with four-point time-spans, " +
          "compositional hierarchy (parent_id), causal/temporal edges (Allen interval algebra) " +
          "and participant roles. Without `id` it LISTS events; with `id` it SHOWS one event " +
          "including its datings, edges and participants. Use `kind` to filter (event|battle|" +
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
            val q = Api.EventQuery(in.query, in.kind, in.historicity, in.placeId,
              in.from, in.to, in.limit, in.offset)
            ToolResult.text(Markdown.eventList(h.listEvents(q)))
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

  // Declared LAST: every tool above must be initialised before the list is built.
  val endpoint = McpServer(tools =
    List(ping, search, facets, getSource, geoWhen, histogram, neighbors, sourceGraph, contradictions,
      putSource, patchSource, exportSource,
      backup, listArticles, getArticle, putArticle, patchArticle, exportArticle,
      articleVersions, articleVersion, places, events,
      attestations, putAttestation, deleteAttestationTool,
      ingest, jobStatus, assets)  // stream 2.4: OCR that keeps the figures, as a job
      ++ OpsTools(h.ops)  // stream 2.8: lint · queue · query
      // stream 2.6: discover / refs / assess / followups / ingest_doi / archive
      ++ histdb.server.acquire.AcquireMcp(acquire).tools)
    .endpoint(List("mcp"))

object McpTools:
  /** Honour Scala default values when DECODING a tool's arguments — without it Kindlings requires
    * every non-Option field, so `get_source {"citekey":"x"}` was rejected for a missing `fullText`.
    * (`histdb.core` carries the same given for the wire DTOs.) */
  given hearth.kindlings.circederivation.Configuration =
    hearth.kindlings.circederivation.Configuration.default.withDefaults.withStrictDecoding

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
      offset: Option[Int] = None,
      // ── the §1.6 nested grouping. Absent = the ordinary list of hits. ──
      groupBy: Option[String] = None,
      depth: Option[Int] = None,
      asOf: Option[Long] = None,
      samples: Option[Int] = None,
      verbose: Boolean = true
  ) derives KindlingsCodecAsObject:
    def toFilter: Filter = Filter.of(query, tags, authors, places, topics, status, yearFrom, yearTo,
      from, to, minLon, minLat, maxLon, maxLat, sort, limit, offset)
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

  /** The MCP spelling of [[histdb.core.Filter]] for `geo_when` — now the FULL filter shape (A2),
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
  final case class PutSourceInput(citekey: String, source: SourcePut) derives KindlingsCodecAsObject
  object PutSourceInput:
    given Schema[PutSourceInput] = KindlingsSchema.derived[PutSourceInput].schema

  final case class PatchSourceInput(citekey: String, patch: SourcePatch) derives KindlingsCodecAsObject
  object PatchSourceInput:
    given Schema[PatchSourceInput] = KindlingsSchema.derived[PatchSourceInput].schema

  final case class ExportSourceInput(citekey: String) derives KindlingsCodecAsObject
  object ExportSourceInput:
    given Schema[ExportSourceInput] = KindlingsSchema.derived[ExportSourceInput].schema
  /** The MCP spelling of [[histdb.core.Filter]] for `histogram` — now the FULL filter shape (A2).
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
      limit: Option[Int] = None,
      offset: Option[Int] = None
  ) derives KindlingsCodecAsObject:
    def toQuery: Api.ArticleQuery = Api.ArticleQuery(kind, query, tag, limit, offset)
  object ListArticlesInput:
    given Schema[ListArticlesInput] = KindlingsSchema.derived[ListArticlesInput].schema
      .modify(_.kind)(_.description("Article kind: concept|myth|culture|region|timeline|report|index"))
      .modify(_.query)(_.description("Full-text search over title, summary and body"))
      .modify(_.tag)(_.description("Filter to articles with this tag"))

  final case class GetArticleInput(slug: String, parts: Option[String] = None) derives KindlingsCodecAsObject
  object GetArticleInput:
    given Schema[GetArticleInput] = KindlingsSchema.derived[GetArticleInput].schema
      .modify(_.slug)(_.description("The article's slug (lowercase, e.g. steppe_hypothesis)"))
      .modify(_.parts)(_.description("Comma-separated parts to return: body,summary,cited,backlinks (default: all)"))

  final case class PutArticleInput(slug: String, article: ArticlePut) derives KindlingsCodecAsObject
  object PutArticleInput:
    given Schema[PutArticleInput] = KindlingsSchema.derived[PutArticleInput].schema

  final case class PatchArticleInput(slug: String, patch: ArticlePatch) derives KindlingsCodecAsObject
  object PatchArticleInput:
    given Schema[PatchArticleInput] = KindlingsSchema.derived[PatchArticleInput].schema

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
      provenance: Option[String] = None
  ) derives KindlingsCodecAsObject
  object IngestInput:
    given Schema[IngestInput] = KindlingsSchema.derived[IngestInput].schema
      .modify(_.citekey)(_.description("Citekey to store the source under"))
      .modify(_.path)(_.description("Server-side file path (alternative to base64)"))
      .modify(_.base64)(_.description("Base64-encoded document (alternative to path)"))
      .modify(_.quality)(_.description("Use the slow, accurate OCR path (docling)"))
      .modify(_.force)(_.description("Accept a file already stored under another citekey"))
      .modify(_.provenance)(_.description("How the source was acquired: manual|external|ill|author|scan|calibre|archive|oa"))

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
  ) derives KindlingsCodecAsObject
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

  final case class DeleteAttestationInput(id: Long) derives KindlingsCodecAsObject
  object DeleteAttestationInput:
    given Schema[DeleteAttestationInput] = KindlingsSchema.derived[DeleteAttestationInput].schema
      .modify(_.id)(_.description("Id of the attestation to delete"))
