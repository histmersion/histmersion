package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

// ── the review screen's draft (`merge_sessions`, V051) ───────────────────────────────────────────

/** The decisions made so far on the review screen for one uploaded bundle: kept on the server, so a reload or another
  * browser picks up where the review was, and exported as the resolution file.
  */
final case class MergeSession(
    bundle: String,
    resolutions: MergeResolutions = MergeResolutions(),
    updatedAt: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object MergeSession:
  given Schema[MergeSession] = summon[KindlingsSchema[MergeSession]].schema
