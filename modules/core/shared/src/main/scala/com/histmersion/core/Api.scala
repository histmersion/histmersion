package com.histmersion.core

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
    * `offset`, `title` (`*` = repeatable). Mapped BOTH ways, so the sttp client can encode a
    * `Filter` back into a request. `title` narrows by the LISTED item's own title: a source's on
    * `/api/sources`, an article's on `/api/articles`, an event's label on `/api/events`. */
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
      .and(query[Option[String]]("title"))
      .mapTo[FilterQuery]
      .map(_.toFilter)(FilterQuery.from)

  val version: PublicEndpoint[Unit, ApiError, VersionInfo, Any] =
    base.get.in("version").out(jsonBody[VersionInfo])

  val health: PublicEndpoint[Unit, ApiError, Health, Any] =
    base.get.in("health").out(jsonBody[Health])

  /** `GET /api/about` — build (version, commit), health, job queue, last backup and store counts. */
  val about: PublicEndpoint[Unit, ApiError, About, Any] =
    base.get.in("about").out(jsonBody[About])

  val config: PublicEndpoint[Unit, ApiError, UiConfig, Any] =
    base.get.in("config").out(jsonBody[UiConfig])

  val search: PublicEndpoint[(Option[String], Option[Int]), ApiError, List[SearchHit], Any] =
    base.get.in("search").in(query[Option[String]]("q")).in(query[Option[Int]]("limit"))
      .out(jsonBody[List[SearchHit]])

  /** `?body=false` leaves `ocrMarkdown` out — a link preview wants the metadata, not a megabyte of
    * OCR text on every hover. */
  /** What a bare identifier names — see [[ResolvedId]]. */
  val resolveId: PublicEndpoint[String, ApiError, ResolvedId, Any] =
    base.get.in("resolve" / path[String]("id")).out(jsonBody[ResolvedId])

  val getSource: PublicEndpoint[(String, Boolean), ApiError, SourceWithFindings, Any] =
    base.get.in("sources" / path[String]("citekey")).in(query[Boolean]("body").default(true))
      .out(jsonBody[SourceWithFindings])
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

  /** `GET /api/sources/{ck}/passages?q=…&regex=…&limit=…&context=…` — find text inside ONE source's
    * body (#954): the passages around each match, with the body offset `get_source` continues from
    * and the heading each sits under. 404 for an unknown citekey, 400 for an empty query or a bad
    * regex. The MCP `passages` tool, the agents' tool and `histmersion passages` all reach
    * `Handlers.passages` through this. */
  final case class PassagesQuery(
      citekey: String,
      q: String,
      regex: Boolean = false,
      limit: Option[Int] = None,
      context: Option[Int] = None
  )

  val passages: PublicEndpoint[PassagesQuery, ApiError, PassageResult, Any] =
    endpoint.get.in("api" / "sources" / path[String]("citekey") / "passages")
      .in(query[String]("q").description("the text to find (literal, case- and accent-insensitive), or a regex with regex=true"))
      .in(query[Boolean]("regex").default(false))
      .in(query[Option[Int]]("limit").description("passages shown (default and cap from config)"))
      .in(query[Option[Int]]("context").description("characters of context around a match"))
      .mapInTo[PassagesQuery]
      .errorOut(writeErrors).out(jsonBody[PassageResult])

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
      samples: Option[Int] = None,
      size: Option[Long] = None
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
      .in(query[Option[Long]]("size"))
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
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[ApiError])(ApiError.hasCode(Write.CodeNotFound)),
      // a write that arrived while a restore holds the maintenance lock (plan §1.10)
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[ApiError])(ApiError.hasCode(Backup.CodeMaintenance)),
      oneOfVariantValueMatcher(StatusCode.PreconditionFailed, jsonBody[ApiError])(ApiError.hasCode(Write.CodePrecondition)),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  /** `?strict=true` on an article or source write: refuse it (400) when the Markdown it writes has a
    * formatting ERROR. Without it the write goes through and the problems come back in `formatting`. */
  private def strictQuery = query[Boolean]("strict").default(false)
    .description("refuse the write when its Markdown has a formatting error (default: report only)")

  /** Idempotent create/replace of EVERYTHING under one citekey. */
  val putSource: PublicEndpoint[(String, SourcePut, Boolean), ApiError, SourceWithFindings, Any] =
    endpoint.put.in("api" / "sources" / path[String]("citekey")).in(jsonBody[SourcePut]).in(strictQuery)
      .errorOut(writeErrors).out(jsonBody[SourceWithFindings])

  /** Selective enrichment: only the parts present in the body are replaced. */
  val patchSource: PublicEndpoint[(String, SourcePatch, Boolean), ApiError, SourceWithFindings, Any] =
    endpoint.patch.in("api" / "sources" / path[String]("citekey")).in(jsonBody[SourcePatch]).in(strictQuery)
      .errorOut(writeErrors).out(jsonBody[SourceWithFindings])

  /** `GET /api/sources/{citekey}/lint` — the formatting problems of a source's summary and notes. */
  val lintSource: PublicEndpoint[String, ApiError, FormatReport, Any] =
    endpoint.get.in("api" / "sources" / path[String]("citekey") / "lint")
      .errorOut(writeErrors).out(jsonBody[FormatReport])

  /** `POST /api/sources/{citekey}/lint/fix` — apply the wording-safe repairs, report what is left. */
  val fixSourceFormat: PublicEndpoint[String, ApiError, FormatReport, Any] =
    endpoint.post.in("api" / "sources" / path[String]("citekey") / "lint" / "fix")
      .errorOut(writeErrors).out(jsonBody[FormatReport])

  /** `POST /api/sources/{citekey}/rename` `{"newCitekey": …}` — give a source a new citekey, in one
    * transaction: every table, finding id and `[[link]]` follows, and the old citekey stays an ALIAS
    * that every read (`/api/sources/{old}`, `/source/{old}`, neighbors, graph, export, wikilinks)
    * still resolves. A write: gated by the API key like every POST. */
  val renameSource: PublicEndpoint[(String, RenameRequest), ApiError, SourceRenamed, Any] =
    writeBase.post.in("sources" / path[String]("citekey") / "rename").in(jsonBody[RenameRequest])
      .out(jsonBody[SourceRenamed])

  /** Every old citekey and the source it names now — what `histmersion export` uses to remove the
    * mirror files of renamed sources. */
  val sourceAliases: PublicEndpoint[Unit, ApiError, List[SourceAlias], Any] =
    base.get.in("source-aliases").out(jsonBody[List[SourceAlias]])

  /** Seed ONE note from raw text — the host-side path of `histmersion seed`, since the CLI has no DB and
    * the server has no host filesystem. Same guard as [[seed]]. */
  val importNote: PublicEndpoint[NoteImport, ApiError, SeedResult, Any] =
    writeBase.post.in("sources" / "import").in(jsonBody[NoteImport]).out(jsonBody[SeedResult])

  /** The note as it would live on disk: frontmatter YAML + the `notes` body. */
  val exportSource: PublicEndpoint[String, ApiError, String, Any] =
    endpoint.get.in("api" / "sources" / path[String]("citekey") / "export")
      .errorOut(writeErrors).out(stringBody)

  /** Every citekey, sorted — so `histmersion export <dir>` can walk the whole corpus. */
  val citekeys: PublicEndpoint[Unit, ApiError, List[String], Any] =
    base.get.in("citekeys").out(jsonBody[List[String]])

  /** Promote unresolved edges whose target now resolves (exactly or by unique short form) to a
    * stored source; run once after a client-driven seed. */
  val resolveEdges: PublicEndpoint[Unit, ApiError, EdgesResolved, Any] =
    writeBase.post.in("edges" / "resolve").out(jsonBody[EdgesResolved])

  // ── backup / restore (plan §1.10) ────────────────────────────────────────────────────────────

  /** `POST /api/backup` — `pg_dump -Fc` of the SERVER's own database into `backups.dir`. The CLI has
    * no credentials (§2.5), so the dump is taken by the process that has them. */
  val backup: PublicEndpoint[Unit, ApiError, BackupStatus, Any] =
    writeBase.post.in("backup").out(jsonBody[BackupStatus])

  /** `GET /api/backup/status` — the running backup job, or the last one; 404 before the first. */
  val backupStatus: PublicEndpoint[Unit, ApiError, BackupStatus, Any] =
    endpoint.get.in("api" / "backup" / "status").errorOut(writeErrors).out(jsonBody[BackupStatus])

  /** `GET /api/backups` — the archives in `backups.dir`, newest first. */
  val backups: PublicEndpoint[Unit, ApiError, List[BackupInfo], Any] =
    base.get.in("backups").out(jsonBody[List[BackupInfo]])

  /** `GET /api/backups/{file}` — streams one archive, so the host can copy it off the server/volume
    * (`histmersion backup --download <dir>`). `file` is validated against the listing: no path components. */
  val downloadBackup: PublicEndpoint[String, ApiError, TapirFile, Any] =
    endpoint.get.in("api" / "backups" / path[String]("file")).errorOut(writeErrors).out(fileBody)

  /** `POST /api/restore` — `pg_restore --clean --if-exists` under the maintenance lock (other writes
    * get 503 meanwhile), then Flyway validate. Refuses a database that still holds sources unless
    * `force`. Deliberately NOT an MCP tool: it destroys data. */
  val restore: PublicEndpoint[RestoreRequest, ApiError, RestoreResult, Any] =
    writeBase.post.in("restore").in(jsonBody[RestoreRequest]).out(jsonBody[RestoreResult])

  // ── portable bundles (docs/research-merge-import.md, milestones M0–M2) ─────────────────────

  /** `GET /api/instance` — this collection's identity: the id every row it creates carries as its
    * `origin`, and the label a bundle names it by. Not an MCP tool, like the bundle operations it
    * serves: none of them is an agent's to run. */
  val instance: PublicEndpoint[Unit, ApiError, InstanceInfo, Any] =
    base.get.in("instance").out(jsonBody[InstanceInfo])

  /** `PUT /api/instance` `{"label": "work"}` — name this collection (blank clears the name). */
  val setInstance: PublicEndpoint[InstanceLabel, ApiError, InstanceInfo, Any] =
    writeBase.put.in("instance").in(jsonBody[InstanceLabel]).out(jsonBody[InstanceInfo])

  /** `GET /api/bundle?since=` — the whole database (or, with `since`, what changed after that
    * moment, deletions included) as a `.hmx` bundle, streamed. Deterministic: an unchanged database
    * gives the same bytes. The manifest's `asOf` is the `since` of the next incremental export. Not
    * an MCP tool: it is a binary download for the host, like a backup. */
  val exportBundle: PublicEndpoint[Option[String], ApiError, (TapirFile, String), Any] =
    endpoint.get.in("api" / "bundle").in(query[Option[String]]("since"))
      .errorOut(writeErrors)
      .out(fileBody)
      .out(header[String](sttp.model.HeaderNames.ContentDisposition))

  /** `POST /api/bundle/verify` — the body is a `.hmx` file: format and schema against this server,
    * every checksum, every record's rev, every blob, and (for a full bundle) every reference. Writes
    * nothing; every problem is listed. Not an MCP tool: its input is a file upload, which an MCP call
    * cannot carry (a server-side path would let a model point the server at any file). */
  val verifyBundle: PublicEndpoint[TapirFile, ApiError, BundleVerifyResult, Any] =
    writeBase.post.in("bundle" / "verify").in(fileBody).out(jsonBody[BundleVerifyResult])

  /** `POST /api/bundle/import?dryRun=` — the body is a `.hmx` file, imported WITHOUT matching: into an
    * empty instance, or back into the one it came from (rows the same uid names; see
    * [[BundleImportResult]]). One transaction; any blocking problem and nothing is written. Not an
    * MCP tool: like `restore`, a human's call. */
  val importBundle: PublicEndpoint[(Boolean, TapirFile), ApiError, BundleImportResult, Any] =
    writeBase.post.in("bundle" / "import").in(query[Boolean]("dryRun").default(false)).in(fileBody)
      .out(jsonBody[BundleImportResult])

  // ── the merge plan (docs/research-merge-import.md §6, milestone M3) ─────────────────────────

  /** `POST /api/bundle/upload` — the body is a `.hmx` file, kept on the server under its sha256 (for
    * `bundles.stale-minutes` after its last use) so `merge plan` and `merge import` can name it: a
    * resolution set is JSON, and a request carries one body. */
  val uploadBundle: PublicEndpoint[TapirFile, ApiError, BundleUpload, Any] =
    writeBase.post.in("bundle" / "upload").in(fileBody).out(jsonBody[BundleUpload])

  /** `POST /api/merge/plan` — what importing an uploaded bundle would do, and EVERY issue, in one run
    * ([[MergePlan]]): pure, deterministic, writes nothing. Also the MCP tool `merge_plan`. */
  val mergePlan: PublicEndpoint[MergePlanRequest, ApiError, MergePlan, Any] =
    writeBase.post.in("merge" / "plan").in(jsonBody[MergePlanRequest]).out(jsonBody[MergePlan])

  /** `POST /api/merge/import` — import an uploaded bundle, taking the resolutions into account; refused
    * (nothing written) while an issue is open or a decision needs `merge apply`. Not an MCP tool. */
  val mergeImport: PublicEndpoint[MergeImportRequest, ApiError, BundleImportResult, Any] =
    writeBase.post.in("merge" / "import").in(jsonBody[MergeImportRequest]).out(jsonBody[BundleImportResult])

  /** `POST /api/merge/apply` — carry out the plan of an uploaded bundle, folds of matched records
    * included (milestone M5): refused while anything is open or when the plan's hash is not the one
    * reviewed; otherwise a backup, then ONE transaction. Not an MCP tool: applying a merge is a
    * person's call, like a restore (docs/research-merge-import.md §7.4). */
  val mergeApply: PublicEndpoint[MergeApplyRequest, ApiError, MergeApplyJob, Any] =
    writeBase.post.in("merge" / "apply").in(jsonBody[MergeApplyRequest]).out(jsonBody[MergeApplyJob])

  /** `GET /api/merge/apply/{job}` — where an apply started by `POST /api/merge/apply` is: it runs in the
    * background (its backup can take minutes), and the POST answers `running` once
    * `merge.apply-wait-seconds` have passed. The jobs of this server's run only. */
  val mergeApplyStatus: PublicEndpoint[String, ApiError, MergeApplyJob, Any] =
    writeBase.get.in("merge" / "apply" / path[String]("job")).out(jsonBody[MergeApplyJob])

  /** `GET /api/merge/sessions/{bundle}` — the decisions made so far on the web review screen for an
    * uploaded bundle (empty when none). Not an MCP tool: it is the screen's draft, and an agent
    * presents a plan rather than deciding it (§7.4). */
  val mergeSession: PublicEndpoint[String, ApiError, MergeSession, Any] =
    writeBase.get.in("merge" / "sessions" / path[String]("bundle")).out(jsonBody[MergeSession])

  /** `PUT /api/merge/sessions/{bundle}` — keep the review screen's decisions (the whole set). */
  val putMergeSession: PublicEndpoint[(String, MergeResolutions), ApiError, MergeSession, Any] =
    writeBase.put.in("merge" / "sessions" / path[String]("bundle")).in(jsonBody[MergeResolutions])
      .out(jsonBody[MergeSession])

  /** `POST /api/merge/resolutions` — read a resolution file (YAML, the body) into resolutions: what the
    * web review screen's upload does with a file the CLI wrote. Pure; writes nothing. */
  val parseResolutions: PublicEndpoint[String, ApiError, MergeResolutions, Any] =
    writeBase.post.in("merge" / "resolutions").in(stringBody).out(jsonBody[MergeResolutions])

  /** `GET /api/merges?limit=` — the merges this collection took in (imports and applies), newest
    * first. Also the MCP tool `merges` (read-only). */
  val merges: PublicEndpoint[Option[Int], ApiError, List[MergeInfo], Any] =
    base.get.in("merges").in(query[Option[Int]]("limit")).out(jsonBody[List[MergeInfo]])

  /** `GET /api/merges/ledger?uid=&merge=&limit=` — the merge ledger: of one record (`uid`: a uid
    * here or merged away, or a citekey), of one merge, or the latest rows. */
  val mergeLedger: PublicEndpoint[(Option[String], Option[String], Option[Int]), ApiError, MergeLedger, Any] =
    writeBase.get.in("merges" / "ledger").in(query[Option[String]]("uid")).in(query[Option[String]]("merge"))
      .in(query[Option[Int]]("limit")).out(jsonBody[MergeLedger])

  /** `GET /api/merges/{id}` — one merge: its report, resolutions and the decisions it remembers. An id
    * prefix (the 8 characters `merge list` prints) is enough when it names one merge. */
  val mergeDetail: PublicEndpoint[String, ApiError, MergeDetail, Any] =
    writeBase.get.in("merges" / path[String]("id")).out(jsonBody[MergeDetail])

  // ── articles: the compiled wiki (plan §5.2 stream 2.7) ──────────────────────────────────────

  /** Input of `GET /api/articles`: the SHARED [[Filter]] (every `/api/sources` parameter) plus what
    * only an article has.
    *
    * The filter selects a set of SOURCES exactly as `/api/sources` would (its `title` aside); an
    * article is listed when it CITES at least one of them (`article_links`). A filter with no
    * source criteria lists every article. `title` is a substring of the article's own title;
    * `limit`/`offset` page the articles; `sort` is ignored (articles sort by kind and title, or by
    * `text` relevance).
    *
    *  - `kind` — the article kind, exact.
    *  - `text` — Postgres FTS over the ARTICLE's own title/summary/body, OR'ed with a title
    *    substring. (`q` is the Filter's: full text over the SOURCES the articles cite; `tag` is the
    *    Filter's too — articles citing a source with that tag.) */
  final case class ArticleQuery(
      filter: Filter = Filter(),
      kind: Option[String] = None,
      text: Option[String] = None
  )

  /** `GET /api/articles?<filter>&kind&text` — the compiled articles citing the filter's sources. */
  val articles: PublicEndpoint[ArticleQuery, ApiError, ArticleList, Any] =
    base.get.in("articles")
      .in(filterQuery)
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("text"))
      .mapInTo[ArticleQuery]
      .out(jsonBody[ArticleList])

  /** `POST /api/articles/{slug}/rename` `{"to": …}` — a new slug: the old one keeps resolving (an
    * alias), and every `[[old]]` in other articles is rewritten. */
  val renameArticle: PublicEndpoint[(String, LabelRename), ApiError, LabelRenamed, Any] =
    writeBase.post.in("articles" / path[String]("slug") / "rename").in(jsonBody[LabelRename]).out(jsonBody[LabelRenamed])

  /** One article with both directions of its wikilink graph resolved. */
  val getArticle: PublicEndpoint[String, ApiError, ArticleView, Any] =
    endpoint.get.in("api" / "articles" / path[String]("slug"))
      .errorOut(writeErrors).out(jsonBody[ArticleView])

  /** Idempotent create/replace of one article. */
  val putArticle: PublicEndpoint[(String, ArticlePut, Boolean), ApiError, ArticleView, Any] =
    endpoint.put.in("api" / "articles" / path[String]("slug")).in(jsonBody[ArticlePut]).in(strictQuery)
      .errorOut(writeErrors).out(jsonBody[ArticleView])

  /** Selective enrichment: only the parts present in the body are replaced. */
  val patchArticle: PublicEndpoint[(String, ArticlePatch, Boolean), ApiError, ArticleView, Any] =
    endpoint.patch.in("api" / "articles" / path[String]("slug")).in(jsonBody[ArticlePatch]).in(strictQuery)
      .errorOut(writeErrors).out(jsonBody[ArticleView])

  /** `GET /api/articles/{slug}/lint` — the formatting problems of an article's body and summary:
    * what the page will not display as the writer meant (a table shown as pipes, literal `**`, a link
    * that is not one), with line, column, excerpt and — when safe — the repair. */
  val lintArticle: PublicEndpoint[String, ApiError, FormatReport, Any] =
    endpoint.get.in("api" / "articles" / path[String]("slug") / "lint")
      .errorOut(writeErrors).out(jsonBody[FormatReport])

  /** `POST /api/articles/{slug}/lint/fix` — apply the repairs that never touch wording (a blank line
    * before a table, an unescaped `\[\[`, a pasted frontmatter block removed), as one guarded patch. */
  val fixArticleFormat: PublicEndpoint[String, ApiError, FormatReport, Any] =
    endpoint.post.in("api" / "articles" / path[String]("slug") / "lint" / "fix")
      .errorOut(writeErrors).out(jsonBody[FormatReport])

  val deleteArticle: PublicEndpoint[String, ApiError, ArticleDeleted, Any] =
    endpoint.delete.in("api" / "articles" / path[String]("slug"))
      .errorOut(writeErrors).out(jsonBody[ArticleDeleted])

  /** The article as it would live on disk: `wiki/<kind>/<slug>.md` (frontmatter + body). */
  val exportArticle: PublicEndpoint[String, ApiError, String, Any] =
    endpoint.get.in("api" / "articles" / path[String]("slug") / "export")
      .errorOut(writeErrors).out(stringBody)

  /** Import ONE article note from raw text — the host-side path of `histmersion seed`, since the CLI has
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

  /** Every slug, sorted — so `histmersion article export <dir>` can walk the whole wiki. */
  val articleSlugs: PublicEndpoint[Unit, ApiError, List[String], Any] =
    base.get.in("article-slugs").out(jsonBody[List[String]])

  /** The whole REST surface: this file's endpoints plus the stream-2.8 operations (lint · queue ·
    * read-only query), which live in [[OpsApi]] so that stream owns one file. */
  val all: List[AnyEndpoint] = List(version, health, about, config, search, sources, facets,
    // `sources/{ck}/articles`, `sources/{ck}/view`, `…/passages` and `sources/{ck}/export` are longer paths than `sources/{ck}` — register first
    sourceArticles, sourceView, passages, lintSource, fixSourceFormat, getSource, findings, aggregate, neighbors, graph, contradictions, seed,
    putSource, patchSource, renameSource, sourceAliases, importNote, exportSource, citekeys, resolveEdges, reindex, backup, backups, downloadBackup, restore,
    // portable bundles: `bundle/verify` and `bundle/import` are POSTs, the export the one GET
    instance, setInstance, exportBundle, verifyBundle, importBundle, uploadBundle, mergePlan, mergeImport, mergeApply,
    mergeApplyStatus, mergeSession, putMergeSession, parseResolutions,
    // `merges/ledger` before `merges/{id}`
    merges, mergeLedger, mergeDetail,
    // label renames (V042, V053): POSTs on longer paths than the records' own
    renameArticle, renameEvent, renameGoal, renamePlace,
    // `articles/import`, `articles/{slug}/export` and `articles/{slug}/versions` are longer paths than
    // `articles/{slug}`, so they are interpreted first — exactly as `exportSource` precedes `getSource`.
    articles, articleSlugs, importArticle, exportArticle, articleVersions, articleVersion,
    lintArticle, fixArticleFormat, getArticle, putArticle, patchArticle, deleteArticle,
    // events (1.2): /{id} before the list
    events, getEvent, putEvent, patchEvent, deleteEvent,
    // attestations (1.3): /{id} before the list
    attestations, getAttestation, createAttestation, patchAttestation, deleteAttestation,
    // goals (1.5): /{slug} before the list
    goals, getGoal, putGoal, patchGoal, deleteGoal,
    // agent state (1.5)
    agentStateList, agentStateGet, agentStateSet, agentStateDelete,
    // the gazetteer (2.1): /{id}/children and /{id}/tree before /{id}
    places, placesGeo, placeChildren, placeTree, placeFootprints, placeLinks,
    place, importPlaces, rebuildPlaces, fetchExternalPlaces, buildHierarchy, periods,
    // named periods (#719) and first-run data (#720); `periods/seed|import|fetch` are POSTs, so the
    // GET `periods/{id}` never shadows them
    period, seedPeriods, importPeriods, fetchPeriods, init,
    // ingest, jobs and assets (2.4); `sources/{ck}/assets` precedes `sources/{ck}` like `export`
    ingest, reocr, original, job, jobs, assets, putAssets, assetDetail, patchAsset, asset,
    // asset links (1.6)
    mediaGeo, assetLinks, createAssetLink, deleteAssetLink,
    // IIIF Image API (4.3): info.json before the image endpoint
    iiifInfo, iiifImage) ++ OpsApi.all ++ AgentsApi.all
  // ── the gazetteer (plan §1.6a) ───────────────────────────────────────────────────────────────

  /** `GET /api/places?q=&asOf=&limit=` — name/label search over the gazetteer, valid at `asOf` (a
    * signed year; omitted = every row whatever its validity). The `id` of a hit is what `place=` on
    * every other endpoint wants: an id matches through the materialised `finding_places` **and the
    * hierarchy as of the filter's period**, where a bare name only matches text. */
  val places: PublicEndpoint[(Option[String], Option[Long], Option[Int]), ApiError, List[PlaceHit], Any] =
    base.get.in("places")
      .in(query[Option[String]]("q")).in(query[Option[Long]]("asOf")).in(query[Option[Int]]("limit"))
      .out(jsonBody[List[PlaceHit]])

  /** GeoJSON FeatureCollection for places with geometry, filtered by kind and time. */
  val placesGeo: PublicEndpoint[(Option[String], Option[Long], Option[Int]), ApiError, String, Any] =
    base.get.in("places" / "geo")
      .in(query[Option[String]]("kind")).in(query[Option[Long]]("asOf")).in(query[Option[Int]]("limit"))
      .out(stringBody)

  /** Children of one place, as of `asOf` — only edges whose OWN period covers it. */
  val placeChildren: PublicEndpoint[(String, Option[Long]), ApiError, List[PlaceSummary], Any] =
    base.get.in("places" / path[String]("id") / "children").in(query[Option[Long]]("asOf"))
      .out(jsonBody[List[PlaceSummary]]).errorOut(statusCode(StatusCode.NotFound))

  /** The subtree under one place as a depth-tagged pre-order walk (the indented list MCP prints). */
  val placeTree: PublicEndpoint[(String, Option[Long], Option[Int]), ApiError, List[PlaceTreeRow], Any] =
    base.get.in("places" / path[String]("id") / "tree")
      .in(query[Option[Long]]("asOf")).in(query[Option[Int]]("depth"))
      .out(jsonBody[List[PlaceTreeRow]]).errorOut(statusCode(StatusCode.NotFound))

  /** One place: the row, its geometry as GeoJSON text, and its parents/children as of `asOf`.
    * `counts=true` adds what the corpus holds for it ([[PlaceCounts]]) — the map's hover card. */
  val place: PublicEndpoint[(String, Option[Long], Option[Boolean]), ApiError, PlaceDetail, Any] =
    base.get.in("places" / path[String]("id")).in(query[Option[Long]]("asOf"))
      .in(query[Option[Boolean]]("counts"))
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

  /** `GET /api/periods/{id}` — one period, its parent and its children (#719). */
  val period: PublicEndpoint[String, ApiError, PeriodDetail, Any] =
    base.get.in("periods" / path[String]("id"))
      .out(jsonBody[PeriodDetail]).errorOut(statusCode(StatusCode.NotFound))

  /** `POST /api/periods/seed?replace=` — upsert the period presets BUNDLED with the server
    * (`db/periods/periodo-seed.json`, a curated PeriodO subset). Idempotent; re-running restores the
    * curated tree and makes the seed's presets the only ones. `replace` also deletes the seed rows
    * (`dataset = seed`, fetched ones included) that the bundled seed no longer has. */
  val seedPeriods: PublicEndpoint[Boolean, ApiError, PeriodImportResult, Any] =
    writeBase.post.in("periods" / "seed").in(query[Boolean]("replace").default(false))
      .out(jsonBody[PeriodImportResult])

  /** `POST /api/periods/import?collection=…&dataset=` — the body is a PeriodO dataset (a dump, or a
    * subset of one like the seed); `collection` (repeatable) keeps only those authorities. `dataset`
    * is `local` (the default: this collection's own periods, carried in a bundle) or `seed`.
    * Idempotent. */
  val importPeriods: PublicEndpoint[(List[String], Option[String], String), ApiError, PeriodImportResult, Any] =
    writeBase.post.in("periods" / "import").in(query[List[String]]("collection"))
      .in(query[Option[String]]("dataset")).in(stringBody)
      .out(jsonBody[PeriodImportResult])

  /** `POST /api/periods/fetch?source=periodo&collection=…` — download the PeriodO dump and upsert the
    * named collections (default: config `periods.periodo-collections`), each under one authority
    * node. Re-runnable: a period that already has a parent keeps it. */
  val fetchPeriods: PublicEndpoint[(String, List[String]), ApiError, PeriodImportResult, Any] =
    writeBase.post.in("periods" / "fetch").in(query[String]("source"))
      .in(query[List[String]]("collection")).out(jsonBody[PeriodImportResult])

  /** `POST /api/init?fetch=` — first-run data (#720): import the bundled gazetteer and period presets
    * (and, with `fetch`, download the network gazetteers and PeriodO) as a background JOB; poll it
    * with `GET /api/jobs/{id}`. The server starts the same job by itself on a first start. */
  val init: PublicEndpoint[Boolean, ApiError, JobInfo, Any] =
    writeBase.post.in("init").in(query[Boolean]("fetch").default(false)).out(jsonBody[JobInfo])

  /** `POST /api/places/import?kind=&dataset=` — the body is a Linked Places Format document or a
    * plain GeoJSON `FeatureCollection` (both read by [[Lpf]]); `kind` is the fallback for features
    * that do not declare one; `dataset` is `local` (the default: this collection's own places,
    * carried in a bundle) or `seed` (`histmersion places seed`). Upsert: re-importing a file is
    * idempotent, and marks its places with the dataset given. Send big files in batches. */
  val importPlaces: PublicEndpoint[(Option[String], Option[String], String), ApiError, PlaceImportResult, Any] =
    // `writeBase`, not `base`: an import IS a write, and the maintenance lock answers 503 — a code
    // the plain error mapping cannot express.
    writeBase.post.in("places" / "import").in(query[Option[String]]("kind")).in(query[Option[String]]("dataset"))
      .in(stringBody).out(jsonBody[PlaceImportResult])

  /** `POST /api/places/rebuild` — re-materialise `finding_places` from the geometry, the periods and
    * the `region_name` fallback. Run it after an import or after the corpus changed. */
  val rebuildPlaces: PublicEndpoint[Unit, ApiError, PlaceRebuildResult, Any] =
    writeBase.post.in("places" / "rebuild").out(jsonBody[PlaceRebuildResult])

  /** `POST /api/places/fetch?source=cliopatria` — download, transform and upsert an external
    * gazetteer. Idempotent. */
  val fetchExternalPlaces: PublicEndpoint[(String, Boolean), ApiError, PlaceImportResult, Any] =
    writeBase.post.in("places" / "fetch").in(query[String]("source"))
      .in(query[Boolean]("dryRun")).out(jsonBody[PlaceImportResult])

  /** `POST /api/places/build-hierarchy` — compute parent-child edges from spatial containment
    * (point inside polygon). Idempotent. Run after imports. */
  val buildHierarchy: PublicEndpoint[Unit, ApiError, SpatialHierarchyResult, Any] =
    writeBase.post.in("places" / "build-hierarchy").out(jsonBody[SpatialHierarchyResult])

  // ── events (plan §1.2) ────────────────────────────────────────────────────────────────────────

  /** Input of `GET /api/events`: the SHARED [[Filter]] plus what only an event has.
    *
    * An event is listed when its source (`citekey`) is among the sources the filter selects —
    * evaluated as `/api/sources` would, WITHOUT the period — and, when the filter has a period
    * (`from`/`to`), its OWN time span overlaps it: an event has its own dating, and the period its
    * source covers is not the event's. A filter with no source criteria puts no condition on the
    * source (events without one included). `title` is a substring of the event's label;
    * `limit`/`offset` page the events.
    *
    *  - `text` — substring of the event's label or description (`q` is the Filter's: full text over
    *    the SOURCES).
    *  - `kind`, `historicity` — exact.
    *  - `placeId` — the event's own place, exact (a `place` filter selects through the sources). */
  final case class EventQuery(
      filter: Filter = Filter(),
      text: Option[String] = None,
      kind: Option[String] = None,
      historicity: Option[String] = None,
      placeId: Option[String] = None
  )

  val events: PublicEndpoint[EventQuery, ApiError, EventList, Any] =
    base.get.in("events")
      .in(filterQuery)
      .in(query[Option[String]]("text"))
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("historicity"))
      .in(query[Option[String]]("placeId"))
      .mapInTo[EventQuery]
      .out(jsonBody[EventList])

  /** `POST /api/events/{id}/rename` `{"to": …}` — a new id; attestations and asset links follow, the
    * old id keeps resolving (an alias). */
  val renameEvent: PublicEndpoint[(String, LabelRename), ApiError, LabelRenamed, Any] =
    writeBase.post.in("events" / path[String]("id") / "rename").in(jsonBody[LabelRename]).out(jsonBody[LabelRenamed])

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

  // ── goals (plan §1.5) ────────────────────────────────────────────────────────────────────────

  final case class GoalQuery(
      status: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  )

  val goals: PublicEndpoint[GoalQuery, ApiError, GoalList, Any] =
    base.get.in("goals")
      .in(query[Option[String]]("status"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .mapInTo[GoalQuery]
      .out(jsonBody[GoalList])

  /** `POST /api/goals/{slug}/rename` `{"to": …}` — a new slug; sub-goals, links and schedules follow,
    * the old slug keeps resolving (an alias). */
  val renameGoal: PublicEndpoint[(String, LabelRename), ApiError, LabelRenamed, Any] =
    writeBase.post.in("goals" / path[String]("slug") / "rename").in(jsonBody[LabelRename]).out(jsonBody[LabelRenamed])

  /** `POST /api/places/{id}/rename` `{"to": …}` — a new gazetteer id (V055); names, parents,
    * footprints, events and finding assignments follow by key, attestations and asset links are
    * moved, and the old id keeps resolving (`GET /api/places/{old}`, a `place=<old>` filter). */
  val renamePlace: PublicEndpoint[(String, LabelRename), ApiError, LabelRenamed, Any] =
    writeBase.post.in("places" / path[String]("id") / "rename").in(jsonBody[LabelRename]).out(jsonBody[LabelRenamed])

  val getGoal: PublicEndpoint[String, ApiError, Goal, Any] =
    endpoint.get.in("api" / "goals" / path[String]("slug"))
      .errorOut(writeErrors).out(jsonBody[Goal])

  val putGoal: PublicEndpoint[(String, GoalPut), ApiError, Goal, Any] =
    endpoint.put.in("api" / "goals" / path[String]("slug")).in(jsonBody[GoalPut])
      .errorOut(writeErrors).out(jsonBody[Goal])

  val patchGoal: PublicEndpoint[(String, GoalPatch), ApiError, Goal, Any] =
    endpoint.patch.in("api" / "goals" / path[String]("slug")).in(jsonBody[GoalPatch])
      .errorOut(writeErrors).out(jsonBody[Goal])

  val deleteGoal: PublicEndpoint[String, ApiError, GoalDeleted, Any] =
    endpoint.delete.in("api" / "goals" / path[String]("slug"))
      .errorOut(writeErrors).out(jsonBody[GoalDeleted])

  // ── agent state (plan §1.5) ──────────────────────────────────────────────────────────────────

  val agentStateList: PublicEndpoint[Unit, ApiError, List[AgentStateEntry], Any] =
    base.get.in("agent-state").out(jsonBody[List[AgentStateEntry]])

  val agentStateGet: PublicEndpoint[String, ApiError, AgentStateEntry, Any] =
    endpoint.get.in("api" / "agent-state" / path[String]("key"))
      .errorOut(writeErrors).out(jsonBody[AgentStateEntry])

  val agentStateSet: PublicEndpoint[(String, AgentStatePut), ApiError, AgentStateEntry, Any] =
    endpoint.put.in("api" / "agent-state" / path[String]("key")).in(jsonBody[AgentStatePut])
      .errorOut(writeErrors).out(jsonBody[AgentStateEntry])

  val agentStateDelete: PublicEndpoint[String, ApiError, AgentStateDeleted, Any] =
    endpoint.delete.in("api" / "agent-state" / path[String]("key"))
      .errorOut(writeErrors).out(jsonBody[AgentStateDeleted])

  // ── ingest, jobs and assets (plan §1.8) ──────────────────────────────────────────────────────

  /** Errors of the ingest surface: a duplicate upload is 409 (with the citekey that already holds
    * it), an unknown citekey is 404, anything else a readable 400. */
  private val ingestErrors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[ApiError])(ApiError.hasCode(Ingest.CodeConflict)),
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[ApiError])(ApiError.hasCode(Write.CodeNotFound)),
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
      .and(query[Option[Int]]("acquireItem")
        .description("id of the acquisition item this upload fulfils; the OCR job ticks it off when it succeeds"))
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

  /** `GET /api/sources/{ck}/original` — the original file the server stored at ingest (the spool,
    * content-addressed by `sources.sha256`), named by [[Ingest.originalDisposition]]. The host-side
    * `histmersion calibre push --replace` puts it into the linked Calibre entry. Not an MCP tool: it is a
    * binary download for the host, and an agent reads the converted body with `get_source`. */
  val original: PublicEndpoint[String, ApiError, (TapirFile, String), Any] =
    endpoint.get.in("api" / "sources" / path[String]("citekey") / "original")
      .errorOut(ingestErrors)
      .out(fileBody)
      .out(header[String](sttp.model.HeaderNames.ContentDisposition))

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

  /** Add figures to an existing source — the host-side path of `histmersion seed`, which can see
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

  // ── media associations (plan §1.6) ────────────────────────────────────────────────────────────

  /** `GET /api/sources/{ck}/assets/{name}/detail` — asset metadata including spatial/temporal. */
  val assetDetail: PublicEndpoint[(String, String), ApiError, AssetDetail, Any] =
    base.get.in("sources" / path[String]("citekey") / "assets" / path[String]("name") / "detail")
      .out(jsonBody[AssetDetail])

  /** `PATCH /api/sources/{ck}/assets/{name}` — update asset metadata. */
  val patchAsset: PublicEndpoint[(String, String, AssetPatch), ApiError, AssetDetail, Any] =
    base.patch.in("sources" / path[String]("citekey") / "assets" / path[String]("name"))
      .in(jsonBody[AssetPatch]).out(jsonBody[AssetDetail])

  /** `GET /api/media-geo?kind=&from=&to=&limit=` — geolocated assets for the media clustering map. */
  val mediaGeo: PublicEndpoint[(Option[String], Option[Long], Option[Long], Option[Int]), ApiError,
      List[MediaGeoHit], Any] =
    base.get.in("media-geo")
      .in(query[Option[String]]("kind")).in(query[Option[Long]]("from")).in(query[Option[Long]]("to"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[List[MediaGeoHit]])

  /** `GET /api/asset-links?citekey=&asset=&target_type=&target_id=` — list asset links. */
  val assetLinks: PublicEndpoint[(Option[String], Option[String], Option[String], Option[String]), ApiError, AssetLinkList, Any] =
    base.get.in("asset-links")
      .in(query[Option[String]]("citekey")).in(query[Option[String]]("asset"))
      .in(query[Option[String]]("target_type")).in(query[Option[String]]("target_id"))
      .out(jsonBody[AssetLinkList])

  /** `POST /api/asset-links` — create an asset link. */
  val createAssetLink: PublicEndpoint[AssetLinkPut, ApiError, AssetLink, Any] =
    base.post.in("asset-links").in(jsonBody[AssetLinkPut]).out(jsonBody[AssetLink])

  /** `DELETE /api/asset-links/{id}` — remove an asset link. */
  val deleteAssetLink: PublicEndpoint[Long, ApiError, AssetLinkDeleted, Any] =
    base.delete.in("asset-links" / path[Long]("id")).out(jsonBody[AssetLinkDeleted])

  // ── IIIF Image API Level 1 (plan §4.3) ─────────────────────────────────────────────────────

  /** `GET /iiif/{citekey}/{name}/info.json` — the IIIF Image Information document.
    * Registered before the image endpoint so Tapir matches it first. */
  val iiifInfo: PublicEndpoint[(String, String), ApiError, io.circe.Json, Any] =
    endpoint.get
      .in("iiif" / path[String]("citekey") / path[String]("name") / "info.json")
      .errorOut(statusCode(StatusCode.NotFound)).errorOut(jsonBody[ApiError])
      .out(jsonBody[io.circe.Json])
      .out(header(sttp.model.Header(sttp.model.HeaderNames.AccessControlAllowOrigin, "*")))

  /** `GET /iiif/{citekey}/{name}/{region}/{size}/{rotation}/default.{format}` — the IIIF image
    * request. Level 1 compliance: region `full|x,y,w,h`, size `max|^max|w,|,h|w,h|!w,h`,
    * rotation `0` only, quality `default`, format `jpg|png`. */
  val iiifImage: PublicEndpoint[(String, String, String, String, String, String), ApiError, (Array[Byte], String), Any] =
    endpoint.get
      .in("iiif" / path[String]("citekey") / path[String]("name") /
        path[String]("region") / path[String]("size") / path[String]("rotation") /
        path[String]("filename"))
      .errorOut(statusCode(StatusCode.NotFound)).errorOut(jsonBody[ApiError])
      .out(byteArrayBody)
      .out(header[String](sttp.model.HeaderNames.ContentType))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.AccessControlAllowOrigin, "*")))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.CacheControl, "public, max-age=31536000, immutable")))
