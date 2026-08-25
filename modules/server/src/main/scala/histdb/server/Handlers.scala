package histdb.server

import histdb.core.*
import histdb.db.{AggregateRepo, Importer, Migrate, SourceRepo}
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
    SourceRepo.search(ds, q.map(_.trim).filter(_.nonEmpty), limit.getOrElse(cfg.limits.search)).toList

  def getSource(citekey: String): Either[ApiError, SourceWithFindings] =
    SourceRepo.get(ds, citekey)
      .map((s, fs) => SourceWithFindings(s, fs.toList))
      .toRight(ApiError(s"No source '$citekey'"))

  def findings(q: Api.FindingsQuery): List[GeoHit] =
    SourceRepo.geoWhen(ds, q.region, q.bbox, q.from, q.to, q.limit.getOrElse(cfg.limits.findings)).toList

  /** `/api/aggregate` — the timeline strip (§1.7) and, from wave 2, the "by time" tree (§1.6).
    * The window defaults to the configured UI span; SQL only fetches periods, `Aggregate` (pure)
    * does the bucketing. */
  def aggregate(q: Api.AggregateQuery): Either[ApiError, TimeHistogram] =
    def norm(o: Option[String], dflt: String) = o.map(_.trim.toLowerCase).filter(_.nonEmpty).getOrElse(dflt)
    val by   = norm(q.by, "time")
    val unit = norm(q.unit, "sources")
    if by != "time" then Left(ApiError(s"unsupported by='$by' — only by=time is available (the region tree is plan §1.6)"))
    else if unit != "sources" && unit != "findings" then Left(ApiError(s"unsupported unit='$unit' — use sources or findings"))
    else
      val t        = cfg.ui.time
      val a        = q.from.getOrElse(t.defaultFrom)
      val b        = q.to.getOrElse(t.defaultTo)
      val (from, to) = if a <= b then (a, b) else (b, a)
      val rows     = AggregateRepo.periods(ds, unit, q.region, q.bbox, Some(from), Some(to)).toList
      val size     = Aggregate.bucketSize(to - from)
      Right(TimeHistogram(from, to, size, Aggregate.histogram(rows, from, to, size)))

  def neighbors(citekey: String): List[NeighborRow] = SourceRepo.neighbors(ds, citekey).toList

  def seed(req: SeedRequest): Either[ApiError, SeedResult] =
    val dir = Option(req.dir).map(_.trim).filter(_.nonEmpty).getOrElse(cfg.corpus.dir)
    if dir.isEmpty then Left(ApiError("no directory given and histdb.corpus.dir is not configured"))
    else if !Files.isDirectory(Path.of(dir)) then Left(ApiError(s"not a directory on the server: $dir"))
    else Right(Importer.importDir(ds, Path.of(dir)))
