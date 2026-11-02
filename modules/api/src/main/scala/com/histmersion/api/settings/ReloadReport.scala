package com.histmersion.api.settings
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ReloadReport(
    published: Boolean,
    generation: Long,
    applied: List[String] = Nil,
    restartOnly: List[String] = Nil,
    rebuilt: List[String] = Nil,
    plugins: List[String] = Nil,
    draining: List[String] = Nil,
    errors: List[String] = Nil,
    warnings: List[String] = Nil,
    file: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema:
  def ok: Boolean = errors.isEmpty

object ReloadReport:
  given Schema[ReloadReport] = summon[KindlingsSchema[ReloadReport]].schema
