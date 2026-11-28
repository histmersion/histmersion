package com.histmersion
package api.automation.activity

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.Paging

final case class Page(entries: List[Entry], next: Option[Page.Next] = None, counts: Counts = Counts())
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object Page:
  export api.Paging.Cursor as Next
