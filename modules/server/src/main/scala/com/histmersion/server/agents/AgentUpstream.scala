package com.histmersion.server.agents

import com.histmersion.core.*
import com.histmersion.server.acquire.AcquireHandlers

/** The acquisition operations an agent run uses — OpenAlex/Crossref metadata, the deposited reference
  * list, the retraction/impact assessment, the citation graph forward — as a small seam over
  * [[AcquireHandlers]] (built after [[com.histmersion.server.Handlers]], so it is attached to the
  * agents once it exists), and so a test can answer from fixtures instead of the network. */
trait AgentUpstream:
  /** Upstream metadata of a DOI. */
  def meta(doi: String): Either[String, WorkRef]
  /** `refs --apply`: the deposited reference list, written as `references` edges (`apply = false`, a
    * dry run: read, nothing written). */
  def refs(citekey: String, apply: Boolean = true): Either[String, RefsResult]
  /** `assess --apply`: the retraction/impact verdict; a retraction overwrites the status (`apply =
    * false`, a dry run: the verdict only — neither applied nor recorded). */
  def assess(citekey: String, apply: Boolean = true): Either[String, Assessment]
  /** The papers citing a source, read only (never `apply`). */
  def followups(id: String, since: Option[Int], limit: Option[Int]): Either[String, FollowupsResult]
  /** An OpenAlex search, each hit marked `inDb` (the discover path). */
  def discover(query: String, limit: Int): Either[String, DiscoverResult]
  /** A watcher's auto-ingest of one DOI: the LEGAL open-access chain only (never an unauthorised
    * fetcher, whatever `acquire.allow-unauthorised` says), refused when no legal file exists (nothing is stored then). Not
    * connected by default: a fixture that does not answer it ingests nothing. */
  def ingestLegal(doi: String): Either[String, IngestReport] = Left("the acquisition toolset is not connected")

object AgentUpstream:

  /** Not connected (a test, or a server built without the acquisition toolset): every call says so,
    * and the steps that need it record that they were skipped. */
  object Offline extends AgentUpstream:
    private val off                                       = Left("the acquisition toolset is not connected")
    def meta(doi: String)                                 = off
    def refs(citekey: String, apply: Boolean)             = off
    def assess(citekey: String, apply: Boolean)           = off
    def followups(id: String, since: Option[Int], limit: Option[Int]) = off
    def discover(query: String, limit: Int)                = off

  /** The server's own acquisition handlers — the same calls `histmersion refs/assess/followups` make. */
  def of(a: AcquireHandlers): AgentUpstream = new AgentUpstream:
    def meta(doi: String) = a.meta(doi).left.map(_.message)
    def refs(citekey: String, apply: Boolean) = a.refs(RefsRequest(citekey, apply = apply)).left.map(_.message)
    def assess(citekey: String, apply: Boolean) =
      (if apply then a.assess(AssessRequest(citekey, apply = true)) else a.assessReadOnly(citekey)).left.map(_.message)
    def followups(id: String, since: Option[Int], limit: Option[Int]) =
      a.followups(FollowupsRequest(id, since, limit, apply = false)).left.map(_.message)
    def discover(query: String, limit: Int) =
      a.discover(query, None, None, Some(limit), notInDb = false).left.map(_.message)
    override def ingestLegal(doi: String) = a.ingestLegal(IngestRequest(doi)).left.map(_.message)
