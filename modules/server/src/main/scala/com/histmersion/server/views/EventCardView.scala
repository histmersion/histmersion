package com.histmersion.server.views

import com.histmersion.api.catalogue.EventEntry
import com.histmersion.api.catalogue.{EventArticleRef, EventPatch, EventPut}
import com.histmersion.api.views.EventDetail
import com.histmersion.api.media.MediaThumb
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.events.EventsControllers
import hearth.kindlings.optics.syntax.*
import io.scalaland.chimney.dsl.*
import com.histmersion.api.Api

/** An event's page as the catalogue, the pictures and the articles show it together: the catalogue's event with its
  * evidence and context (the events' controllers, whose card [[EventCardView.cited]] gives the articles citing the
  * sources that attest it), and its pictures. What `GET`, `PUT` and `PATCH /api/events/{id}` and the `events` and
  * `patch_event` tools answer.
  */
final class EventCardView(events: EventsControllers, pictures: EventCardView.PicturesArg1 => List[MediaThumb]):

  /** The catalogue's event with its pictures. */
  def page(e: EventEntry): EventDetail =
    e.into[EventDetail].withFieldConst(_.media, pictures(e.event.id)).transform

  def get(id: com.histmersion.server.views.EventCardView.GetId): Either[Api.Error, EventDetail] = events.get(id).map(page)

  def put(caller: Caller, id: com.histmersion.server.views.EventCardView.PutId, in: EventPut): Either[Api.Error, EventDetail] =
    events.put(caller, id, in).map(page)

  def patch(caller: Caller, id: com.histmersion.server.views.EventCardView.PatchId, p: EventPatch): Either[Api.Error, EventDetail] =
    events.patch(caller, id, p).map(page)

object EventCardView:

  /** An event's card: the catalogue's context (`context`), then the articles citing the sources that attest it. */
  type PicturesArg1 = PicturesArg1.T
  object PicturesArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type GetId = GetId.T
  object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutId = PutId.T
  object PutId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchId = PatchId.T
  object PatchId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedCitingArg1 = CitedCitingArg1.T
  object CitedCitingArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedCitingArg2 = CitedCitingArg2.T
  object CitedCitingArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def cited(context: EventEntry => EventEntry, citing: (List[com.histmersion.server.views.EventCardView.CitedCitingArg1], com.histmersion.server.views.EventCardView.CitedCitingArg2) => List[EventArticleRef])(
      e: EventEntry
  ): EventEntry =
    val withContext = context(e)
    withContext.modify(_.articles).setTo(citing(withContext.sources.map(_.citekey), 10))
