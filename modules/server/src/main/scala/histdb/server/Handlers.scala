package histdb.server

import histdb.core.*
import histdb.db.{AggregateRepo, ArticleRepo, BackupRepo, Importer, Migrate, PlaceRepo, PlaceScope, SourceRepo, TreeRepo}
import java.nio.file.{Files, Path}
import java.time.Instant

/** The ONE implementation of every operation in the contract. REST routes and MCP tools both call
  * these; the CLI reaches them over HTTP. Pure functions of (deps, input) except for the DB. */
final class Handlers(deps: Deps, converter: ConverterClient):

  /** The ordinary construction: the OCR sidecar client comes from config. The two-argument form is
    * the seam an HTTP test uses to substitute a fake converter (plan §1.8). */
  def this(deps: Deps) = this(deps, ConverterClient.fromConfig(deps.cfg.converter))

  private val ds  = deps.ds
  private val cfg = deps.cfg
  private val backups = BackupService(cfg)

  /** One lock per server (plan §1.10): held by a restore, checked by every write below. */
  private val maintenance = Maintenance()

  /** Stream 2.8 — lint, the bookkeeping queues and the read-only query. Its own object (one file per
    * stream), wired into REST and MCP through this same `Handlers` value. */
  val ops: OpsHandlers = OpsHandlers(deps)

  /** The ingest/jobs/assets surface (plan §1.8), in its own file but reached only through here — so
    * "one implementation per operation" still holds for REST, MCP and the CLI alike. */
  val ingest: IngestHandlers = IngestHandlers(deps, converter)

  def version: VersionInfo = VersionInfo(Version.name, Version.version)

  def health: Health =
    val conv = ingest.converterHealth
    scala.util.Try(Migrate.current(ds)) match
      case scala.util.Success(v) => Health(if conv == "ok" then "ok" else "degraded", "ok", v, Some(conv))
      case scala.util.Failure(e) => Health("degraded", s"error: ${e.getMessage}", None, Some(conv))

  def uiConfig: UiConfig = cfg.ui

  /** Legacy `GET /api/search` — kept working (the web page uses it) by delegating to the filtered
    * search and dropping the evidence, so there is ONE query path (FTS ranking and snippets
    * included). New callers should use [[sources]]. */
  def search(q: Option[String], limit: Option[Int]): List[SearchHit] =
    sources(Filter(q = q.map(_.trim).filter(_.nonEmpty), limit = limit.getOrElse(0))).hits.map(_.source)


  /** `limit <= 0` means "use the configured page size" — no literal page size exists in the code. */
  private def resolve(f: Filter): Filter =
    f.copy(limit = if f.limit <= 0 then cfg.limits.search else f.limit, offset = f.offset.max(0))

  /** How the gazetteer reads this filter's `places` values (plan §1.6a): the ids among them expand
    * to themselves plus their descendants as of `f.period`; the rest fall back to the name ILIKE.
    * Resolved ONCE per request and threaded into every query, so the sidebar, the map, the facets and
    * the histogram all agree. No `places` filter ⇒ no query at all. */
  private def scopeOf(f: Filter): PlaceScope =
    if f.places.isEmpty then PlaceScope.empty else PlaceRepo.scopeFor(ds, f.places, f.period)

  /** `GET /api/sources` — THE discovery operation: sources with their matched findings and reasons. */
  def sources(f: Filter): SearchResult =
    SourceRepo.sources(ds, resolve(f), cfg.limits.snippetWords, scopeOf(f))

  /** `GET /api/facets` — the vocabulary of one dimension inside the current filter. */
  def facets(field: String, prefix: Option[String], f: Filter): Either[ApiError, List[Facet]] =
    FacetField.parse(field)
      .toRight(ApiError(s"unknown facet field '$field' (one of: ${FacetField.all.mkString(", ")})"))
      .map(ff => SourceRepo.facets(ds, ff, prefix, resolve(f), resolve(f).limit, scopeOf(f)).toList)

  def getSource(citekey: String): Either[ApiError, SourceWithFindings] =
    SourceRepo.get(ds, citekey)
      .map((s, fs) => SourceWithFindings(s, fs.toList))
      .toRight(ApiError(s"No source '$citekey'"))

  /** `GET /api/findings` — the MAP FEED (plan §1.0): the findings that themselves satisfy the
    * filter's finding-level criteria, of the sources that satisfy its source-level ones. Same query
    * parameters as [[sources]]; `limit <= 0` means the configured `limits.findings`. */
  def findings(q: Api.FindingsQuery): List[GeoHit] =
    val f = q.toFilter
    SourceRepo.findingsFor(ds, f.copy(limit = if f.limit <= 0 then cfg.limits.findings else f.limit),
      scopeOf(f)).toList

  /** `/api/aggregate` — the timeline strip (§1.7) AND the nested tree (§1.6). Narrowed by the SAME
    * filter as `/api/sources` and `/api/findings`; the window defaults to the configured UI span.
    *
    * `by=time` alone keeps the wave-1 behaviour byte for byte: the flat bars, gaps included, which
    * is what the strip draws. Adding `depth`/`samples` nests the same numbers, and
    * `by=region|time,region|region,time` fills `tree` instead. SQL only ever SELECTS rows;
    * [[Aggregate]] and [[Tree]] (both pure) do all the bucketing, so the strip, the tabs, the CLI
    * and MCP cannot drift apart.
    *
    * Deeper levels are LAZY by construction: there is no cursor — a client narrows `from`/`to` (or
    * `place`) to one bucket and asks again, which is the same query with a smaller window. */
  def aggregate(q: Api.AggregateQuery): Either[ApiError, TimeHistogram] =
    def norm(o: Option[String], dflt: String) = o.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse(dflt)
    val unit = norm(q.unit, "sources")
    Tree.parseBy(norm(q.by, Tree.KindTime))
      .toRight(ApiError(s"unsupported by='${norm(q.by, Tree.KindTime)}' — use " +
        "time | region | time,region | region,time"))
      .flatMap { axes =>
        if unit != "sources" && unit != "findings" then
          Left(ApiError(s"unsupported unit='$unit' — use sources or findings"))
        else Right(aggregateOn(axes, unit, q))
      }

  /** The aggregation itself, once `by`/`unit` have been validated. */
  private def aggregateOn(axes: List[String], unit: String, q: Api.AggregateQuery): TimeHistogram =
    val t = cfg.ui.time
    // the window to bucket = the filter's deep-time range, defaulted (half-open bounds included)
    val a = q.filter.period.map(_.start).filter(_ != Filter.Earliest).getOrElse(t.defaultFrom)
    val b = q.filter.period.map(_.end).filter(_ != Filter.Latest).getOrElse(t.defaultTo)
    val (from, to) = if a <= b then (a, b) else (b, a)
    val window  = q.filter.copy(period = Some(Period(from, to)))
    val scope   = scopeOf(window)
    val size    = Aggregate.bucketSize(to - from)
    val depth   = q.depth.filter(_ > 0).getOrElse(cfg.ui.treeDepth).max(1).min(Tree.MaxDepth)
    val samples = q.samples.getOrElse(cfg.ui.treeSamples).max(0).min(Tree.MaxSamples)
    val flat =
      if axes.headOption.contains(Tree.KindTime) then
        Aggregate.histogram(AggregateRepo.periods(ds, unit, window, scope).toList, from, to, size)
      else Nil
    val base = TimeHistogram(from, to, size, flat, by = axes.mkString(","), unit = unit, depth = depth)
    // The TIMELINE STRIP asks for `by=time` on every span change and only wants `buckets`; building
    // the tree as well would double its cost for nothing. A caller that wants the nested answer for
    // the same axis says so by asking for a `depth`/`samples` — which is exactly what the "by time"
    // tab does, and what the CLI's `--group time` sends.
    if axes == List(Tree.KindTime) && q.depth.isEmpty && q.samples.isEmpty then base
    else treeOn(axes, unit, window, scope, from, to, depth, samples, q.asOf, base)

  /** The nested part of [[aggregateOn]] — split out so the flat strip never pays for it. */
  private def treeOn(axes: List[String], unit: String, window: Filter, scope: PlaceScope,
      from: Long, to: Long, depth: Int, samples: Int, askedAsOf: Option[Long],
      base: TimeHistogram): TimeHistogram =
    val rows = TreeRepo.rows(ds, unit, window, scope).toList
    // the gazetteer slice these rows hang off — one upward closure, not the whole 16 k-place table
    def gaz = TreeRepo.gazetteer(ds, rows)
    // `by=region` reads the hierarchy at ONE year: the caller's, else the middle of the window
    val asOf = askedAsOf.orElse(Some(Tree.midpoint(from, to)))
    // LAZY DEEPER LEVELS: `place=<one gazetteer id>` roots the region tree AT that place, so
    // expanding a node returns its subtree rather than the whole forest with the node buried in it.
    // (Two place chips, or a name that is not a gazetteer id, is an ordinary filter — no re-rooting.)
    val root = window.places.filter(p => scope.idsFor(p).isDefined) match
      case one :: Nil => Some(one)
      case _          => None
    val tree = axes match
      case Tree.KindTime :: Nil                    => Tree.timeTree(rows, from, to, depth, samples)
      case Tree.KindRegion :: Nil                  =>
        val (ns, rs) = gaz; Tree.regionTree(rows, ns, rs, asOf, depth, samples, root)
      case Tree.KindTime :: Tree.KindRegion :: Nil =>
        val (ns, rs) = gaz; Tree.timeRegionTree(rows, from, to, ns, rs, depth, samples)
      case _                                       =>
        val (ns, rs) = gaz; Tree.regionTimeTree(rows, from, to, ns, rs, asOf, depth, samples, root)
    base.copy(
      total = rows.iterator.map(_.key).toSet.size,
      // `time,region` reads the gazetteer at each bucket's OWN midpoint, so no single asOf applies
      asOf = if axes == List(Tree.KindTime, Tree.KindRegion) || axes == List(Tree.KindTime) then None else asOf,
      tree = tree)

  def neighbors(citekey: String): List[NeighborRow] = SourceRepo.neighbors(ds, citekey).toList

  def seed(req: SeedRequest): Either[ApiError, SeedResult] = maintenance.guard("seed"):
    val dir = Option(req.dir).map(_.trim).filter(_.nonEmpty).getOrElse(cfg.corpus.dir)
    if dir.isEmpty then Left(ApiError("no directory given and histdb.corpus.dir is not configured"))
    else if !Files.isDirectory(Path.of(dir)) then Left(ApiError(s"not a directory on the server: $dir"))
    else Right(Importer.importDir(ds, Path.of(dir), cfg.limits.chunkChars, req.force, cfg.limits.assetBytes))

  // ── writes (plan §1.9a) ─────────────────────────────────────────────────────────────────────

  /** Create or REPLACE everything under one citekey. */
  def putSource(citekey: String, in: SourcePut): Either[ApiError, SourceWithFindings] =
    maintenance.guard("put_source"):
      Write.applyPut(citekey, in)
        .left.map(m => ApiError(m, Some(Write.CodeInvalid)))
        .flatMap { s => val _ = SourceRepo.put(ds, s, cfg.limits.chunkChars); getSource(citekey) }

  /** Merge only the parts present in the patch (read + merge + write in one transaction). */
  def patchSource(citekey: String, p: SourcePatch): Either[ApiError, SourceWithFindings] =
    maintenance.guard("patch_source"):
      SourceRepo.patch(ds, citekey, p, cfg.limits.chunkChars).flatMap(_ => getSource(citekey))

  /** The note as it would live on disk — frontmatter YAML + the `notes` body. */
  def exportSource(citekey: String): Either[ApiError, String] =
    SourceRepo.load(ds, citekey).map(Export.note).toRight(Write.notFound(citekey))

  def citekeys: List[String] = SourceRepo.citekeys(ds).toList

  def resolveEdges: Either[ApiError, EdgesResolved] =
    maintenance.guard("edges/resolve"):
      Right(EdgesResolved(SourceRepo.resolveEdges(ds)))

  /** Seed ONE note from raw text (the host-side path of `histdb seed`, for a corpus dir the SERVER
    * cannot see). Applies the same "stored row is newer than the file" guard as [[seed]] whenever the
    * client sends the file's mtime. */
  def importNote(req: NoteImport): Either[ApiError, SeedResult] = maintenance.guard("import"):
    Frontmatter.parseNote(req.text, req.citekey.getOrElse("")) match
      case None => Left(ApiError("no YAML frontmatter in the note"))
      case Some(parsed) =>
        val ck = parsed.citekey
        if ck.isEmpty then Left(ApiError("note has no citekey (none in the frontmatter, none supplied)"))
        else if !req.force && req.mtime.exists(m => Importer.isStale(SourceRepo.updatedAtMillis(ds), ck, m))
        then Right(SeedResult(0, Nil, 0, List(ck)))
        else
          // `body` is the converted paper text the client read from `md_path`; NUL bytes occur in some
          // OCR output and Postgres `text` rejects them.
          val withBody = req.body.map(_.filterNot(_.toInt == 0)).filter(_.nonEmpty) match
            case Some(b) => parsed.copy(ocrMarkdown = Some(b))
            case None    => parsed
          // figures of the old file pipeline (plan §1.8): `figures/<name>` is normalised to the
          // canonical `assets/<name>`, and any base64 payloads that travelled with the note stored
          val normalised = withBody.copy(ocrMarkdown = withBody.ocrMarkdown.map(AssetLinks.fromFigures))
          val _ = SourceRepo.put(ds, normalised, cfg.limits.chunkChars)
          if req.assets.isEmpty then Right(SeedResult(1, Nil, 0, Nil))
          else ingest.putAssets(ck, req.assets).map(_ => SeedResult(1, Nil, 0, Nil))

  /** `get_source` as Markdown, with image links made ABSOLUTE (plan §1.8) so an image-capable MCP
    * client can fetch the figures. The ONE renderer both MCP and the article page go through. */
  def sourceMarkdown(citekey: String, fullText: Boolean): Either[ApiError, String] =
    getSource(citekey).map(sw =>
      AssetLinks.toAbsolute(Markdown.source(sw.source, sw.findings.toVector, fullText), citekey,
        cfg.server.publicUrl))

  // ── the gazetteer (plan §1.6a) ──────────────────────────────────────────────────────────────

  /** `GET /api/places` — name search; `limit <= 0` means the configured `places.search-limit`. */
  def places(q: Option[String], asOf: Option[Long], limit: Option[Int]): List[PlaceHit] =
    PlaceRepo.search(ds, q, asOf, limit.filter(_ > 0).getOrElse(cfg.places.searchLimit)).toList

  /** `GET /api/places/{id}` — the row, its GeoJSON geometry and the hierarchy around it at `asOf`. */
  def place(id: String, asOf: Option[Long]): Either[ApiError, PlaceDetail] =
    PlaceRepo.get(ds, id, asOf).toRight(ApiError(s"No place '$id'"))

  def placeChildren(id: String, asOf: Option[Long]): Either[ApiError, List[PlaceSummary]] =
    place(id, asOf).map(_ => PlaceRepo.children(ds, id, asOf, cfg.places.childrenLimit).toList)

  /** The subtree under one place, depth-tagged; `depth <= 0` means the configured `places.tree-depth`. */
  def placeTree(id: String, asOf: Option[Long], depth: Option[Int]): Either[ApiError, List[PlaceTreeRow]] =
    place(id, asOf).map(_ =>
      PlaceRepo.hierarchy(ds, asOf, Some(id), depth.filter(_ > 0).getOrElse(cfg.places.treeDepth),
        cfg.places.treeLimit).toList)

  /** `POST /api/places/import` — an LPF document or a plain GeoJSON FeatureCollection. Parsing is
    * pure ([[Lpf]]) and all-or-nothing: a malformed document is a 400, never a half import. */
  def importPlaces(kind: Option[String], body: String): Either[ApiError, PlaceImportResult] =
    val k = kind.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse(PlaceKind.Region)
    if !PlaceKind.all.contains(k) then
      Left(ApiError(s"unknown kind '$k' (one of: ${PlaceKind.all.mkString(", ")})"))
    else Lpf.parse(body, k).left.map(ApiError(_)).map(PlaceRepo.upsertPlaces(ds, _))

  /** `POST /api/places/rebuild` — re-materialise `finding_places`. */
  def rebuildPlaces(): PlaceRebuildResult =
    PlaceRepo.rebuildFindingPlaces(ds, cfg.places.pointToleranceKm)

  /** One resumable slice of the FTS chunk rebuild (plan §1.4); loop on `remaining` > 0. */
  def reindex(after: Option[String], limit: Option[Int]): Either[ApiError, ReindexResult] =
    maintenance.guard("reindex"):
      Right(SourceRepo.reindexChunks(ds, cfg.limits.chunkChars, after.map(_.trim).filter(_.nonEmpty),
        limit.filter(_ > 0), cfg.limits.reindexChars))

  // ── backup / restore (plan §1.10) ───────────────────────────────────────────────────────────

  /** `pg_dump -Fc` of the server's own database into `backups.dir`. */
  def backup(): Either[ApiError, BackupResult] =
    maintenance.guard("backup")(backups.create(Instant.now()))

  def backupList: Either[ApiError, List[BackupInfo]] = backups.list

  /** The archive itself, for `GET /api/backups/{file}` — validated against the listing first. */
  def backupFile(file: String): Either[ApiError, java.io.File] = backups.resolve(file).map(_.toFile)

  /** Restore under the maintenance lock: every other write is answered 503 until it returns.
    * Refuses a database that still holds sources unless `force` (`pg_restore --clean` DROPS them),
    * and re-validates the schema with Flyway afterwards so a stale dump cannot pass silently. */
  def restore(req: RestoreRequest): Either[ApiError, RestoreResult] =
    maintenance.exclusively("restore"):
      val before = BackupRepo.sourceCount(ds)
      for
        path <- backups.resolve(req.file)
        _    <- if req.force || before == 0 then Right(())
                else Left(ApiError(s"the database still holds $before source(s); restoring would drop them — " +
                  "re-run with force"))
        r    <- backups.restore(path)
        v    <- Migrate.validate(ds).left.map(m => ApiError(s"restored ${req.file}, but the schema does not validate: $m"))
      yield RestoreResult(req.file, v, before, BackupRepo.sourceCount(ds), r._1, r._2)

  // ── articles: the compiled wiki (plan §5.2 stream 2.7) ──────────────────────────────────────

  /** `GET /api/articles` — the compiled articles, filtered by kind/tag and searched with `q`. */
  def listArticles(q: Api.ArticleQuery): ArticleList =
    ArticleRepo.list(ds, q.kind, q.q, q.tag,
      q.limit.filter(_ > 0).getOrElse(cfg.limits.search), q.offset.getOrElse(0))

  /** One article, with its backlinks and the sources it cites. */
  def getArticle(slug: String): Either[ApiError, ArticleView] =
    ArticleRepo.view(ds, norm(slug)).toRight(Articles.notFound(slug))

  /** Create or REPLACE one article. */
  def putArticle(slug: String, in: ArticlePut): Either[ApiError, ArticleView] =
    Articles.applyPut(slug, in)
      .left.map(m => ApiError(m, Some(Write.CodeInvalid)))
      .flatMap { a => val _ = ArticleRepo.put(ds, a); getArticle(a.slug) }

  /** Merge only the parts present in the patch (read + merge + write in one transaction). */
  def patchArticle(slug: String, p: ArticlePatch): Either[ApiError, ArticleView] =
    ArticleRepo.patch(ds, norm(slug), p).flatMap(_ => getArticle(slug))

  def deleteArticle(slug: String): Either[ApiError, ArticleDeleted] =
    if ArticleRepo.delete(ds, norm(slug)) then Right(ArticleDeleted(norm(slug), true))
    else Left(Articles.notFound(slug))

  /** The article as it would live on disk — `wiki/<kind>/<slug>.md`. */
  def exportArticle(slug: String): Either[ApiError, String] =
    ArticleRepo.load(ds, norm(slug)).map(ArticleNote.render).toRight(Articles.notFound(slug))

  def articleSlugs: List[String] = ArticleRepo.slugs(ds).toList

  /** Import ONE article note from raw text (the host-side path of `histdb seed`). Applies the same
    * "stored row is newer than the file" guard as the source seed whenever the client sends the
    * file's mtime, so a reseed cannot wipe an agent's compiled prose. */
  def importArticle(req: ArticleImport): Either[ApiError, SeedResult] =
    val fallbackSlug = req.slug.map(_.trim).filter(_.nonEmpty).getOrElse("")
    val fallbackKind = req.kind.map(_.trim).filter(_.nonEmpty).getOrElse(Articles.Kinds.head)
    ArticleNote.parse(req.text, fallbackSlug, fallbackKind) match
      case None => Left(ApiError("no YAML frontmatter in the article note"))
      case Some(parsed) =>
        Articles.slugOf(parsed.slug).left.map(m => ApiError(m, Some(Write.CodeInvalid))).flatMap { s =>
          if !req.force && req.mtime.exists(m =>
               Importer.isStale(ArticleRepo.updatedAtMillis(ds), s, m))
          then Right(SeedResult(0, Nil, 0, List(s)))
          else Articles.kindOf(parsed.kind).left.map(m => ApiError(m, Some(Write.CodeInvalid))).map { k =>
            val _ = ArticleRepo.put(ds, parsed.copy(slug = s, kind = k))
            SeedResult(1, Nil, 0, Nil)
          }
        }

  /** Slugs are lower-case by construction; be forgiving about what a caller sends. */
  private def norm(slug: String): String = slug.trim.toLowerCase
