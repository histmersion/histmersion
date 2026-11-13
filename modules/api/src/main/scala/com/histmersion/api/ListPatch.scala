package com.histmersion
package api

import scala.annotation.tailrec
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given

final case class ListPatch(
    add: List[ListPatch.Add] = Nil,
    remove: List[ListPatch.Remove] = Nil,
    replace: Option[List[ListPatch.Replace]] = None
) derives StrictJson,
      KindlingsSchema,
      com.histmersion.api.json.Schema
object ListPatch:

  export Write.{ListItem as Add, ListItem as Remove, ListItem as Replace}
