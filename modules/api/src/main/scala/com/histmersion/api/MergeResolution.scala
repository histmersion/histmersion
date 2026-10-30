package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** One line of a resolution file: an issue id and the decision, as the user wrote it (checked by the plan, which
  * reports every bad one at once).
  */
final case class MergeResolution(
    issue: String,
    decision: String,
    /** The merged text of a `file:<path>` decision: the CLI reads the file and sends it, since the server cannot read
      * the user's files.
      */
    text: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object MergeResolution:
  given Schema[MergeResolution] = summon[KindlingsSchema[MergeResolution]].schema
