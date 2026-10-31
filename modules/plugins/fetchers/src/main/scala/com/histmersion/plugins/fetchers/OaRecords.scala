package com.histmersion.plugins.fetchers

import com.histmersion.plugin.{PluginHttp, PluginLog}
import com.histmersion.upstreams.{json, OpenAlexCrossref, RepositoryLanding, UpstreamSettings, Url}
import io.circe.Json

/** The open-access indexes' records of one DOI, asked lazily and at most once per fetch, shared through the fetch's
  * memo by the index steps and the landing step of every open-access index plugin.
  */
final class OaRecords(
    val doi: String,
    cfg: UpstreamSettings,
    http: PluginHttp,
    openalexClient: OpenAlexCrossref,
    log: PluginLog
):
  lazy val openalex: Option[Json] = openalexClient.raw(doi).toOption

  /** Where DataCite says the DOI resolves — `None` for a DOI it does not register (a Crossref one). */
  lazy val datacite: Option[String] = OaRecords.dataciteUrl(cfg, http, log, doi)

  /** Where the DOI handle API says it resolves (any registration agency) — asked only for an open location on an OA
    * publisher.
    */
  lazy val handle: Option[String] = OaRecords.handleUrl(cfg, http, log, doi)

object OaRecords:
  def handleUrl(cfg: UpstreamSettings, http: PluginHttp, log: PluginLog, doi: String): Option[String] =
    http.json(Url(cfg.doiHandles.url, s"/$doi", "type" -> "URL")) match
      case Right(j) => RepositoryLanding.handleUrl(j)
      case Left(e)  =>
        log.info(s"doi handles: no URL for $doi ($e)"); None

  def dataciteUrl(cfg: UpstreamSettings, http: PluginHttp, log: PluginLog, doi: String): Option[String] =
    http.json(Url(cfg.datacite.url, s"/dois/$doi")) match
      case Right(j) => RepositoryLanding.dataciteUrl(j)
      case Left(e)  =>
        log.info(s"datacite: no record for $doi ($e)"); None
