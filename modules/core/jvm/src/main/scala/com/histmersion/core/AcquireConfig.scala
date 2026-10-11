package com.histmersion.core

import hearth.kindlings.sconfigderivation.ConfigReader

/** Configuration for the acquisition & assessment toolset (plan §5.2, stream 2.6).
  *
  * Every endpoint, key, threshold and mirror lives in `reference.conf` under `histmersion.acquire` with a
  * `${?HISTMERSION_ACQUIRE_*}` override — no URL, no e-mail and no magic number appears in the code. The
  * contact e-mail is used ONLY the way the Python pipeline used it: as the polite-pool identifier in
  * the `User-Agent` header and in the `mailto`/`email` parameters Crossref, OpenAlex and Unpaywall
  * ask for. It is never sent anywhere else.
  */

/** A keyless upstream service. */
final case class ServiceConfig(url: String) derives ConfigReader

/** An upstream that accepts an API key (empty = keyless, which is how they run today). */
final case class KeyedServiceConfig(url: String, apiKey: String) derives ConfigReader:
  def key: Option[String] = Option(apiKey).map(_.trim).filter(_.nonEmpty)

/** Retraction Watch: the CSV Crossref publishes, mirrored into the `retraction_watch` table. */
final case class RetractionWatchConfig(csvUrl: String) derives ConfigReader

/** The unauthorised mirror is the LAST resort and OFF by default. It distributes copyrighted papers without
  * publisher authorisation and its legality varies by jurisdiction; enabling it is a deliberate,
  * per-deployment decision, and every use is logged as a warning. */
final case class UnlicensedConfig(enabled: Boolean, mirror: String) derives ConfigReader

/** Tunables that used to be `discover:` defaults and inline thresholds in `assess.py`. */
final case class AcquireDefaults(
    searchLimit: Int,      // discover search page size
    minYear: Int,          // discover: oldest publication year considered
    followupsLimit: Int,   // followups: max citing papers fetched
    archiveRows: Int,      // archive.org search rows
    wellCitedAt: Int,      // citations at/above which a source is "current" rather than "low-impact"
    lowCitedAt: Int,       // citations below which an OLD source is "low-impact"
    staleBefore: Int,      // publication year before which the low-citation rule applies
    titleMatchAt: Double   // an import PROPOSES an open acquisition item whose text scores this or more (#970)
) derives ConfigReader

/** @param spoolDir server-side directory the fetched PDFs are written to before the ingest job picks
  *                 them up; empty = the JVM's temp directory. */
final case class AcquireConfig(
    contactEmail: String,
    openalex: ServiceConfig,
    crossref: ServiceConfig,
    unpaywall: ServiceConfig,
    core: KeyedServiceConfig,
    semanticScholar: KeyedServiceConfig,
    europepmc: ServiceConfig,
    pmcDataset: ServiceConfig,
    publicUserAgent: String,
    arxiv: ServiceConfig,
    archiveOrg: ServiceConfig,
    wikipedia: ServiceConfig,
    retractionWatch: RetractionWatchConfig,
    unlicensed: UnlicensedConfig,
    userAgent: String,
    timeoutSeconds: Int,
    spoolDir: String,
    defaults: AcquireDefaults
) derives ConfigReader:

  /** The contact e-mail, when one is configured. It is a per-install setting (never in the repo), so
    * a blank default means "anonymous": no polite pool, and no Unpaywall, which refuses requests
    * without an e-mail. */
  def contact: Option[String] = Option(contactEmail).map(_.trim).filter(_.nonEmpty)

  /** The identifier every upstream sees: `histmersion/0.1 (mailto:…)`, or just the product without
    * a contact. */
  def politeUserAgent: String = contact.fold(userAgent)(e => s"$userAgent (mailto:$e)")

  /** The `mailto` value; empty without a contact, and `Url` drops an empty parameter. */
  def mailto: String = contact.getOrElse("")

/** Host-side Calibre integration (the ONE local integration the CLI is allowed — Calibre lives on
  * the user's machine, not in the container).
  *
  * @param libraryDir   the Calibre library root (`metadata.db` lives here); `~` is expanded
  * @param calibredb    the `calibredb` executable — preferred reader/writer
  * @param sqlite       the `sqlite3` executable — read-only fallback when the Calibre GUI holds the DB
  * @param originalsDir optional host directory holding the fetched originals as `<citekey>/paper.*`,
  *                     an override for `calibre push`/`sync`, which otherwise download the
  *                     original the server stored
  */
final case class CalibreCliConfig(libraryDir: String, calibredb: String, sqlite: String, originalsDir: String)
    derives ConfigReader:

  private def expand(p: String): String =
    if p.startsWith("~") then System.getProperty("user.home") + p.drop(1) else p

  def library: String   = expand(libraryDir)
  def originals: Option[String] = Option(originalsDir).map(expand).map(_.trim).filter(_.nonEmpty)
