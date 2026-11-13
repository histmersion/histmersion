package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** Add source `citekey`'s stored original to a collection as a new item — or with `replace`, swap the file of the item
  * it is linked to.
  */
final case class CollectionPushRequest(
    citekey: Citekey,
    replace: CollectionPushRequest.Replace = CollectionPushRequest.Replace(false)
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema

object CollectionPushRequest:
  type Replace = Replace.T
  object Replace extends Newtype[Boolean, Pure]
