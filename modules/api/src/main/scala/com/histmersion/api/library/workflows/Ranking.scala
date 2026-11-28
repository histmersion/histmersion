package com.histmersion
package api.library.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema.annotations.description

final case class Ranking(@description("The candidates that belong to the goal, most important first; leave out the rest") picks: List[Pick] = Nil) derives JsonCodec, KindlingsSchema, Schema
