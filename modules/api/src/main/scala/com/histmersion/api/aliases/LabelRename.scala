package com.histmersion.api.aliases
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class LabelRename(to: LabelRename.To) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object LabelRename:
  type To = com.histmersion.api.library.Label.Text
  val To: _root_.com.histmersion.api.library.Label.Text.type = _root_.com.histmersion.api.library.Label.Text
