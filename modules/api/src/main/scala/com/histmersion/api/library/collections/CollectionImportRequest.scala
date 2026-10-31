package com.histmersion.api.library.collections

import com.histmersion.api.given_Configuration
import com.histmersion.api.*
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Import item `id` of a collection into the library: under `citekey` (else the generated one), its `format` (else the
  * collection's preference), converted unless `convert` is false, with its cover when `cover`, ticking off acquisition
  * item `acquireItem` when the conversion succeeds.
  */
final case class CollectionImportRequest(
    id: String,
    citekey: Option[String] = None,
    format: Option[String] = None,
    convert: Boolean = true,
    cover: Boolean = true,
    acquireItem: Option[Int] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema

object CollectionImportRequest:
  given Schema[CollectionImportRequest] = summon[KindlingsSchema[CollectionImportRequest]].schema
