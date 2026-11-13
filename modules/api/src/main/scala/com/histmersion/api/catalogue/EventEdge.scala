package com.histmersion
package api.catalogue
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure

final case class EventEdge(
    srcId: Event.Id,
    dstId: Event.Id,
    kind: EventEdge.Kind,
    citekey: Citekey,
    note: Option[EventEdge.Note] = None
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object EventEdge:
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]
