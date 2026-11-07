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
  def batch: MediaConfig.ImportBatch
  def targets(
      place: Option[com.histmersion.domain.catalogue.Place.Id],
      event: Option[com.histmersion.domain.catalogue.Event.Id],
      limit: com.histmersion.domain.media.imports.ImportService.TargetsLimit,
      refresh: com.histmersion.domain.media.imports.ImportService.Refresh,
      since: Option[com.histmersion.domain.media.imports.ImportService.Since]
  ): Either[com.histmersion.domain.media.imports.ImportService.TargetsText, List[Target]]
  def pending(since: Option[com.histmersion.domain.media.imports.ImportService.Since]): com.histmersion.domain.media.imports.ImportService.PendingCount

  /** A run: one polite client for its whole length (the pace is per run). */
  def start(): MediaImporter.Run

object MediaImporter:
  type CheckName = CheckName.T
  object CheckName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DryRun = DryRun.T
  object DryRun extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ImporterName = ImporterName.T
  object ImporterName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OverText = OverText.T
  object OverText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  trait Run:
    def run(targets: List[Target], dryRun: com.histmersion.domain.media.imports.MediaImporter.DryRun, refresh: com.histmersion.domain.media.imports.ImportService.Refresh): MediaImportReport
    def requests: com.histmersion.pluginhost.Polite.RequestCount

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
    val checkName = MediaImporter.CheckName(BuiltinMedia.checkOf(BuiltinMedia.CheckOfName(name.value)).value)
    val what = title
    new MediaImporter:
      val check = checkName
      val title = what
      val over = MediaImporter.over(asks)
      def batch = cfg().importBatch
      def targets(place: Option[com.histmersion.domain.catalogue.Place.Id], event: Option[com.histmersion.domain.catalogue.Event.Id], limit: com.histmersion.domain.media.imports.ImportService.TargetsLimit, refresh: com.histmersion.domain.media.imports.ImportService.Refresh, since: Option[com.histmersion.domain.media.imports.ImportService.Since]) =
        if both then imports.targets(ImportService.Provider(check.value), place, event, limit, refresh, since)
        else
          imports.collectionTargets(
            ImportService.CheckName(check.value),
            places = ImportService.Places(!events),
            place,
            event,
            ImportService.CollectionTargetsLimit(limit.value),
            refresh,
            since,
            pleiadesOnly = ImportService.PleiadesOnly(pleiades)
          )
      def pending(since: Option[com.histmersion.domain.media.imports.ImportService.Since]) =
        if both then imports.pending(ImportService.Provider(check.value), since)
        else
          imports.collectionPending(
            ImportService.CheckName(check.value),
            places = ImportService.Places(!events),
            since,
            pleiadesOnly = ImportService.PleiadesOnly(pleiades)
          )
      def start() =
        val media = cfg()
        // the collection's own pace, one host at a time, and its own patience with a refusal (#2561)
        val fetch = Polite(
          http,
          media
            .modify(_.requestIntervalMs)
            .setTo(
              PluginMedia
                .guard(Right(col.requestIntervalMs))
                .map(ms => MediaConfig.RequestIntervalMs(ms.value))
                .getOrElse(media.requestIntervalMs)
            ),
          refusalBackoffMs =
            PluginMedia.guard(Right(col.refusalBackoffMs)).map(_.map(ms => Polite.RefusalBackoffMs(ms.value))).getOrElse(Nil)
        )
        val runs: (List[Target], com.histmersion.domain.media.imports.MediaImporter.DryRun, com.histmersion.domain.media.imports.ImportService.Refresh) => MediaImportReport =
          if own then OwnImport(name, col, fetch, media).run(_, _, _, store)
          else
            val collection = CollectionImport(
              PluginCollection(
                ImportService.Provider(BuiltinMedia.providerOf(BuiltinMedia.ProviderOfName(name.value)).value),
                col,
                MediaCollectionConfig.Licences(BuiltinMedia.licencesFor(BuiltinMedia.LicencesForName(name.value), media).value)
              ),
              fetch,
              media,
              converter
            )
            (ts, dryRun, refresh) =>
              collection.run(
                ts,
                CollectionImport.DryRun(dryRun.value),
                CollectionImport.Refresh(refresh.value),
                store
              )
        new Run:
          def run(ts: List[Target], dryRun: com.histmersion.domain.media.imports.MediaImporter.DryRun, refresh: com.histmersion.domain.media.imports.ImportService.Refresh) = runs(ts, dryRun, refresh)
          def requests = fetch.requests
