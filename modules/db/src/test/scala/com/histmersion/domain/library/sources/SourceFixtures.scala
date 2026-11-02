package com.histmersion.domain.library.sources

import com.histmersion.api.*
import com.histmersion.domain.library.sources.referrers.SourceReferrers
import com.histmersion.util.ocr.Chunk

import com.augustnagro.magnum.*
import com.histmersion.domain.catalogue.places.FindingPlacement
import com.histmersion.util.database.Transactor
import javax.sql.DataSource

/** The db suites' reads and writes of the library's sources on a database of their own, each in a transaction of its
  * own as the service runs them. A rename follows into every domain's records through the same referrers the server
  * registers.
  */
object SourceFixtures:
  private def db(ds: DataSource): Transactor = Transactor(ds)

  def citekeyOfSha256(ds: DataSource, sha: String): Option[String] = db(ds).read(SourceRepo.citekeyOfSha256(sha))
  def citekeys(ds: DataSource): Vector[String] = db(ds).read(SourceRepo.citekeys())
  def load(ds: DataSource, citekey: String): Option[Source] = db(ds).read(SourceRepo.load(citekey))
  def resolveEdges(ds: DataSource): Int = db(ds).transact(SourceRepo.resolveEdges())
  def setSha256(ds: DataSource, citekey: String, sha: String): Unit =
    db(ds).transact(SourceRepo.setSha256(citekey, sha))
  def sha256Of(ds: DataSource, citekey: String): Option[String] = db(ds).read(SourceRepo.sha256Of(citekey))

  def put(
      ds: DataSource,
      s: Source,
      chunkChars: Int,
      toleranceKm: Double = FindingPlacement.DefaultToleranceKm
  ): String = db(ds).transact(SourceRepo.putIn(s, chunkChars, toleranceKm))

  def upsert(
      ds: DataSource,
      s: Source,
      chunkChars: Int,
      toleranceKm: Double = FindingPlacement.DefaultToleranceKm
  ): Unit =
    val _ = put(ds, s, chunkChars, toleranceKm)

  def patch(
      ds: DataSource,
      requested: String,
      p: SourcePatch,
      chunkChars: Int,
      toleranceKm: Double = FindingPlacement.DefaultToleranceKm
  ): Either[ApiError, String] = db(ds).transact(SourceRepo.patchIn(requested, p, chunkChars, toleranceKm))

  def reindexChunks(
      ds: DataSource,
      chunkChars: Int,
      after: Option[String] = None,
      maxSources: Option[Int] = None,
      maxChars: Long = Long.MaxValue
  ): ReindexResult = SourceRepairs(db(ds)).reindexChunks(chunkChars, after, maxSources, maxChars)

  /** The server's referrers, in its order (`Deps.sourceReferrers`). */
  val referrers: SourceReferrers = SourceReferrers(
    List(
      com.histmersion.domain.catalogue.evidence.AttestationSourceReferrer(),
      com.histmersion.domain.media.links.AssetLinkSourceReferrer(),
      com.histmersion.domain.research.articles.ArticleSourceReferrer(),
      com.histmersion.domain.catalogue.events.EventSourceReferrer(),
      com.histmersion.util.jobs.JobSourceReferrer(),
      com.histmersion.domain.catalogue.places.PlaceSourceReferrer(),
      com.histmersion.domain.automation.goals.GoalSourceReferrer(),
      com.histmersion.domain.automation.worklists.WorkListSourceReferrer()
    )
  )

  /** Edges stored as acquisition stores them (routed by their target); how many were new. */
  def addEdges(ds: DataSource, edges: List[Edge]): Int =
    if edges.isEmpty then 0 else transact(ds)(SourceRepo.insertEdges(edges))

  def get(ds: DataSource, citekey: String): Option[(SourceDetail, Vector[FindingRow])] =
    connect(ds)(SourceRepo.get(citekey))
  def rename(ds: DataSource, oldCk: String, newRaw: String): Either[ApiError, RenameCounts] =
    transact(ds)(SourceRepo.rename(oldCk, newRaw, referrers))
  def canonical(ds: DataSource, id: String): Option[String] = connect(ds)(SourceRepo.canonical(id))
  def aliases(ds: DataSource): List[SourceAlias] = connect(ds)(SourceRepo.aliases)
  def aliasOf(ds: DataSource, ck: String): Option[String] = connect(ds)(SourceRepo.aliasOf(ck))
  def bodyText(ds: DataSource, ck: String): String = connect(ds)(SourceRepo.bodyText(ck))
  def bodyChunks(ds: DataSource, ck: String): List[Chunk] = connect(ds)(SourceRepo.bodyChunks(ck))
  def markdown(ds: DataSource, ck: String): String = connect(ds)(SourceRepo.markdown(ck))
  def resolveAliases(ds: DataSource, targets: List[String]): Map[String, String] =
    connect(ds)(SourceRepo.resolveAliases(targets))
  def naming(ds: DataSource, name: String, limit: Int): List[String] = connect(ds)(SourceRepo.naming(name, limit))
  def plan(ds: DataSource, ck: String): Either[ApiError, SourceDeletePlan] =
    connect(ds)(SourceDeleteRepo.planIn(ck, referrers))
