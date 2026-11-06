package com.histmersion.api.personal

import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.ItemKind
import com.histmersion.api.json.NewtypeCodecs.given

final case class ReadOpened(kind: ItemKind, id: ItemKey, title: Option[com.histmersion.api.library.Title] = None)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
