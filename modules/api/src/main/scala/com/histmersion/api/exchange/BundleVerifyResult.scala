package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class BundleVerifyResult(
    ok: Boolean,
    manifest: Option[BundleManifest],
    blobsChecked: Int,
    problems: List[String]
) derives KindlingsCodecAsObject,
      KindlingsSchema
object BundleVerifyResult:
  given Schema[BundleVerifyResult] = summon[KindlingsSchema[BundleVerifyResult]].schema
