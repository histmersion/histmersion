package com.histmersion.pluginhost

import com.histmersion.plugin.{Access, FetchedFile, WorkKey}

/** Resolve a legal copy of a work: the acquisition chain ([[FetchChain]]) — the source fetchers the plugins registered,
  * in `acquire.chain` order. Built in: archive.org, Unpaywall → CORE → OpenAlex OA locations → Europe PMC / PMC → arXiv
  * → repository landing pages resolved one step (Zenodo's API, an institutional repository's `citation_pdf_url`) →
  * PMC's JATS full text (when there is no PDF anywhere).
  */
trait OaFetcher:
  def fetch(doi: Option[com.histmersion.domain.library.Doi], arxiv: Option[com.histmersion.domain.library.ArxivId]): Either[com.histmersion.pluginhost.OaFetcher.Problem, FetchedFile]

  /** [[fetch]] through the LEGAL sources only — never an `Access.Unauthorised` fetcher, whatever
    * `acquire.allow-unauthorised` says (a watcher's auto-ingest). An implementation that can run one must override it;
    * one that cannot (a test's fixture) is legal as it is.
    */
  def fetchLegal(doi: Option[com.histmersion.domain.library.Doi], arxiv: Option[com.histmersion.domain.library.ArxivId]): Either[com.histmersion.pluginhost.OaFetcher.Problem, FetchedFile] = fetch(doi, arxiv)

  /** A work by every identifier the caller has ([[WorkKey]]). An implementation that reads only a DOI and an arXiv id
    * (a test's fixture) has no copy of anything else.
    */
  def fetchWork(work: WorkKey, legalOnly: com.histmersion.pluginhost.FetchChain.LegalOnly): Either[com.histmersion.pluginhost.OaFetcher.Problem, FetchedFile] =
    val doi = work.doi.map(d => com.histmersion.domain.library.Doi.assume(d.value))
    val arxiv = work.arxiv.map(a => com.histmersion.domain.library.ArxivId(a.value))
    if work.doi.isEmpty && work.arxiv.isEmpty then Left(OaFetcher.Problem(FetchChain.NoCopy))
    else if legalOnly.value then fetchLegal(doi, arxiv)
    else fetch(doi, arxiv)

  /** [[fetchWork]], with what each fetcher that ran answered when none had a copy (the ingest report shows it). An
    * implementation that is no chain (a test's fixture) has no answers to give.
    */
  def fetchWorkExplained(work: WorkKey, legalOnly: com.histmersion.pluginhost.FetchChain.LegalOnly): FetchChain.Outcome =
    FetchChain.Outcome(fetchWork(work, legalOnly), Nil)

object OaFetcher:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
