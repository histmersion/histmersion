package com.histmersion.api.library
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

/** The refresh downloads ~30 MB and rewrites ~60 000 rows, far longer than one HTTP request may take, so `POST` starts
  * it and `GET` is polled until `running` is false.
  */
final case class RetractionsUpdate(
    rows: RetractionsUpdate.Rows,
    source: RetractionsUpdate.Source,
    running: RetractionsUpdate.Running = _root_.com.histmersion.api.library.RetractionsUpdate.Running(false),
    error: Option[RetractionsUpdate.Error] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema

object RetractionsUpdate:
  type Rows = Rows.T
  object Rows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Source = Source.T
  object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Running = Running.T
  object Running extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Error = Error.T
  object Error extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
