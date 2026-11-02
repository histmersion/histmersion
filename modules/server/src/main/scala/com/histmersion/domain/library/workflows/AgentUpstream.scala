package com.histmersion.domain.library.workflows

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.library.acquisition.AcquisitionControllers

/** The acquisition operations an agent run uses — OpenAlex/Crossref metadata, the deposited reference list, the
  * retraction/impact assessment, the citation graph forward — as a small seam over [[AcquisitionControllers]] (built
  * after [[com.histmersion.server.Handlers]], so it is attached to the agents once it exists), and so a test can answer
  * from fixtures instead of the network.
  */
trait AgentUpstream:
  /** Upstream metadata of a DOI. */
  def meta(doi: String): Either[String, WorkRef]

  /** `refs --apply`: the deposited reference list, written as `references` edges (`apply = false`, a dry run: read,
    * nothing written).
    */
  def refs(citekey: String, apply: Boolean = true): Either[String, RefsResult]

  /** `assess --apply`: the retraction/impact verdict; a retraction overwrites the status (`apply = false`, a dry run:
    * the verdict only — neither applied nor recorded).
    */
  def assess(citekey: String, apply: Boolean = true): Either[String, Assessment]

  /** The papers citing a source, read only (never `apply`). */
  def followups(id: String, since: Option[Int], limit: Option[Int]): Either[String, FollowupsResult]

  /** An OpenAlex search, each hit marked `inDb` (the discover path). */
  def discover(query: String, limit: Int): Either[String, DiscoverResult]

  /** A watcher's auto-ingest of one DOI: the LEGAL open-access chain only (never an unauthorised fetcher, whatever
    * `acquire.allow-unauthorised` says), refused when no legal file exists (nothing is stored then). Not connected by
    * default: a fixture that does not answer it ingests nothing.
    */
  def ingestLegal(doi: String): Either[String, IngestReport] = Left("the acquisition toolset is not connected")

  /** The acquisition list's re-check of one DOI: [[ingestLegal]], fetched and converted into the metadata-only source
    * `into` when there is one (its enrichment stays).
    */
  def ingestLegalInto(doi: String, into: Option[String]): Either[String, IngestReport] = ingestLegal(doi)

  /** The legal chain's generation (a digest that changes with a fetcher or a key); "" when not connected. */
  def fetcherGeneration: String = ""

  /** Whether the acquisition toolset is attached (Offline is not). */
  def connected: Boolean = true

  /** The deterministic new-source search of a goal (#3761): its watches read from their watermarks, the new candidates
    * stored (a dry run stores nothing). Not connected by default: a fixture that does not answer it keeps the sweep's
    * live search.
    */
  def sweepCandidates(goal: String, dryRun: Boolean): Either[String, CandidateSweep] = Left(
    "the candidate search is not connected"
  )

  /** A goal's candidates in one state (`new`: the unseen), oldest first. */
  def candidates(goal: String, state: String, limit: Int): Either[String, List[SourceCandidate]] = Left(
    "the candidate search is not connected"
  )

  /** Record a decision on candidates by id. */
  def markCandidates(ids: List[Long], state: String): Either[String, Int] = Left(
    "the candidate search is not connected"
  )

object AgentUpstream:

  /** Not connected (a test, or a server built without the acquisition toolset): every call says so, and the steps that
    * need it record that they were skipped.
    */
  object Offline extends AgentUpstream:
    private val off = Left("the acquisition toolset is not connected")
    def meta(doi: String) = off
    def refs(citekey: String, apply: Boolean) = off
    def assess(citekey: String, apply: Boolean) = off
    def followups(id: String, since: Option[Int], limit: Option[Int]) = off
    def discover(query: String, limit: Int) = off
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
    override def ingestLegal(doi: String) = a.ingestLegal(IngestRequest(doi)).left.map(_.message)
    override def ingestLegalInto(doi: String, into: Option[String]) =
      a.ingestLegal(IngestRequest(doi, citekey = into)).left.map(_.message)
    override def fetcherGeneration = a.fetcherGeneration
    override def sweepCandidates(goal: String, dryRun: Boolean) =
      a.sweepCandidates(CandidateSweepRequest(goal, dryRun)).left.map(_.message)
    override def candidates(goal: String, state: String, limit: Int) =
      a.candidates(Some(goal), Some(state), Some(limit), None).map(_.candidates).left.map(_.message)
    override def markCandidates(ids: List[Long], state: String) =
      a.markCandidates(CandidateMark(ids, state)).map(_.updated).left.map(_.message)
