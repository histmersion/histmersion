package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `/api/health`: each dependency reports ok/error text (`converter` = the OCR sidecar, plan §1.8). `warnings`: what
  * the server runs without but someone should know — a style file refused, say — never a reason for `status` to change.
  */
final case class Health(
    status: String,
    db: String,
    schemaVersion: Option[String],
    converter: Option[String] = None,
    warnings: List[String] = Nil
) derives KindlingsCodecAsObject,
      KindlingsSchema
object Health:
  given Schema[Health] = summon[KindlingsSchema[Health]].schema
