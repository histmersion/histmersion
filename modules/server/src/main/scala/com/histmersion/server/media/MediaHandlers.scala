package com.histmersion.server.media

import com.histmersion.core.*
import com.histmersion.db.MediaRepo
import com.histmersion.server.Deps

import java.util.concurrent.locks.ReentrantLock

/** Media from outside providers (#1446): the import, the list and its facets, one picture, the bytes
  * the cards and the media layer show, and the figure-licence fill. Reached through
  * `Handlers.media`, so REST and the CLI share this one implementation.
  *
  * Not an MCP tool, on purpose: an import is an operator's decision that sends traffic to a third
  * party, and a picture is for the reader of the web UI — an agent compiling prose has no use for
  * either (its `places`/`events` tools say nothing new with a thumbnail URL). `lint --rule
  * media-licence` is how an agent sees the media's state. */
final class MediaHandlers(deps: Deps, http: MediaHttp):

  private val ds  = deps.ds
  private val cfg = deps.cfg

  def this(deps: Deps) = this(deps, JavaMediaHttp(deps.cfg.media.timeoutSeconds))

  /** One import at a time: the polite pace is per run, and two runs would ask twice as fast. */
  private val running = ReentrantLock()

  private val Uuid = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"

  private def notFound(uid: String) = ApiError(s"No picture '$uid'", Some(Write.CodeNotFound))

  /** Pure: the provider an import request names — `wikidata` (the default), `commons` or
    * `wikimedia-commons` all mean the Wikidata → Commons provider, the only one so far. */
  private[media] def providerOf(p: String): Either[ApiError, String] =
    p.trim.toLowerCase(java.util.Locale.ROOT) match
      case "" | "wikidata" | "commons" | MediaProvider.Commons => Right(MediaProvider.Commons)
      case other => Left(ApiError(s"unknown media provider '$other' (one of: wikidata)", Some(Write.CodeInvalid)))

  def importMedia(req: MediaImportRequest): Either[ApiError, MediaImportReport] =
    def go: Either[ApiError, MediaImportReport] =
      providerOf(req.provider).flatMap { provider =>
        if !running.tryLock() then Left(ApiError("a media import is already running — try again when it ends"))
        else
          try
            MediaRepo.targets(ds, provider, req.place, req.event, cfg.media.limit(req.limit), req.refresh)
              .left.map(ApiError(_, Some(Write.CodeNotFound)))
              .map { targets =>
                val fetch  = Polite(http, cfg.media)
                val report = WikidataCommons(fetch, cfg.media).run(targets, req.dryRun, req.refresh, DbMediaStore(ds))
                scribe.info(s"media import (${if req.dryRun then "dry run" else "stored"}): ${targets.size} target(s), " +
                  s"${report.files} file(s), ${report.stored} stored, ${fetch.requests} request(s)")
                report
              }
          finally running.unlock()
      }
    if req.dryRun then go else deps.maintenance.guard("media_import")(go)

  def list(q: Api.MediaQuery): MediaList = MediaRepo.list(ds, q, cfg.limits.page(q.limit, 50))

  def facets: MediaFacets = MediaRepo.facets(ds)

  def show(uid: String): Either[ApiError, MediaItem] =
    Option(uid.trim).filter(_.matches(Uuid)).flatMap(MediaRepo.show(ds, _)).toRight(notFound(uid))

  def thumb(uid: String): Either[ApiError, (Array[Byte], String)] =
    Option(uid.trim).filter(_.matches(Uuid)).flatMap(MediaRepo.thumb(ds, _)).map((m, b) => (b, m)).toRight(notFound(uid))

  def file(uid: String): Either[ApiError, (Array[Byte], String)] =
    Option(uid.trim).filter(_.matches(Uuid)).flatMap(MediaRepo.file(ds, _)).map((m, b) => (b, m)).toRight(notFound(uid))

  def fillLicences(apply: Boolean): Either[ApiError, FigureLicenceFill] =
    if apply then deps.maintenance.guard("media_fill_licences")(Right(MediaRepo.fillFigureLicences(ds, apply = true)))
    else Right(MediaRepo.fillFigureLicences(ds, apply = false))
