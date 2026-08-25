package histdb.core

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode

/** The REST contract, as Tapir endpoint DEFINITIONS (no logic). The server interprets them
  * (netty-sync) and the CLI derives an sttp client from the very same values, so both sides agree by
  * construction. MCP tools wrap the same server handlers. */
object Api:

  /** Input of `GET /api/findings` (the map feed) and MCP `geo_when`: the SHARED [[Filter]] plus the
    * backwards-compatible `region` parameter older links/clients still send. `region` is simply one
    * more place name (places are any-of), so `?region=Anatolia` keeps meaning what it always did:
    * findings whose `region_name` contains "Anatolia". */
  final case class FindingsQuery(filter: Filter, region: Option[String]):
    def toFilter: Filter =
      region.map(_.trim).filter(_.nonEmpty).fold(filter)(r => filter.copy(places = filter.places :+ r))

  object FindingsQuery:
    def of(filter: Filter): FindingsQuery = FindingsQuery(filter, None)

  private val base = endpoint.in("api").errorOut(jsonBody[ApiError])

  /** `base` for every MUTATING endpoint: same path prefix, but the richer error mapping below, so a
    * write refused by the restore maintenance lock answers 503 wherever it arrives (plan §1.10).
    * A `def` (not a `val`) because endpoints defined above `writeErrors` use it too. */
  private def writeBase = endpoint.in("api").errorOut(writeErrors)

  /** The shared [[Filter]] query parameters (plan §1.0). The names are FIXED — the web UI, the CLI
    * and the other streams rely on them: `q`, `tag`*, `author`*, `place`*, `topic`*, `status`*,
    * `yearFrom`, `yearTo`, `from`, `to`, `minLon`, `minLat`, `maxLon`, `maxLat`, `sort`, `limit`,
    * `offset` (`*` = repeatable). Mapped BOTH ways, so the sttp client can encode a `Filter` back
    * into a request. */
  val filterQuery: EndpointInput[Filter] =
    query[Option[String]]("q")
      .and(query[List[String]]("tag"))
      .and(query[List[String]]("author"))
      .and(query[List[String]]("place"))
      .and(query[List[String]]("topic"))
      .and(query[List[String]]("status"))
      .and(query[Option[Long]]("yearFrom"))
      .and(query[Option[Long]]("yearTo"))
      .and(query[Option[Long]]("from"))
      .and(query[Option[Long]]("to"))
      .and(query[Option[Double]]("minLon"))
      .and(query[Option[Double]]("minLat"))
      .and(query[Option[Double]]("maxLon"))
      .and(query[Option[Double]]("maxLat"))
      .and(query[Option[String]]("sort"))
      .and(query[Option[Int]]("limit"))
      .and(query[Option[Int]]("offset"))
      .mapTo[FilterQuery]
      .map(_.toFilter)(FilterQuery.from)

  val version: PublicEndpoint[Unit, ApiError, VersionInfo, Any] =
    base.get.in("version").out(jsonBody[VersionInfo])

  val health: PublicEndpoint[Unit, ApiError, Health, Any] =
    base.get.in("health").out(jsonBody[Health])

  val config: PublicEndpoint[Unit, ApiError, UiConfig, Any] =
    base.get.in("config").out(jsonBody[UiConfig])

  val search: PublicEndpoint[(Option[String], Option[Int]), ApiError, List[SearchHit], Any] =
    base.get.in("search").in(query[Option[String]]("q")).in(query[Option[Int]]("limit"))
      .out(jsonBody[List[SearchHit]])

  val getSource: PublicEndpoint[String, ApiError, SourceWithFindings, Any] =
    base.get.in("sources" / path[String]("citekey")).out(jsonBody[SourceWithFindings])
      .errorOut(statusCode(StatusCode.NotFound))

  /** `GET /api/findings?<filter>[&region=…]` — the MAP FEED (plan §1.0: the map draws findings, the
    * sidebar lists sources). Takes the same query parameters as `/api/sources`; a finding is returned
    * when IT satisfies the finding-level criteria (deep-time overlap, viewport, place, tags) and its
    * source satisfies the source-level ones (`q`, authors, status, publication years, topics).
    * `limit` (from the shared filter) defaults to the configured `limits.findings`. */
  val findings: PublicEndpoint[FindingsQuery, ApiError, List[GeoHit], Any] =
    base.get.in("findings")
      .in(filterQuery)
      .in(query[Option[String]]("region"))
      .mapInTo[FindingsQuery]
      .out(jsonBody[List[GeoHit]])

  /** Input of `GET /api/aggregate`: the aggregation axes plus the SHARED [[Filter]]. `by` = `time`
    * (wave 1; `region` / `time,region` arrive with the §1.6 tree). `unit` = `sources` (default, the
    * unit of every result) | `findings`. The window to bucket is the filter's `from`/`to`; absent, the
    * configured default span is used. */
  final case class AggregateQuery(by: Option[String], unit: Option[String], filter: Filter)

  object AggregateQuery:
    def time(filter: Filter, unit: Option[String] = None): AggregateQuery =
      AggregateQuery(Some("time"), unit, filter)

  /** The timeline strip's data source (plan §1.7): how many sources fall in each time bucket of the
    * currently visible span. Same aggregation as the §1.6 "by time" tree, so the two always agree.
    * Filtered by exactly the same parameters as `/api/sources` and `/api/findings`. */
  val aggregate: PublicEndpoint[AggregateQuery, ApiError, TimeHistogram, Any] =
    base.get.in("aggregate")
      .in(query[Option[String]]("by"))
      .in(query[Option[String]]("unit"))
      .in(filterQuery)
      .mapInTo[AggregateQuery]
      .out(jsonBody[TimeHistogram])

  val neighbors: PublicEndpoint[String, ApiError, List[NeighborRow], Any] =
    base.get.in("neighbors" / path[String]("citekey")).out(jsonBody[List[NeighborRow]])

  /** Seed from a server-side directory (bind-mounted corpus; `dir` empty → config `corpus.dir`).
    * Skips any file older than its stored row unless `force` — see [[SeedResult.skipped]]. */
  val seed: PublicEndpoint[SeedRequest, ApiError, SeedResult, Any] =
    writeBase.post.in("seed").in(jsonBody[SeedRequest]).out(jsonBody[SeedResult])

  /** Rebuild the FTS chunk index (plan §1.4) — needed once after V004, and after any bulk change to
    * `ocr_markdown` that bypassed `upsert`. Resumable: one call does a bounded amount of work from the
    * `after` cursor (see [[ReindexResult]]); `limit` caps the sources per call. */
  val reindex: PublicEndpoint[(Option[String], Option[Int]), ApiError, ReindexResult, Any] =
    writeBase.post.in("reindex").in(query[Option[String]]("after")).in(query[Option[Int]]("limit"))
      .out(jsonBody[ReindexResult])

  /** `GET /api/sources?<filter>` — the primary discovery endpoint (plan §1.0/§1.3). The SOURCE is the
    * unit of the result; every hit carries the findings that made it match and why. */
  val sources: PublicEndpoint[Filter, ApiError, SearchResult, Any] =
    base.get.in("sources").in(filterQuery).out(jsonBody[SearchResult])

  /** `GET /api/facets?field=…&prefix=…&<filter>` — the vocabulary of a dimension inside the current
    * filter, counted as DISTINCT sources (so a chip shows how many sources it would leave). */
  val facets: PublicEndpoint[(String, Option[String], Filter), ApiError, List[Facet], Any] =
    base.get.in("facets")
      .in(query[String]("field"))
      .in(query[Option[String]]("prefix"))
      .in(filterQuery)
      .out(jsonBody[List[Facet]])

  // ── writes (plan §1.9a) ──────────────────────────────────────────────────────────────────────

  /** One error output carrying three statuses, selected by [[ApiError.code]]: a missing citekey is
    * 404, a failed `ifUpdatedAt` precondition is 412, anything else is a 400 with a readable message. */
  private def writeErrors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[ApiError]) {
        case ApiError(_, Some(Write.CodeNotFound)) => true
      },
      // a write that arrived while a restore holds the maintenance lock (plan §1.10)
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[ApiError]) {
        case ApiError(_, Some(Backup.CodeMaintenance)) => true
      },
      oneOfVariantValueMatcher(StatusCode.PreconditionFailed, jsonBody[ApiError]) {
        case ApiError(_, Some(Write.CodePrecondition)) => true
      },
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  /** Idempotent create/replace of EVERYTHING under one citekey. */
  val putSource: PublicEndpoint[(String, SourcePut), ApiError, SourceWithFindings, Any] =
    endpoint.put.in("api" / "sources" / path[String]("citekey")).in(jsonBody[SourcePut])
      .errorOut(writeErrors).out(jsonBody[SourceWithFindings])

  /** Selective enrichment: only the parts present in the body are replaced. */
  val patchSource: PublicEndpoint[(String, SourcePatch), ApiError, SourceWithFindings, Any] =
    endpoint.patch.in("api" / "sources" / path[String]("citekey")).in(jsonBody[SourcePatch])
      .errorOut(writeErrors).out(jsonBody[SourceWithFindings])

  /** Seed ONE note from raw text — the host-side path of `histdb seed`, since the CLI has no DB and
    * the server has no host filesystem. Same guard as [[seed]]. */
  val importNote: PublicEndpoint[NoteImport, ApiError, SeedResult, Any] =
    writeBase.post.in("sources" / "import").in(jsonBody[NoteImport]).out(jsonBody[SeedResult])

  /** The note as it would live on disk: frontmatter YAML + the `notes` body. */
  val exportSource: PublicEndpoint[String, ApiError, String, Any] =
    endpoint.get.in("api" / "sources" / path[String]("citekey") / "export")
      .errorOut(writeErrors).out(stringBody)

  /** Every citekey, sorted — so `histdb export <dir>` can walk the whole corpus. */
  val citekeys: PublicEndpoint[Unit, ApiError, List[String], Any] =
    base.get.in("citekeys").out(jsonBody[List[String]])

  /** Unify short edge targets to full citekeys; run once after a client-driven seed. */
  val resolveEdges: PublicEndpoint[Unit, ApiError, EdgesResolved, Any] =
    writeBase.post.in("edges" / "resolve").out(jsonBody[EdgesResolved])

  // ── backup / restore (plan §1.10) ────────────────────────────────────────────────────────────

  /** `POST /api/backup` — `pg_dump -Fc` of the SERVER's own database into `backups.dir`. The CLI has
    * no credentials (§2.5), so the dump is taken by the process that has them. */
  val backup: PublicEndpoint[Unit, ApiError, BackupResult, Any] =
    writeBase.post.in("backup").out(jsonBody[BackupResult])

  /** `GET /api/backups` — the archives in `backups.dir`, newest first. */
  val backups: PublicEndpoint[Unit, ApiError, List[BackupInfo], Any] =
    base.get.in("backups").out(jsonBody[List[BackupInfo]])

  /** `GET /api/backups/{file}` — streams one archive, so the host can copy it off the server/volume
    * (`histdb backup --download <dir>`). `file` is validated against the listing: no path components. */
  val downloadBackup: PublicEndpoint[String, ApiError, TapirFile, Any] =
    endpoint.get.in("api" / "backups" / path[String]("file")).errorOut(writeErrors).out(fileBody)

  /** `POST /api/restore` — `pg_restore --clean --if-exists` under the maintenance lock (other writes
    * get 503 meanwhile), then Flyway validate. Refuses a database that still holds sources unless
    * `force`. Deliberately NOT an MCP tool: it destroys data. */
  val restore: PublicEndpoint[RestoreRequest, ApiError, RestoreResult, Any] =
    writeBase.post.in("restore").in(jsonBody[RestoreRequest]).out(jsonBody[RestoreResult])

  // ── articles: the compiled wiki (plan §5.2 stream 2.7) ──────────────────────────────────────

  /** Input of `GET /api/articles`. Deliberately NOT a [[Filter]]: an article has no findings, no
    * bbox and no authors — the dimensions that exist are kind, free text and one tag. */
  final case class ArticleQuery(
      kind: Option[String] = None,
      q: Option[String] = None,
      tag: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  )

  /** `GET /api/articles?kind&q&tag&limit&offset` — the compiled articles. `q` is full text over
    * title/summary/body plus a title substring (the atlas tab types straight into it). */
  val articles: PublicEndpoint[ArticleQuery, ApiError, ArticleList, Any] =
    base.get.in("articles")
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("q"))
      .in(query[Option[String]]("tag"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .mapInTo[ArticleQuery]
      .out(jsonBody[ArticleList])

  /** One article with both directions of its wikilink graph resolved. */
  val getArticle: PublicEndpoint[String, ApiError, ArticleView, Any] =
    endpoint.get.in("api" / "articles" / path[String]("slug"))
      .errorOut(writeErrors).out(jsonBody[ArticleView])

  /** Idempotent create/replace of one article. */
  val putArticle: PublicEndpoint[(String, ArticlePut), ApiError, ArticleView, Any] =
    endpoint.put.in("api" / "articles" / path[String]("slug")).in(jsonBody[ArticlePut])
      .errorOut(writeErrors).out(jsonBody[ArticleView])

  /** Selective enrichment: only the parts present in the body are replaced. */
  val patchArticle: PublicEndpoint[(String, ArticlePatch), ApiError, ArticleView, Any] =
    endpoint.patch.in("api" / "articles" / path[String]("slug")).in(jsonBody[ArticlePatch])
      .errorOut(writeErrors).out(jsonBody[ArticleView])

  val deleteArticle: PublicEndpoint[String, ApiError, ArticleDeleted, Any] =
    endpoint.delete.in("api" / "articles" / path[String]("slug"))
      .errorOut(writeErrors).out(jsonBody[ArticleDeleted])

  /** The article as it would live on disk: `wiki/<kind>/<slug>.md` (frontmatter + body). */
  val exportArticle: PublicEndpoint[String, ApiError, String, Any] =
    endpoint.get.in("api" / "articles" / path[String]("slug") / "export")
      .errorOut(writeErrors).out(stringBody)

  /** Import ONE article note from raw text — the host-side path of `histdb seed`, since the CLI has
    * no DB and the server cannot see the host's `wiki/`. Same "newer in DB" guard as [[seed]]. */
  val importArticle: PublicEndpoint[ArticleImport, ApiError, SeedResult, Any] =
    base.post.in("articles" / "import").in(jsonBody[ArticleImport]).out(jsonBody[SeedResult])

  /** Every slug, sorted — so `histdb article export <dir>` can walk the whole wiki. */
  val articleSlugs: PublicEndpoint[Unit, ApiError, List[String], Any] =
    base.get.in("article-slugs").out(jsonBody[List[String]])

  val all = List(version, health, config, search, sources, facets, getSource, findings, aggregate, neighbors, seed,
    putSource, patchSource, importNote, exportSource, citekeys, resolveEdges, reindex, backup, backups, downloadBackup, restore,
    // `articles/import` and `articles/{slug}/export` are prefixes of `articles/{slug}`, so they are
    // interpreted first — exactly as `exportSource` precedes `getSource`.
    articles, articleSlugs, importArticle, exportArticle, getArticle, putArticle, patchArticle,
    deleteArticle)
