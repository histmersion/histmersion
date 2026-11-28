package com.histmersion
package domain.library.workflows

import com.histmersion.domain.acquirerecheck.LastCheck
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
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The acquisition operations an agent run uses — OpenAlex/Crossref metadata, the deposited reference list, the
  * retraction/impact assessment, the citation graph forward — as a small seam over [[AcquisitionControllers]] (built
  * after [[com.histmersion.server.ServerWiring]], so it is attached to the agents once it exists), and so a test can
  * answer from fixtures instead of the network.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait AgentUpstream:
  /** Upstream metadata of a DOI. */
  def meta(doi: Doi): Either[Problems, WorkRef]

  /** `refs --apply`: the deposited reference list, written as `references` edges (`apply = false`, a dry run: read,
    * nothing written).
    */
  def refs(
      citekey: Citekey,
      apply: AgentUpstream.RefsApply = AgentUpstream.RefsApply(true)
  )(using Creator.New): Either[Problems, RefsResult]

  /** `assess --apply`: the retraction/impact verdict; a retraction overwrites the status (`apply = false`, a dry run:
    * the verdict only — neither applied nor recorded).
    */
  def assess(
      citekey: Citekey,
      apply: AgentUpstream.AssessApply = AgentUpstream.AssessApply(true)
  )(using Creator.New): Either[Problems, Assessment]

  /** The papers citing a source, read only (never `apply`). */
  def followups(
      id: AgentUpstream.FollowupsId,
      since: Option[Year],
      limit: Option[FollowupsInput.Limit]
  )(using Creator.New): Either[Problems, FollowupsResult]

  /** An OpenAlex search, each hit marked `inDb` (the discover path). */
  def discover(query: Search.Query, limit: AgentUpstream.Limit): Either[Problems, DiscoverResult]

  /** A watcher's auto-ingest of one DOI: the LEGAL open-access chain only (never an unauthorised fetcher, whatever
    * `acquire.allow-unauthorised` says), refused when no legal file exists (nothing is stored then). Not connected by
    * default: a fixture that does not answer it ingests nothing.
    */
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def ingestLegal(doi: Doi, by: domain.Creator.New): Either[Problems, Ingest.Report] = Left(
    Problem.Unclassified(Legacy.Text("the acquisition toolset is not connected"))
  )

  /** The acquisition list's re-check of one DOI: [[ingestLegal]], fetched and converted into the metadata-only source
    * `into` when there is one (its enrichment stays).
    */
  def ingestLegalInto(doi: Doi, into: Option[Citekey], by: domain.Creator.New): Either[Problems, Ingest.Report] =
    ingestLegal(doi, by)

  /** The legal chain's generation (a digest that changes with a fetcher or a key); "" when not connected. */
  def fetcherGeneration: LastCheck.Generation = LastCheck.Generation("")

  /** Whether the acquisition toolset is attached (Offline is not). */
  def connected: Boolean = true

  /** The deterministic new-source search of a goal (#3761): its watches read from their watermarks, the new candidates
    * stored (a dry run stores nothing). Not connected by default: a fixture that does not answer it keeps the sweep's
    * live search.
    */
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def sweepCandidates(
      goal: Goal.Slug,
      dryRun: AgentUpstream.SweepCandidatesDryRun
  )(using Creator.New): Either[Problems, CandidateSweep] = Left(
    Problem.Unclassified(Legacy.Text("the candidate search is not connected"))
  )

  /** A goal's candidates in one state (`new`: the unseen), oldest first. */
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def candidates(
      goal: Goal.Slug,
      state: Candidates.State.Wire,
      limit: AgentUpstream.Limit
  ): Either[Problems, List[Source.Candidate]] = Left(
    Problem.Unclassified(Legacy.Text("the candidate search is not connected"))
  )

  /** Record a decision on candidates by id. */
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
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
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    private val off = Left(Problem.Unclassified(Legacy.Text("the acquisition toolset is not connected")))
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def meta(doi: Doi) = off
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def refs(citekey: Citekey, apply: RefsApply)(using Creator.New) = off
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def assess(citekey: Citekey, apply: AssessApply)(using Creator.New) = off
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def followups(id: FollowupsId, since: Option[Year], limit: Option[FollowupsInput.Limit])(using Creator.New) = off
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    def discover(query: Search.Query, limit: Limit) = off
    override def connected = false

  /** The server's own acquisition handlers — the same calls `histmersion refs/assess/followups` make. */
  def of(a: AcquisitionControllers): AgentUpstream = new AgentUpstream:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def meta(doi: Doi) = a.meta(AcquireApi.Identifier(doi.value)).left.map(problem)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def refs(citekey: Citekey, apply: RefsApply)(using Creator.New) =
      a.refs(
        RefsRequest(
          RefsRequest.Id(citekey.value),
          apply = (if apply.value then RefsRequest.Apply.Apply else RefsRequest.Apply.DryRun)
        )
      ).left
        .map(problem)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def assess(citekey: Citekey, apply: AssessApply)(using Creator.New) =
      (if apply.value then a.assess(AssessRequest(AssessRequest.Id(citekey.value), apply = AssessRequest.Apply.Apply))
       else a.assessReadOnly(AssessRequest.Id(citekey.value))).left.map(problem)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def followups(id: FollowupsId, since: Option[Year], limit: Option[FollowupsInput.Limit])(using Creator.New) =
      a.followups(
        FollowupsRequest(
          FollowupsRequest.Id(id.value),
          since.map(y => FollowupsRequest.Since(y.value)),
          limit.map(l => FollowupsRequest.Limit(l.value)),
          apply = FollowupsRequest.Apply.DryRun
        )
      ).left
        .map(problem)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def discover(query: Search.Query, limit: Limit) =
      a.discover(AcquireApi.Query(query.value), None, None, Some(AcquireApi.Limit(limit.value)), notInDb = false)
        .left
        .map(problem)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def ingestLegal(doi: Doi, by: domain.Creator.New) =
      a.ingestLegal(Ingest.Request(Ingest.Request.Id(doi.value)), by).left.map(problem)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    override def ingestLegalInto(doi: Doi, into: Option[Citekey], by: domain.Creator.New) =
      a.ingestLegal(
        Ingest
          .Request(Ingest.Request.Id(doi.value), citekey = into.map(c => com.histmersion.api.library.Citekey(c.value))),
        by
      ).left
        .map(problem)
    override def fetcherGeneration = a.fetcherGeneration
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def sweepCandidates(goal: Goal.Slug, dryRun: SweepCandidatesDryRun)(using Creator.New) =
      a.sweepCandidates(
        CandidateSweep.Request(
          CandidateSweep.Request.Goal(goal.value),
          (if dryRun.value then CandidateSweep.Request.DryRun.DryRun else CandidateSweep.Request.DryRun.Apply)
        )
      ).left
        .map(problem)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    override def candidates(goal: Goal.Slug, state: Candidates.State.Wire, limit: Limit) =
      a.candidates(
        Some(com.histmersion.api.automation.Goal.Slug(goal.value)),
        Some(state),
        Some(AcquireApi.Limit(limit.value)),
        None
      ).map(_.candidates)
        .left
        .map(problem)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def markCandidates(ids: List[Source.Candidate.Id], state: Candidates.State.Wire) =
      a.markCandidates(CandidateMark(ids, Source.Candidate.State(state.value)))
        .map(m => RowCount(m.updated.value))
        .left
        .map(problem)
