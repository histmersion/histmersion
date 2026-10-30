package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `POST /api/merge/plan`: plan an uploaded bundle (by its sha256) with these resolutions. */
final case class MergePlanRequest(bundle: String, resolutions: MergeResolutions = MergeResolutions())
    derives KindlingsCodecAsObject,
      KindlingsSchema
object MergePlanRequest:
  given Schema[MergePlanRequest] = summon[KindlingsSchema[MergePlanRequest]].schema
