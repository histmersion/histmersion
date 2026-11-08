package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class InstanceLabel(label: Option[InstanceLabel.Label]) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object InstanceLabel:
  type Label = Label.T
  object Label extends Newtype[String, Pure]
