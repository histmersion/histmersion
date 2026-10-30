package com.histmersion.api

import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.values.EdgeKind

/** One edge in a patch. It is stored on the PATCHED source, so `src` may be omitted; when given it must name the
  * patched citekey (#698: a different `src` used to be silently replaced, turning a critic → target edge into a
  * self-edge). To record `a --kind--> b`, patch `a`.
  */
final case class EdgeInput(dst: String, kind: EdgeKind, src: Option[String] = None) derives KindlingsSchema
object EdgeInput:
  given Schema[EdgeInput] = summon[KindlingsSchema[EdgeInput]].schema

  /** Strict (#813): an unknown key is refused, not dropped — see [[StrictJson]]. */
  given Codec.AsObject[EdgeInput] = StrictJson.codec(KindlingsCodecAsObject.derived[EdgeInput])
