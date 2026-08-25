package histdb.server

import histdb.core.*
import histdb.db.{AggregateRepo, ArticleRepo, Importer, Migrate, SourceRepo}
import java.nio.file.{Files, Path}

/** The ONE implementation of every operation in the contract. REST routes and MCP tools both call
  * these; the CLI reaches them over HTTP. Pure functions of (deps, input) except for the DB. */
final class Handlers(deps: Deps):
  private val ds  = deps.ds
  private val cfg = deps.cfg

  def version: VersionInfo = VersionInfo(Version.name, Version.version)

  def health: Health =
    scala.util.Try(Migrate.current(ds)) match
      case scala.util.Success(v) => Health("ok", "ok", v)
      case scala.util.Failure(e) => Health("degraded", s"error: ${e.getMessage}", None)

  def uiConfig: UiConfig = cfg.ui

  /** Legacy `GET /api/search` — kept working (the web page uses it) by delegating to the filtered
    * search and dropping the evidence, so there is ONE query path (FTS ranking and snippets
    * included). New callers should use [[sources]]. */
  def search(q: Option[String], limit: Option[Int]): List[SearchHit] =
    sources(Filter(q = q.map(_.trim).filter(_.nonEmpty), limit = limit.getOrElse(0))).hits.map(_.source)


  /** `limit <= 0` means "use the configured page size" — no literal page size exists in the code. */
  private def resolve(f: Filter): Filter =
    f.copy(limit = if f.limit <= 0 then cfg.limits.search else f.limit, offset = f.offset.max(0))

  /** `GET /api/sources` — THE discovery operation: sources with their matched findings and reasons. */
  def sources(f: Filter): SearchResult = SourceRepo.sources(ds, resolve(f), cfg.limits.snippetWords)

  /** `GET /api/facets` — the vocabulary of one dimension inside the current filter. */
  def facets(field: String, prefix: Option[String], f: Filter): Either[ApiError, List[Facet]] =
    FacetField.parse(field)
      .toRight(ApiError(s"unknown facet field '$field' (one of: ${FacetField.all.mkString(", ")})"))
      .map(ff => SourceRepo.facets(ds, ff, prefix, resolve(f), resolve(f).limit).toList)

  def getSource(citekey: String): Either[ApiError, SourceWithFindings] =
    SourceRepo.get(ds, citekey)
      .map((s, fs) => SourceWithFindings(s, fs.toList))
      .toRight(ApiError(s"No source '$citekey'"))

  /** `GET /api/findings` — the MAP FEED (plan §1.0): the findings that themselves satisfy the
    * filter's finding-level criteria, of the sources that satisfy its source-level ones. Same query
    * parameters as [[sources]]; `limit <= 0` means the configured `limits.findings`. */
  def findings(q: Api.FindingsQuery): List[GeoHit] =
    val f = q.toFilter
    SourceRepo.findingsFor(ds, f.copy(limit = if f.limit <= 0 then cfg.limits.findings else f.limit)).toList

  /** `/api/aggregate` — the timeline strip (§1.7) and, from wave 2, the "by time" tree (§1.6).
    * Narrowed by the SAME filter as `/api/sources` and `/api/findings`; the window defaults to the
    * configured UI span. SQL only fetches periods, `Aggregate` (pure) does the bucketing. */
  def aggregate(q: Api.AggregateQuery): Either[ApiError, TimeHistogram] =
    def norm(o: Option[String], dflt: String) = o.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse(dflt)
    val by   = norm(q.by, "time")
    val unit = norm(q.unit, "sources")
    if by != "time" then Left(ApiError(s"unsupported by='$by' — only by=time is available (the region tree is plan §1.6)"))
    else if unit != "sources" && unit != "findings" then Left(ApiError(s"unsupported unit='$unit' — use sources or findings"))
    else
      val t = cfg.ui.time
      // the window to bucket = the filter's deep-time range, defaulted (half-open bounds included)
      val a = q.filter.period.map(_.start).filter(_ != Filter.Earliest).getOrElse(t.defaultFrom)
      val b = q.filter.period.map(_.end).filter(_ != Filter.Latest).getOrElse(t.defaultTo)
      val (from, to) = if a <= b then (a, b) else (b, a)
      val rows = AggregateRepo.periods(ds, unit, q.filter.copy(period = Some(Period(from, to)))).toList
      val size = Aggregate.bucketSize(to - from)
      Right(TimeHistogram(from, to, size, Aggregate.histogram(rows, from, to, size)))

  def neighbors(citekey: String): List[NeighborRow] = SourceRepo.neighbors(ds, citekey).toList

  def seed(req: SeedRequest): Either[ApiError, SeedResult] =
    val dir = Option(req.dir).map(_.trim).filter(_.nonEmpty).getOrElse(cfg.corpus.dir)
    if dir.isEmpty then Left(ApiError("no directory given and histdb.corpus.dir is not configured"))
    else if !Files.isDirectory(Path.of(dir)) then Left(ApiError(s"not a directory on the server: $dir"))
    else Right(Importer.importDir(ds, Path.of(dir), cfg.limits.chunkChars, req.force))

  // ── writes (plan §1.9a) ─────────────────────────────────────────────────────────────────────

  /** Create or REPLACE everything under one citekey. */
  def putSource(citekey: String, in: SourcePut): Either[ApiError, SourceWithFindings] =
    Write.applyPut(citekey, in)
      .left.map(m => ApiError(m, Some(Write.CodeInvalid)))
      .flatMap { s => val _ = SourceRepo.put(ds, s, cfg.limits.chunkChars); getSource(citekey) }

  /** Merge only the parts present in the patch (read + merge + write in one transaction). */
  def patchSource(citekey: String, p: SourcePatch): Either[ApiError, SourceWithFindings] =
    SourceRepo.patch(ds, citekey, p, cfg.limits.chunkChars).flatMap(_ => getSource(citekey))

  /** The note as it would live on disk — frontmatter YAML + the `notes` body. */
  def exportSource(citekey: String): Either[ApiError, String] =
    SourceRepo.load(ds, citekey).map(Export.note).toRight(Write.notFound(citekey))

  def citekeys: List[String] = SourceRepo.citekeys(ds).toList

  def resolveEdges: EdgesResolved = EdgesResolved(SourceRepo.resolveEdges(ds))

  /** Seed ONE note from raw text (the host-side path of `histdb seed`, for a corpus dir the SERVER
    * cannot see). Applies the same "stored row is newer than the file" guard as [[seed]] whenever the
    * client sends the file's mtime. */
  def importNote(req: NoteImport): Either[ApiError, SeedResult] =
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
          val _ = SourceRepo.put(ds, withBody, cfg.limits.chunkChars)
          Right(SeedResult(1, Nil, 0, Nil))

  /** One resumable slice of the FTS chunk rebuild (plan §1.4); loop on `remaining` > 0. */
  def reindex(after: Option[String], limit: Option[Int]): ReindexResult =
    SourceRepo.reindexChunks(ds, cfg.limits.chunkChars, after.map(_.trim).filter(_.nonEmpty),
      limit.filter(_ > 0), cfg.limits.reindexChars)

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
