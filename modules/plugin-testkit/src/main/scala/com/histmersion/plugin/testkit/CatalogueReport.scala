package com.histmersion.plugin.testkit

import com.histmersion.plugin.*

/** One import of an event catalogue, read as the server reads it: its parts, each part page by page.
  *
  * @param pages
  *   each page read (`part → events on it`, or why it could not be read)
  * @param accepted
  *   the events the server would store
  * @param refused
  *   the records it would refuse, with the reason (no id, a dating that runs backwards, …)
  */
final case class CatalogueReport(
    parts: List[String],
    pages: List[(String, Either[String, Int])],
    accepted: List[AcceptedEvent],
    refused: List[String],
    failure: Option[String],
    log: List[LogLine],
    requests: List[String]
)
