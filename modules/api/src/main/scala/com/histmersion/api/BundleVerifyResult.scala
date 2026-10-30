package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `POST /api/bundle/verify` — every problem found, not just the first. */
final case class BundleVerifyResult(
    ok: Boolean,
    manifest: Option[BundleManifest],
    blobsChecked: Int,
    problems: List[String]
) derives KindlingsCodecAsObject,
      KindlingsSchema
object BundleVerifyResult:
  given Schema[BundleVerifyResult] = summon[KindlingsSchema[BundleVerifyResult]].schema
