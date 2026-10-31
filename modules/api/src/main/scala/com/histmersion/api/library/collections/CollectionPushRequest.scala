package com.histmersion.api.library.collections

import com.histmersion.api.given_Configuration
import com.histmersion.api.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Add source `citekey`'s stored original to a collection as a new item — or with `replace`, swap the file of the
  * item it is linked to.
  */
final case class CollectionPushRequest(
    citekey: String,
    replace: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionPushRequest:
  given Schema[CollectionPushRequest] = summon[KindlingsSchema[CollectionPushRequest]].schema
