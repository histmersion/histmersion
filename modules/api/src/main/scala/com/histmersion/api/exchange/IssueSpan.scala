package com.histmersion
package api.exchange
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.catalogue.Year

final case class IssueSpan(start: Option[Year], end: Option[Year])
    derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema
