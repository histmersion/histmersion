package com.histmersion
package api.automation.agentscheduler

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class SchedulerSet(strategy: SchedulerSet.Strategy, by: Option[SchedulerSet.By] = None)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object SchedulerSet:
  type Strategy = Strategy.T
  object Strategy extends Newtype[String, Pure]
  type By = By.T
  object By extends Newtype[String, Pure]
