package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `POST /api/bundle/upload`: a bundle kept on the server under its sha256, so it can be planned and imported (with
  * resolutions) by name, and an MCP client can plan it without sending a file.
  */
final case class BundleUpload(sha256: String, bytes: Long, manifest: Option[BundleManifest], problems: List[String])
    derives KindlingsCodecAsObject,
      KindlingsSchema
object BundleUpload:
  given Schema[BundleUpload] = summon[KindlingsSchema[BundleUpload]].schema
