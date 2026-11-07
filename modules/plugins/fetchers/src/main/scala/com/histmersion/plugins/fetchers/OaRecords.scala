package com.histmersion.plugins.fetchers

import com.histmersion.upstreams.http.json
import com.histmersion.upstreams.openalex.OpenAlexCrossref
import com.histmersion.upstreams.landing.RepositoryLanding
import com.histmersion.upstreams.settings.UpstreamSettings
import com.histmersion.upstreams.http.Url
import io.circe.Json
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
  lazy val datacite: Option[com.histmersion.plugins.fetchers.OaRecords.Datacite] = OaRecords.dataciteUrl(cfg, http, log, doi)

  /** Where the DOI handle API says it resolves (any registration agency) — asked only for an open location on an OA
    * publisher.
    */
  lazy val handle: Option[com.histmersion.plugins.fetchers.OaRecords.Handle] = OaRecords.handleUrl(cfg, http, log, doi)

object OaRecords:
  type Doi = Doi.T
  object Doi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Datacite = Datacite.T
  object Datacite extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Handle = Handle.T
  object Handle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def handleUrl(cfg: UpstreamSettings, http: Plugin.Http, log: Plugin.Log, doi: com.histmersion.plugins.fetchers.OaRecords.Doi): Option[com.histmersion.plugin.Plugin.Http.GetUrl] =
    http.json(Url(cfg.doiHandles.url, s"/$doi", "type" -> "URL")) match
      case Right(j) => RepositoryLanding.handleUrl(j)
      case Left(e)  =>
        log.info(s"doi handles: no URL for $doi ($e)"); None

  def dataciteUrl(cfg: UpstreamSettings, http: Plugin.Http, log: Plugin.Log, doi: com.histmersion.plugins.fetchers.OaRecords.Doi): Option[com.histmersion.plugin.Plugin.Http.GetUrl] =
    http.json(Url(cfg.datacite.url, s"/dois/$doi")) match
      case Right(j) => RepositoryLanding.dataciteUrl(j)
      case Left(e)  =>
        log.info(s"datacite: no record for $doi ($e)"); None
