package com.histmersion.domain.library.acquisition
import com.histmersion.plugin.{Doi, WorkKey, Year}
import io.circe.Json
import com.histmersion.upstreams.http.Url
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.upstreams.settings.UpstreamSettings
import com.histmersion.plugin.Plugin

/** A work's metadata by its DOI, as an ingest looks it up before asking the acquisition chain: OpenAlex first (richer,
  * keyless), Crossref as the fallback, with Unpaywall's open-access status when Crossref answered. `json` is the
  * acquisition client's GET of a JSON document.
  */
object WorkLookup:

  /** The OpenAlex `select` list — asking for only these fields keeps a search page small. */
  type WorkFields = WorkFields.T
  object WorkFields extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WorkUrlResult = WorkUrlResult.T
  object WorkUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CrossrefUrlResult = CrossrefUrlResult.T
  object CrossrefUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MetaResultPart1 = MetaResultPart1.T
  object MetaResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MetaJsonArg1 = MetaJsonArg1.T
  object MetaJsonArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MetaJsonResultPart1 = MetaJsonResultPart1.T
  object MetaJsonResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithUnpaywallJsonArg1 = WithUnpaywallJsonArg1.T
  object WithUnpaywallJsonArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithUnpaywallJsonResultPart1 = WithUnpaywallJsonResultPart1.T
  object WithUnpaywallJsonResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KeyArxiv = KeyArxiv.T
  object KeyArxiv extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val WorkFields: com.histmersion.domain.library.acquisition.WorkLookup.WorkFields =
    "id,doi,display_name,publication_year,authorships,open_access,primary_location,best_oa_location," +
      "locations,cited_by_count,is_retracted,type,abstract_inverted_index,referenced_works"

  /** OpenAlex resolves a DOI as a path segment: `/works/https://doi.org/10.1234/x`. */
  def workUrl(cfg: UpstreamSettings, doi: com.histmersion.domain.library.Doi): com.histmersion.domain.library.acquisition.WorkLookup.WorkUrlResult =
    s"${cfg.openalex.url}/works/https://doi.org/$doi"

  /** Crossref's record of a DOI. */
  def crossrefUrl(cfg: UpstreamSettings, doi: com.histmersion.domain.library.Doi): com.histmersion.domain.library.acquisition.WorkLookup.CrossrefUrlResult =
    Url(cfg.crossref.url, s"/works/$doi", "mailto" -> cfg.mailto)

  def meta(cfg: UpstreamSettings, json: com.histmersion.domain.library.acquisition.WorkLookup.MetaJsonArg1 => Either[com.histmersion.domain.library.acquisition.WorkLookup.MetaJsonResultPart1, Json], log: Plugin.Log)(
      doi: com.histmersion.domain.library.Doi
  ): Either[com.histmersion.domain.library.acquisition.WorkLookup.MetaResultPart1, WorkRef] =
    json(Url(workUrl(cfg, doi), "", "select" -> WorkFields, "mailto" -> cfg.mailto))
      .map(AcquireParse.openalexWork)
      .left
      .flatMap { why =>
        // a miss (404) and a refusal (429/503) both land here; the reason says which
        log.info(s"acquire: OpenAlex did not answer for $doi ($why) — falling back to Crossref")
        json(crossrefUrl(cfg, doi)).map(AcquireParse.crossrefWork).map(withUnpaywall(cfg, json, doi, _))
      }

  /** Crossref records no open-access status at all, so a work that came back through the Crossref fallback (OpenAlex
    * down, rate-limited or missing the DOI) would read "no OA copy known" even for a gold-OA article. Ask Unpaywall —
    * where OpenAlex's own OA data comes from — and leave the status unknown (`oaStatus = None`) when it cannot answer:
    * Unpaywall refuses a request without a contact e-mail.
    */
  private def withUnpaywall(
      cfg: UpstreamSettings,
      json: com.histmersion.domain.library.acquisition.WorkLookup.WithUnpaywallJsonArg1 => Either[com.histmersion.domain.library.acquisition.WorkLookup.WithUnpaywallJsonResultPart1, Json],
      doi: com.histmersion.domain.library.Doi,
      w: WorkRef
  ): WorkRef =
    cfg.contact
      .flatMap(email => json(Url(cfg.unpaywall.url, s"/$doi", "email" -> email)).toOption)
      .map(AcquireParse.unpaywallAccess)
      .fold(w)(a => w.copy(isOa = a.isOa, oaStatus = a.status, oaUrl = a.url))

  /** Pure: what the acquisition chain is asked for one work — its identifiers and what its metadata record says of it.
    * A DOI that is none is not passed on.
    */
  def key(doi: Option[com.histmersion.domain.library.Doi], arxiv: Option[com.histmersion.domain.library.acquisition.WorkLookup.KeyArxiv], work: Option[WorkRef]): WorkKey =
    WorkKey(
      doi = doi.flatMap(d => Doi.option(d.trim)),
      arxiv = arxiv.orElse(work.flatMap(_.arxivId)),
      title = work.flatMap(_.title),
      authors = work.map(_.authors).getOrElse(Nil),
      year = work.flatMap(_.year).map(Year(_))
    )
