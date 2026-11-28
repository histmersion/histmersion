package com.histmersion
package api.automation.activity

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class Counts(
    running: Counts.Running = Counts.Running(0),
    problemsWeek: Counts.ProblemsWeek = Counts.ProblemsWeek(0)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object Counts:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type Running = Running.T
  object Running extends Newtype[Int, Pure]
  type ProblemsWeek = ProblemsWeek.T
  object ProblemsWeek extends Newtype[Int, Pure]
