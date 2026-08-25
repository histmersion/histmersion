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

  def search(q: Option[String], limit: Option[Int]): List[SearchHit] =
    SourceRepo
      .search(ds, q.map(_.trim).filter(_.nonEmpty), limit.getOrElse(cfg.limits.search), cfg.limits.snippetWords)
      .toList

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
    else Right(Importer.importDir(ds, Path.of(dir), cfg.limits.chunkChars))

  /** One resumable slice of the FTS chunk rebuild (plan §1.4); loop on `remaining` > 0. */
  def reindex(after: Option[String], limit: Option[Int]): ReindexResult =
    SourceRepo.reindexChunks(ds, cfg.limits.chunkChars, after.map(_.trim).filter(_.nonEmpty),
      limit.filter(_ > 0), cfg.limits.reindexChars)
