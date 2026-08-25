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
        "Fetch one compiled article by slug: its Markdown body, what links TO it (backlinks: other " +
          "articles, and the source notes that mention it) and the sources it cites with their " +
          s"title, year and status. $LinkRules Read the article before patching it. $PatchFirstArticle")
      .input[GetArticleInput]
      .handle { in =>
        h.getArticle(in.slug) match
          case Right(v) => ToolResult.text(ArticleMarkdown.article(v))
          case Left(e)  => ToolResult.text(s"_${e.message}_")
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
          "exactly as it is. `append` adds Markdown at the end (the normal way to add a section as " +
          "new sources land), `body` rewrites the prose, `title`, `summary`, `kind`, `regions`, " +
          "`period{start,end}` and `tags{add,remove,replace}` touch just themselves. Pass " +
          "ifUpdatedAt with the updatedAt you last read to be told — instead of silently clobbering " +
          s"— when someone else changed the article meanwhile. $ArticleRules $LinkRules")
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
            ToolResult.text(Markdown.places(h.places(in.query, in.asOf, in.limit).toVector, in.asOf))
      }


  // Declared LAST: every tool above must be initialised before the list is built.
  val endpoint = McpServer(tools =
    List(ping, search, facets, getSource, geoWhen, histogram, neighbors, putSource, patchSource, exportSource,
      backup, listArticles, getArticle, putArticle, patchArticle, exportArticle, places)
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

  final case class GetArticleInput(slug: String) derives KindlingsCodecAsObject
  object GetArticleInput:
    given Schema[GetArticleInput] = KindlingsSchema.derived[GetArticleInput].schema

  final case class PutArticleInput(slug: String, article: ArticlePut) derives KindlingsCodecAsObject
  object PutArticleInput:
    given Schema[PutArticleInput] = KindlingsSchema.derived[PutArticleInput].schema

  final case class PatchArticleInput(slug: String, patch: ArticlePatch) derives KindlingsCodecAsObject
  object PatchArticleInput:
    given Schema[PatchArticleInput] = KindlingsSchema.derived[PatchArticleInput].schema

  final case class ExportArticleInput(slug: String) derives KindlingsCodecAsObject
  object ExportArticleInput:
    given Schema[ExportArticleInput] = KindlingsSchema.derived[ExportArticleInput].schema
  /** Input of the `places` tool. Declared LAST in this file on purpose: native-image metadata indexes
    * the derived lazies per compilation unit, so new `derives KindlingsCodecAsObject` types go at the
    * end rather than in the middle. */
  final case class PlacesInput(
      query: Option[String] = None,
      id: Option[String] = None,
      asOf: Option[Long] = None,
      limit: Option[Int] = None
  ) derives KindlingsCodecAsObject
  object PlacesInput:
    given Schema[PlacesInput] = KindlingsSchema.derived[PlacesInput].schema
