package com.histmersion.server.views

import com.histmersion.api.{EventArticleRef, EventDetail, MediaThumb}
import hearth.kindlings.optics.syntax.*

/** An event's page as the catalogue, the pictures and the articles show it together: the catalogue's `card` (the
  * event's evidence and context), its pictures, and the articles citing the sources that attest it.
  */
final class EventCardView(
    card: EventDetail => EventDetail,
    pictures: String => List[MediaThumb],
    citing: (List[String], Int) => List[EventArticleRef]
):
  def apply(d: EventDetail): EventDetail =
    val withContext = card(d.modify(_.media).setTo(pictures(d.event.id)))
    withContext.modify(_.articles).setTo(citing(withContext.sources.map(_.citekey), 10))
