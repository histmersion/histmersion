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
  def check: com.histmersion.domain.media.imports.MediaImporter.CheckResult

  /** The job's description: "Wikidata pictures", "Met objects". */
  def title: com.histmersion.domain.media.imports.MediaImporter.TitleResult

  /** What it asks about, for the job's description: "places and events", "places", "events". */
  def over: com.histmersion.domain.media.imports.MediaImporter.OverResult

  /** Targets per job batch. */
  def batch: com.histmersion.domain.media.imports.MediaImporter.BatchResult
  def targets(
      place: Option[com.histmersion.domain.media.imports.MediaImporter.TargetsPlace],
      event: Option[com.histmersion.domain.media.imports.MediaImporter.TargetsEvent],
      limit: com.histmersion.domain.media.imports.MediaImporter.TargetsLimit,
      refresh: com.histmersion.domain.media.imports.MediaImporter.TargetsRefresh,
      since: Option[com.histmersion.domain.media.imports.MediaImporter.TargetsSince]
  ): Either[com.histmersion.domain.media.imports.MediaImporter.TargetsResultPart1, List[Target]]
  def pending(since: Option[com.histmersion.domain.media.imports.MediaImporter.PendingSince]): com.histmersion.domain.media.imports.MediaImporter.PendingResult

  /** A run: one polite client for its whole length (the pace is per run). */
  def start(): MediaImporter.Run

object MediaImporter:

  type CheckResult = CheckResult.T
  object CheckResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TitleResult = TitleResult.T
  object TitleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OverResult = OverResult.T
  object OverResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BatchResult = BatchResult.T
  object BatchResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsResultPart1 = TargetsResultPart1.T
  object TargetsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsPlace = TargetsPlace.T
  object TargetsPlace extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsEvent = TargetsEvent.T
  object TargetsEvent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsLimit = TargetsLimit.T
  object TargetsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsRefresh = TargetsRefresh.T
  object TargetsRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsSince = TargetsSince.T
  object TargetsSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingResult = PendingResult.T
  object PendingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingSince = PendingSince.T
  object PendingSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfName = OfName.T
  object OfName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OfTitle = OfTitle.T
  object OfTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  trait Run:
    def run(targets: List[Target], dryRun: com.histmersion.domain.media.imports.MediaImporter.Run.RunDryRun, refresh: com.histmersion.domain.media.imports.MediaImporter.Run.RunRefresh): MediaImportReport
    def requests: com.histmersion.domain.media.imports.MediaImporter.Run.RequestsResult

  object Run:
    type RunDryRun = RunDryRun.T
    object RunDryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RunRefresh = RunRefresh.T
    object RunRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RequestsResult = RequestsResult.T
    object RequestsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** What a collection that asks about `asks` is said to ask about: "places", "events", "places and events". */
  def over(asks: Set[MediaTarget]): com.histmersion.domain.media.imports.MediaImporter.OverResult =
    if asks(MediaTarget.Places) && asks(MediaTarget.Events) then "places and events"
    else if asks(MediaTarget.Events) then "events"
    else "places"

  /** The import of the collection registered as `name`, over media's services (`imports` chooses the targets, `store`
    * is where a run writes) and this server's settings (`cfg`, read at each use): the targets it asks about — the
    * places on the map one per Wikidata item and the events with one for a collection that asks about both, the events
    * with an item for one that asks about events, else the places on the map (those carrying a Pleiades id for one that
    * asks for them) — not asked yet, at the collection's own pace and with its own patience with a refusal, run by
    * [[OwnImport]] for a collection of its targets' own pictures and by [[CollectionImport]] for one searched by a
    * place's name.
    */
  def of(
      name: com.histmersion.domain.media.imports.MediaImporter.OfName,
      col: MediaCollection,
      title: com.histmersion.domain.media.imports.MediaImporter.OfTitle,
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
      def targets(place: Option[String], event: Option[String], limit: Int, refresh: Boolean, since: Option[Long]) =
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
      def pending(since: Option[Long]) =
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
        val runs: (List[Target], Boolean, Boolean) => MediaImportReport =
          if own then OwnImport(name, col, fetch, media).run(_, _, _, store)
          else
            CollectionImport(
              PluginCollection(BuiltinMedia.providerOf(name), col, BuiltinMedia.licencesFor(name, media)),
              fetch,
              media,
              converter
            ).run(_, _, _, store)
        new Run:
          def run(ts: List[Target], dryRun: Boolean, refresh: Boolean) = runs(ts, dryRun, refresh)
          def requests = fetch.requests
