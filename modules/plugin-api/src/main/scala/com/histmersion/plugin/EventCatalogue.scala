package com.histmersion
package plugin

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Event catalogues, the third part of the app that plugins extend: a catalogue of events — eruptions, earthquakes,
  * battles, treaties — that `histmersion events import <id>` reads into the map as RESEARCH LEADS (drawn lighter than
  * the events a source in the corpus attests). A plugin adds one or more [[EventCatalogue]]s to
  * [[Plugin.Context.eventCatalogues]].
  *
  * The plugin READS: it splits one import into parts and returns each part's records. The host owns the rest: which
  * parts are read (a part read before is not read again unless `--refresh`), the years and the `--bbox`/size
  * thresholds, holding a dating inside the present, merging a record with the event another catalogue (or an earlier
  * import) already holds, storing it, and the card's provenance line (the catalogue's title, licence and citation).
  */

/** One catalogue. Called by the host from one import at a time; failures are values (`Left`), an exception is caught by
  * the host and reported for that part.
  */
trait EventCatalogue:
  /** What to call it: "Smithsonian Global Volcanism Program". */
  def title: EventCatalogue.TitleResult

  /** The terms its data comes under, as the provider states them. */
  def licence: Licence

  /** How it asks to be cited. */
  def citation: EventCatalogue.CitationResult

  /** Its home page. */
  def home: EventCatalogue.HomeResult

  /** The least time between two requests to one host, in milliseconds (the provider's own terms). */
  def requestIntervalMs: EventCatalogue.RequestIntervalMsResult = EventCatalogue.RequestIntervalMsResult(1000)

  /** The earliest year any of its events can be, when the catalogue covers a period that starts there — as the
    * Smithsonian's list is of HOLOCENE eruptions (`events.gvp.floor-year`). A dating whose uncertainty reaches further
    * back is held at it by the host. `None` (the default): no floor.
    */
  def floorYear: Option[Year] = None

  /** The parts of one import, in order (time windows, classes, datasets) — each a stable key, so a part read and
    * recorded is not asked again by the next import.
    */
  def parts(query: CatalogueQuery): List[EventCatalogue.PartsResult]

  /** The records of one part. `http` is the host's polite client of this import. */
  def read(part: EventCatalogue.ReadPart, query: CatalogueQuery, http: Plugin.Http): Either[EventCatalogue.ReadResultPart1, CataloguePage]

  /** Whether every part is read at every import, whatever an earlier import read: for a catalogue whose parts are pages
    * of a list that changes (page 2 of the newest records is other records next week), so a part's key does not stand
    * for the same records next time. `false` (the default): a part read and recorded is not read again.
    */
  def everyPartEachImport: EventCatalogue.EveryPartEachImportResult = EventCatalogue.EveryPartEachImportResult(false)

  /** One stored record read again, without the network, for `histmersion events rederive`: its dating (and what the
    * catalogue derives with it) computed again from the catalogue's own record ([[CatalogueEvent.raw]]) by today's
    * rules, as [[read]] would make it now, keeping what an import added to it (other names). `stored` is the record as
    * the host keeps it, its id, kind and raw text included. `None` (the default): the catalogue does not re-read; the
    * host derives the stored record's dating as at the import.
    */
  def reread(stored: CatalogueEvent): Option[CatalogueEvent] = None

object EventCatalogue:
  type TitleResult = TitleResult.T
  object TitleResult extends RefinedType[String, Pure]
  type CitationResult = CitationResult.T
  object CitationResult extends RefinedType[String, Pure]
  type HomeResult = HomeResult.T
  object HomeResult extends RefinedType[String, Pure]
  type RequestIntervalMsResult = RequestIntervalMsResult.T
  object RequestIntervalMsResult extends RefinedType[Int, Pure]
  type PartsResult = PartsResult.T
  object PartsResult extends RefinedType[String, Pure]
  type ReadResultPart1 = ReadResultPart1.T
  object ReadResultPart1 extends RefinedType[String, Pure]
  type ReadPart = ReadPart.T
  object ReadPart extends RefinedType[String, Pure]
  type EveryPartEachImportResult = EveryPartEachImportResult.T
  object EveryPartEachImportResult extends RefinedType[Boolean, Pure]
