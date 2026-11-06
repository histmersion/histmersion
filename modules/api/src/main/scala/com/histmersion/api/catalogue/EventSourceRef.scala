package com.histmersion.api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EventSourceRef(
    citekey: com.histmersion.api.library.Citekey,
    title: Option[EventSourceRef.Title] = None,
    year: Option[com.histmersion.api.catalogue.Year] = None,
    via: EventSourceRef.Via = _root_.com.histmersion.api.catalogue.EventSourceRef.Via("citekey"),
    quote: Option[EventSourceRef.Quote] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object EventSourceRef:
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Via = Via.T
  object Via extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Quote = com.histmersion.api.catalogue.EventPassage.Quote
  val Quote: _root_.com.histmersion.api.catalogue.EventPassage.Quote.type = _root_.com.histmersion.api.catalogue.EventPassage.Quote
