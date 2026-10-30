package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID

/** `manifest.json`. Deterministic: there is no wall-clock export time — `asOf` is the newest change the bundle carries
  * (the value to pass as the next `--since`), and `bundleId` is derived from the instance and the content, so two
  * exports of an unchanged database are byte-identical.
  */
final case class BundleManifest(
    format: String,
    formatVersion: Int,
    schemaVersion: String,
    bundleId: UUID,
    instance: BundleInstance,
    since: Option[String],
    asOf: Option[String],
    files: List[BundleFileInfo],
    blobs: BundleBlobs,
    reference: BundleReference
) derives KindlingsCodecAsObject,
      KindlingsSchema
object BundleManifest:
  given Schema[BundleManifest] = summon[KindlingsSchema[BundleManifest]].schema
