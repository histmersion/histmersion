package com.histmersion.pluginhost

import com.histmersion.plugin.{Access, FetchedFile, WorkKey}

/** Resolve a legal copy of a work: the acquisition chain ([[FetchChain]]) — the source fetchers the plugins registered,
  * in `acquire.chain` order. Built in: archive.org, Unpaywall → CORE → OpenAlex OA locations → Europe PMC / PMC → arXiv
  * → repository landing pages resolved one step (Zenodo's API, an institutional repository's `citation_pdf_url`) →
  * PMC's JATS full text (when there is no PDF anywhere).
  */
trait OaFetcher:
  def fetch(doi: Option[String], arxiv: Option[String]): Either[String, FetchedFile]

  /** [[fetch]] through the LEGAL sources only — never an `Access.Unauthorised` fetcher, whatever
    * `acquire.allow-unauthorised` says (a watcher's auto-ingest). An implementation that can run one must override it;
    * one that cannot (a test's fixture) is legal as it is.
    */
  def fetchLegal(doi: Option[String], arxiv: Option[String]): Either[String, FetchedFile] = fetch(doi, arxiv)

  /** A work by every identifier the caller has ([[WorkKey]]). An implementation that reads only a DOI and an arXiv id
    * (a test's fixture) has no copy of anything else.
    */
  def fetchWork(work: WorkKey, legalOnly: Boolean): Either[String, FetchedFile] =
    if work.doi.isEmpty && work.arxiv.isEmpty then Left(FetchChain.NoCopy)
    else if legalOnly then fetchLegal(work.doi.map(_.value), work.arxiv)
    else fetch(work.doi.map(_.value), work.arxiv)

  /** [[fetchWork]], with what each fetcher that ran answered when none had a copy (the ingest report shows it). An
    * implementation that is no chain (a test's fixture) has no answers to give.
    */
  def fetchWorkExplained(work: WorkKey, legalOnly: Boolean): FetchChain.Outcome =
    FetchChain.Outcome(fetchWork(work, legalOnly), Nil)
