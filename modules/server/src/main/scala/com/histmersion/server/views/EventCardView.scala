package com.histmersion
package server.views

import com.histmersion.api.catalogue.EventEntry
import com.histmersion.api.catalogue.{EventArticleRef, EventPatch, EventPut}
import com.histmersion.api.views.EventDetail
import com.histmersion.api.media.MediaThumb
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.events.EventsControllers
import hearth.kindlings.optics.syntax.*
import io.scalaland.chimney.dsl.*
import com.histmersion.api.Api
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** An event's page as the catalogue, the pictures and the articles show it together: the catalogue's event with its
  * evidence and context (the events' controllers, whose card [[EventCardView.cited]] gives the articles citing the
  * sources that attest it), and its pictures. What `GET`, `PUT` and `PATCH /api/events/{id}` and the `events` and
  * `patch_event` tools answer.
  */
final class EventCardView(events: EventsControllers, pictures: Event.Id => List[MediaThumb]):

  /** The catalogue's event with its pictures. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def page(e: EventEntry): EventDetail =
    e.into[EventDetail].withFieldConst(_.media, pictures(Event.Id.assume(e.event.id.value))).transform

  def get(id: Event.Id): Either[Api.Error, EventDetail] = events.get(id).map(page)

  def put(caller: Caller, id: Event.Id, in: EventPut)(using Creator.New): Either[Api.Error, EventDetail] =
    events.put(caller, id, in).map(page)

  def patch(caller: Caller, id: Event.Id, p: EventPatch)(using Creator.New): Either[Api.Error, EventDetail] =
    events.patch(caller, id, p).map(page)

object EventCardView:
  /** An event's card: the catalogue's context (`context`), then the articles citing the sources that attest it. */

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def cited(context: EventEntry => EventEntry, citing: (List[Citekey], PageSize) => List[EventArticleRef])(
      e: EventEntry
  ): EventEntry =
    val withContext = context(e)
    withContext
      .modify(_.articles)
      .setTo(citing(withContext.sources.map(s => Citekey.assume(s.citekey.value)), PageSize(10)))
