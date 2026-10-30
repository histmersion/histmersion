package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Mark a source or an article read (`read`) or not read yet. `title`: what it is called, as the person sees it.
  */
final case class ReadMarkSet(kind: ItemKind, id: ItemKey, read: Boolean, title: Option[String] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema

object ReadMarkSet:
  given Schema[ReadMarkSet] = summon[KindlingsSchema[ReadMarkSet]].schema
