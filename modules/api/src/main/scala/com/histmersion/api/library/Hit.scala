package com.histmersion
package api.library
import com.histmersion.api.Problem
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class Hit(source: SearchHit, matched: List[Finding.Match], why: List[Problem])
    derives KindlingsCodecAsObject,
      KindlingsSchema,
      com.histmersion.api.json.Schema
