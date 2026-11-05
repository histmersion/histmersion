package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.ItemKind

final case class ReadMark(
    kind: ItemKind,
    id: ItemKey,
    title: String,
    read: Boolean = false,
    readAt: Option[String] = None,
    openedAt: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object ReadMark:


  final case class Set(kind: ItemKind, id: ItemKey, read: Boolean, title: Option[String] = None)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
