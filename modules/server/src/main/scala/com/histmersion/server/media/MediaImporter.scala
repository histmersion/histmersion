package com.histmersion.server.media

import com.histmersion.pluginhost.{HttpTransport, Polite}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.MediaRepo
import com.histmersion.db.MediaRepo.Target
import com.histmersion.server.{ConverterClient, Deps}

/** One of the imports `media import <provider>` runs (#1446) — what it asks about, what it has still to ask, and a run
  * over a batch of targets. The one-run endpoint and the job share it, so every provider gets the same dry run,
  * `--limit/--place/--event/--refresh`, progress and per-item errors.
  */
trait MediaImporter:
  /** The name `media_checks` records it under (and the report's provider). */
  def check: String

  /** The job's description: "Wikidata pictures", "Met objects". */
  def title: String

  /** What it asks about, for the job's description: "places and events", "places", "events". */
  def over: String

  /** Targets per job batch. */
  def batch: Int
  def targets(
      place: Option[String],
      event: Option[String],
      limit: Int,
      refresh: Boolean,
      since: Option[Long]
  ): Either[String, List[Target]]
  def pending(since: Option[Long]): Int

  /** A run: one polite client for its whole length (the pace is per run). */
  def start(): MediaImporter.Run

object MediaImporter:
  val Wikidata = "wikidata"
  val Depicts = "depicts"
  val Met = "met"
  val Cleveland = "cleveland"
  val Aic = "aic"
  val Loc = "loc"
  val Pleiades = "pleiades"
  val OpenContext = "opencontext"

  /** The key-gated collections ([[BuiltinMedia.keyed]]): built by their plugins, not by [[of]]. */
  val Smithsonian = "smithsonian"
  val Europeana = "europeana"
  val names: List[String] = List(Wikidata, Depicts, Met, Cleveland, Aic, Loc, Pleiades, OpenContext)

  trait Run:
    def run(targets: List[Target], dryRun: Boolean, refresh: Boolean): MediaImportReport
    def requests: Int

  /** The importer `name` names, or why it cannot run (a collection switched off in the config). */
  def of(name: String, deps: Deps, http: HttpTransport, converter: ConverterClient): Either[String, MediaImporter] =
    val ds = deps.ds
    val cfg = deps.cfg.media
    val store = DbMediaStore(ds)
    name match
      case Wikidata =>
        Right(new MediaImporter:
          val check = MediaProvider.Commons
          val title = "Wikidata pictures"
          val over = "places and events"
          val batch = cfg.importBatch
          def targets(place: Option[String], event: Option[String], limit: Int, refresh: Boolean, since: Option[Long]) =
            MediaRepo.targets(ds, check, place, event, limit, refresh, since)
          def pending(since: Option[Long]) = MediaRepo.pending(ds, check, since)
          def start() =
            val fetch = Polite(http, cfg)
            val w = WikidataCommons(fetch, cfg)
            new Run:
              def run(ts: List[Target], dryRun: Boolean, refresh: Boolean) = w.run(ts, dryRun, refresh, store)
              def requests = fetch.requests)
      case Depicts =>
        Right(new MediaImporter:
          val check = CommonsDepictions.Check
          val title = "Commons depictions"
          val over = "events"
          val batch = cfg.importBatch
          def targets(place: Option[String], event: Option[String], limit: Int, refresh: Boolean, since: Option[Long]) =
            MediaRepo.collectionTargets(ds, check, places = false, place, event, limit, refresh, since)
          def pending(since: Option[Long]) = MediaRepo.collectionPending(ds, check, places = false, since)
          def start() =
            val fetch = Polite(http, cfg)
            val d = CommonsDepictions(fetch, cfg)
            new Run:
              def run(ts: List[Target], dryRun: Boolean, refresh: Boolean) = d.run(ts, dryRun, refresh, store)
              def requests = fetch.requests)
      case Met       => collection(MetCollection(cfg.met), "Met objects", "media.met", ds, cfg, http, converter, store)
      case Cleveland =>
        collection(
          ClevelandCollection(cfg.cleveland),
          "Cleveland Museum of Art objects",
          "media.cleveland",
          ds,
          cfg,
          http,
          converter,
          store
        )
      case Aic =>
        collection(
          ArtInstituteCollection(cfg.aic, cfg.thumbPx),
          "Art Institute of Chicago objects",
          "media.aic",
          ds,
          cfg,
          http,
          converter,
          store
        )
      case Loc =>
        collection(
          LocCollection(cfg.loc, cfg.thumbPx),
          "Library of Congress maps",
          "media.loc",
          ds,
          cfg,
          http,
          converter,
          store
        )
      case OpenContext =>
        collection(
          OpenContextCollection(cfg.openContext),
          "Open Context media",
          "media.open-context",
          ds,
          cfg,
          http,
          converter,
          store
        )
      case Pleiades =>
        if !cfg.pleiades.enabled then
          Left(s"${MediaProvider.title(MediaProvider.Pleiades)} is switched off (media.pleiades.enabled = false)")
        else
          Right(new MediaImporter:
            val check = MediaProvider.Pleiades
            val title = "Pleiades-linked pictures"
            val over = "places"
            val batch = cfg.importBatch
            def targets(
                place: Option[String],
                event: Option[String],
                limit: Int,
                refresh: Boolean,
                since: Option[Long]
            ) =
              MediaRepo.collectionTargets(
                ds,
                check,
                places = true,
                place,
                event,
                limit,
                refresh,
                since,
                pleiadesOnly = true
              )
            def pending(since: Option[Long]) =
              MediaRepo.collectionPending(ds, check, places = true, since, pleiadesOnly = true)
            def start() =
              // Pleiades' own pace; Commons is asked in batches through the same polite client
              val fetch = Polite(http, cfg.copy(requestIntervalMs = cfg.pleiades.requestIntervalMs))
              val p = PleiadesImages(fetch, cfg)
              new Run:
                def run(ts: List[Target], dryRun: Boolean, refresh: Boolean) = p.run(ts, dryRun, refresh, store)
                def requests = fetch.requests)
      case other => Left(s"unknown media provider '$other' (one of: ${names.mkString(", ")})")

  /** The import of one [[Collection]] (a built-in one, or a plugin's through [[PluginCollection]]): the places not
    * asked yet, at the collection's own pace.
    */
  private[media] def collection(
      col: Collection,
      what: String,
      key: String,
      ds: javax.sql.DataSource,
      cfg: MediaConfig,
      http: HttpTransport,
      converter: ConverterClient,
      store: MediaStore
  ): Either[String, MediaImporter] =
    if !col.conf.enabled then Left(s"${MediaProvider.title(col.provider)} is switched off ($key.enabled = false)")
    else
      Right(new MediaImporter:
        val check = col.provider
        val title = what
        val over = "places"
        val batch = cfg.importBatch
        def targets(place: Option[String], event: Option[String], limit: Int, refresh: Boolean, since: Option[Long]) =
          MediaRepo.collectionTargets(ds, check, places = true, place, event, limit, refresh, since)
        def pending(since: Option[Long]) = MediaRepo.collectionPending(ds, check, places = true, since)
        def start() =
          // the collection's own pace, one host at a time, and its own patience with a refusal (#2561)
          val fetch = Polite(
            http,
            cfg.copy(requestIntervalMs = col.conf.requestIntervalMs),
            refusalBackoffMs = col.conf.refusalBackoffMs
          )
          val imp = CollectionImport(col, fetch, cfg, converter)
          new Run:
            def run(ts: List[Target], dryRun: Boolean, refresh: Boolean) = imp.run(ts, dryRun, refresh, store)
            def requests = fetch.requests)
