package com.histmersion
package api.library
import scala.annotation.tailrec
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

final case class EdgesResolved(resolved: EdgesResolved.Resolved) derives JsonCodec, KindlingsSchema, api.json.Schema

object EdgesResolved:
  type Resolved = Resolved.T
  object Resolved extends Newtype[Int, Pure]
