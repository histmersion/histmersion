package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given

/** Rewrite the item source `citekey` is linked to from its record. */
final case class CollectionUpdateOne(
    citekey: Citekey
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema
