package com.histmersion
package api.aliases

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.exchange.Bundle
import io.github.iltotore.iron.constraint.any.Pure

final case class LabelRenamed(
    entity: Bundle.Kind,
    from: LabelRenamed.From,
    to: LabelRenamed.To,
    rewritten: List[LabelRenamed.Rewritten] = Nil
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object LabelRenamed:
  export api.library.Label.{Text as From, Text as To}
  type Rewritten = Rewritten.T
  object Rewritten extends Newtype[String, Pure]
