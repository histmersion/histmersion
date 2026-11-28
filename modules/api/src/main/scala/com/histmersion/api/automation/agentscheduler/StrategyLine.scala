package com.histmersion
package api.automation.agentscheduler

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class StrategyLine(name: StrategyLine.Name, describe: StrategyLine.Describe = StrategyLine.Describe(""))
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object StrategyLine:
  type Name = Name.T
  object Name extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Describe = Describe.T
  object Describe extends Newtype[String, Pure]
