package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class CandidateMark(ids: List[com.histmersion.api.library.Source.Candidate.Id], state: com.histmersion.api.library.Source.Candidate.State) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema
