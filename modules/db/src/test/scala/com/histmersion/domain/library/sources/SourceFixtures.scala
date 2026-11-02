package com.histmersion.domain.library.sources

import com.histmersion.api.*
import com.histmersion.domain.library.sources.referrers.SourceReferrers
import com.histmersion.util.ocr.Chunk

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The db suites' reads and writes of the library's sources on a database of their own, each in a transaction of its
  * own as the service runs them. A rename follows into every domain's records through the same referrers the server
  * registers.
  */
object SourceFixtures:

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
