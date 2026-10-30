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
    contactEmail: String,
    openalex: ServiceConfig,
    crossref: ServiceConfig,
    unpaywall: ServiceConfig,
    core: CoreServiceConfig,
    semanticScholar: KeyedServiceConfig,
    europepmc: ServiceConfig,
    pmcDataset: ServiceConfig,
    publicUserAgent: String,
    zenodo: ServiceConfig,
    datacite: ServiceConfig,
    doiHandles: ServiceConfig,
    landing: LandingConfig,
    arxiv: ServiceConfig,
    archiveOrg: ServiceConfig,
    wikipedia: ServiceConfig,
    retractionWatch: RetractionWatchConfig,
    chain: CommaList,
    allowUnauthorised: Boolean,
    userAgent: String,
    timeoutSeconds: Int,
    spoolDir: String,
    defaults: AcquireDefaults,
    tls: TlsConfig,
    candidates: CandidatesConfig,
    recheck: RecheckConfig
) derives ConfigReader:

  /** The contact e-mail, when one is configured. It is a per-install setting (never in the repo), so a blank default
    * means "anonymous": no polite pool, and no Unpaywall, which refuses requests without an e-mail.
    */
  def contact: Option[String] = Option(contactEmail).map(_.trim).filter(_.nonEmpty)

  /** The `mailto` value; empty without a contact, and `Url` drops an empty parameter. */
  def mailto: String = contact.getOrElse("")

  /** The acquisition chain's order: the source-fetcher ids `chain` lists (comma-separated); fetchers it does not name
    * run after these, in plugin registration order.
    */
  def chainIds: List[String] = chain.entries
