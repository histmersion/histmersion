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

  /** `GET /api/sources/{citekey}/articles` — the compiled articles that CITE this source.
    *
    * The other direction of `GET /api/articles/{slug}`'s `sourcesCited`, and the half that was
    * missing: without it the wikilink graph is only ever walkable from an article down to its
    * sources, never from a source back up to what the corpus has written about it. Kept as its own
    * route rather than a field on [[SourceWithFindings]] so the (much hotter) `get_source` read does
    * not grow a join it usually does not need. */
  val sourceArticles: PublicEndpoint[String, ApiError, List[Backlink], Any] =
    base.get.in("sources" / path[String]("citekey") / "articles").out(jsonBody[List[Backlink]])
      .errorOut(statusCode(StatusCode.NotFound))

  /** `GET /api/sources/{ck}/view?section=…&heading=…&offset=…&chars=…` — the source as Markdown,
    * with sectioning (plan §6 A1/A7/A18). The MCP `get_source` tool and the CLI `get --section`
    * both reach `Handlers.sourceMarkdown` through this. */
  final case class SourceViewQuery(
      citekey: String,
      fullText: Boolean = false,
      section: Option[String] = None,
      heading: Option[String] = None,
      offset: Option[Int] = None,
      chars: Option[Int] = None
  )

  val sourceView: PublicEndpoint[SourceViewQuery, ApiError, String, Any] =
    endpoint.get.in("api" / "sources" / path[String]("citekey") / "view")
      .in(query[Boolean]("fullText").default(false))
      .in(query[Option[String]]("section"))
      .in(query[Option[String]]("heading"))
      .in(query[Option[Int]]("offset"))
      .in(query[Option[Int]]("chars"))
      .mapInTo[SourceViewQuery]
      .errorOut(statusCode(StatusCode.NotFound)).errorOut(jsonBody[ApiError])
      .out(stringBody)

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

  /** Input of `GET /api/aggregate`: the aggregation axes plus the SHARED [[Filter]].
    *
    *  - `by` = `time` (default, the flat strip of §1.7) | `region` | `time,region` | `region,time` —
    *    the NESTED tree of §1.6, parsed by [[Tree.parseBy]].
    *  - `unit` = `sources` (default, the unit of every result) | `findings`.
    *  - `depth` = how many levels the tree nests, 1…[[Tree.MaxDepth]]; `0` → config `ui.tree-depth`.
    *    ABSENT is different from `0` for `by=time`: with no `depth`/`samples` at all that axis
    *    returns only the flat §1.7 strip, so the timeline never pays for a tree it does not draw.
    *  - `asOf` = the signed year the gazetteer hierarchy is read at, for `by=region`/`region,time`;
    *    absent → the middle of the window. (`time,region` ignores it: each time bucket reads the
    *    hierarchy at its OWN midpoint, which is the point of nesting time first.)
    *  - `samples` = how many sample sources each LEAF carries, 0…5.
    *
    * The window to bucket is the filter's `from`/`to`; absent, the configured default span is used.
    * Narrowing `from`/`to` (or `place`) to ONE bucket and asking again is how a client fetches a
    * deeper level lazily. */
  final case class AggregateQuery(
      by: Option[String],
      unit: Option[String],
      filter: Filter,
      depth: Option[Int] = None,
      asOf: Option[Long] = None,
      samples: Option[Int] = None
  )

  object AggregateQuery:
    def time(filter: Filter, unit: Option[String] = None): AggregateQuery =
      AggregateQuery(Some("time"), unit, filter)

    /** The §1.6 tree: `by` is one of `region|time,region|region,time` (or `time` for a NESTED strip).
      *
      * `depth` defaults to `Some(0)` = "the server's configured `ui.tree-depth`". That is not the
      * same as leaving it out: `by=time` without any `depth`/`samples` is the flat §1.7 strip (what
      * the timeline asks for on every span change, and it must not pay for a tree it ignores), while
      * `by=time&depth=0` is the nested answer at the default depth. */
    def tree(by: String, filter: Filter, unit: Option[String] = None, depth: Option[Int] = None,
        asOf: Option[Long] = None, samples: Option[Int] = None): AggregateQuery =
      AggregateQuery(Some(by), unit, filter, depth.orElse(Some(0)), asOf, samples)

  /** The timeline strip's data source (plan §1.7) AND the §1.6 nested tree: how many sources fall in
    * each time bucket / gazetteer node of the current filter. One aggregation, so the strip, the
    * "by time" tab, the CLI `--group` output and the MCP `groupBy` answer can never disagree.
    * Filtered by exactly the same parameters as `/api/sources` and `/api/findings`. */
  val aggregate: PublicEndpoint[AggregateQuery, ApiError, TimeHistogram, Any] =
    base.get.in("aggregate")
      .in(query[Option[String]]("by"))
      .in(query[Option[String]]("unit"))
      .in(filterQuery)
      .in(query[Option[Int]]("depth"))
      .in(query[Option[Long]]("asOf"))
      .in(query[Option[Int]]("samples"))
      .mapInTo[AggregateQuery]
      .out(jsonBody[TimeHistogram])

  val neighbors: PublicEndpoint[(String, List[String]), ApiError, List[NeighborRow], Any] =
    base.get.in("neighbors" / path[String]("citekey"))
      .in(query[List[String]]("kind"))
      .out(jsonBody[List[NeighborRow]])

  val graph: PublicEndpoint[(String, Option[Int]), ApiError, SourceGraph, Any] =
    base.get.in("graph" / path[String]("citekey"))
      .in(query[Option[Int]]("depth"))
      .out(jsonBody[SourceGraph])

  /** `GET /api/contradictions?citekey=&since=` — disagreement edges and contested statuses.
    * With a citekey: its challenges/refutes/updates/superseded_by edges in both directions.
    * Without: corpus-wide sources that have such an edge OR status in contested|superseded|debunked|retracted. */
  val contradictions: PublicEndpoint[(Option[String], Option[Long]), ApiError, ContradictionsResult, Any] =
    base.get.in("contradictions")
      .in(query[Option[String]]("citekey"))
      .in(query[Option[Long]]("since"))
      .out(jsonBody[ContradictionsResult])

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

  /** `GET /api/articles/{slug}/versions` — the edit history of one article (newest first). */
  val articleVersions: PublicEndpoint[String, ApiError, List[ArticleVersion], Any] =
    base.get.in("articles" / path[String]("slug") / "versions").out(jsonBody[List[ArticleVersion]])

  /** `GET /api/articles/{slug}/versions/{id}` — one historical version's full body. */
  val articleVersion: PublicEndpoint[(String, Long), ApiError, ArticleVersionFull, Any] =
    base.get.in("articles" / path[String]("slug") / "versions" / path[Long]("versionId"))
      .out(jsonBody[ArticleVersionFull])

  /** Every slug, sorted — so `histdb article export <dir>` can walk the whole wiki. */
  val articleSlugs: PublicEndpoint[Unit, ApiError, List[String], Any] =
    base.get.in("article-slugs").out(jsonBody[List[String]])

  /** The whole REST surface: this file's endpoints plus the stream-2.8 operations (lint · queue ·
    * read-only query), which live in [[OpsApi]] so that stream owns one file. */
  val all: List[AnyEndpoint] = List(version, health, config, search, sources, facets,
    // `sources/{ck}/articles`, `sources/{ck}/view` and `sources/{ck}/export` are longer paths than `sources/{ck}` — register first
    sourceArticles, sourceView, getSource, findings, aggregate, neighbors, graph, contradictions, seed,
    putSource, patchSource, importNote, exportSource, citekeys, resolveEdges, reindex, backup, backups, downloadBackup, restore,
    // `articles/import`, `articles/{slug}/export` and `articles/{slug}/versions` are longer paths than
    // `articles/{slug}`, so they are interpreted first — exactly as `exportSource` precedes `getSource`.
    articles, articleSlugs, importArticle, exportArticle, articleVersions, articleVersion,
    getArticle, putArticle, patchArticle, deleteArticle,
    // events (1.2): /{id} before the list
    events, getEvent, putEvent, patchEvent, deleteEvent,
    // attestations (1.3): /{id} before the list
    attestations, getAttestation, createAttestation, patchAttestation, deleteAttestation,
    // the gazetteer (2.1): /{id}/children and /{id}/tree before /{id}
    places, placeChildren, placeTree, placeFootprints, placeLinks,
    place, importPlaces, rebuildPlaces, periods,
    // ingest, jobs and assets (2.4); `sources/{ck}/assets` precedes `sources/{ck}` like `export`
    ingest, reocr, job, jobs, assets, putAssets, asset) ++ OpsApi.all
  // ── the gazetteer (plan §1.6a) ───────────────────────────────────────────────────────────────

  /** `GET /api/places?q=&asOf=&limit=` — name/label search over the gazetteer, valid at `asOf` (a
    * signed year; omitted = every row whatever its validity). The `id` of a hit is what `place=` on
    * every other endpoint wants: an id matches through the materialised `finding_places` **and the
    * hierarchy as of the filter's period**, where a bare name only matches text. */
  val places: PublicEndpoint[(Option[String], Option[Long], Option[Int]), ApiError, List[PlaceHit], Any] =
    base.get.in("places")
      .in(query[Option[String]]("q")).in(query[Option[Long]]("asOf")).in(query[Option[Int]]("limit"))
      .out(jsonBody[List[PlaceHit]])

  /** Children of one place, as of `asOf` — only edges whose OWN period covers it. */
  val placeChildren: PublicEndpoint[(String, Option[Long]), ApiError, List[PlaceSummary], Any] =
    base.get.in("places" / path[String]("id") / "children").in(query[Option[Long]]("asOf"))
      .out(jsonBody[List[PlaceSummary]]).errorOut(statusCode(StatusCode.NotFound))

  /** The subtree under one place as a depth-tagged pre-order walk (the indented list MCP prints). */
  val placeTree: PublicEndpoint[(String, Option[Long], Option[Int]), ApiError, List[PlaceTreeRow], Any] =
    base.get.in("places" / path[String]("id") / "tree")
      .in(query[Option[Long]]("asOf")).in(query[Option[Int]]("depth"))
      .out(jsonBody[List[PlaceTreeRow]]).errorOut(statusCode(StatusCode.NotFound))

  /** One place: the row, its geometry as GeoJSON text, and its parents/children as of `asOf`. */
  val place: PublicEndpoint[(String, Option[Long]), ApiError, PlaceDetail, Any] =
    base.get.in("places" / path[String]("id")).in(query[Option[Long]]("asOf"))
      .out(jsonBody[PlaceDetail]).errorOut(statusCode(StatusCode.NotFound))

  /** Dated geometries for a place — the `place_footprints` table (V016). */
  val placeFootprints: PublicEndpoint[(String, Option[Long]), ApiError, List[PlaceFootprint], Any] =
    base.get.in("places" / path[String]("id") / "footprints").in(query[Option[Long]]("asOf"))
      .out(jsonBody[List[PlaceFootprint]]).errorOut(statusCode(StatusCode.NotFound))

  /** External cross-references for a place — Pleiades, Wikidata, TGN etc. */
  val placeLinks: PublicEndpoint[String, ApiError, List[PlaceLink], Any] =
    base.get.in("places" / path[String]("id") / "links")
      .out(jsonBody[List[PlaceLink]]).errorOut(statusCode(StatusCode.NotFound))

  /** Named historical periods (PeriodO-shaped). `q` searches label, `region` narrows by scope. */
  val periods: PublicEndpoint[(Option[String], Option[String], Option[Long], Option[Long]), ApiError,
      List[HistoricalPeriod], Any] =
    base.get.in("periods")
      .in(query[Option[String]]("q")).in(query[Option[String]]("region"))
      .in(query[Option[Long]]("from")).in(query[Option[Long]]("to"))
      .out(jsonBody[List[HistoricalPeriod]])

  /** `POST /api/places/import` — the body is a Linked Places Format document or a plain GeoJSON
    * `FeatureCollection` (both read by [[Lpf]]); `kind` is the fallback for features that do not
    * declare one. Upsert: re-importing a file is idempotent. Send big files in batches. */
  val importPlaces: PublicEndpoint[(Option[String], String), ApiError, PlaceImportResult, Any] =
    // `writeBase`, not `base`: an import IS a write, and the maintenance lock answers 503 — a code
    // the plain error mapping cannot express.
    writeBase.post.in("places" / "import").in(query[Option[String]]("kind")).in(stringBody)
      .out(jsonBody[PlaceImportResult])

  /** `POST /api/places/rebuild` — re-materialise `finding_places` from the geometry, the periods and
    * the `region_name` fallback. Run it after an import or after the corpus changed. */
  val rebuildPlaces: PublicEndpoint[Unit, ApiError, PlaceRebuildResult, Any] =
    writeBase.post.in("places" / "rebuild").out(jsonBody[PlaceRebuildResult])

  // ── events (plan §1.2) ────────────────────────────────────────────────────────────────────────

  final case class EventQuery(
      q: Option[String] = None,
      kind: Option[String] = None,
      historicity: Option[String] = None,
      placeId: Option[String] = None,
      from: Option[Long] = None,
      to: Option[Long] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  )

  val events: PublicEndpoint[EventQuery, ApiError, EventList, Any] =
    base.get.in("events")
      .in(query[Option[String]]("q"))
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("historicity"))
      .in(query[Option[String]]("placeId"))
      .in(query[Option[Long]]("from"))
      .in(query[Option[Long]]("to"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .mapInTo[EventQuery]
      .out(jsonBody[EventList])

  val getEvent: PublicEndpoint[String, ApiError, EventDetail, Any] =
    endpoint.get.in("api" / "events" / path[String]("id"))
      .errorOut(writeErrors).out(jsonBody[EventDetail])

  val putEvent: PublicEndpoint[(String, EventPut), ApiError, EventDetail, Any] =
    endpoint.put.in("api" / "events" / path[String]("id")).in(jsonBody[EventPut])
      .errorOut(writeErrors).out(jsonBody[EventDetail])

  val patchEvent: PublicEndpoint[(String, EventPatch), ApiError, EventDetail, Any] =
    endpoint.patch.in("api" / "events" / path[String]("id")).in(jsonBody[EventPatch])
      .errorOut(writeErrors).out(jsonBody[EventDetail])

  val deleteEvent: PublicEndpoint[String, ApiError, EventDeleted, Any] =
    endpoint.delete.in("api" / "events" / path[String]("id"))
      .errorOut(writeErrors).out(jsonBody[EventDeleted])

  // ── attestations (plan §1.3) ─────────────────────────────────────────────────────────────────

  final case class AttestationQuery(
      citekey: Option[String] = None,
      targetType: Option[String] = None,
      targetId: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  )

  val attestations: PublicEndpoint[AttestationQuery, ApiError, AttestationList, Any] =
    base.get.in("attestations")
      .in(query[Option[String]]("citekey"))
      .in(query[Option[String]]("targetType"))
      .in(query[Option[String]]("targetId"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .mapInTo[AttestationQuery]
      .out(jsonBody[AttestationList])

  val getAttestation: PublicEndpoint[Long, ApiError, Attestation, Any] =
    endpoint.get.in("api" / "attestations" / path[Long]("id"))
      .errorOut(writeErrors).out(jsonBody[Attestation])

  val createAttestation: PublicEndpoint[AttestationPut, ApiError, Attestation, Any] =
    endpoint.post.in("api" / "attestations").in(jsonBody[AttestationPut])
      .errorOut(writeErrors).out(jsonBody[Attestation])

  val patchAttestation: PublicEndpoint[(Long, AttestationPatch), ApiError, Attestation, Any] =
    endpoint.patch.in("api" / "attestations" / path[Long]("id")).in(jsonBody[AttestationPatch])
      .errorOut(writeErrors).out(jsonBody[Attestation])

  val deleteAttestation: PublicEndpoint[Long, ApiError, AttestationDeleted, Any] =
    endpoint.delete.in("api" / "attestations" / path[Long]("id"))
      .errorOut(writeErrors).out(jsonBody[AttestationDeleted])

  // ── ingest, jobs and assets (plan §1.8) ──────────────────────────────────────────────────────

  /** Errors of the ingest surface: a duplicate upload is 409 (with the citekey that already holds
    * it), an unknown citekey is 404, anything else a readable 400. */
  private val ingestErrors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[ApiError]) {
        case ApiError(_, Some(Ingest.CodeConflict)) => true
      },
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[ApiError]) {
        case ApiError(_, Some(Write.CodeNotFound)) => true
      },
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  /** The upload's metadata, as QUERY parameters. Not a multipart part list: tapir's netty server has
    * no multipart request-body support (`RawBodyType.MultipartBody` throws
    * `UnsupportedOperationException`), so the document travels as the raw body and everything else
    * as typed parameters — which the sttp client derives from this very definition. */
  val ingestParams: EndpointInput[IngestParams] =
    query[String]("citekey")
      .and(query[Option[String]]("filename"))
      .and(query[Boolean]("quality").default(false))
      .and(query[Boolean]("force").default(false))
      .and(query[Option[String]]("title"))
      .and(query[List[String]]("author"))
      .and(query[Option[Int]]("year"))
      .and(query[Option[String]]("doi"))
      .and(query[Option[String]]("provenance"))
      .mapTo[IngestParams]

  /** `POST /api/ingest` — store the upload, enqueue an OCR job, answer 202 with its id. OCR of a
    * long scan takes tens of minutes, so this NEVER waits for the conversion: poll [[job]]. */
  val ingest: PublicEndpoint[(IngestParams, TapirFile), ApiError, IngestAccepted, Any] =
    endpoint.post.in("api" / "ingest").in(ingestParams).in(fileBody)
      .errorOut(ingestErrors)
      .out(statusCode(StatusCode.Accepted)).out(jsonBody[IngestAccepted])

  /** Re-run the conversion of a source from the original the server spooled at ingest time. */
  val reocr: PublicEndpoint[(String, Boolean), ApiError, IngestAccepted, Any] =
    endpoint.post.in("api" / "sources" / path[String]("citekey") / "reocr")
      .in(query[Boolean]("quality").default(false))
      .errorOut(ingestErrors)
      .out(statusCode(StatusCode.Accepted)).out(jsonBody[IngestAccepted])

  val job: PublicEndpoint[String, ApiError, JobInfo, Any] =
    base.get.in("jobs" / path[String]("id")).out(jsonBody[JobInfo])
      .errorOut(statusCode(StatusCode.NotFound))

  /** `GET /api/jobs?status=queued|running|done|failed` — newest first. */
  val jobs: PublicEndpoint[(Option[String], Option[Int]), ApiError, List[JobInfo], Any] =
    base.get.in("jobs").in(query[Option[String]]("status")).in(query[Option[Int]]("limit"))
      .out(jsonBody[List[JobInfo]])

  /** The figures stored with a source, without their bytes. */
  val assets: PublicEndpoint[String, ApiError, List[AssetInfo], Any] =
    base.get.in("sources" / path[String]("citekey") / "assets").out(jsonBody[List[AssetInfo]])

  /** Add figures to an existing source — the host-side path of `histdb seed`, which can see
    * `md/<citekey>/figures/…` that the server cannot. Sent in bounded batches. */
  val putAssets: PublicEndpoint[(String, List[AssetInput]), ApiError, AssetsStored, Any] =
    endpoint.post.in("api" / "sources" / path[String]("citekey") / "assets").in(jsonBody[List[AssetInput]])
      .errorOut(ingestErrors).out(jsonBody[AssetsStored])

  /** `GET /source/{citekey}/assets/{name}` — the bytes, with their own `Content-Type`. Immutable:
    * an asset name is only ever rewritten by a re-conversion, which replaces the body too. */
  val asset: PublicEndpoint[(String, String), ApiError, (Array[Byte], String), Any] =
    endpoint.get.in("source" / path[String]("citekey") / "assets" / path[String]("name"))
      .errorOut(statusCode(StatusCode.NotFound)).errorOut(jsonBody[ApiError])
      .out(byteArrayBody)
      .out(header[String](sttp.model.HeaderNames.ContentType))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.CacheControl, Ingest.AssetCacheControl)))
