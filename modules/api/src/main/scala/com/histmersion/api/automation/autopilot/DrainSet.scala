package com.histmersion
package api.automation.autopilot

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class DrainSet(on: DrainSet.On, by: Option[DrainSet.By] = None)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object DrainSet:
  enum On derives CanEqual:
    case On, Off
  object On extends TwoStates[On]:
    extension (on: On) def on: Boolean = on == On
  type By = By.T
  object By extends Newtype[String, Pure]
