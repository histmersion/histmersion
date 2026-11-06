package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given

final case class ListPatch(
    add: List[ListPatch.Add] = Nil,
    remove: List[ListPatch.Remove] = Nil,
    replace: Option[List[ListPatch.Replace]] = None
) derives KindlingsSchema, com.histmersion.api.json.Schema
object ListPatch:


  type Add = com.histmersion.api.Write.ListItem
  val Add: _root_.com.histmersion.api.Write.ListItem.type = _root_.com.histmersion.api.Write.ListItem
  type Remove = com.histmersion.api.Write.ListItem
  val Remove: _root_.com.histmersion.api.Write.ListItem.type = _root_.com.histmersion.api.Write.ListItem
  type Replace = com.histmersion.api.Write.ListItem
  val Replace: _root_.com.histmersion.api.Write.ListItem.type = _root_.com.histmersion.api.Write.ListItem

  given Codec.AsObject[ListPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[ListPatch])
