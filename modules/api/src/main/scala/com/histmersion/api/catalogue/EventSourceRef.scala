package com.histmersion
package api.catalogue
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

final case class EventSourceRef(
    citekey: Citekey,
    title: Option[EventSourceRef.Title] = None,
    year: Option[Year] = None,
    via: EventSourceRef.Via = EventSourceRef.Via("citekey"),
    quote: Option[EventSourceRef.Quote] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object EventSourceRef:
  type Title = Title.T
  object Title extends Newtype[String, Pure]
  type Via = Via.T
  object Via extends Newtype[String, Pure]
  export EventPassage.Quote
