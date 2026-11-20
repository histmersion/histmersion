package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

/** Configuration for the acquisition & assessment toolset (plan §5.2, stream 2.6).
  *
  * Every endpoint, key, threshold and mirror lives in `reference.conf` under `histmersion.acquire` with a
  * `${?HISTMERSION_ACQUIRE_*}` override — no URL, no e-mail and no magic number appears in the code. The contact e-mail
  * is used ONLY in the `mailto`/`email` parameters Crossref, OpenAlex and Unpaywall define for it — never in a header,
  * never anywhere else ([[HttpPolicy]], #1946: it used to ride in the `User-Agent` of every acquisition request too).
  */

/** @param spoolDir
  *   server-side directory the fetched PDFs are written to before the ingest job picks them up; empty = the JVM's temp
  *   directory.
  */
final case class AcquireConfig(
    contactEmail: AcquireConfig.ContactEmail,
    openalex: ServiceConfig,
    crossref: ServiceConfig,
    unpaywall: ServiceConfig,
    core: CoreServiceConfig,
    semanticScholar: KeyedServiceConfig,
    europepmc: ServiceConfig,
    pmcDataset: ServiceConfig,
    publicUserAgent: AcquireConfig.PublicUserAgent,
    zenodo: ServiceConfig,
    datacite: ServiceConfig,
    doiHandles: ServiceConfig,
    landing: LandingConfig,
    arxiv: ServiceConfig,
    archiveOrg: ServiceConfig,
    wikipedia: ServiceConfig,
    retractionWatch: RetractionWatchConfig,
    chain: CommaList,
    allowUnauthorised: AcquireConfig.AllowUnauthorised,
    agentsMayUseUnauthorised: AcquireConfig.AgentsMayUseUnauthorised,
    userAgent: AcquireConfig.UserAgent,
    timeoutSeconds: AcquireConfig.TimeoutSeconds,
    spoolDir: AcquireConfig.SpoolDir,
    defaults: AcquireDefaults,
    tls: TlsConfig,
    candidates: CandidatesConfig,
    recheck: RecheckConfig
) derives ConfigReader:

  /** The contact e-mail, when one is configured. It is a per-install setting (never in the repo), so a blank default
    * means "anonymous": no polite pool, and no Unpaywall, which refuses requests without an e-mail.
    */
  def contact: Option[AcquireConfig.ContactEmail] =
    Option(contactEmail).map(_.value.trim).filter(_.nonEmpty).map(AcquireConfig.ContactEmail(_))

  /** The `mailto` value; empty without a contact, and `Url` drops an empty parameter. */
  def mailto: AcquireConfig.ContactEmail = contact.getOrElse(AcquireConfig.ContactEmail(""))

  /** The acquisition chain's order: the source-fetcher ids `chain` lists (comma-separated); fetchers it does not name
    * run after these, in plugin registration order.
    */
  def chainIds: List[AcquireConfig.ChainId] = chain.entries.map(e => AcquireConfig.ChainId(e.value))

object AcquireConfig:
  type ChainId = ChainId.T
  object ChainId extends Newtype[String, Pure]

  type ContactEmail = ContactEmail.T
  object ContactEmail extends Newtype[String, Pure]
  type PublicUserAgent = PublicUserAgent.T
  object PublicUserAgent extends Newtype[String, Pure]
  type AllowUnauthorised = AllowUnauthorised.T
  object AllowUnauthorised extends Newtype[Boolean, Pure]

  /** Whether an ingest an agent starts (an MCP call) may ask a fetcher the config marks unauthorised when it asks for
    * that explicitly. Off, every agent ingest is legal-only. Only a person sets it: the owner's standing rule is that
    * agents fetch legal copies alone.
    */
  type AgentsMayUseUnauthorised = AgentsMayUseUnauthorised.T
  object AgentsMayUseUnauthorised extends Newtype[Boolean, Pure]
  type UserAgent = UserAgent.T
  object UserAgent extends Newtype[String, Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends Newtype[Int, Pure]
  type SpoolDir = SpoolDir.T
  object SpoolDir extends Newtype[String, Pure]
