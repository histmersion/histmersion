package histdb.server

import histdb.core.*
import histdb.db.{Importer, Migrate, SourceRepo}
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
    * search and dropping the evidence. New callers should use [[sources]]. */
  def search(q: Option[String], limit: Option[Int]): List[SearchHit] =
    sources(Filter(q = q.map(_.trim).filter(_.nonEmpty), limit = limit.getOrElse(0))).hits.map(_.source)

  /** `limit <= 0` means "use the configured page size" — no literal page size exists in the code. */
  private def resolve(f: Filter): Filter =
    f.copy(limit = if f.limit <= 0 then cfg.limits.search else f.limit, offset = f.offset.max(0))

  /** `GET /api/sources` — THE discovery operation: sources with their matched findings and reasons. */
  def sources(f: Filter): SearchResult = SourceRepo.sources(ds, resolve(f))

  /** `GET /api/facets` — the vocabulary of one dimension inside the current filter. */
  def facets(field: String, prefix: Option[String], f: Filter): Either[ApiError, List[Facet]] =
    FacetField.parse(field)
      .toRight(ApiError(s"unknown facet field '$field' (one of: ${FacetField.all.mkString(", ")})"))
      .map(ff => SourceRepo.facets(ds, ff, prefix, resolve(f), resolve(f).limit).toList)

  def getSource(citekey: String): Either[ApiError, SourceWithFindings] =
    SourceRepo.get(ds, citekey)
      .map((s, fs) => SourceWithFindings(s, fs.toList))
      .toRight(ApiError(s"No source '$citekey'"))

  def findings(q: Api.FindingsQuery): List[GeoHit] =
    SourceRepo.geoWhen(ds, q.region, q.bbox, q.from, q.to, q.limit.getOrElse(cfg.limits.findings)).toList

  def neighbors(citekey: String): List[NeighborRow] = SourceRepo.neighbors(ds, citekey).toList

  def seed(req: SeedRequest): Either[ApiError, SeedResult] =
    val dir = Option(req.dir).map(_.trim).filter(_.nonEmpty).getOrElse(cfg.corpus.dir)
    if dir.isEmpty then Left(ApiError("no directory given and histdb.corpus.dir is not configured"))
    else if !Files.isDirectory(Path.of(dir)) then Left(ApiError(s"not a directory on the server: $dir"))
    else Right(Importer.importDir(ds, Path.of(dir)))
