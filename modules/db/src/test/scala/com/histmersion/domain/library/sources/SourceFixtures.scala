package com.histmersion.domain.library.sources

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.library.sources.referrers.SourceReferrers
import com.histmersion.util.ocr.Chunk

import com.augustnagro.magnum.*
import com.histmersion.domain.catalogue.places.FindingPlacement
import com.histmersion.util.database.Transactor
import javax.sql.DataSource
import com.histmersion.api.library.{Edge, Source}
import com.histmersion.api.maintenance.ReindexResult
import com.histmersion.api.Api
import com.histmersion.api.library.Finding
import com.histmersion.api.library.Rename
import com.histmersion.api.text.WikiLinks

/** The db suites' reads and writes of the library's sources on a database of their own, each in a transaction of its
  * own as the service runs them. A rename follows into every domain's records through the same referrers the server
  * registers.
  */
object SourceFixtures:
  export SourceRepo.{Name, Limit}

  private def db(ds: DataSource): Transactor = Transactor(ds)

  def citekeyOfSha256(ds: DataSource, sha: com.histmersion.Sha256): Option[com.histmersion.domain.library.Citekey] = db(ds).read(SourceRepo.citekeyOfSha256(sha))
  def citekeys(ds: DataSource): Vector[com.histmersion.domain.library.Citekey] = db(ds).read(SourceRepo.citekeys())
  def load(ds: DataSource, citekey: com.histmersion.domain.library.Citekey): Option[Source] = db(ds).read(SourceRepo.load(citekey))
  def resolveEdges(ds: DataSource): com.histmersion.RowCount = db(ds).transact(SourceRepo.resolveEdges())
  def setSha256(ds: DataSource, citekey: com.histmersion.domain.library.Citekey, sha: com.histmersion.Sha256): Unit =
    db(ds).transact(SourceRepo.setSha256(citekey, sha))
  def sha256Of(ds: DataSource, citekey: com.histmersion.domain.library.Citekey): Option[com.histmersion.Sha256] = db(ds).read(SourceRepo.sha256Of(citekey))

  def put(
      ds: DataSource,
      s: Source,
      chunkChars: com.histmersion.config.Limits.ChunkChars,
      toleranceKm: com.histmersion.config.PlacesConfig.PointToleranceKm = com.histmersion.config.PlacesConfig.PointToleranceKm(FindingPlacement.DefaultToleranceKm.value)
  ): Source.UpdatedAt = db(ds).transact(
    SourceRepo.putIn(s, com.histmersion.util.ocr.Chunker.ChunkMaxChars(chunkChars.value), FindingPlacement.Implementation.ToleranceKm(toleranceKm.value))
  )

  def upsert(
      ds: DataSource,
      s: Source,
      chunkChars: com.histmersion.config.Limits.ChunkChars,
      toleranceKm: com.histmersion.config.PlacesConfig.PointToleranceKm = com.histmersion.config.PlacesConfig.PointToleranceKm(FindingPlacement.DefaultToleranceKm.value)
  ): Unit =
    val _ = put(ds, s, chunkChars, toleranceKm)

  def patch(
      ds: DataSource,
      requested: com.histmersion.domain.library.Citekey,
      p: Source.Patch,
      chunkChars: com.histmersion.config.Limits.ChunkChars,
      toleranceKm: com.histmersion.config.PlacesConfig.PointToleranceKm = com.histmersion.config.PlacesConfig.PointToleranceKm(FindingPlacement.DefaultToleranceKm.value)
  ): Either[Api.Error, Source.UpdatedAt] = db(ds).transact(
    SourceRepo.patchIn(requested, p, com.histmersion.util.ocr.Chunker.ChunkMaxChars(chunkChars.value), FindingPlacement.Implementation.ToleranceKm(toleranceKm.value))
  )

  def reindexChunks(
      ds: DataSource,
      chunkChars: com.histmersion.config.Limits.ChunkChars,
      after: Option[com.histmersion.domain.library.Citekey] = None,
      maxSources: Option[SourceRepairs.MaxSources] = None,
      maxChars: SourceRepairs.MaxChars = SourceRepairs.MaxChars(Long.MaxValue)
  ): ReindexResult =
    SourceRepairs(db(ds)).reindexChunks(com.histmersion.util.ocr.Chunker.ChunkMaxChars(chunkChars.value), after, maxSources, maxChars)

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
  def addEdges(ds: DataSource, edges: List[Edge]): com.histmersion.RowCount =
    if edges.isEmpty then com.histmersion.RowCount(0) else transact(ds)(SourceRepo.insertEdges(edges))

  def get(ds: DataSource, citekey: com.histmersion.domain.library.Citekey): Option[(Source.Detail, Vector[Finding.Row])] =
    connect(ds)(SourceRepo.get(citekey))
  def rename(ds: DataSource, oldCk: com.histmersion.domain.library.Citekey, newRaw: Citekeys.RenameToNewRaw): Either[Api.Error, Rename.Counts] =
    transact(ds)(SourceRepo.rename(oldCk, newRaw, referrers))
  def canonical(ds: DataSource, id: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.Citekey] = connect(ds)(SourceRepo.canonical(id))
  def aliases(ds: DataSource): List[Source.Alias] = connect(ds)(SourceRepo.aliases)
  def aliasOf(ds: DataSource, ck: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.Citekey] = connect(ds)(SourceRepo.aliasOf(ck))
  def bodyText(ds: DataSource, ck: com.histmersion.domain.library.Citekey): com.histmersion.domain.library.sources.FullSourceRow.OcrMarkdown = connect(ds)(SourceRepo.bodyText(ck))
  def bodyChunks(ds: DataSource, ck: com.histmersion.domain.library.Citekey): List[Chunk] = connect(ds)(SourceRepo.bodyChunks(ck))
  def markdown(ds: DataSource, ck: com.histmersion.domain.library.Citekey): com.histmersion.domain.library.sources.FullSourceRow.OcrMarkdown = connect(ds)(SourceRepo.markdown(ck))
  def resolveAliases(ds: DataSource, targets: List[WikiLinks.Occurrence.Target]): Map[WikiLinks.Occurrence.Target, com.histmersion.domain.library.Citekey] =
    connect(ds)(SourceRepo.resolveAliases(targets))
  def naming(ds: DataSource, name: SourceRepo.Name, limit: SourceRepo.Limit): List[com.histmersion.domain.library.Citekey] = connect(ds)(SourceRepo.naming(name, limit))
  def plan(ds: DataSource, ck: com.histmersion.domain.library.Citekey): Either[Api.Error, SourceDeletePlan] =
    connect(ds)(SourceDeleteRepo.planIn(ck, referrers))
