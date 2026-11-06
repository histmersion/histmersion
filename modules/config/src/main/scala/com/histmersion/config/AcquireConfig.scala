package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

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
  def contact: Option[com.histmersion.config.AcquireConfig.ContactEmail] = Option(contactEmail).map(_.trim).filter(_.nonEmpty)

  /** The `mailto` value; empty without a contact, and `Url` drops an empty parameter. */
  def mailto: com.histmersion.config.AcquireConfig.ContactEmail = contact.getOrElse("")

  /** The acquisition chain's order: the source-fetcher ids `chain` lists (comma-separated); fetchers it does not name
    * run after these, in plugin registration order.
    */
  def chainIds: List[com.histmersion.config.AcquireConfig.ChainId] = chain.entries

object AcquireConfig:
  type ChainId = ChainId.T
  object ChainId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ContactEmail = ContactEmail.T
  object ContactEmail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PublicUserAgent = PublicUserAgent.T
  object PublicUserAgent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AllowUnauthorised = AllowUnauthorised.T
  object AllowUnauthorised extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UserAgent = UserAgent.T
  object UserAgent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TimeoutSeconds = TimeoutSeconds.T
  object TimeoutSeconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SpoolDir = SpoolDir.T
  object SpoolDir extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
