package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema

/** Set-like edit of a string list: `replace` first (when given), then `remove`, then `add`. */
final case class ListPatch(
    add: List[String] = Nil,
    remove: List[String] = Nil,
    replace: Option[List[String]] = None
) derives KindlingsSchema
object ListPatch:
  given Schema[ListPatch] = summon[KindlingsSchema[ListPatch]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[ListPatch] = StrictJson.codec(KindlingsCodecAsObject.derived[ListPatch])
