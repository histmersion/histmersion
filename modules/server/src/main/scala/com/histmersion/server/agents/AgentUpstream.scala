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
  /** `refs --apply`: the deposited reference list, written as `references` edges. */
  def refs(citekey: String): Either[String, RefsResult]
  /** `assess --apply`: the retraction/impact verdict; a retraction overwrites the status. */
  def assess(citekey: String): Either[String, Assessment]
  /** The papers citing a source, read only (never `apply`). */
  def followups(id: String, since: Option[Int], limit: Option[Int]): Either[String, FollowupsResult]

object AgentUpstream:

  /** Not connected (a test, or a server built without the acquisition toolset): every call says so,
    * and the steps that need it record that they were skipped. */
  object Offline extends AgentUpstream:
    private val off                                       = Left("the acquisition toolset is not connected")
    def meta(doi: String)                                 = off
    def refs(citekey: String)                             = off
    def assess(citekey: String)                           = off
    def followups(id: String, since: Option[Int], limit: Option[Int]) = off

  /** The server's own acquisition handlers — the same calls `histmersion refs/assess/followups` make. */
  def of(a: AcquireHandlers): AgentUpstream = new AgentUpstream:
    def meta(doi: String) = a.meta(doi).left.map(_.message)
    def refs(citekey: String) = a.refs(RefsRequest(citekey, apply = true)).left.map(_.message)
    def assess(citekey: String) = a.assess(AssessRequest(citekey, apply = true)).left.map(_.message)
    def followups(id: String, since: Option[Int], limit: Option[Int]) =
      a.followups(FollowupsRequest(id, since, limit, apply = false)).left.map(_.message)
