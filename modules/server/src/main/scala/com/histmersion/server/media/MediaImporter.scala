package com.histmersion.server.media

import com.histmersion.pluginhost.{HttpTransport, PluginMedia, Polite}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.MediaRepo
import com.histmersion.db.MediaRepo.Target
import com.histmersion.plugin.{MediaCollection, MediaMatchRule, MediaTarget}
import com.histmersion.server.{ConverterClient, Deps}
import com.histmersion.domain.media.OwnImport

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

  trait Run:
    def run(targets: List[Target], dryRun: Boolean, refresh: Boolean): MediaImportReport
    def requests: Int

  /** What a collection that asks about `asks` is said to ask about: "places", "events", "places and events". */
  def over(asks: Set[MediaTarget]): String =
    if asks(MediaTarget.Places) && asks(MediaTarget.Events) then "places and events"
    else if asks(MediaTarget.Events) then "events"
    else "places"

  /** The import of the collection registered as `name`, over this server's store: the targets it asks about — the
    * places on the map one per Wikidata item and the events with one for a collection that asks about both, the events
    * with an item for one that asks about events, else the places on the map (those carrying a Pleiades id for one that
    * asks for them) — not asked yet, at the collection's own pace and with its own patience with a refusal, run by
    * [[OwnImport]] for a collection of its targets' own pictures and by [[CollectionImport]] for one searched by a
    * place's name.
    */
  def of(
      name: String,
      col: MediaCollection,
      title: String,
      deps: Deps,
      http: HttpTransport,
      converter: ConverterClient
  ): MediaImporter =
    val ds = deps.ds
    val store = DbMediaStore(ds)
    val asks = PluginMedia.guard(Right(col.asks)).getOrElse(Set(MediaTarget.Places))
    val both = asks(MediaTarget.Places) && asks(MediaTarget.Events)
    val events = !asks(MediaTarget.Places)
    val pleiades = PluginMedia.guard(Right(col.placesCarrying)).toOption.flatten.isDefined
    val own = PluginMedia.guard(Right(col.matchRule)).toOption.contains(MediaMatchRule.Own)
    val checkName = BuiltinMedia.checkOf(name)
    val what = title
    new MediaImporter:
      val check = checkName
      val title = what
      val over = MediaImporter.over(asks)
      def batch = deps.cfg.media.importBatch
      def targets(place: Option[String], event: Option[String], limit: Int, refresh: Boolean, since: Option[Long]) =
        if both then MediaRepo.targets(ds, check, place, event, limit, refresh, since)
        else
          MediaRepo.collectionTargets(
            ds,
            check,
            places = !events,
            place,
            event,
            limit,
            refresh,
            since,
            pleiadesOnly = pleiades
          )
      def pending(since: Option[Long]) =
        if both then MediaRepo.pending(ds, check, since)
        else MediaRepo.collectionPending(ds, check, places = !events, since, pleiadesOnly = pleiades)
      def start() =
        val cfg = deps.cfg.media
        // the collection's own pace, one host at a time, and its own patience with a refusal (#2561)
        val fetch = Polite(
          http,
          cfg.copy(requestIntervalMs =
            PluginMedia.guard(Right(col.requestIntervalMs)).getOrElse(cfg.requestIntervalMs)
          ),
          refusalBackoffMs = PluginMedia.guard(Right(col.refusalBackoffMs)).getOrElse(Nil)
        )
        val runs: (List[Target], Boolean, Boolean) => MediaImportReport =
          if own then OwnImport(name, col, fetch, cfg).run(_, _, _, store)
          else
            CollectionImport(
              PluginCollection(BuiltinMedia.providerOf(name), col, BuiltinMedia.licencesFor(name, cfg)),
              fetch,
              cfg,
              converter
            ).run(_, _, _, store)
        new Run:
          def run(ts: List[Target], dryRun: Boolean, refresh: Boolean) = runs(ts, dryRun, refresh)
          def requests = fetch.requests
