package com.histmersion
package domain.library.acquisition

import com.histmersion.util.json.JsonCodec.given
import com.histmersion.upstreams.landing.RepositoryLanding.Candidate
import com.histmersion.upstreams.settings.UpstreamSettings
import com.histmersion.upstreams.http.Url
import com.histmersion.util.problem.Text.sentence
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.Problem
import com.histmersion.upstreams.parse.AcquireParse
import com.histmersion.plugin.Plugin

import com.histmersion.plugin.Doi
import com.histmersion.plugin.Year
import com.histmersion.plugin.WorkKey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.plugin.Licence
import com.histmersion.lint.{tolerated, LintRule}
final case class WorkRef(
    doi: Option[Doi] = None,
    openalexId: Option[WorkRef.OpenalexId] = None,
    arxivId: Option[WorkRef.ArxivId] = None,
    title: Option[WorkRef.Title] = None,
    year: Option[Year] = None,
    authors: List[WorkKey.Authors] = Nil,
    venue: Option[WorkRef.Venue] = None,
    citedByCount: Option[WorkRef.CitedByCount] = None,
    isOa: WorkRef.IsOa = WorkRef.IsOa(false),
    oaStatus: Option[WorkRef.OaStatus] = None,
    oaUrl: Option[WorkRef.OaUrl] = None,
    isRetracted: WorkRef.IsRetracted = WorkRef.IsRetracted(false),
    workType: Option[WorkRef.WorkType] = None,
    abstractText: Option[WorkRef.AbstractText] = None,
    referencedWorks: List[WorkRef.ReferencedWorks] = Nil,
    oaLocations: List[WorkRef.OaLocations] = Nil,
    oaLicence: Option[WorkRef.OaLicence] = None
)

object WorkRef:
  type OpenalexId = OpenalexId.T
  object OpenalexId extends Newtype[String, Pure]
  export com.histmersion.upstreams.ArxivId
  export WorkKey.Title
  type Venue = Venue.T
  object Venue extends Newtype[String, Pure]
  type CitedByCount = CitedByCount.T
  object CitedByCount extends Newtype[Int, Pure]
  type IsOa = IsOa.T
  object IsOa extends Newtype[Boolean, Pure]
  type OaStatus = OaStatus.T
  object OaStatus extends Newtype[String, Pure]
  type OaUrl = OaUrl.T
  object OaUrl extends Newtype[String, Pure]
  type IsRetracted = IsRetracted.T
  object IsRetracted extends Newtype[Boolean, Pure]
  type WorkType = WorkType.T
  object WorkType extends Newtype[String, Pure]
  type AbstractText = AbstractText.T
  object AbstractText extends Newtype[String, Pure]
  type ReferencedWorks = ReferencedWorks.T
  object ReferencedWorks extends Newtype[String, Pure]
  type OaLocations = OaLocations.T
  object OaLocations extends Newtype[String, Pure]
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type OaLicence = Licence
  val OaLicence: Licence.type = Licence

  type WorkFields = WorkFieldsValue

  type WorkFieldsValue = WorkFieldsValue.T
  object WorkFieldsValue extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val WorkFields: WorkFields =
    WorkFieldsValue(
      "id,doi,display_name,publication_year,authorships,open_access,primary_location,best_oa_location," +
        "locations,cited_by_count,is_retracted,type,abstract_inverted_index,referenced_works"
    )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def workUrl(cfg: UpstreamSettings, doi: Doi): Candidate.Url =
    Candidate.Url(s"${cfg.openalex.url}/works/https://doi.org/$doi")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def crossrefUrl(cfg: UpstreamSettings, doi: Doi): Candidate.Url =
    Url(Candidate.Url(cfg.crossref.url.value), Url.UrlPath(s"/works/$doi"), "mailto" -> cfg.mailto.value)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def meta(cfg: UpstreamSettings, json: Candidate.Url => Either[Problem, Json], log: Plugin.Log)(
      doi: Doi
  ): Either[Problem, WorkRef] =
    json(Url(workUrl(cfg, doi), Url.UrlPath(""), "select" -> (WorkFields).value, "mailto" -> cfg.mailto.value))
      .map(AcquireParse.openalexWork)
      .left
      .flatMap { why =>
        log.info(
          Plugin.Log.Line(s"acquire: OpenAlex did not answer for $doi (${why.sentence}) — falling back to Crossref")
        )
        json(crossrefUrl(cfg, doi)).map(AcquireParse.crossrefWork).map(withUnpaywall(cfg, json, doi, _))
      }

  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
          isOa = a.isOa,
          oaStatus = a.status.map(s => OaStatus(s.value)),
          oaUrl = a.url.map(u => OaUrl(u.value))
        )
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def key(doi: Option[Doi], arxiv: Option[ArxivId], work: Option[WorkRef]): WorkKey =
    WorkKey(
      doi = doi.map(_.value.trim).filter(_.matches("10\\.[0-9]+/\\S+")).map(Doi(_)),
      arxiv = arxiv.orElse(work.flatMap(_.arxivId)).map(a => WorkKey.Arxiv(a.value)),
      title = work.flatMap(_.title),
      authors = work.map(_.authors).getOrElse(Nil),
      year = work.flatMap(_.year)
    )
