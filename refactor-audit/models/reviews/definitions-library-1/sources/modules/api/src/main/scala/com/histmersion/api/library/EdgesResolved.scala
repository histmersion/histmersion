package com.histmersion.api.library
import scala.annotation.tailrec
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given

final case class EdgesResolved(resolved: EdgesResolved.Resolved) derives KindlingsCodecAsObject, KindlingsSchema, com.histmersion.api.json.Schema

object EdgesResolved:
  type Resolved = Resolved.T
  object Resolved extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
