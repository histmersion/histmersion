package com.histmersion
package plugin.testkit

import com.histmersion.plugin.*
import com.histmersion.pluginhost.PluginEvents
import io.github.iltotore.iron.constraint.any.Pure

/** One import of an event catalogue, read as the server reads it: its parts, each part page by page.
  *
  * @param pages
  *   each page read (`part → events on it`, or why it could not be read)
  * @param accepted
  *   the events the server would store
  * @param refused
  *   the records it would refuse, with the reason (no id, a dating that runs backwards, …)
  * @param leftOut
  *   the values it would leave out of a record it keeps, with the reason (a position off the globe, a Wikidata id that
  *   is no item)
  */
final case class CatalogueReport(
    parts: List[PluginEvents.PartName],
    pages: List[(PluginEvents.PartName, Either[PluginEvents.Problem, CatalogueReport.Events])],
    accepted: List[AcceptedEvent],
    refused: List[CatalogueReport.Refused],
    leftOut: List[CatalogueReport.LeftOut],
    failure: Option[CatalogueReport.Failure],
    log: List[LogLine],
    requests: List[PluginHarness.Request]
)

object CatalogueReport:
  type Events = Events.T
  object Events extends Newtype[Int, Pure]
  type Refused = Refused.T
  object Refused extends Newtype[String, Pure]
  type LeftOut = LeftOut.T
  object LeftOut extends Newtype[String, Pure]
  type Failure = Failure.T
  object Failure extends Newtype[String, Pure]
