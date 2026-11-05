package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class ContradictionsResult(rows: List[ContradictionRow], total: Int)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema
