package com.histmersion
package api.json
import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema

import api.problem.Text.sentence
import util.problem.{Legacy, Problem}

/** The circe codec and tapir schema of every newtype (`type X = X.T; object X extends Newtype[Base, C]`), from its base
  * type's: the same JSON and the same schema, with the constraint checked on decoding. A newtype whose decoding error
  * text is part of the contract keeps a hand-written instance instead (`Citekey.parse`).
  *
  * An unconstrained (`Pure`) newtype — every one of the wire contract's — decodes as its base type does, with no check:
  * there is nothing to check, and not calling Iron's `either` keeps every companion's runtime constraint out of the web
  * page's bundle.
  *
  * Until the wire carries problems as cases (G60 step 5), a [[Problem]] in a wire record is the string its sentence is,
  * as the String `Problem` newtypes it replaced were: the same JSON and schema, read back as the unclassified text.
  */
object NewtypeCodecs:
  given encoder: [T] => (m: RefinedType.Mirror[T]) => (base: Encoder[m.BaseType]) => Encoder[T] =
    base.asInstanceOf[Encoder[T]]

  inline given decoder: [T] => (m: RefinedType.Mirror[T]) => (base: Decoder[m.BaseType]) => Decoder[T] =
    compiletime.summonFrom {
      case _: (m.ConstraintType =:= Pure) => base.asInstanceOf[Decoder[T]]
      case _                              => base.emap(m.ops.either).asInstanceOf[Decoder[T]]
    }

  given schema: [T] => (m: RefinedType.Mirror[T]) => (base: Schema[m.BaseType]) => Schema[T] =
    base.as[T]

  given problemEncoder: Encoder[Problem] = Encoder.encodeString.contramap(_.sentence)

  given problemDecoder: Decoder[Problem] = Decoder.decodeString.map(text => Problem.Unclassified(Legacy.Text(text)))

  given problemSchema: Schema[Problem] = Schema.schemaForString.as[Problem]
