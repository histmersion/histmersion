package com.histmersion
package api.exchange
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class IssueDetail(
    fields: List[IssueField] = Nil,
    text: Option[IssueText] = None,
    place: Option[IssuePlace] = None
) derives JsonCodec,
      KindlingsSchema,
      com.histmersion.api.json.Schema
