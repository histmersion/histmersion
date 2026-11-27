package com.histmersion
package pluginhost
import com.histmersion.util.problem.{Problem, Problems}

import com.histmersion.plugin.{Access, FetchedFile, WorkKey}
import com.histmersion.domain.library.Doi
import com.histmersion.domain.library.ArxivId
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** Resolve a legal copy of a work: the acquisition chain ([[FetchChain]]) — the source fetchers the plugins registered,
  * in `acquire.chain` order. Built in: archive.org, Unpaywall → CORE → OpenAlex OA locations → Europe PMC / PMC → arXiv
  * → repository landing pages resolved one step (Zenodo's API, an institutional repository's `citation_pdf_url`) →
  * PMC's JATS full text (when there is no PDF anywhere).
  */
trait OaFetcher:
  def fetch(doi: Option[Doi], arxiv: Option[ArxivId]): Either[Problems, FetchedFile]

  /** [[fetch]] through the LEGAL sources only — never an `Access.Unauthorised` fetcher, whatever
    * `acquire.allow-unauthorised` says (a watcher's auto-ingest). An implementation that can run one must override it;
    * one that cannot (a test's fixture) is legal as it is.
    */
  def fetchLegal(doi: Option[Doi], arxiv: Option[ArxivId]): Either[Problems, FetchedFile] = fetch(doi, arxiv)

  /** A work by every identifier the caller has ([[WorkKey]]). An implementation that reads only a DOI and an arXiv id
    * (a test's fixture) has no copy of anything else.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def fetchWork(work: WorkKey, legalOnly: FetchChain.LegalOnly): Either[Problems, FetchedFile] =
    val doi = work.doi.map(d => Doi.assume(d.value))
    val arxiv = work.arxiv.map(a => ArxivId(a.value))
    if work.doi.isEmpty && work.arxiv.isEmpty then
      Left(Problem.NotFound.Copy(Problem.Raw(s"${FetchChain.describe(work)}"), Vector.empty))
    else if legalOnly.value then fetchLegal(doi, arxiv)
    else fetch(doi, arxiv)

  /** [[fetchWork]], with what each fetcher that ran answered when none had a copy (the ingest report shows it). An
    * implementation that is no chain (a test's fixture) has no answers to give.
    */
  def fetchWorkExplained(work: WorkKey, legalOnly: FetchChain.LegalOnly): FetchChain.Outcome =
    FetchChain.Outcome(fetchWork(work, legalOnly), Nil)
