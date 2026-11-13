package com.histmersion
package domain.library.acquisition
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.util.problem.Problem

import com.histmersion.plugin.{Doi, WorkKey, Year}
import com.histmersion.upstreams.http.Url
import com.histmersion.upstreams.landing.RepositoryLanding.Candidate
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.settings.UpstreamSettings
import com.histmersion.plugin.Plugin
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.upstreams.ArxivId

/** A work's metadata by its DOI, as an ingest looks it up before asking the acquisition chain: OpenAlex first (richer,
  * keyless), Crossref as the fallback, with Unpaywall's open-access status when Crossref answered. `json` is the
  * acquisition client's GET of a JSON document.
  */
object WorkLookup:

  /** The OpenAlex `select` list — asking for only these fields keeps a search page small. */
  type WorkFields = WorkFieldsValue
  type WorkFieldsValue = WorkFieldsValue.T
  object WorkFieldsValue extends Newtype[String, Pure]

  val WorkFields: WorkFields =
    WorkFieldsValue(
      "id,doi,display_name,publication_year,authorships,open_access,primary_location,best_oa_location," +
        "locations,cited_by_count,is_retracted,type,abstract_inverted_index,referenced_works"
    )

  /** OpenAlex resolves a DOI as a path segment: `/works/https://doi.org/10.1234/x`. */
  def workUrl(cfg: UpstreamSettings, doi: Doi): Candidate.Url =
    Candidate.Url(s"${cfg.openalex.url}/works/https://doi.org/$doi")

  /** Crossref's record of a DOI. */
  def crossrefUrl(cfg: UpstreamSettings, doi: Doi): Candidate.Url =
    Url(Candidate.Url(cfg.crossref.url.value), Url.UrlPath(s"/works/$doi"), "mailto" -> cfg.mailto.value)

  def meta(cfg: UpstreamSettings, json: Candidate.Url => Either[Problem, Json], log: Plugin.Log)(
      doi: Doi
  ): Either[Problem, WorkRef] =
    json(Url(workUrl(cfg, doi), Url.UrlPath(""), "select" -> (WorkFields).value, "mailto" -> cfg.mailto.value))
      .map(AcquireParse.openalexWork)
      .left
      .flatMap { why =>
        // a miss (404) and a refusal (429/503) both land here; the reason says which
        log.info(
          Plugin.Log.Line(s"acquire: OpenAlex did not answer for $doi (${why.legacyText}) — falling back to Crossref")
        )
        json(crossrefUrl(cfg, doi)).map(AcquireParse.crossrefWork).map(withUnpaywall(cfg, json, doi, _))
      }

  /** Crossref records no open-access status at all, so a work that came back through the Crossref fallback (OpenAlex
    * down, rate-limited or missing the DOI) would read "no OA copy known" even for a gold-OA article. Ask Unpaywall —
    * where OpenAlex's own OA data comes from — and leave the status unknown (`oaStatus = None`) when it cannot answer:
    * Unpaywall refuses a request without a contact e-mail.
    */
  private def withUnpaywall(
      cfg: UpstreamSettings,
      json: Candidate.Url => Either[Problem, Json],
      doi: Doi,
      w: WorkRef
  ): WorkRef =
    cfg.contact
      .flatMap(email =>
        json(Url(Candidate.Url(cfg.unpaywall.url.value), Url.UrlPath(s"/$doi"), "email" -> email.value)).toOption
      )
      .map(AcquireParse.unpaywallAccess)
      .fold(w)(a =>
        w.copy(
          isOa = WorkRef.IsOa(a.isOa.value),
          oaStatus = a.status.map(s => WorkRef.OaStatus(s.value)),
          oaUrl = a.url.map(u => WorkRef.OaUrl(u.value))
        )
      )

  /** Pure: what the acquisition chain is asked for one work — its identifiers and what its metadata record says of it.
    * A DOI that is none is not passed on.
    */
  def key(doi: Option[Doi], arxiv: Option[ArxivId], work: Option[WorkRef]): WorkKey =
    WorkKey(
      doi = doi.map(_.value.trim).filter(_.matches("10\\.[0-9]+/\\S+")).map(Doi(_)),
      arxiv = arxiv.orElse(work.flatMap(_.arxivId)).map(a => WorkKey.Arxiv(a.value)),
      title = work.flatMap(_.title),
      authors = work.map(_.authors).getOrElse(Nil),
      year = work.flatMap(_.year)
    )
