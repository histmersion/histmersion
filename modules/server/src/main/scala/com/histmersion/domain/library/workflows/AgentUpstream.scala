package com.histmersion
package domain.library.workflows
import com.histmersion.util.problem.{Legacy, Problem, Problems}

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
import com.histmersion.api.library.AcquireApi
import com.histmersion.api.library.Candidates
import com.histmersion.domain.library.Doi
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.search.Search
import com.histmersion.domain.automation.Goal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Api

/** The acquisition operations an agent run uses — OpenAlex/Crossref metadata, the deposited reference list, the
  * retraction/impact assessment, the citation graph forward — as a small seam over [[AcquisitionControllers]] (built
  * after [[com.histmersion.server.ServerWiring]], so it is attached to the agents once it exists), and so a test can
  * answer from fixtures instead of the network.
  */
trait AgentUpstream:
  /** Upstream metadata of a DOI. */
  def meta(doi: Doi): Either[Problems, WorkRef]

  /** `refs --apply`: the deposited reference list, written as `references` edges (`apply = false`, a dry run: read,
    * nothing written).
    */
  def refs(
      citekey: Citekey,
      apply: AgentUpstream.RefsApply = AgentUpstream.RefsApply(true)
  ): Either[Problems, RefsResult]

  /** `assess --apply`: the retraction/impact verdict; a retraction overwrites the status (`apply = false`, a dry run:
    * the verdict only — neither applied nor recorded).
    */
  def assess(
      citekey: Citekey,
      apply: AgentUpstream.AssessApply = AgentUpstream.AssessApply(true)
  ): Either[Problems, Assessment]

  /** The papers citing a source, read only (never `apply`). */
  def followups(
      id: AgentUpstream.FollowupsId,
      since: Option[Year],
      limit: Option[FollowupsInput.Limit]
  ): Either[Problems, FollowupsResult]

  /** An OpenAlex search, each hit marked `inDb` (the discover path). */
  def discover(query: Search.Query, limit: AgentUpstream.Limit): Either[Problems, DiscoverResult]

  /** A watcher's auto-ingest of one DOI: the LEGAL open-access chain only (never an unauthorised fetcher, whatever
    * `acquire.allow-unauthorised` says), refused when no legal file exists (nothing is stored then). Not connected by
    * default: a fixture that does not answer it ingests nothing.
    */
  def ingestLegal(doi: Doi): Either[Problems, Ingest.Report] = Left(
    Problem.Unclassified(Legacy.Text("the acquisition toolset is not connected"))
  )

  /** The acquisition list's re-check of one DOI: [[ingestLegal]], fetched and converted into the metadata-only source
    * `into` when there is one (its enrichment stays).
    */
  def ingestLegalInto(doi: Doi, into: Option[Citekey]): Either[Problems, Ingest.Report] = ingestLegal(doi)

  /** The legal chain's generation (a digest that changes with a fetcher or a key); "" when not connected. */
  def fetcherGeneration: AcquireRecheck.LastCheck.Generation = AcquireRecheck.LastCheck.Generation("")

  /** Whether the acquisition toolset is attached (Offline is not). */
  def connected: Boolean = true

  /** The deterministic new-source search of a goal (#3761): its watches read from their watermarks, the new candidates
    * stored (a dry run stores nothing). Not connected by default: a fixture that does not answer it keeps the sweep's
    * live search.
    */
  def sweepCandidates(
      goal: Goal.Slug,
      dryRun: AgentUpstream.SweepCandidatesDryRun
  ): Either[Problems, CandidateSweep] = Left(
    Problem.Unclassified(Legacy.Text("the candidate search is not connected"))
  )

  /** A goal's candidates in one state (`new`: the unseen), oldest first. */
  def candidates(
      goal: Goal.Slug,
      state: Candidates.State.Wire,
      limit: AgentUpstream.Limit
  ): Either[Problems, List[Source.Candidate]] = Left(
    Problem.Unclassified(Legacy.Text("the candidate search is not connected"))
  )

  /** Record a decision on candidates by id. */
  def markCandidates(
      ids: List[Source.Candidate.Id],
      state: Candidates.State.Wire
  ): Either[Problems, RowCount] = Left(
    Problem.Unclassified(Legacy.Text("the candidate search is not connected"))
  )

object AgentUpstream:
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]

  type RefsApply = RefsApply.T
  object RefsApply extends Newtype[Boolean, Pure]
  type AssessApply = AssessApply.T
  object AssessApply extends Newtype[Boolean, Pure]
  type FollowupsId = FollowupsId.T
  object FollowupsId extends Newtype[String, Pure]
  type SweepCandidatesDryRun = SweepCandidatesDryRun.T
  object SweepCandidatesDryRun extends Newtype[Boolean, Pure]

  private def problem(e: Api.Error): Problems = e.problems

  /** Not connected (a test, or a server built without the acquisition toolset): every call says so, and the steps that
    * need it record that they were skipped.
    */
  object Offline extends AgentUpstream:
    private val off = Left(Problem.Unclassified(Legacy.Text("the acquisition toolset is not connected")))
    def meta(doi: Doi) = off
    def refs(citekey: Citekey, apply: RefsApply) = off
    def assess(citekey: Citekey, apply: AssessApply) = off
    def followups(id: FollowupsId, since: Option[Year], limit: Option[FollowupsInput.Limit]) = off
    def discover(query: Search.Query, limit: Limit) = off
    override def connected = false

  /** The server's own acquisition handlers — the same calls `histmersion refs/assess/followups` make. */
  def of(a: AcquisitionControllers): AgentUpstream = new AgentUpstream:
    def meta(doi: Doi) = a.meta(AcquireApi.Identifier(doi.value)).left.map(problem)
    def refs(citekey: Citekey, apply: RefsApply) =
      a.refs(RefsRequest(RefsRequest.Id(citekey.value), apply = RefsRequest.Apply(apply.value))).left.map(problem)
    def assess(citekey: Citekey, apply: AssessApply) =
      (if apply.value then a.assess(AssessRequest(AssessRequest.Id(citekey.value), apply = AssessRequest.Apply(true)))
       else a.assessReadOnly(AssessRequest.Id(citekey.value))).left.map(problem)
    def followups(id: FollowupsId, since: Option[Year], limit: Option[FollowupsInput.Limit]) =
      a.followups(
        FollowupsRequest(
          FollowupsRequest.Id(id.value),
          since.map(y => FollowupsRequest.Since(y.value)),
          limit.map(l => FollowupsRequest.Limit(l.value)),
          apply = FollowupsRequest.Apply(false)
        )
      ).left
        .map(problem)
    def discover(query: Search.Query, limit: Limit) =
      a.discover(AcquireApi.Query(query.value), None, None, Some(AcquireApi.Limit(limit.value)), notInDb = false)
        .left
        .map(problem)
    override def ingestLegal(doi: Doi) =
      a.ingestLegal(Ingest.Request(Ingest.Request.Id(doi.value))).left.map(problem)
    override def ingestLegalInto(doi: Doi, into: Option[Citekey]) =
      a.ingestLegal(
        Ingest
          .Request(Ingest.Request.Id(doi.value), citekey = into.map(c => com.histmersion.api.library.Citekey(c.value)))
      ).left
        .map(problem)
    override def fetcherGeneration = a.fetcherGeneration
    override def sweepCandidates(goal: Goal.Slug, dryRun: SweepCandidatesDryRun) =
      a.sweepCandidates(
        CandidateSweep.Request(CandidateSweep.Request.Goal(goal.value), CandidateSweep.Request.DryRun(dryRun.value))
      ).left
        .map(problem)
    override def candidates(goal: Goal.Slug, state: Candidates.State.Wire, limit: Limit) =
      a.candidates(
        Some(com.histmersion.api.automation.Goal.Slug(goal.value)),
        Some(state),
        Some(AcquireApi.Limit(limit.value)),
        None
      ).map(_.candidates)
        .left
        .map(problem)
    override def markCandidates(ids: List[Source.Candidate.Id], state: Candidates.State.Wire) =
      a.markCandidates(CandidateMark(ids, Source.Candidate.State(state.value)))
        .map(m => RowCount(m.updated.value))
        .left
        .map(problem)
