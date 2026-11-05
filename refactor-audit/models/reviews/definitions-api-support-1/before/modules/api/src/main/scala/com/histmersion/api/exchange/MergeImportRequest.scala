package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class MergeImportRequest(
    bundle: String,
    resolutions: MergeResolutions = MergeResolutions(),
    dryRun: Boolean = false
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
