package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class CalibrePushRequest(citekey: com.histmersion.api.library.Citekey, calibreId: com.histmersion.api.library.Source.CalibreId) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
