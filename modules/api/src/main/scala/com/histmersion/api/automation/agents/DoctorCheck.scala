package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.agents.CheckLevel
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.Display
import com.histmersion.Newtype

final case class DoctorCheck(name: DoctorCheck.Name, level: CheckLevel, detail: DoctorCheck.Detail)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object DoctorCheck:
  type Name = Name.T
  object Name extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Detail = Detail.T
  object Detail extends Newtype[String, Pure]
