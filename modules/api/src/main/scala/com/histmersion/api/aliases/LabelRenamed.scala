package com.histmersion.api.aliases

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.exchange.Bundle

final case class LabelRenamed(entity: Bundle.Kind, from: LabelRenamed.From, to: LabelRenamed.To, rewritten: List[LabelRenamed.Rewritten] = Nil)
    derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object LabelRenamed:
  export com.histmersion.api.library.Label.{Text as From, Text as To}
  type Rewritten = Rewritten.T
  object Rewritten extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
