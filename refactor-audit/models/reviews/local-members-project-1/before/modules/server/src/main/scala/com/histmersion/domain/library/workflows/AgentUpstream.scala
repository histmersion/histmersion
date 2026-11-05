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
  def meta(doi: com.histmersion.domain.library.Doi): Either[com.histmersion.domain.library.workflows.AgentUpstream.MetaResultPart1, WorkRef]

  /** `refs --apply`: the deposited reference list, written as `references` edges (`apply = false`, a dry run: read,
    * nothing written).
    */
  def refs(citekey: com.histmersion.domain.library.Citekey, apply: com.histmersion.domain.library.workflows.AgentUpstream.RefsApply = true): Either[com.histmersion.domain.library.workflows.AgentUpstream.RefsResultPart1, RefsResult]

  /** `assess --apply`: the retraction/impact verdict; a retraction overwrites the status (`apply = false`, a dry run:
    * the verdict only — neither applied nor recorded).
    */
  def assess(citekey: com.histmersion.domain.library.Citekey, apply: com.histmersion.domain.library.workflows.AgentUpstream.AssessApply = true): Either[com.histmersion.domain.library.workflows.AgentUpstream.AssessResultPart1, Assessment]

  /** The papers citing a source, read only (never `apply`). */
  def followups(id: com.histmersion.domain.library.workflows.AgentUpstream.FollowupsId, since: Option[com.histmersion.domain.library.workflows.AgentUpstream.FollowupsSince], limit: Option[com.histmersion.domain.library.workflows.AgentUpstream.FollowupsLimit]): Either[com.histmersion.domain.library.workflows.AgentUpstream.FollowupsResultPart1, FollowupsResult]

  /** An OpenAlex search, each hit marked `inDb` (the discover path). */
  def discover(query: com.histmersion.domain.library.workflows.AgentUpstream.DiscoverQuery, limit: com.histmersion.domain.library.workflows.AgentUpstream.DiscoverLimit): Either[com.histmersion.domain.library.workflows.AgentUpstream.DiscoverResultPart1, DiscoverResult]

  /** A watcher's auto-ingest of one DOI: the LEGAL open-access chain only (never an unauthorised fetcher, whatever
    * `acquire.allow-unauthorised` says), refused when no legal file exists (nothing is stored then). Not connected by
    * default: a fixture that does not answer it ingests nothing.
    */
  def ingestLegal(doi: com.histmersion.domain.library.Doi): Either[com.histmersion.domain.library.workflows.AgentUpstream.IngestLegalResultPart1, Ingest.Report] = Left("the acquisition toolset is not connected")

  /** The acquisition list's re-check of one DOI: [[ingestLegal]], fetched and converted into the metadata-only source
    * `into` when there is one (its enrichment stays).
    */
  def ingestLegalInto(doi: com.histmersion.domain.library.Doi, into: Option[com.histmersion.domain.library.workflows.AgentUpstream.IngestLegalIntoInto]): Either[com.histmersion.domain.library.workflows.AgentUpstream.IngestLegalIntoResultPart1, Ingest.Report] = ingestLegal(doi)

  /** The legal chain's generation (a digest that changes with a fetcher or a key); "" when not connected. */
  def fetcherGeneration: com.histmersion.domain.library.workflows.AgentUpstream.FetcherGenerationResult = ""

  /** Whether the acquisition toolset is attached (Offline is not). */
  def connected: com.histmersion.domain.library.workflows.AgentUpstream.ConnectedResult = true

  /** The deterministic new-source search of a goal (#3761): its watches read from their watermarks, the new candidates
    * stored (a dry run stores nothing). Not connected by default: a fixture that does not answer it keeps the sweep's
    * live search.
    */
  def sweepCandidates(goal: com.histmersion.domain.library.workflows.AgentUpstream.SweepCandidatesGoal, dryRun: com.histmersion.domain.library.workflows.AgentUpstream.SweepCandidatesDryRun): Either[com.histmersion.domain.library.workflows.AgentUpstream.SweepCandidatesResultPart1, CandidateSweep] = Left(
    "the candidate search is not connected"
  )

  /** A goal's candidates in one state (`new`: the unseen), oldest first. */
  def candidates(goal: com.histmersion.domain.library.workflows.AgentUpstream.CandidatesGoal, state: com.histmersion.domain.library.workflows.AgentUpstream.CandidatesState, limit: com.histmersion.domain.library.workflows.AgentUpstream.CandidatesLimit): Either[com.histmersion.domain.library.workflows.AgentUpstream.CandidatesResultPart1, List[Source.Candidate]] = Left(
    "the candidate search is not connected"
  )

  /** Record a decision on candidates by id. */
  def markCandidates(ids: List[com.histmersion.domain.library.workflows.AgentUpstream.MarkCandidatesIds], state: com.histmersion.domain.library.workflows.AgentUpstream.MarkCandidatesState): Either[com.histmersion.domain.library.workflows.AgentUpstream.MarkCandidatesResultPart1, com.histmersion.domain.library.workflows.AgentUpstream.MarkCandidatesResultPart2] = Left(
    "the candidate search is not connected"
  )

object AgentUpstream:

  /** Not connected (a test, or a server built without the acquisition toolset): every call says so, and the steps that
    * need it record that they were skipped.
    */
  type MetaResultPart1 = MetaResultPart1.T
  object MetaResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefsResultPart1 = RefsResultPart1.T
  object RefsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefsApply = RefsApply.T
  object RefsApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssessResultPart1 = AssessResultPart1.T
  object AssessResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssessApply = AssessApply.T
  object AssessApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FollowupsResultPart1 = FollowupsResultPart1.T
  object FollowupsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FollowupsId = FollowupsId.T
  object FollowupsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FollowupsSince = FollowupsSince.T
  object FollowupsSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FollowupsLimit = FollowupsLimit.T
  object FollowupsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DiscoverResultPart1 = DiscoverResultPart1.T
  object DiscoverResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DiscoverQuery = DiscoverQuery.T
  object DiscoverQuery extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DiscoverLimit = DiscoverLimit.T
  object DiscoverLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestLegalResultPart1 = IngestLegalResultPart1.T
  object IngestLegalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestLegalIntoResultPart1 = IngestLegalIntoResultPart1.T
  object IngestLegalIntoResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestLegalIntoInto = IngestLegalIntoInto.T
  object IngestLegalIntoInto extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetcherGenerationResult = FetcherGenerationResult.T
  object FetcherGenerationResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConnectedResult = ConnectedResult.T
  object ConnectedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SweepCandidatesResultPart1 = SweepCandidatesResultPart1.T
  object SweepCandidatesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SweepCandidatesGoal = SweepCandidatesGoal.T
  object SweepCandidatesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SweepCandidatesDryRun = SweepCandidatesDryRun.T
  object SweepCandidatesDryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesResultPart1 = CandidatesResultPart1.T
  object CandidatesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesGoal = CandidatesGoal.T
  object CandidatesGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesState = CandidatesState.T
  object CandidatesState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesLimit = CandidatesLimit.T
  object CandidatesLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkCandidatesResultPart1 = MarkCandidatesResultPart1.T
  object MarkCandidatesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkCandidatesResultPart2 = MarkCandidatesResultPart2.T
  object MarkCandidatesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkCandidatesIds = MarkCandidatesIds.T
  object MarkCandidatesIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkCandidatesState = MarkCandidatesState.T
  object MarkCandidatesState extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Offline extends AgentUpstream:
    type RefsApply = RefsApply.T
    object RefsApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AssessApply = AssessApply.T
    object AssessApply extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FollowupsId = FollowupsId.T
    object FollowupsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FollowupsSince = FollowupsSince.T
    object FollowupsSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FollowupsLimit = FollowupsLimit.T
    object FollowupsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DiscoverQuery = DiscoverQuery.T
    object DiscoverQuery extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DiscoverLimit = DiscoverLimit.T
    object DiscoverLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

    private val off = Left("the acquisition toolset is not connected")
    def meta(doi: com.histmersion.domain.library.Doi) = off
    def refs(citekey: com.histmersion.domain.library.Citekey, apply: com.histmersion.domain.library.workflows.AgentUpstream.Offline.RefsApply) = off
    def assess(citekey: com.histmersion.domain.library.Citekey, apply: com.histmersion.domain.library.workflows.AgentUpstream.Offline.AssessApply) = off
    def followups(id: com.histmersion.domain.library.workflows.AgentUpstream.Offline.FollowupsId, since: Option[com.histmersion.domain.library.workflows.AgentUpstream.Offline.FollowupsSince], limit: Option[com.histmersion.domain.library.workflows.AgentUpstream.Offline.FollowupsLimit]) = off
    def discover(query: com.histmersion.domain.library.workflows.AgentUpstream.Offline.DiscoverQuery, limit: com.histmersion.domain.library.workflows.AgentUpstream.Offline.DiscoverLimit) = off
    override def connected = false

  /** The server's own acquisition handlers — the same calls `histmersion refs/assess/followups` make. */
  def of(a: AcquisitionControllers): AgentUpstream = new AgentUpstream:
    def meta(doi: String) = a.meta(doi).left.map(_.message)
    def refs(citekey: String, apply: Boolean) = a.refs(RefsRequest(citekey, apply = apply)).left.map(_.message)
    def assess(citekey: String, apply: Boolean) =
      (if apply then a.assess(AssessRequest(citekey, apply = true)) else a.assessReadOnly(citekey)).left.map(_.message)
    def followups(id: String, since: Option[Int], limit: Option[Int]) =
      a.followups(FollowupsRequest(id, since, limit, apply = false)).left.map(_.message)
    def discover(query: String, limit: Int) =
      a.discover(query, None, None, Some(limit), notInDb = false).left.map(_.message)
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
