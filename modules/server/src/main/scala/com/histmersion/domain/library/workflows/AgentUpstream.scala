package com.histmersion.domain.library.workflows

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.library.acquisition.AcquisitionControllers
import com.histmersion.api.library.{
  Assessment,
  AssessRequest,
  CandidateMark,
  CandidateSweep,
  DiscoverResult,
  FollowupsRequest,
  FollowupsResult,
  RefsRequest,
  RefsResult,
  WorkRef
}
import com.histmersion.api.library.Ingest
import com.histmersion.api.library.Source

/** The acquisition operations an agent run uses — OpenAlex/Crossref metadata, the deposited reference list, the
  * retraction/impact assessment, the citation graph forward — as a small seam over [[AcquisitionControllers]] (built
  * after [[com.histmersion.server.ServerWiring]], so it is attached to the agents once it exists), and so a test can
  * answer from fixtures instead of the network.
  */
trait AgentUpstream:
  /** Upstream metadata of a DOI. */
  def meta(doi: com.histmersion.domain.library.Doi): Either[com.histmersion.domain.library.workflows.AgentUpstream.Problem, WorkRef]

  /** `refs --apply`: the deposited reference list, written as `references` edges (`apply = false`, a dry run: read,
    * nothing written).
    */
  def refs(citekey: com.histmersion.domain.library.Citekey, apply: com.histmersion.domain.library.workflows.AgentUpstream.RefsApply = com.histmersion.domain.library.workflows.AgentUpstream.RefsApply(true)): Either[com.histmersion.domain.library.workflows.AgentUpstream.Problem, RefsResult]

  /** `assess --apply`: the retraction/impact verdict; a retraction overwrites the status (`apply = false`, a dry run:
    * the verdict only — neither applied nor recorded).
    */
  def assess(citekey: com.histmersion.domain.library.Citekey, apply: com.histmersion.domain.library.workflows.AgentUpstream.AssessApply = com.histmersion.domain.library.workflows.AgentUpstream.AssessApply(true)): Either[com.histmersion.domain.library.workflows.AgentUpstream.Problem, Assessment]

  /** The papers citing a source, read only (never `apply`). */
  def followups(id: com.histmersion.domain.library.workflows.AgentUpstream.FollowupsId, since: Option[com.histmersion.Year], limit: Option[com.histmersion.domain.library.workflows.FollowupsInput.Limit]): Either[com.histmersion.domain.library.workflows.AgentUpstream.Problem, FollowupsResult]

  /** An OpenAlex search, each hit marked `inDb` (the discover path). */
  def discover(query: com.histmersion.domain.library.search.Search.Query, limit: com.histmersion.domain.library.workflows.AgentUpstream.Limit): Either[com.histmersion.domain.library.workflows.AgentUpstream.Problem, DiscoverResult]

  /** A watcher's auto-ingest of one DOI: the LEGAL open-access chain only (never an unauthorised fetcher, whatever
    * `acquire.allow-unauthorised` says), refused when no legal file exists (nothing is stored then). Not connected by
    * default: a fixture that does not answer it ingests nothing.
    */
  def ingestLegal(doi: com.histmersion.domain.library.Doi): Either[com.histmersion.domain.library.workflows.AgentUpstream.Problem, Ingest.Report] = Left(com.histmersion.domain.library.workflows.AgentUpstream.Problem("the acquisition toolset is not connected"))

  /** The acquisition list's re-check of one DOI: [[ingestLegal]], fetched and converted into the metadata-only source
    * `into` when there is one (its enrichment stays).
    */
  def ingestLegalInto(doi: com.histmersion.domain.library.Doi, into: Option[com.histmersion.domain.library.workflows.AgentUpstream.Into]): Either[com.histmersion.domain.library.workflows.AgentUpstream.Problem, Ingest.Report] = ingestLegal(doi)

  /** The legal chain's generation (a digest that changes with a fetcher or a key); "" when not connected. */
  def fetcherGeneration: com.histmersion.domain.library.acquisition.AcquireClients.Generation = com.histmersion.domain.library.acquisition.AcquireClients.Generation("")

  /** Whether the acquisition toolset is attached (Offline is not). */
  def connected: Boolean = true

  /** The deterministic new-source search of a goal (#3761): its watches read from their watermarks, the new candidates
    * stored (a dry run stores nothing). Not connected by default: a fixture that does not answer it keeps the sweep's
    * live search.
    */
  def sweepCandidates(goal: com.histmersion.domain.automation.Goal.Slug, dryRun: com.histmersion.domain.library.workflows.AgentUpstream.SweepCandidatesDryRun): Either[com.histmersion.domain.library.workflows.AgentUpstream.Problem, CandidateSweep] = Left(
    com.histmersion.domain.library.workflows.AgentUpstream.Problem("the candidate search is not connected")
  )

  /** A goal's candidates in one state (`new`: the unseen), oldest first. */
  def candidates(goal: com.histmersion.domain.automation.Goal.Slug, state: com.histmersion.domain.library.acquisition.CandidateService.State, limit: com.histmersion.domain.library.workflows.AgentUpstream.Limit): Either[com.histmersion.domain.library.workflows.AgentUpstream.Problem, List[Source.Candidate]] = Left(
    com.histmersion.domain.library.workflows.AgentUpstream.Problem("the candidate search is not connected")
  )

  /** Record a decision on candidates by id. */
  def markCandidates(ids: List[com.histmersion.domain.library.workflows.AgentUpstream.MarkCandidatesIds], state: com.histmersion.domain.library.acquisition.CandidateService.State): Either[com.histmersion.domain.library.workflows.AgentUpstream.Problem, com.histmersion.RowCount] = Left(
    com.histmersion.domain.library.workflows.AgentUpstream.Problem("the candidate search is not connected")
  )

object AgentUpstream:
  type Into = Into.T
  object Into extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Not connected (a test, or a server built without the acquisition toolset): every call says so, and the steps that
    * need it record that they were skipped.
    */
  type OfRefsApply = OfRefsApply.T
  object OfRefsApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfAssessApply = OfAssessApply.T
  object OfAssessApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfFollowupsId = OfFollowupsId.T
  object OfFollowupsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type RefsApply = RefsApply.T
  object RefsApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssessApply = AssessApply.T
  object AssessApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FollowupsId = FollowupsId.T
  object FollowupsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SweepCandidatesDryRun = SweepCandidatesDryRun.T
  object SweepCandidatesDryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkCandidatesIds = MarkCandidatesIds.T
  object MarkCandidatesIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Offline extends AgentUpstream:
    type RefsApply = RefsApply.T
    object RefsApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AssessApply = AssessApply.T
    object AssessApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FollowupsId = FollowupsId.T
    object FollowupsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    private val off = Left("the acquisition toolset is not connected")
    def meta(doi: com.histmersion.domain.library.Doi) = off
    def refs(citekey: com.histmersion.domain.library.Citekey, apply: com.histmersion.domain.library.workflows.AgentUpstream.Offline.RefsApply) = off
    def assess(citekey: com.histmersion.domain.library.Citekey, apply: com.histmersion.domain.library.workflows.AgentUpstream.Offline.AssessApply) = off
    def followups(id: com.histmersion.domain.library.workflows.AgentUpstream.Offline.FollowupsId, since: Option[com.histmersion.Year], limit: Option[com.histmersion.domain.library.workflows.FollowupsInput.Limit]) = off
    def discover(query: com.histmersion.domain.library.search.Search.Query, limit: com.histmersion.domain.library.workflows.AgentUpstream.Limit) = off
    override def connected = false

  /** The server's own acquisition handlers — the same calls `histmersion refs/assess/followups` make. */
  def of(a: AcquisitionControllers): AgentUpstream = new AgentUpstream:
    def meta(doi: com.histmersion.domain.library.Doi) = a.meta(doi).left.map(_.message)
    def refs(citekey: com.histmersion.domain.library.Citekey, apply: com.histmersion.domain.library.workflows.AgentUpstream.OfRefsApply) = a.refs(RefsRequest(citekey, apply = apply)).left.map(_.message)
    def assess(citekey: com.histmersion.domain.library.Citekey, apply: com.histmersion.domain.library.workflows.AgentUpstream.OfAssessApply) =
      (if apply then a.assess(AssessRequest(citekey, apply = com.histmersion.api.library.AssessRequest.Apply(true))) else a.assessReadOnly(citekey)).left.map(_.message)
    def followups(id: com.histmersion.domain.library.workflows.AgentUpstream.OfFollowupsId, since: Option[com.histmersion.Year], limit: Option[com.histmersion.domain.library.workflows.FollowupsInput.Limit]) =
      a.followups(FollowupsRequest(id, since, limit, apply = com.histmersion.api.library.FollowupsRequest.Apply(false))).left.map(_.message)
    def discover(query: com.histmersion.domain.library.search.Search.Query, limit: com.histmersion.domain.library.workflows.AgentUpstream.Limit) =
      a.discover(query, None, None, Some(limit), notInDb = com.histmersion.domain.library.acquisition.AcquisitionControllers.(false)).left.map(_.message)
    override def ingestLegal(doi: String) = a.ingestLegal(Ingest.Request(doi)).left.map(_.message)
    override def ingestLegalInto(doi: String, into: Option[String]) =
      a.ingestLegal(Ingest.Request(doi, citekey = into)).left.map(_.message)
    override def fetcherGeneration = a.fetcherGeneration
    override def sweepCandidates(goal: String, dryRun: Boolean) =
      a.sweepCandidates(CandidateSweep.Request(goal, dryRun)).left.map(_.message)
    override def candidates(goal: String, state: String, limit: Int) =
      a.candidates(Some(goal), Some(state), Some(limit), None).map(_.candidates).left.map(_.message)
    override def markCandidates(ids: List[Long], state: String) =
      a.markCandidates(CandidateMark(ids, state)).map(_.updated).left.map(_.message)
