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
  type From = com.histmersion.api.library.Label.Text
  val From: _root_.com.histmersion.api.library.Label.Text.type = _root_.com.histmersion.api.library.Label.Text
  type To = com.histmersion.api.library.Label.Text
  val To: _root_.com.histmersion.api.library.Label.Text.type = _root_.com.histmersion.api.library.Label.Text
  type Rewritten = Rewritten.T
  object Rewritten extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
