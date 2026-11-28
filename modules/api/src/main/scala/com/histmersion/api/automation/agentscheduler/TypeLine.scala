package com.histmersion
package api.automation.agentscheduler

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.jobs.JobKinds

final case class TypeLine(
    jobType: TypeLine.JobType,
    label: TypeLine.Label = TypeLine.Label(""),
    weight: TypeLine.Weight = TypeLine.Weight(0),
    priority: TypeLine.Priority = TypeLine.Priority(0),
    recent: TypeLine.Recent = TypeLine.Recent(0),
    waiting: TypeLine.Waiting = TypeLine.Waiting(0)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object TypeLine:
  export JobKinds.Wire as JobType
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Weight = Weight.T
  object Weight extends Newtype[Int, Pure]
  type Priority = Priority.T
  object Priority extends Newtype[Int, Pure]
  type Recent = Recent.T
  object Recent extends Newtype[Int, Pure]
  type Waiting = Waiting.T
  object Waiting extends Newtype[Int, Pure]
