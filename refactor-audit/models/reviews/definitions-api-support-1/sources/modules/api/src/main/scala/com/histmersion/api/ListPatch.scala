package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

final case class ListPatch(
    add: List[ListPatch.Add] = Nil,
    remove: List[ListPatch.Remove] = Nil,
    replace: Option[List[ListPatch.Replace]] = None
) derives KindlingsSchema, com.histmersion.api.json.Schema
object ListPatch:


  type Add = Add.T
  object Add extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Remove = Remove.T
  object Remove extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Replace = Replace.T
  object Replace extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  given Codec.AsObject[ListPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[ListPatch])
