package com.histmersion
package domain.library.sources

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
import com.histmersion.domain.library.Citekey
import com.histmersion.config.Limits
import com.histmersion.config.PlacesConfig
import com.histmersion.util.ocr.Chunker
import com.histmersion.domain.catalogue.evidence.AttestationSourceReferrer
import com.histmersion.domain.media.links.AssetLinkSourceReferrer
import com.histmersion.domain.research.articles.ArticleSourceReferrer
import com.histmersion.domain.catalogue.events.EventSourceReferrer
import com.histmersion.util.jobs.JobSourceReferrer
import com.histmersion.domain.catalogue.places.PlaceSourceReferrer
import com.histmersion.domain.automation.goals.GoalSourceReferrer
import com.histmersion.domain.automation.worklists.WorkListSourceReferrer
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** The db suites' reads and writes of the library's sources on a database of their own, each in a transaction of its
  * own as the service runs them. A rename follows into every domain's records through the same referrers the server
  * registers.
  */
object SourceFixtures:
  export SourceRepo.{Limit, Name}

  private def db(ds: DataSource): Transactor = Transactor(ds)

  def citekeyOfSha256(ds: DataSource, sha: Sha256): Option[Citekey] = db(ds).read(SourceRepo.citekeyOfSha256(sha))
  def citekeys(ds: DataSource): Vector[Citekey] = db(ds).read(SourceRepo.citekeys())
  def load(ds: DataSource, citekey: Citekey): Option[Source] = db(ds).read(SourceRepo.load(citekey))
  def resolveEdges(ds: DataSource): RowCount = db(ds).write(SourceRepo.resolveEdges())
  def setSha256(ds: DataSource, citekey: Citekey, sha: Sha256): Unit =
    db(ds).write(SourceRepo.setSha256(citekey, sha))
  def sha256Of(ds: DataSource, citekey: Citekey): Option[Sha256] = db(ds).read(SourceRepo.sha256Of(citekey))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def put(
      ds: DataSource,
      s: Source,
      chunkChars: Limits.ChunkChars,
      toleranceKm: PlacesConfig.PointToleranceKm =
        PlacesConfig.PointToleranceKm(FindingPlacement.DefaultToleranceKm.value)
  ): Source.UpdatedAt = db(ds).write(
    SourceRepo.putIn(
      s,
      Chunker.ChunkMaxChars(chunkChars.value),
      FindingPlacement.Implementation.ToleranceKm(toleranceKm.value)
    )
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def upsert(
      ds: DataSource,
      s: Source,
      chunkChars: Limits.ChunkChars,
      toleranceKm: PlacesConfig.PointToleranceKm =
        PlacesConfig.PointToleranceKm(FindingPlacement.DefaultToleranceKm.value)
  ): Unit =
    val _ = put(ds, s, chunkChars, toleranceKm)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def patch(
      ds: DataSource,
      requested: Citekey,
      p: Source.Patch,
      chunkChars: Limits.ChunkChars,
      toleranceKm: PlacesConfig.PointToleranceKm =
        PlacesConfig.PointToleranceKm(FindingPlacement.DefaultToleranceKm.value)
  ): Either[Api.Error, Source.UpdatedAt] = db(ds).write(
    SourceRepo.patchIn(
      requested,
      p,
      Chunker.ChunkMaxChars(chunkChars.value),
      FindingPlacement.Implementation.ToleranceKm(toleranceKm.value)
    )
  )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def reindexChunks(
      ds: DataSource,
      chunkChars: Limits.ChunkChars,
      after: Option[Citekey] = None,
      maxSources: Option[SourceRepairs.MaxSources] = None,
      maxChars: SourceRepairs.MaxChars = SourceRepairs.MaxChars(Long.MaxValue)
  ): ReindexResult =
    SourceRepairs(db(ds)).reindexChunks(Chunker.ChunkMaxChars(chunkChars.value), after, maxSources, maxChars)

  /** The server's referrers, in its order (`Deps.sourceReferrers`). */
  val referrers: SourceReferrers = SourceReferrers(
    List(
      AttestationSourceReferrer(),
      AssetLinkSourceReferrer(),
      ArticleSourceReferrer(),
      EventSourceReferrer(),
      JobSourceReferrer(),
      PlaceSourceReferrer(),
      GoalSourceReferrer(),
      WorkListSourceReferrer()
    )
  )

  /** Edges stored as acquisition stores them (routed by their target); how many were new. */
  def addEdges(ds: DataSource, edges: List[Edge]): RowCount =
    if edges.isEmpty then RowCount(0) else transact(ds)(SourceRepo.insertEdges(edges))

  def get(ds: DataSource, citekey: Citekey): Option[(Source.Detail, Vector[Finding.Row])] =
    connect(ds)(SourceRepo.get(citekey))
  def rename(ds: DataSource, oldCk: Citekey, newRaw: Citekeys.RenameToNewRaw): Either[Api.Error, Rename.Counts] =
    transact(ds)(SourceRepo.rename(oldCk, newRaw, referrers))
  def canonical(ds: DataSource, id: Citekey): Option[Citekey] = connect(ds)(SourceRepo.canonical(id))
  def aliases(ds: DataSource): List[Source.Alias] = connect(ds)(SourceRepo.aliases)
  def aliasOf(ds: DataSource, ck: Citekey): Option[Citekey] = connect(ds)(SourceRepo.aliasOf(ck))
  def bodyText(ds: DataSource, ck: Citekey): FullSourceRow.OcrMarkdown = connect(ds)(SourceRepo.bodyText(ck))
  def bodyChunks(ds: DataSource, ck: Citekey): List[Chunk] = connect(ds)(SourceRepo.bodyChunks(ck))
  def markdown(ds: DataSource, ck: Citekey): FullSourceRow.OcrMarkdown = connect(ds)(SourceRepo.markdown(ck))
  def resolveAliases(
      ds: DataSource,
      targets: List[WikiLinks.Occurrence.Target]
  ): Map[WikiLinks.Occurrence.Target, Citekey] =
    connect(ds)(SourceRepo.resolveAliases(targets))
  def naming(ds: DataSource, name: SourceRepo.Name, limit: SourceRepo.Limit): List[Citekey] =
    connect(ds)(SourceRepo.naming(name, limit))
  def plan(ds: DataSource, ck: Citekey): Either[Api.Error, SourceDeletePlan] =
    connect(ds)(SourceDeleteRepo.planIn(ck, referrers))
