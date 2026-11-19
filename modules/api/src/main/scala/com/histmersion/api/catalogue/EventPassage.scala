package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class EventPassage(
    start: EventPassage.Start,
    end: EventPassage.End,
    quote: EventPassage.Quote,
    name: EventPassage.Name,
    context: EventPassage.Context = EventPassage.Context("")
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object EventPassage:
  type Start = Start.T
  object Start extends Newtype[Int, Pure]
  type End = End.T
  object End extends Newtype[Int, Pure]
  type Quote = Quote.T
  object Quote extends Newtype[String, Pure]
  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Context = Context.T
  object Context extends Newtype[String, Pure]
