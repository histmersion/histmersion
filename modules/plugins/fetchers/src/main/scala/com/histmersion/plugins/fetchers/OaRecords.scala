package com.histmersion
package plugins.fetchers
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import hearth.kindlings.jsoniterjson.Json

import com.histmersion.upstreams.http.json
import com.histmersion.upstreams.openalex.OpenAlexCrossref
import com.histmersion.upstreams.landing.RepositoryLanding
import com.histmersion.upstreams.settings.UpstreamSettings
import com.histmersion.upstreams.http.Url
import com.histmersion.plugin.Plugin

/** The open-access indexes' records of one DOI, asked lazily and at most once per fetch, shared through the fetch's
  * memo by the index steps and the landing step of every open-access index plugin.
  */
final class OaRecords(
    val doi: OaRecords.Doi,
    cfg: UpstreamSettings,
    http: Plugin.Http,
    openalexClient: OpenAlexCrossref,
    log: Plugin.Log
):
  lazy val openalex: Option[Json] = openalexClient.raw(doi).toOption

  /** Where DataCite says the DOI resolves — `None` for a DOI it does not register (a Crossref one). */
  lazy val datacite: Option[Plugin.Http.Url] = OaRecords.dataciteUrl(cfg, http, log, doi)

  /** Where the DOI handle API says it resolves (any registration agency) — asked only for an open location on an OA
    * publisher.
    */
  lazy val handle: Option[Plugin.Http.Url] = OaRecords.handleUrl(cfg, http, log, doi)

object OaRecords:
  type Doi = com.histmersion.plugin.Doi
  val Doi: com.histmersion.plugin.Doi.type = com.histmersion.plugin.Doi

  def handleUrl(cfg: UpstreamSettings, http: Plugin.Http, log: Plugin.Log, doi: Doi): Option[Plugin.Http.Url] =
    http.json(
      Url(
        RepositoryLanding.Candidate.Url(cfg.doiHandles.url.value),
        Url.UrlPath(s"/${doi.value}"),
        "type" -> "URL"
      ).value
    ) match
      case Right(j) => RepositoryLanding.handleUrl(j).map(u => Plugin.Http.Url(u.value))
      case Left(e)  =>
        log.info(Plugin.Log.Line(s"doi handles: no URL for ${doi.value} ($e)")); None

  def dataciteUrl(cfg: UpstreamSettings, http: Plugin.Http, log: Plugin.Log, doi: Doi): Option[Plugin.Http.Url] =
    http.json(
      Url(RepositoryLanding.Candidate.Url(cfg.datacite.url.value), Url.UrlPath(s"/dois/${doi.value}")).value
    ) match
      case Right(j) => RepositoryLanding.dataciteUrl(j).map(u => Plugin.Http.Url(u.value))
      case Left(e)  =>
        log.info(Plugin.Log.Line(s"datacite: no record for ${doi.value} ($e)")); None
