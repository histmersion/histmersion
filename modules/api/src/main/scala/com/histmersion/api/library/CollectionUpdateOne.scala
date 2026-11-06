package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given

/** Rewrite the item source `citekey` is linked to from its record. */
final case class CollectionUpdateOne(
    citekey: com.histmersion.api.library.Citekey
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
