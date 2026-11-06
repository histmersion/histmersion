package com.histmersion.api.library
import com.histmersion.api.given_Configuration
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Add source `citekey`'s stored original to a collection as a new item — or with `replace`, swap the file of the item
  * it is linked to.
  */
final case class CollectionPushRequest(
    citekey: com.histmersion.api.library.Citekey,
    replace: CollectionPushRequest.Replace = false
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object CollectionPushRequest:
  type Replace = Replace.T
  object Replace extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
