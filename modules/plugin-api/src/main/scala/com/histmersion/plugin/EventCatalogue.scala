package com.histmersion
package plugin

import com.histmersion.util.problem.Problem
import io.github.iltotore.iron.constraint.any.Pure

/** One catalogue. Called by the host from one import at a time; failures are values (`Left`), an exception is caught by
  * the host and reported for that part.
  */
trait EventCatalogue:
  def title: EventCatalogue.Title

  def licence: Licence

  def citation: EventCatalogue.Citation

  def home: EventCatalogue.Home

  /** The least time between two requests to one host, in milliseconds (the provider's own terms). */
  def requestIntervalMs: EventCatalogue.RequestIntervalMs = EventCatalogue.RequestIntervalMs(1000)

  /** The earliest year any of its events can be, when the catalogue covers a period that starts there — as the
    * Smithsonian's list is of HOLOCENE eruptions (`events.gvp.floor-year`). A dating whose uncertainty reaches further
    * back is held at it by the host. `None` (the default): no floor.
    */
  def floorYear: Option[Year] = None

  /** The parts of one import, in order (time windows, classes, datasets) — each a stable key, so a part read and
    * recorded is not asked again by the next import.
    */
  def parts(query: CatalogueQuery): List[EventCatalogue.ReadPart]

  /** The records of one part. `http` is the host's polite client of this import. */
  def read(
      part: EventCatalogue.ReadPart,
      query: CatalogueQuery,
      http: Plugin.Http
  ): Either[Problem, CataloguePage]

  /** Whether every part is read at every import, whatever an earlier import read: for a catalogue whose parts are pages
    * of a list that changes (page 2 of the newest records is other records next week), so a part's key does not stand
    * for the same records next time. `false` (the default): a part read and recorded is not read again.
    */
  def everyPartEachImport: EventCatalogue.EveryPartEachImport = EventCatalogue.EveryPartEachImport(false)

  /** One stored record read again, without the network, for `histmersion events rederive`: its dating (and what the
    * catalogue derives with it) computed again from the catalogue's own record ([[CatalogueEvent.raw]]) by today's
    * rules, as [[read]] would make it now, keeping what an import added to it (other names). `stored` is the record as
    * the host keeps it, its id, kind and raw text included. `None` (the default): the catalogue does not re-read; the
    * host derives the stored record's dating as at the import.
    */
  def reread(stored: CatalogueEvent): Option[CatalogueEvent] = None

object EventCatalogue:
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Citation = Citation.T
  object Citation extends Newtype[String, Pure]
  type Home = Home.T
  object Home extends Newtype[String, Pure]
  type RequestIntervalMs = RequestIntervalMs.T
  object RequestIntervalMs extends Newtype[Int, Pure]
  type ReadPart = ReadPart.T
  object ReadPart extends Newtype[String, Pure]
  type EveryPartEachImport = EveryPartEachImport.T
  object EveryPartEachImport extends Newtype[Boolean, Pure]
