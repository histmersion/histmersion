package com.histmersion
package api.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.json.Schema
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema.annotations.description

final case class Notes(@description("Markdown") text: Notes.Text) derives JsonCodec, KindlingsSchema, Schema

object Notes:
  type Text = Text.T
  object Text extends Newtype[String, Pure]
