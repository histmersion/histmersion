package com.histmersion
package api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure

/** The refresh downloads ~30 MB and rewrites ~60 000 rows, far longer than one HTTP request may take, so `POST` starts
  * it and `GET` is polled until `running` is false.
  */
final case class RetractionsUpdate(
    rows: RetractionsUpdate.Rows,
    source: RetractionsUpdate.Source,
    running: RetractionsUpdate.Running = RetractionsUpdate.Running(false),
    error: Option[RetractionsUpdate.Error] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object RetractionsUpdate:
  type Rows = Rows.T
  object Rows extends Newtype[Int, Pure]
  type Source = Source.T
  object Source extends Newtype[String, Pure]
  type Running = Running.T
  object Running extends Newtype[Boolean, Pure]
  type Error = Error.T
  object Error extends Newtype[String, Pure]
