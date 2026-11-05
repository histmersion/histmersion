package com.histmersion.pluginhost

import com.histmersion.plugin.{Access, FetchedFile, WorkKey}

/** Resolve a legal copy of a work: the acquisition chain ([[FetchChain]]) — the source fetchers the plugins registered,
  * in `acquire.chain` order. Built in: archive.org, Unpaywall → CORE → OpenAlex OA locations → Europe PMC / PMC → arXiv
  * → repository landing pages resolved one step (Zenodo's API, an institutional repository's `citation_pdf_url`) →
  * PMC's JATS full text (when there is no PDF anywhere).
  */
trait OaFetcher:
  def fetch(doi: Option[com.histmersion.pluginhost.OaFetcher.FetchDoi], arxiv: Option[com.histmersion.pluginhost.OaFetcher.FetchArxiv]): Either[com.histmersion.pluginhost.OaFetcher.FetchResultPart1, FetchedFile]

  /** [[fetch]] through the LEGAL sources only — never an `Access.Unauthorised` fetcher, whatever
    * `acquire.allow-unauthorised` says (a watcher's auto-ingest). An implementation that can run one must override it;
    * one that cannot (a test's fixture) is legal as it is.
    */
  def fetchLegal(doi: Option[com.histmersion.pluginhost.OaFetcher.FetchLegalDoi], arxiv: Option[com.histmersion.pluginhost.OaFetcher.FetchLegalArxiv]): Either[com.histmersion.pluginhost.OaFetcher.FetchLegalResultPart1, FetchedFile] = fetch(doi, arxiv)

  /** A work by every identifier the caller has ([[WorkKey]]). An implementation that reads only a DOI and an arXiv id
    * (a test's fixture) has no copy of anything else.
    */
  def fetchWork(work: WorkKey, legalOnly: com.histmersion.pluginhost.OaFetcher.FetchWorkLegalOnly): Either[com.histmersion.pluginhost.OaFetcher.FetchWorkResultPart1, FetchedFile] =
    if work.doi.isEmpty && work.arxiv.isEmpty then Left(FetchChain.NoCopy)
    else if legalOnly then fetchLegal(work.doi.map(_.value), work.arxiv)
    else fetch(work.doi.map(_.value), work.arxiv)

  /** [[fetchWork]], with what each fetcher that ran answered when none had a copy (the ingest report shows it). An
    * implementation that is no chain (a test's fixture) has no answers to give.
    */
  def fetchWorkExplained(work: WorkKey, legalOnly: com.histmersion.pluginhost.OaFetcher.FetchWorkExplainedLegalOnly): FetchChain.Outcome =
    FetchChain.Outcome(fetchWork(work, legalOnly), Nil)

object OaFetcher:
  type FetchResultPart1 = FetchResultPart1.T
  object FetchResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchDoi = FetchDoi.T
  object FetchDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchArxiv = FetchArxiv.T
  object FetchArxiv extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchLegalResultPart1 = FetchLegalResultPart1.T
  object FetchLegalResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchLegalDoi = FetchLegalDoi.T
  object FetchLegalDoi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchLegalArxiv = FetchLegalArxiv.T
  object FetchLegalArxiv extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchWorkResultPart1 = FetchWorkResultPart1.T
  object FetchWorkResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchWorkLegalOnly = FetchWorkLegalOnly.T
  object FetchWorkLegalOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FetchWorkExplainedLegalOnly = FetchWorkExplainedLegalOnly.T
  object FetchWorkExplainedLegalOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
