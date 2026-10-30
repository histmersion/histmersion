package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `POST /api/merge/import`: import an uploaded bundle, taking the resolutions into account. */
final case class MergeImportRequest(
    bundle: String,
    resolutions: MergeResolutions = MergeResolutions(),
    dryRun: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema
object MergeImportRequest:
  given Schema[MergeImportRequest] = summon[KindlingsSchema[MergeImportRequest]].schema
