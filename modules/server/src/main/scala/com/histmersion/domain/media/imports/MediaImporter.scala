package com.histmersion.domain.media.imports

import hearth.kindlings.optics.syntax.*

import com.histmersion.pluginhost.{HttpTransport, PluginMedia, Polite}

import com.histmersion.config.*
import com.histmersion.plugin.{MediaCollection, MediaMatchRule, MediaTarget}
import com.histmersion.util.ocr.ConverterClient
import com.histmersion.api.media.MediaImportReport

/** One of the imports `media import <provider>` runs (#1446) — what it asks about, what it has still to ask, and a run
  * over a batch of targets. The one-run endpoint and the job share it, so every provider gets the same dry run,
  * `--limit/--place/--event/--refresh`, progress and per-item errors.
  */
trait MediaImporter:
  /** The name `media_checks` records it under (and the report's provider). */
  def check: com.histmersion.domain.media.imports.MediaImporter.CheckName

  /** The job's description: "Wikidata pictures", "Met objects". */
  def title: com.histmersion.domain.Title

  /** What it asks about, for the job's description: "places and events", "places", "events". */
  def over: com.histmersion.domain.media.imports.MediaImporter.OverText

  /** Targets per job batch. */
  def batch: com.histmersion.domain.media.imports.MediaImporter.BatchCount
  def targets(
      place: Option[com.histmersion.domain.catalogue.Place.Id],
      event: Option[com.histmersion.domain.catalogue.Event.Id],
      limit: com.histmersion.domain.media.imports.MediaImporter.TargetsLimit,
      refresh: com.histmersion.domain.media.imports.MediaImporter.Refresh,
      since: Option[com.histmersion.domain.media.imports.MediaImporter.Since]
  ): Either[com.histmersion.domain.media.imports.MediaImporter.TargetsText, List[Target]]
  def pending(since: Option[com.histmersion.domain.media.imports.MediaImporter.Since]): com.histmersion.domain.media.imports.MediaImporter.PendingCount

  /** A run: one polite client for its whole length (the pace is per run). */
  def start(): MediaImporter.Run

object MediaImporter:
  type BatchCount = BatchCount.T
  object BatchCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckName = CheckName.T
  object CheckName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DryRun = DryRun.T
  object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImporterName = ImporterName.T
  object ImporterName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OverText = OverText.T
  object OverText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingCount = PendingCount.T
  object PendingCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Refresh = Refresh.T
  object Refresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RequestCount = RequestCount.T
  object RequestCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Since = Since.T
  object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsText = TargetsText.T
  object TargetsText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type OfTargetsLimit = OfTargetsLimit.T
  object OfTargetsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type TargetsLimit = TargetsLimit.T
  object TargetsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  trait Run:
    def run(targets: List[Target], dryRun: com.histmersion.domain.media.imports.MediaImporter.DryRun, refresh: com.histmersion.domain.media.imports.MediaImporter.Refresh): MediaImportReport
    def requests: com.histmersion.domain.media.imports.MediaImporter.RequestCount


  /** What a collection that asks about `asks` is said to ask about: "places", "events", "places and events". */
  def over(asks: Set[MediaTarget]): com.histmersion.domain.media.imports.MediaImporter.OverText =
    if asks(MediaTarget.Places) && asks(MediaTarget.Events) then com.histmersion.domain.media.imports.MediaImporter.OverText("places and events")
    else if asks(MediaTarget.Events) then com.histmersion.domain.media.imports.MediaImporter.OverText("events")
    else com.histmersion.domain.media.imports.MediaImporter.OverText("places")

  /** The import of the collection registered as `name`, over media's services (`imports` chooses the targets, `store`
    * is where a run writes) and this server's settings (`cfg`, read at each use): the targets it asks about — the
    * places on the map one per Wikidata item and the events with one for a collection that asks about both, the events
    * with an item for one that asks about events, else the places on the map (those carrying a Pleiades id for one that
    * asks for them) — not asked yet, at the collection's own pace and with its own patience with a refusal, run by
    * [[OwnImport]] for a collection of its targets' own pictures and by [[CollectionImport]] for one searched by a
    * place's name.
    */
  def of(
      name: com.histmersion.domain.media.imports.MediaImporter.ImporterName,
      col: MediaCollection,
      title: com.histmersion.domain.Title,
      imports: ImportService,
      store: MediaStore,
      cfg: () => MediaConfig,
      http: HttpTransport,
      converter: ConverterClient
  ): MediaImporter =
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
      def batch = cfg().importBatch
      def targets(place: Option[com.histmersion.domain.catalogue.Place.Id], event: Option[com.histmersion.domain.catalogue.Event.Id], limit: com.histmersion.domain.media.imports.MediaImporter.OfTargetsLimit, refresh: com.histmersion.domain.media.imports.MediaImporter.Refresh, since: Option[com.histmersion.domain.media.imports.MediaImporter.Since]) =
        if both then imports.targets(check, place, event, limit, refresh, since)
        else
          imports.collectionTargets(
            check,
            places = !events,
            place,
            event,
            limit,
            refresh,
            since,
            pleiadesOnly = pleiades
          )
      def pending(since: Option[com.histmersion.domain.media.imports.MediaImporter.Since]) =
        if both then imports.pending(check, since)
        else imports.collectionPending(check, places = !events, since, pleiadesOnly = pleiades)
      def start() =
        val media = cfg()
        // the collection's own pace, one host at a time, and its own patience with a refusal (#2561)
        val fetch = Polite(
          http,
          media
            .modify(_.requestIntervalMs)
            .setTo(PluginMedia.guard(Right(col.requestIntervalMs)).getOrElse(media.requestIntervalMs)),
          refusalBackoffMs = PluginMedia.guard(Right(col.refusalBackoffMs)).getOrElse(Nil)
        )
        val runs: (List[Target], com.histmersion.domain.media.imports.MediaImporter.DryRun, com.histmersion.domain.media.imports.MediaImporter.Refresh) => MediaImportReport =
          if own then OwnImport(name, col, fetch, media).run(_, _, _, store)
          else
            CollectionImport(
              PluginCollection(BuiltinMedia.providerOf(name), col, BuiltinMedia.licencesFor(name, media)),
              fetch,
              media,
              converter
            ).run(_, _, _, store)
        new Run:
          def run(ts: List[Target], dryRun: com.histmersion.domain.media.imports.MediaImporter.DryRun, refresh: com.histmersion.domain.media.imports.MediaImporter.Refresh) = runs(ts, dryRun, refresh)
          def requests = fetch.requests
