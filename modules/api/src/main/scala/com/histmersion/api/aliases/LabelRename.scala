package com.histmersion
package api.aliases
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

final case class LabelRename(to: LabelRename.To) derives JsonCodec, KindlingsSchema, com.histmersion.api.json.Schema

object LabelRename:
  export com.histmersion.api.library.Label.Text as To
