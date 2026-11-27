package com.histmersion
package api.library
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.aliases.InstanceInfo

final case class InstanceLabel(label: Option[InstanceLabel.Label]) derives JsonCodec, KindlingsSchema, api.json.Schema

object InstanceLabel:
  export InstanceInfo.Label
