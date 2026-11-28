package com.histmersion
package api.library.workflows

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure

final case class Notes(notes: Notes.Value) derives JsonCodec, KindlingsSchema, Schema

object Notes:
  type Value = Value.T
  object Value extends Newtype[String, Pure]
