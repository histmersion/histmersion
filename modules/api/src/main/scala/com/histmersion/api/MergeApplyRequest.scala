package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── merge apply (§6.1, §6.4; milestone M5) ────────────────────────────────────────────────────────

/** `POST /api/merge/apply`: carry out the plan of an uploaded bundle. `planHash` is the hash of the plan the user
  * reviewed: the server plans again and refuses when the hash differs (the collection, the bundle or the resolutions
  * changed since).
  */
final case class MergeApplyRequest(bundle: String, planHash: String, resolutions: MergeResolutions = MergeResolutions())
    derives KindlingsCodecAsObject,
      KindlingsSchema
object MergeApplyRequest:
  given Schema[MergeApplyRequest] = summon[KindlingsSchema[MergeApplyRequest]].schema
