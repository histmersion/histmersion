package com.histmersion.plugin

/** Source fetching, the first extension point that moves part of the app into plugins. A plugin adds one or more
  * [[SourceFetcher]]s to [[PluginContext.sourceFetchers]]; the host runs them as ONE chain, in the configured order,
  * until one answers [[Fetch.Found]]. The host owns everything around the fetchers: the order, the legal gate on
  * [[Access]], the check that a "PDF" is one, the per-work [[FetchContext.memo]] and the log.
  */

/** Every identifier the caller has for one work. A fetcher reads the ones it understands and answers [[Fetch.Miss]]
  * when none fits.
  *
  * @param doi
  *   a DOI, lower-cased, without `https://doi.org/`
  * @param arxiv
  *   an arXiv id (`2101.00001`, `hep-th/9901001`)
  * @param pmid
  *   a PubMed id
  * @param pmcid
  *   a PubMed Central id (`PMC6091220`)
  * @param archive
  *   an archive.org item identifier
  * @param isbn
  *   an ISBN
  * @param title
  *   the work's title, as the caller knows it
  * @param authors
  *   its authors, as the caller knows them
  * @param year
  *   its publication year (signed; BCE negative)
  * @param format
  *   the file format the caller prefers (`pdf`, `epub`) when a work has several files
  */
final case class WorkKey(
    doi: Option[Doi] = None,
    arxiv: Option[String] = None,
    pmid: Option[String] = None,
    pmcid: Option[String] = None,
    archive: Option[String] = None,
    isbn: Option[String] = None,
    title: Option[String] = None,
    authors: List[String] = Nil,
    year: Option[Year] = None,
    format: Option[String] = None
):
  /** No identifier at all (title, authors, year and format alone identify nothing to fetch). */
  def isEmpty: Boolean = List(doi, arxiv, pmid, pmcid, archive, isbn).forall(_.isEmpty)

/** On what terms a fetcher obtains its copy. The host decides from this alone whether it may run. */
enum Access:
  /** Openly licensed or free to read: an open-access index, a repository, a public archive. */
  case Open

  /** Through the user's own subscription, key or entitlement, within that provider's terms. */
  case Subscription

  /** Without the rights holder's authorisation. Never run on the legal-only path, and on the ordinary path only when
    * `histmersion.acquire.allow-unauthorised = true`.
    */
  case Unauthorised

/** A downloaded copy of a work.
  *
  * @param source
  *   the channel it came from, recorded as the source's provenance (`arxiv`, `pmc`, …)
  * @param url
  *   where it was downloaded from
  * @param bytes
  *   the file
  * @param extension
  *   `pdf`, `epub`, `xml` (a JATS full text), … — the host refuses a `pdf` that is not one
  * @param licence
  *   the licence the copy is published under, when the fetcher knows it — an SPDX id (`CC-BY-4.0`) or `public-domain`,
  *   else the licence's URL; the host records it as the source's licence when that has none
  */
final case class FetchedFile(
    source: String,
    url: Option[String],
    bytes: Array[Byte],
    extension: String,
    licence: Option[Licence] = None
)

/** One fetcher's answer for one work. */
enum Fetch:
  /** A copy. */
  case Found(file: FetchedFile)

  /** No copy here (no identifier this fetcher reads, nothing found, the upstream did not answer). */
  case Miss(why: String)

  /** A copy exists but may not be taken (a lending-only item, a bot challenge, no entitlement). The host logs it and
    * moves on; it never asks the same fetcher again for this work.
    */
  case Refused(why: String)

/** One step of the acquisition chain. Called by the host, possibly from several threads at once (one per work being
  * fetched), so a fetcher keeps no per-work state of its own: that goes in [[FetchContext.memo]]. Failures are values
  * ([[Fetch.Miss]] / [[Fetch.Refused]]); an exception is caught by the host and read as a miss.
  */
trait SourceFetcher:
  def access: Access
  def fetch(work: WorkKey, ctx: FetchContext): Fetch

/** What the host lends a fetcher for ONE fetch of one work. */
trait FetchContext:
  /** A value computed at most once per fetch of this work and shared by every fetcher of the chain — an index record
    * two steps both read. `compute` runs on the first call for `key`; later calls return that value. Name keys
    * `<plugin id>:<what>:<identifier>`: the value is stored untyped, so a key reused with another type fails.
    */
  def memo[A](key: String)(compute: => A): A

  /** The host's log, with the fetcher's id on every line. */
  def log: PluginLog
