package com.histmersion.api

import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode

/** Every endpoint here and in the other `*Api` objects is a `lazy val`: initialising an object runs all of its strict
  * vals, so Scala.js would link every endpoint and every codec it names into the web bundle as soon as the page used
  * one of them.
  */
object Api:

  /** `region` is still sent by older links and clients; it is one more place name. */
  final case class FindingsQuery(filter: Filter, region: Option[String]):
    def toFilter: Filter =
      region.map(_.trim).filter(_.nonEmpty).fold(filter)(r => filter.copy(places = filter.places :+ r))

  object FindingsQuery:
    def of(filter: Filter): FindingsQuery = FindingsQuery(filter, None)

  private lazy val base = endpoint.in("api").errorOut(jsonBody[ApiError])

  /** A `def`, not a `val`: endpoints defined above `writeErrors` use it too. */
  private def writeBase = endpoint.in("api").errorOut(writeErrors)

  /** The parameter names are fixed: saved links, the web UI and the CLI rely on them. */
  lazy val filterQuery: EndpointInput[Filter] =
    import FilterDocs as D
    def q[A](p: FilterDocs.Param)(using Codec[List[String], A, CodecFormat.TextPlain]) =
      query[A](p.rest).description(p.doc)
    q[Option[String]](D.query)
      .and(q[List[String]](D.tags))
      .and(q[List[String]](D.authors))
      .and(q[List[String]](D.places))
      .and(q[List[String]](D.topics))
      .and(q[List[String]](D.status))
      .and(q[Option[Long]](D.yearFrom))
      .and(q[Option[Long]](D.yearTo))
      .and(q[Option[Long]](D.from))
      .and(q[Option[Long]](D.to))
      .and(q[Option[Double]](D.minLon))
      .and(q[Option[Double]](D.minLat))
      .and(q[Option[Double]](D.maxLon))
      .and(q[Option[Double]](D.maxLat))
      .and(q[Option[String]](D.sort))
      .and(q[Option[Int]](D.limit))
      .and(q[Option[Int]](D.offset))
      .and(q[Option[String]](D.title))
      .and(q[Option[String]](D.goal))
      .mapTo[FilterQuery]
      .map(_.toFilter)(FilterQuery.from)

  lazy val version: PublicEndpoint[Unit, ApiError, VersionInfo, Any] =
    base.get.in("version").out(jsonBody[VersionInfo])

  lazy val health: PublicEndpoint[Unit, ApiError, Health, Any] =
    base.get.in("health").out(jsonBody[Health])

  lazy val about: PublicEndpoint[Unit, ApiError, About, Any] =
    base.get.in("about").out(jsonBody[About])

  lazy val config: PublicEndpoint[Unit, ApiError, UiConfig, Any] =
    base.get.in("config").out(jsonBody[UiConfig])

  lazy val search: PublicEndpoint[(Option[String], Option[Int]), ApiError, List[SearchHit], Any] =
    base.get
      .in("search")
      .in(query[Option[String]]("q"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[List[SearchHit]])

  lazy val resolveId: PublicEndpoint[String, ApiError, ResolvedId, Any] =
    base.get.in("resolve" / path[String]("id")).out(jsonBody[ResolvedId])

  lazy val getSource: PublicEndpoint[(String, Boolean), ApiError, SourceWithFindings, Any] =
    base.get
      .in("sources" / path[String]("citekey"))
      .in(query[Boolean]("body").default(true))
      .out(jsonBody[SourceWithFindings])
      .errorOut(statusCode(StatusCode.NotFound))

  lazy val sourceArticles: PublicEndpoint[String, ApiError, List[Backlink], Any] =
    base.get
      .in("sources" / path[String]("citekey") / "articles")
      .out(jsonBody[List[Backlink]])
      .errorOut(statusCode(StatusCode.NotFound))

  final case class SourceViewQuery(
      citekey: String,
      fullText: Boolean = false,
      section: Option[String] = None,
      heading: Option[String] = None,
      offset: Option[Int] = None,
      chars: Option[Int] = None
  )

  lazy val sourceView: PublicEndpoint[SourceViewQuery, ApiError, String, Any] =
    endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "view")
      .in(query[Boolean]("fullText").default(false))
      .in(query[Option[String]]("section"))
      .in(query[Option[String]]("heading"))
      .in(query[Option[Int]]("offset"))
      .in(query[Option[Int]]("chars"))
      .mapInTo[SourceViewQuery]
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[ApiError])
      .out(stringBody)

  final case class PassagesQuery(
      citekey: String,
      q: String,
      regex: Boolean = false,
      limit: Option[Int] = None,
      context: Option[Int] = None
  )

  lazy val passages: PublicEndpoint[PassagesQuery, ApiError, PassageResult, Any] =
    endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "passages")
      .in(
        query[String]("q").description(
          "the text to find (literal, case- and accent-insensitive), or a regex with regex=true"
        )
      )
      .in(query[Boolean]("regex").default(false))
      .in(query[Option[Int]]("limit").description("passages shown (default and cap from config)"))
      .in(query[Option[Int]]("context").description("characters of context around a match"))
      .mapInTo[PassagesQuery]
      .errorOut(writeErrors)
      .out(jsonBody[PassageResult])

  lazy val findings: PublicEndpoint[FindingsQuery, ApiError, List[GeoHit], Any] =
    base.get
      .in("findings")
      .in(filterQuery)
      .in(query[Option[String]]("region"))
      .mapInTo[FindingsQuery]
      .out(jsonBody[List[GeoHit]])

  lazy val findingsPage: PublicEndpoint[FindingsQuery, ApiError, GeoHitPage, Any] =
    base.get
      .in("findings" / "page")
      .in(filterQuery)
      .in(query[Option[String]]("region"))
      .mapInTo[FindingsQuery]
      .out(jsonBody[GeoHitPage])

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

    def tree(
        by: String,
        filter: Filter,
        unit: Option[String] = None,
        depth: Option[Int] = None,
        asOf: Option[Long] = None,
        samples: Option[Int] = None
    ): AggregateQuery =
      AggregateQuery(Some(by), unit, filter, depth.orElse(Some(0)), asOf, samples)

  lazy val aggregate: PublicEndpoint[AggregateQuery, ApiError, TimeHistogram, Any] =
    base.get
      .in("aggregate")
      .in(query[Option[String]]("by"))
      .in(query[Option[String]]("unit"))
      .in(filterQuery)
      .in(query[Option[Int]]("depth"))
      .in(query[Option[Long]]("asOf"))
      .in(query[Option[Int]]("samples"))
      .in(query[Option[Long]]("size"))
      .mapInTo[AggregateQuery]
      .out(jsonBody[TimeHistogram])

  lazy val neighbors: PublicEndpoint[(String, List[String]), ApiError, List[NeighborRow], Any] =
    base.get
      .in("neighbors" / path[String]("citekey"))
      .in(query[List[String]]("kind"))
      .out(jsonBody[List[NeighborRow]])

  lazy val graph: PublicEndpoint[(String, Option[Int]), ApiError, SourceGraph, Any] =
    base.get
      .in("graph" / path[String]("citekey"))
      .in(query[Option[Int]]("depth"))
      .out(jsonBody[SourceGraph])

  lazy val contradictions: PublicEndpoint[(Option[String], Option[Long]), ApiError, ContradictionsResult, Any] =
    base.get
      .in("contradictions")
      .in(query[Option[String]]("citekey"))
      .in(query[Option[Long]]("since"))
      .out(jsonBody[ContradictionsResult])

  lazy val seed: PublicEndpoint[SeedRequest, ApiError, SeedResult, Any] =
    writeBase.post.in("seed").in(jsonBody[SeedRequest]).out(jsonBody[SeedResult])

  lazy val reindex: PublicEndpoint[(Option[String], Option[Int]), ApiError, ReindexResult, Any] =
    writeBase.post
      .in("reindex")
      .in(query[Option[String]]("after"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[ReindexResult])

  lazy val sources: PublicEndpoint[Filter, ApiError, SearchResult, Any] =
    base.get.in("sources").in(filterQuery).out(jsonBody[SearchResult])

  lazy val facets: PublicEndpoint[(String, Option[String], Filter), ApiError, List[Facet], Any] =
    base.get
      .in("facets")
      .in(query[String]("field"))
      .in(query[Option[String]]("prefix"))
      .in(filterQuery)
      .out(jsonBody[List[Facet]])

  private[api] def writeErrors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[ApiError])(ApiError.hasCode(Write.CodeNotFound)),
      oneOfVariantValueMatcher(StatusCode.ServiceUnavailable, jsonBody[ApiError])(
        ApiError.hasCode(Backup.CodeMaintenance)
      ),
      oneOfVariantValueMatcher(StatusCode.PreconditionFailed, jsonBody[ApiError])(
        ApiError.hasCode(Write.CodePrecondition)
      ),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  private def strictQuery = query[Boolean]("strict")
    .default(false)
    .description("refuse the write when its Markdown has a formatting error (default: report only)")

  lazy val putSource: PublicEndpoint[(String, SourcePut, Boolean), ApiError, SourceWithFindings, Any] =
    endpoint.put
      .in("api" / "sources" / path[String]("citekey"))
      .in(jsonBody[SourcePut])
      .in(strictQuery)
      .errorOut(writeErrors)
      .out(jsonBody[SourceWithFindings])

  lazy val patchSource: PublicEndpoint[(String, SourcePatch, Boolean), ApiError, SourceWithFindings, Any] =
    endpoint.patch
      .in("api" / "sources" / path[String]("citekey"))
      .in(jsonBody[SourcePatch])
      .in(strictQuery)
      .errorOut(writeErrors)
      .out(jsonBody[SourceWithFindings])

  lazy val lintSource: PublicEndpoint[String, ApiError, FormatReport, Any] =
    endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "lint")
      .errorOut(writeErrors)
      .out(jsonBody[FormatReport])

  lazy val fixSourceFormat: PublicEndpoint[String, ApiError, FormatReport, Any] =
    endpoint.post
      .in("api" / "sources" / path[String]("citekey") / "lint" / "fix")
      .errorOut(writeErrors)
      .out(jsonBody[FormatReport])

  lazy val renameSource: PublicEndpoint[(String, RenameRequest), ApiError, SourceRenamed, Any] =
    writeBase.post
      .in("sources" / path[String]("citekey") / "rename")
      .in(jsonBody[RenameRequest])
      .out(jsonBody[SourceRenamed])

  lazy val deleteSource: PublicEndpoint[(String, SourceDeleteRequest), ApiError, SourceDeletion, Any] =
    writeBase.post
      .in("sources" / path[String]("citekey") / "delete")
      .in(jsonBody[SourceDeleteRequest])
      .out(jsonBody[SourceDeletion])

  lazy val sourceAliases: PublicEndpoint[Unit, ApiError, List[SourceAlias], Any] =
    base.get.in("source-aliases").out(jsonBody[List[SourceAlias]])

  lazy val importNote: PublicEndpoint[NoteImport, ApiError, SeedResult, Any] =
    writeBase.post.in("sources" / "import").in(jsonBody[NoteImport]).out(jsonBody[SeedResult])

  lazy val exportSource: PublicEndpoint[String, ApiError, String, Any] =
    endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "export")
      .errorOut(writeErrors)
      .out(stringBody)

  lazy val citekeys: PublicEndpoint[Unit, ApiError, List[String], Any] =
    base.get.in("citekeys").out(jsonBody[List[String]])

  lazy val resolveEdges: PublicEndpoint[Unit, ApiError, EdgesResolved, Any] =
    writeBase.post.in("edges" / "resolve").out(jsonBody[EdgesResolved])

  lazy val configReload: PublicEndpoint[Unit, ApiError, ReloadReport, Any] =
    writeBase.post.in("config" / "reload").out(jsonBody[ReloadReport])

  lazy val backup: PublicEndpoint[Unit, ApiError, BackupStatus, Any] =
    writeBase.post.in("backup").out(jsonBody[BackupStatus])

  /** A job, not a request: Postgres cannot run `VACUUM` inside a transaction. */
  lazy val maintenanceVacuum: PublicEndpoint[VacuumRequest, ApiError, JobInfo, Any] =
    writeBase.post.in("maintenance" / "vacuum").in(jsonBody[VacuumRequest]).out(jsonBody[JobInfo])

  lazy val backupStatus: PublicEndpoint[Unit, ApiError, BackupStatus, Any] =
    endpoint.get.in("api" / "backup" / "status").errorOut(writeErrors).out(jsonBody[BackupStatus])

  lazy val backups: PublicEndpoint[Unit, ApiError, List[BackupInfo], Any] =
    base.get.in("backups").out(jsonBody[List[BackupInfo]])

  lazy val downloadBackup: PublicEndpoint[String, ApiError, TapirFile, Any] =
    endpoint.get.in("api" / "backups" / path[String]("file")).errorOut(writeErrors).out(fileBody)

  lazy val restore: PublicEndpoint[RestoreRequest, ApiError, RestoreResult, Any] =
    writeBase.post.in("restore").in(jsonBody[RestoreRequest]).out(jsonBody[RestoreResult])

  lazy val instance: PublicEndpoint[Unit, ApiError, InstanceInfo, Any] =
    base.get.in("instance").out(jsonBody[InstanceInfo])

  lazy val setInstance: PublicEndpoint[InstanceLabel, ApiError, InstanceInfo, Any] =
    writeBase.put.in("instance").in(jsonBody[InstanceLabel]).out(jsonBody[InstanceInfo])

  lazy val exportBundle: PublicEndpoint[Option[String], ApiError, (TapirFile, String), Any] =
    endpoint.get
      .in("api" / "bundle")
      .in(query[Option[String]]("since"))
      .errorOut(writeErrors)
      .out(fileBody)
      .out(header[String](sttp.model.HeaderNames.ContentDisposition))

  lazy val verifyBundle: PublicEndpoint[TapirFile, ApiError, BundleVerifyResult, Any] =
    writeBase.post.in("bundle" / "verify").in(fileBody).out(jsonBody[BundleVerifyResult])

  /** Not an MCP tool: its input is a file upload, which an MCP call cannot carry. */
  lazy val importBundle: PublicEndpoint[(Boolean, TapirFile), ApiError, BundleImportResult, Any] =
    writeBase.post
      .in("bundle" / "import")
      .in(query[Boolean]("dryRun").default(false))
      .in(fileBody)
      .out(jsonBody[BundleImportResult])

  /** Kept on the server under its sha256 so `merge plan` and `merge import` can name it: their body is the resolution
    * set, and a request carries one body.
    */
  lazy val uploadBundle: PublicEndpoint[TapirFile, ApiError, BundleUpload, Any] =
    writeBase.post.in("bundle" / "upload").in(fileBody).out(jsonBody[BundleUpload])

  lazy val mergePlan: PublicEndpoint[MergePlanRequest, ApiError, MergePlan, Any] =
    writeBase.post.in("merge" / "plan").in(jsonBody[MergePlanRequest]).out(jsonBody[MergePlan])

  lazy val mergeImport: PublicEndpoint[MergeImportRequest, ApiError, BundleImportResult, Any] =
    writeBase.post.in("merge" / "import").in(jsonBody[MergeImportRequest]).out(jsonBody[BundleImportResult])

  lazy val mergeApply: PublicEndpoint[MergeApplyRequest, ApiError, MergeApplyJob, Any] =
    writeBase.post.in("merge" / "apply").in(jsonBody[MergeApplyRequest]).out(jsonBody[MergeApplyJob])

  lazy val mergeApplyStatus: PublicEndpoint[String, ApiError, MergeApplyJob, Any] =
    writeBase.get.in("merge" / "apply" / path[String]("job")).out(jsonBody[MergeApplyJob])

  lazy val mergeSession: PublicEndpoint[String, ApiError, MergeSession, Any] =
    writeBase.get.in("merge" / "sessions" / path[String]("bundle")).out(jsonBody[MergeSession])

  lazy val putMergeSession: PublicEndpoint[(String, MergeResolutions), ApiError, MergeSession, Any] =
    writeBase.put
      .in("merge" / "sessions" / path[String]("bundle"))
      .in(jsonBody[MergeResolutions])
      .out(jsonBody[MergeSession])

  lazy val parseResolutions: PublicEndpoint[String, ApiError, MergeResolutions, Any] =
    writeBase.post.in("merge" / "resolutions").in(stringBody).out(jsonBody[MergeResolutions])

  lazy val merges: PublicEndpoint[Option[Int], ApiError, List[MergeInfo], Any] =
    base.get.in("merges").in(query[Option[Int]]("limit")).out(jsonBody[List[MergeInfo]])

  lazy val mergeLedger: PublicEndpoint[(Option[String], Option[String], Option[Int]), ApiError, MergeLedger, Any] =
    writeBase.get
      .in("merges" / "ledger")
      .in(query[Option[String]]("uid"))
      .in(query[Option[String]]("merge"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[MergeLedger])

  lazy val mergeDetail: PublicEndpoint[String, ApiError, MergeDetail, Any] =
    writeBase.get.in("merges" / path[String]("id")).out(jsonBody[MergeDetail])

  final case class ArticleQuery(
      filter: Filter = Filter(),
      kind: Option[String] = None,
      text: Option[String] = None
  )

  lazy val articles: PublicEndpoint[ArticleQuery, ApiError, ArticleList, Any] =
    base.get
      .in("articles")
      .in(filterQuery)
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("text"))
      .mapInTo[ArticleQuery]
      .out(jsonBody[ArticleList])

  lazy val renameArticle: PublicEndpoint[(String, LabelRename), ApiError, LabelRenamed, Any] =
    writeBase.post
      .in("articles" / path[String]("slug") / "rename")
      .in(jsonBody[LabelRename])
      .out(jsonBody[LabelRenamed])

  lazy val getArticle: PublicEndpoint[(String, Boolean), ApiError, ArticleView, Any] =
    endpoint.get
      .in("api" / "articles" / path[String]("slug"))
      .in(
        query[Boolean]("brief")
          .default(false)
          .description("leave out the formatting lint and the coverage (a hover card's read)")
      )
      .errorOut(writeErrors)
      .out(jsonBody[ArticleView])

  lazy val putArticle: PublicEndpoint[(String, ArticlePut, Boolean), ApiError, ArticleView, Any] =
    endpoint.put
      .in("api" / "articles" / path[String]("slug"))
      .in(jsonBody[ArticlePut])
      .in(strictQuery)
      .errorOut(writeErrors)
      .out(jsonBody[ArticleView])

  lazy val patchArticle: PublicEndpoint[(String, ArticlePatch, Boolean), ApiError, ArticleView, Any] =
    endpoint.patch
      .in("api" / "articles" / path[String]("slug"))
      .in(jsonBody[ArticlePatch])
      .in(strictQuery)
      .errorOut(writeErrors)
      .out(jsonBody[ArticleView])

  lazy val lintArticle: PublicEndpoint[String, ApiError, FormatReport, Any] =
    endpoint.get
      .in("api" / "articles" / path[String]("slug") / "lint")
      .errorOut(writeErrors)
      .out(jsonBody[FormatReport])

  lazy val fixArticleFormat: PublicEndpoint[String, ApiError, FormatReport, Any] =
    endpoint.post
      .in("api" / "articles" / path[String]("slug") / "lint" / "fix")
      .errorOut(writeErrors)
      .out(jsonBody[FormatReport])

  lazy val checkArticle
      : PublicEndpoint[(String, ArticleEdits.ArticleCheckRequest), ApiError, ArticleEdits.ArticleCheck, Any] =
    endpoint.post
      .in("api" / "articles" / path[String]("slug") / "check")
      .in(jsonBody[ArticleEdits.ArticleCheckRequest])
      .errorOut(writeErrors)
      .out(jsonBody[ArticleEdits.ArticleCheck])

  lazy val articleCoverage: PublicEndpoint[String, ApiError, Coverage, Any] =
    endpoint.get
      .in("api" / "articles" / path[String]("slug") / "coverage")
      .errorOut(writeErrors)
      .out(jsonBody[Coverage])

  lazy val articleReview: PublicEndpoint[String, ApiError, ArticleReviewView, Any] =
    endpoint.get
      .in("api" / "articles" / path[String]("slug") / "review")
      .errorOut(writeErrors)
      .out(jsonBody[ArticleReviewView])

  lazy val deleteArticle: PublicEndpoint[String, ApiError, ArticleDeleted, Any] =
    endpoint.delete
      .in("api" / "articles" / path[String]("slug"))
      .errorOut(writeErrors)
      .out(jsonBody[ArticleDeleted])

  lazy val exportArticle: PublicEndpoint[String, ApiError, String, Any] =
    endpoint.get
      .in("api" / "articles" / path[String]("slug") / "export")
      .errorOut(writeErrors)
      .out(stringBody)

  lazy val importArticle: PublicEndpoint[ArticleImport, ApiError, SeedResult, Any] =
    base.post.in("articles" / "import").in(jsonBody[ArticleImport]).out(jsonBody[SeedResult])

  lazy val articleVersions: PublicEndpoint[String, ApiError, List[ArticleVersion], Any] =
    base.get.in("articles" / path[String]("slug") / "versions").out(jsonBody[List[ArticleVersion]])

  lazy val articleVersion: PublicEndpoint[(String, Long), ApiError, ArticleVersionFull, Any] =
    base.get
      .in("articles" / path[String]("slug") / "versions" / path[Long]("versionId"))
      .out(jsonBody[ArticleVersionFull])

  lazy val articleSlugs: PublicEndpoint[Unit, ApiError, List[String], Any] =
    base.get.in("article-slugs").out(jsonBody[List[String]])

  // lazy: [[EventsApi]] builds on this object's inputs, so the list waits until both exist
  lazy val all: List[AnyEndpoint] = List(
    version,
    health,
    about,
    config,
    search,
    sources,
    facets,
    // Tapir tries endpoints in order: the longer `sources/{ck}/…` paths go before `sources/{ck}`
    sourceArticles,
    sourceView,
    passages,
    lintSource,
    fixSourceFormat,
    getSource,
    findingsPage,
    findings,
    aggregate,
    neighbors,
    graph,
    contradictions,
    seed,
    putSource,
    patchSource,
    renameSource,
    deleteSource,
    sourceAliases,
    importNote,
    exportSource,
    citekeys,
    resolveEdges,
    reindex,
    configReload,
    backup,
    backups,
    downloadBackup,
    restore,
    maintenanceVacuum,
    instance,
    setInstance,
    exportBundle,
    verifyBundle,
    importBundle,
    uploadBundle,
    mergePlan,
    mergeImport,
    mergeApply,
    mergeApplyStatus,
    mergeSession,
    putMergeSession,
    parseResolutions,
    // `merges/ledger` before `merges/{id}`
    merges,
    mergeLedger,
    mergeDetail,
    renameArticle,
    renameEvent,
    renameGoal,
    renamePlace,
    // `articles/import` and the longer `articles/{slug}/…` paths go before `articles/{slug}`
    articles,
    articleSlugs,
    importArticle,
    exportArticle,
    articleVersions,
    articleVersion,
    lintArticle,
    fixArticleFormat,
    checkArticle,
    articleCoverage,
    articleReview,
    getArticle,
    putArticle,
    patchArticle,
    deleteArticle,
    // `events/geo` before `events/{id}`
    events,
    eventsGeo,
    getEvent,
    putEvent,
    patchEvent,
    deleteEvent,
    attestations,
    getAttestation,
    createAttestation,
    patchAttestation,
    deleteAttestation,
    goals,
    getGoal,
    putGoal,
    patchGoal,
    deleteGoal,
    agentStateList,
    agentStateGet,
    agentStateSet,
    agentStateDelete,
    // `/{id}/children` and `/{id}/tree` before `/{id}`
    places,
    placesGeo,
    placeNames,
    placeOverrides,
    placeSources,
    placeChildren,
    placeTree,
    placeFootprints,
    placeLinks,
    place,
    addPlaceName,
    removePlaceName,
    setPlaceOverride,
    mergePlace,
    removePlace,
    addPlace,
    importPlaces,
    rebuildPlaces,
    fetchExternalPlaces,
    loadPlaces,
    buildHierarchy,
    periods,
    // `periods/seed|import|fetch` are POSTs, so the GET `periods/{id}` never shadows them
    period,
    seedPeriods,
    importPeriods,
    fetchPeriods,
    init,
    // `sources/{ck}/assets` before `sources/{ck}`
    ingest,
    reocr,
    original,
    job,
    jobControl,
    jobs,
    activity,
    assets,
    putAssets,
    assetDetail,
    patchAsset,
    asset,
    mediaGeo,
    assetLinks,
    createAssetLink,
    deleteAssetLink,
    // `media/facets|import|fill-licences` before `media/{uid}`
    mediaFacets,
    mediaProviders,
    mediaImport,
    mediaImportJob,
    mediaCovers,
    mediaCoversJob,
    putSourceCover,
    fillFigureLicences,
    mediaList,
    mediaShow,
    mediaThumb,
    mediaFile,
    // IIIF `info.json` before the image endpoint
    iiifInfo,
    iiifImage,
    resolveId,
    backupStatus
  ) ++ OpsApi.all ++ AgentsApi.all ++ ManagersApi.all ++ UiApi.all ++ EventsApi.all ++ AcquireApi.all ++ BasemapApi.all ++ TilesApi.all ++ ConfigEditApi.all ++ personal.PersonalApi.all ++
    library.collections.CollectionsApi.all

  lazy val places: PublicEndpoint[(Option[String], Option[Long], Option[Int]), ApiError, List[PlaceHit], Any] =
    base.get
      .in("places")
      .in(query[Option[String]]("q"))
      .in(query[Option[Long]]("asOf"))
      .in(query[Option[Int]]("limit"))
      .out(jsonBody[List[PlaceHit]])

  final case class PlacesGeoQuery(
      kind: Option[String] = None,
      asOf: Option[Long] = None,
      limit: Option[Int] = None,
      from: Option[Long] = None,
      to: Option[Long] = None,
      pick: Option[String] = None
  )

  lazy val placesGeo: PublicEndpoint[PlacesGeoQuery, ApiError, String, Any] =
    base.get
      .in("places" / "geo")
      .in(
        query[Option[String]]("kind")
          .and(query[Option[Long]]("asOf"))
          .and(query[Option[Int]]("limit"))
          .and(query[Option[Long]]("from"))
          .and(query[Option[Long]]("to"))
          .and(query[Option[String]]("pick"))
          .mapTo[PlacesGeoQuery]
      )
      .out(stringBody)

  lazy val placeChildren: PublicEndpoint[(String, Option[Long]), ApiError, List[PlaceSummary], Any] =
    base.get
      .in("places" / path[String]("id") / "children")
      .in(query[Option[Long]]("asOf"))
      .out(jsonBody[List[PlaceSummary]])
      .errorOut(statusCode(StatusCode.NotFound))

  lazy val placeTree: PublicEndpoint[(String, Option[Long], Option[Int]), ApiError, List[PlaceTreeRow], Any] =
    base.get
      .in("places" / path[String]("id") / "tree")
      .in(query[Option[Long]]("asOf"))
      .in(query[Option[Int]]("depth"))
      .out(jsonBody[List[PlaceTreeRow]])
      .errorOut(statusCode(StatusCode.NotFound))

  lazy val place: PublicEndpoint[(String, Option[Long], Option[Boolean]), ApiError, PlaceDetail, Any] =
    base.get
      .in("places" / path[String]("id"))
      .in(query[Option[Long]]("asOf"))
      .in(query[Option[Boolean]]("counts"))
      .out(jsonBody[PlaceDetail])
      .errorOut(statusCode(StatusCode.NotFound))

  lazy val placeFootprints: PublicEndpoint[(String, Option[Long]), ApiError, List[PlaceFootprint], Any] =
    base.get
      .in("places" / path[String]("id") / "footprints")
      .in(query[Option[Long]]("asOf"))
      .out(jsonBody[List[PlaceFootprint]])
      .errorOut(statusCode(StatusCode.NotFound))

  lazy val placeLinks: PublicEndpoint[String, ApiError, List[PlaceLink], Any] =
    base.get
      .in("places" / path[String]("id") / "links")
      .out(jsonBody[List[PlaceLink]])
      .errorOut(statusCode(StatusCode.NotFound))

  lazy val periods: PublicEndpoint[(Option[String], Option[String], Option[Long], Option[Long]), ApiError, List[
    HistoricalPeriod
  ], Any] =
    base.get
      .in("periods")
      .in(query[Option[String]]("q"))
      .in(query[Option[String]]("region"))
      .in(query[Option[Long]]("from"))
      .in(query[Option[Long]]("to"))
      .out(jsonBody[List[HistoricalPeriod]])

  lazy val period: PublicEndpoint[String, ApiError, PeriodDetail, Any] =
    base.get
      .in("periods" / path[String]("id"))
      .out(jsonBody[PeriodDetail])
      .errorOut(statusCode(StatusCode.NotFound))

  lazy val seedPeriods: PublicEndpoint[Boolean, ApiError, PeriodImportResult, Any] =
    writeBase.post
      .in("periods" / "seed")
      .in(query[Boolean]("replace").default(false))
      .out(jsonBody[PeriodImportResult])

  lazy val importPeriods: PublicEndpoint[(List[String], Option[String], String), ApiError, PeriodImportResult, Any] =
    writeBase.post
      .in("periods" / "import")
      .in(query[List[String]]("collection"))
      .in(query[Option[String]]("dataset"))
      .in(stringBody)
      .out(jsonBody[PeriodImportResult])

  lazy val fetchPeriods: PublicEndpoint[(String, List[String]), ApiError, PeriodImportResult, Any] =
    writeBase.post
      .in("periods" / "fetch")
      .in(query[String]("source"))
      .in(query[List[String]]("collection"))
      .out(jsonBody[PeriodImportResult])

  lazy val init: PublicEndpoint[Boolean, ApiError, JobInfo, Any] =
    writeBase.post.in("init").in(query[Boolean]("fetch").default(false)).out(jsonBody[JobInfo])

  lazy val importPlaces: PublicEndpoint[(Option[String], Option[String], String), ApiError, PlaceImportResult, Any] =
    writeBase.post
      .in("places" / "import")
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("dataset"))
      .in(stringBody)
      .out(jsonBody[PlaceImportResult])

  lazy val rebuildPlaces: PublicEndpoint[Unit, ApiError, PlaceRebuildResult, Any] =
    writeBase.post.in("places" / "rebuild").out(jsonBody[PlaceRebuildResult])

  lazy val fetchExternalPlaces: PublicEndpoint[(String, Boolean), ApiError, PlaceImportResult, Any] =
    writeBase.post
      .in("places" / "fetch")
      .in(query[String]("source"))
      .in(query[Boolean]("dryRun"))
      .out(jsonBody[PlaceImportResult])

  /** Declared before `places/{id}` so `sources` is never read as an id. */
  lazy val placeSources: PublicEndpoint[Unit, ApiError, List[GazetteerSourceInfo], Any] =
    base.get.in("places" / "sources").out(jsonBody[List[GazetteerSourceInfo]])

  lazy val loadPlaces: PublicEndpoint[(String, Boolean), ApiError, JobInfo, Any] =
    writeBase.post
      .in("places" / "load")
      .in(query[String]("source"))
      .in(query[Boolean]("apply").default(false))
      .out(jsonBody[JobInfo])

  /** Declared before `places/{id}` so `names` is never read as an id. */
  lazy val placeNames: PublicEndpoint[Option[String], ApiError, List[ManualPlaceName], Any] =
    base.get.in("places" / "names").in(query[Option[String]]("place")).out(jsonBody[List[ManualPlaceName]])

  lazy val addPlaceName: PublicEndpoint[(String, PlaceNameAdd), ApiError, PlaceNameChanged, Any] =
    writeBase.post
      .in("places" / path[String]("id") / "names")
      .in(jsonBody[PlaceNameAdd])
      .out(jsonBody[PlaceNameChanged])

  lazy val removePlaceName: PublicEndpoint[(String, PlaceNameRemove), ApiError, PlaceNameChanged, Any] =
    writeBase.post
      .in("places" / path[String]("id") / "names" / "remove")
      .in(jsonBody[PlaceNameRemove])
      .out(jsonBody[PlaceNameChanged])

  /** Declared before `places/{id}` so `overrides` is never read as an id. */
  lazy val placeOverrides: PublicEndpoint[Option[String], ApiError, List[PlaceOverride], Any] =
    base.get.in("places" / "overrides").in(query[Option[String]]("place")).out(jsonBody[List[PlaceOverride]])

  lazy val setPlaceOverride: PublicEndpoint[(String, PlaceOverrideSet), ApiError, PlaceOverrideChanged, Any] =
    writeBase.post
      .in("places" / path[String]("id") / "override")
      .in(jsonBody[PlaceOverrideSet])
      .out(jsonBody[PlaceOverrideChanged])

  lazy val mergePlace: PublicEndpoint[(String, PlaceMergeIn), ApiError, PlaceGone, Any] =
    writeBase.post.in("places" / path[String]("id") / "merge").in(jsonBody[PlaceMergeIn]).out(jsonBody[PlaceGone])

  lazy val removePlace: PublicEndpoint[(String, PlaceRemoveIn), ApiError, PlaceGone, Any] =
    writeBase.post.in("places" / path[String]("id") / "remove").in(jsonBody[PlaceRemoveIn]).out(jsonBody[PlaceGone])

  lazy val addPlace: PublicEndpoint[PlaceAdd, ApiError, PlaceSummary, Any] =
    writeBase.post.in("places").in(jsonBody[PlaceAdd]).out(jsonBody[PlaceSummary])

  lazy val buildHierarchy: PublicEndpoint[Unit, ApiError, SpatialHierarchyResult, Any] =
    writeBase.post.in("places" / "build-hierarchy").out(jsonBody[SpatialHierarchyResult])

  final case class EventQuery(
      filter: Filter = Filter(),
      text: Option[String] = None,
      kind: Option[String] = None,
      historicity: Option[String] = None,
      placeId: Option[String] = None,
      tier: Option[String] = None,
      context: Option[Boolean] = None,
      area: Option[String] = None
  )

  lazy val events: PublicEndpoint[EventQuery, ApiError, EventList, Any] =
    base.get
      .in("events")
      .in(filterQuery)
      .in(query[Option[String]]("text"))
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("historicity"))
      .in(query[Option[String]]("placeId"))
      .in(query[Option[String]]("tier"))
      .in(query[Option[Boolean]]("context"))
      .in(query[Option[String]]("area"))
      .mapInTo[EventQuery]
      .out(jsonBody[EventList])

  lazy val eventsGeo: PublicEndpoint[EventQuery, ApiError, EventGeoList, Any] =
    base.get
      .in("events" / "geo")
      .in(filterQuery)
      .in(query[Option[String]]("text"))
      .in(query[Option[String]]("kind"))
      .in(query[Option[String]]("historicity"))
      .in(query[Option[String]]("placeId"))
      .in(query[Option[String]]("tier"))
      .in(query[Option[Boolean]]("context"))
      .in(query[Option[String]]("area"))
      .mapInTo[EventQuery]
      .out(jsonBody[EventGeoList])

  lazy val renameEvent: PublicEndpoint[(String, LabelRename), ApiError, LabelRenamed, Any] =
    writeBase.post.in("events" / path[String]("id") / "rename").in(jsonBody[LabelRename]).out(jsonBody[LabelRenamed])

  lazy val getEvent: PublicEndpoint[String, ApiError, EventDetail, Any] =
    endpoint.get
      .in("api" / "events" / path[String]("id"))
      .errorOut(writeErrors)
      .out(jsonBody[EventDetail])

  lazy val putEvent: PublicEndpoint[(String, EventPut), ApiError, EventDetail, Any] =
    endpoint.put
      .in("api" / "events" / path[String]("id"))
      .in(jsonBody[EventPut])
      .errorOut(writeErrors)
      .out(jsonBody[EventDetail])

  lazy val patchEvent: PublicEndpoint[(String, EventPatch), ApiError, EventDetail, Any] =
    endpoint.patch
      .in("api" / "events" / path[String]("id"))
      .in(jsonBody[EventPatch])
      .errorOut(writeErrors)
      .out(jsonBody[EventDetail])

  lazy val deleteEvent: PublicEndpoint[String, ApiError, EventDeleted, Any] =
    endpoint.delete
      .in("api" / "events" / path[String]("id"))
      .errorOut(writeErrors)
      .out(jsonBody[EventDeleted])

  final case class AttestationQuery(
      citekey: Option[String] = None,
      targetType: Option[String] = None,
      targetId: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  )

  lazy val attestations: PublicEndpoint[AttestationQuery, ApiError, AttestationList, Any] =
    base.get
      .in("attestations")
      .in(query[Option[String]]("citekey"))
      .in(query[Option[String]]("targetType"))
      .in(query[Option[String]]("targetId"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .mapInTo[AttestationQuery]
      .out(jsonBody[AttestationList])

  lazy val getAttestation: PublicEndpoint[Long, ApiError, Attestation, Any] =
    endpoint.get
      .in("api" / "attestations" / path[Long]("id"))
      .errorOut(writeErrors)
      .out(jsonBody[Attestation])

  lazy val createAttestation: PublicEndpoint[AttestationPut, ApiError, Attestation, Any] =
    endpoint.post
      .in("api" / "attestations")
      .in(jsonBody[AttestationPut])
      .errorOut(writeErrors)
      .out(jsonBody[Attestation])

  lazy val patchAttestation: PublicEndpoint[(Long, AttestationPatch), ApiError, Attestation, Any] =
    endpoint.patch
      .in("api" / "attestations" / path[Long]("id"))
      .in(jsonBody[AttestationPatch])
      .errorOut(writeErrors)
      .out(jsonBody[Attestation])

  lazy val deleteAttestation: PublicEndpoint[Long, ApiError, AttestationDeleted, Any] =
    endpoint.delete
      .in("api" / "attestations" / path[Long]("id"))
      .errorOut(writeErrors)
      .out(jsonBody[AttestationDeleted])

  final case class GoalQuery(
      status: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  )

  lazy val goals: PublicEndpoint[GoalQuery, ApiError, GoalList, Any] =
    base.get
      .in("goals")
      .in(query[Option[String]]("status"))
      .in(query[Option[Int]]("limit"))
      .in(query[Option[Int]]("offset"))
      .mapInTo[GoalQuery]
      .out(jsonBody[GoalList])

  lazy val renameGoal: PublicEndpoint[(String, LabelRename), ApiError, LabelRenamed, Any] =
    writeBase.post.in("goals" / path[String]("slug") / "rename").in(jsonBody[LabelRename]).out(jsonBody[LabelRenamed])

  lazy val renamePlace: PublicEndpoint[(String, LabelRename), ApiError, LabelRenamed, Any] =
    writeBase.post.in("places" / path[String]("id") / "rename").in(jsonBody[LabelRename]).out(jsonBody[LabelRenamed])

  lazy val getGoal: PublicEndpoint[String, ApiError, Goal, Any] =
    endpoint.get
      .in("api" / "goals" / path[String]("slug"))
      .errorOut(writeErrors)
      .out(jsonBody[Goal])

  lazy val putGoal: PublicEndpoint[(String, GoalPut), ApiError, Goal, Any] =
    endpoint.put
      .in("api" / "goals" / path[String]("slug"))
      .in(jsonBody[GoalPut])
      .errorOut(writeErrors)
      .out(jsonBody[Goal])

  lazy val patchGoal: PublicEndpoint[(String, GoalPatch), ApiError, Goal, Any] =
    endpoint.patch
      .in("api" / "goals" / path[String]("slug"))
      .in(jsonBody[GoalPatch])
      .errorOut(writeErrors)
      .out(jsonBody[Goal])

  lazy val deleteGoal: PublicEndpoint[String, ApiError, GoalDeleted, Any] =
    endpoint.delete
      .in("api" / "goals" / path[String]("slug"))
      .errorOut(writeErrors)
      .out(jsonBody[GoalDeleted])

  lazy val agentStateList: PublicEndpoint[Unit, ApiError, List[AgentStateEntry], Any] =
    base.get.in("agent-state").out(jsonBody[List[AgentStateEntry]])

  lazy val agentStateGet: PublicEndpoint[String, ApiError, AgentStateEntry, Any] =
    endpoint.get
      .in("api" / "agent-state" / path[String]("key"))
      .errorOut(writeErrors)
      .out(jsonBody[AgentStateEntry])

  lazy val agentStateSet: PublicEndpoint[(String, AgentStatePut), ApiError, AgentStateEntry, Any] =
    endpoint.put
      .in("api" / "agent-state" / path[String]("key"))
      .in(jsonBody[AgentStatePut])
      .errorOut(writeErrors)
      .out(jsonBody[AgentStateEntry])

  lazy val agentStateDelete: PublicEndpoint[String, ApiError, AgentStateDeleted, Any] =
    endpoint.delete
      .in("api" / "agent-state" / path[String]("key"))
      .errorOut(writeErrors)
      .out(jsonBody[AgentStateDeleted])

  private lazy val ingestErrors =
    oneOf[ApiError](
      oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[ApiError])(ApiError.hasCode(Ingest.CodeConflict)),
      oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[ApiError])(ApiError.hasCode(Write.CodeNotFound)),
      oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
    )

  /** Query parameters, not a multipart part list: tapir's netty server has no multipart request-body support
    * (`RawBodyType.MultipartBody` throws `UnsupportedOperationException`), so the document travels as the raw body.
    */
  lazy val ingestParams: EndpointInput[IngestParams] =
    query[String]("citekey")
      .and(query[Option[String]]("filename"))
      .and(query[Boolean]("quality").default(false))
      .and(query[Boolean]("force").default(false))
      .and(query[Option[String]]("title"))
      .and(query[List[String]]("author"))
      .and(query[Option[Int]]("year"))
      .and(query[Option[String]]("doi"))
      .and(query[Option[String]]("provenance"))
      .and(
        query[Option[Int]]("acquireItem")
          .description("id of the acquisition item this upload fulfils; the OCR job ticks it off when it succeeds")
      )
      .mapTo[IngestParams]

  lazy val ingest: PublicEndpoint[(IngestParams, TapirFile), ApiError, IngestAccepted, Any] =
    endpoint.post
      .in("api" / "ingest")
      .in(ingestParams)
      .in(fileBody)
      .errorOut(ingestErrors)
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[IngestAccepted])

  lazy val reocr: PublicEndpoint[(String, Boolean), ApiError, IngestAccepted, Any] =
    endpoint.post
      .in("api" / "sources" / path[String]("citekey") / "reocr")
      .in(query[Boolean]("quality").default(false))
      .errorOut(ingestErrors)
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[IngestAccepted])

  lazy val original: PublicEndpoint[String, ApiError, (TapirFile, String), Any] =
    endpoint.get
      .in("api" / "sources" / path[String]("citekey") / "original")
      .errorOut(ingestErrors)
      .out(fileBody)
      .out(header[String](sttp.model.HeaderNames.ContentDisposition))

  lazy val job: PublicEndpoint[String, ApiError, JobInfo, Any] =
    base.get
      .in("jobs" / path[String]("id"))
      .out(jsonBody[JobInfo])
      .errorOut(statusCode(StatusCode.NotFound))

  lazy val jobControl: PublicEndpoint[(String, String), ApiError, JobInfo, Any] =
    endpoint.post
      .in("api" / "jobs" / path[String]("id") / path[String]("action"))
      .errorOut(
        oneOf[ApiError](
          oneOfVariantValueMatcher(StatusCode.NotFound, jsonBody[ApiError])(ApiError.hasCode(Write.CodeNotFound)),
          oneOfVariantValueMatcher(StatusCode.Conflict, jsonBody[ApiError])(ApiError.hasCode(JobActions.CodeConflict)),
          oneOfDefaultVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiError]))
        )
      )
      .out(jsonBody[JobInfo])

  lazy val activity: PublicEndpoint[
    (Option[String], Option[String], Option[String], Option[Int], Option[String]),
    ApiError,
    Activity.Page,
    Any
  ] =
    base.get
      .in("activity")
      .in(
        query[Option[String]]("show").description(
          s"one of: ${Activity.Show.values.map(_.wire).mkString(", ")} (default all)"
        )
      )
      .in(query[Option[String]]("since").description("from: 2026-10-01, an ISO instant, or 7d / 12h / 30m ago"))
      .in(query[Option[String]]("goal").description("only what concerns this research goal (its slug)"))
      .in(
        query[Option[Int]]("limit")
          .description(s"entries per page (default ${Activity.DefaultLimit}, at most ${Activity.MaxLimit})")
      )
      .in(query[Option[String]]("before").description("the next older page: the cursor the last page gave"))
      .out(jsonBody[Activity.Page])

  lazy val jobs: PublicEndpoint[
    (ListFilters.JobFilter, Option[Int], Option[String], Option[String]),
    ApiError,
    (List[JobInfo], Paging.Page),
    Any
  ] =
    base.get
      .in("jobs")
      .in(ListFilters.jobQuery)
      .in(query[Option[Int]]("limit"))
      .in(query[Option[String]]("before"))
      .in(query[Option[String]]("upTo"))
      .out(jsonBody[List[JobInfo]])
      .out(Paging.headers)

  lazy val assets: PublicEndpoint[String, ApiError, List[AssetInfo], Any] =
    base.get.in("sources" / path[String]("citekey") / "assets").out(jsonBody[List[AssetInfo]])

  lazy val putAssets: PublicEndpoint[(String, List[AssetInput]), ApiError, AssetsStored, Any] =
    endpoint.post
      .in("api" / "sources" / path[String]("citekey") / "assets")
      .in(jsonBody[List[AssetInput]])
      .errorOut(ingestErrors)
      .out(jsonBody[AssetsStored])

  lazy val asset: PublicEndpoint[(String, String), ApiError, (Array[Byte], String), Any] =
    endpoint.get
      .in("source" / path[String]("citekey") / "assets" / path[String]("name"))
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[ApiError])
      .out(byteArrayBody)
      .out(header[String](sttp.model.HeaderNames.ContentType))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.CacheControl, Ingest.AssetCacheControl)))

  lazy val assetDetail: PublicEndpoint[(String, String), ApiError, AssetDetail, Any] =
    base.get
      .in("sources" / path[String]("citekey") / "assets" / path[String]("name") / "detail")
      .out(jsonBody[AssetDetail])

  lazy val patchAsset: PublicEndpoint[(String, String, AssetPatch), ApiError, AssetDetail, Any] =
    base.patch
      .in("sources" / path[String]("citekey") / "assets" / path[String]("name"))
      .in(jsonBody[AssetPatch])
      .out(jsonBody[AssetDetail])

  final case class MediaGeoQuery(
      kind: Option[String] = None,
      from: Option[Long] = None,
      to: Option[Long] = None,
      limit: Option[Int] = None,
      provider: Option[String] = None,
      license: Option[String] = None
  )

  lazy val mediaGeo: PublicEndpoint[MediaGeoQuery, ApiError, List[MediaGeoHit], Any] =
    base.get
      .in("media-geo")
      .in(
        query[Option[String]]("kind")
          .and(query[Option[Long]]("from"))
          .and(query[Option[Long]]("to"))
          .and(query[Option[Int]]("limit"))
          .and(query[Option[String]]("provider"))
          .and(query[Option[String]]("license"))
          .mapTo[MediaGeoQuery]
      )
      .out(jsonBody[List[MediaGeoHit]])

  final case class MediaQuery(
      provider: Option[String] = None,
      license: Option[String] = None,
      place: Option[String] = None,
      event: Option[String] = None,
      q: Option[String] = None,
      limit: Option[Int] = None,
      offset: Option[Int] = None
  )

  lazy val mediaList: PublicEndpoint[MediaQuery, ApiError, MediaList, Any] =
    base.get
      .in("media")
      .in(
        query[Option[String]]("provider")
          .and(query[Option[String]]("license"))
          .and(query[Option[String]]("place"))
          .and(query[Option[String]]("event"))
          .and(query[Option[String]]("q"))
          .and(query[Option[Int]]("limit"))
          .and(query[Option[Int]]("offset"))
          .mapTo[MediaQuery]
      )
      .out(jsonBody[MediaList])

  lazy val mediaFacets: PublicEndpoint[Unit, ApiError, MediaFacets, Any] =
    base.get.in("media" / "facets").out(jsonBody[MediaFacets])

  lazy val mediaProviders: PublicEndpoint[Unit, ApiError, List[MediaProviderInfo], Any] =
    base.get.in("media" / "providers").out(jsonBody[List[MediaProviderInfo]])

  lazy val mediaShow: PublicEndpoint[String, ApiError, MediaItem, Any] =
    base.get
      .in("media" / path[String]("uid"))
      .out(jsonBody[MediaItem])
      .errorOut(statusCode(StatusCode.NotFound))

  lazy val mediaImport: PublicEndpoint[MediaImportRequest, ApiError, MediaImportReport, Any] =
    base.post.in("media" / "import").in(jsonBody[MediaImportRequest]).out(jsonBody[MediaImportReport])

  lazy val mediaImportJob: PublicEndpoint[MediaImportRequest, ApiError, JobInfo, Any] =
    base.post
      .in("media" / "import" / "job")
      .in(jsonBody[MediaImportRequest])
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobInfo])

  lazy val mediaCovers: PublicEndpoint[MediaCoversRequest, ApiError, MediaCoversReport, Any] =
    base.post.in("media" / "covers").in(jsonBody[MediaCoversRequest]).out(jsonBody[MediaCoversReport])

  lazy val mediaCoversJob: PublicEndpoint[MediaCoversRequest, ApiError, JobInfo, Any] =
    base.post
      .in("media" / "covers" / "job")
      .in(jsonBody[MediaCoversRequest])
      .out(statusCode(StatusCode.Accepted))
      .out(jsonBody[JobInfo])

  lazy val putSourceCover: PublicEndpoint[(SourceCoverParams, Array[Byte]), ApiError, MediaItem, Any] =
    writeBase.put
      .in("media" / "sources" / path[String]("citekey") / "cover")
      .in(query[String]("provider").default(MediaProvider.Calibre))
      .in(query[String]("key"))
      .in(query[Option[String]]("mime"))
      .mapInTo[SourceCoverParams]
      .in(byteArrayBody)
      .out(jsonBody[MediaItem])

  lazy val fillFigureLicences: PublicEndpoint[Boolean, ApiError, FigureLicenceFill, Any] =
    base.post
      .in("media" / "fill-licences")
      .in(query[Boolean]("apply").default(false))
      .out(jsonBody[FigureLicenceFill])

  lazy val mediaThumb: PublicEndpoint[String, ApiError, (Array[Byte], String), Any] =
    endpoint.get
      .in("media" / path[String]("uid") / "thumb")
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[ApiError])
      .out(byteArrayBody)
      .out(header[String](sttp.model.HeaderNames.ContentType))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.CacheControl, Ingest.MediaCacheControl)))

  lazy val mediaFile: PublicEndpoint[String, ApiError, (Array[Byte], String), Any] =
    endpoint.get
      .in("media" / path[String]("uid") / "file")
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[ApiError])
      .out(byteArrayBody)
      .out(header[String](sttp.model.HeaderNames.ContentType))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.CacheControl, Ingest.MediaCacheControl)))

  lazy val assetLinks
      : PublicEndpoint[(Option[String], Option[String], Option[String], Option[String]), ApiError, AssetLinkList, Any] =
    base.get
      .in("asset-links")
      .in(query[Option[String]]("citekey"))
      .in(query[Option[String]]("asset"))
      .in(query[Option[String]]("target_type"))
      .in(query[Option[String]]("target_id"))
      .out(jsonBody[AssetLinkList])

  lazy val createAssetLink: PublicEndpoint[AssetLinkPut, ApiError, AssetLink, Any] =
    base.post.in("asset-links").in(jsonBody[AssetLinkPut]).out(jsonBody[AssetLink])

  lazy val deleteAssetLink: PublicEndpoint[Long, ApiError, AssetLinkDeleted, Any] =
    base.delete.in("asset-links" / path[Long]("id")).out(jsonBody[AssetLinkDeleted])

  /** Registered before the image endpoint so Tapir matches it first. */
  lazy val iiifInfo: PublicEndpoint[(String, String), ApiError, io.circe.Json, Any] =
    endpoint.get
      .in("iiif" / path[String]("citekey") / path[String]("name") / "info.json")
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[ApiError])
      .out(jsonBody[io.circe.Json])
      .out(header(sttp.model.Header(sttp.model.HeaderNames.AccessControlAllowOrigin, "*")))

  /** IIIF Image API Level 1: region `full|x,y,w,h`, size `max|^max|w,|,h|w,h|!w,h`, rotation `0` only, quality
    * `default`, format `jpg|png`.
    */
  lazy val iiifImage
      : PublicEndpoint[(String, String, String, String, String, String), ApiError, (Array[Byte], String), Any] =
    endpoint.get
      .in(
        "iiif" / path[String]("citekey") / path[String]("name") /
          path[String]("region") / path[String]("size") / path[String]("rotation") /
          path[String]("filename")
      )
      .errorOut(statusCode(StatusCode.NotFound))
      .errorOut(jsonBody[ApiError])
      .out(byteArrayBody)
      .out(header[String](sttp.model.HeaderNames.ContentType))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.AccessControlAllowOrigin, "*")))
      .out(header(sttp.model.Header(sttp.model.HeaderNames.CacheControl, "public, max-age=31536000, immutable")))
