package com.histmersion
package api.library.workflows

import com.histmersion.api.catalogue.Period
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.api.library.Summary
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema.SName
import sttp.tapir.Schema.annotations.customise
import sttp.tapir.Schema.annotations.description

@customise(_.name(SName("Classification")))
final case class ClassifiedSource(@description("3-6 sentences") summary: Summary, @description("null if none") period: Option[Period] = None) derives JsonCodec, KindlingsSchema, Schema
