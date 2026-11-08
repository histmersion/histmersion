package com.histmersion
package api.json
import io.circe.{Decoder, Encoder}
import io.github.iltotore.iron.RefinedType
import sttp.tapir.Schema

/** The circe codec and tapir schema of every newtype (`type X = X.T; object X extends Newtype[Base, C]`), from its
  * base type's: the same JSON and the same schema, with the constraint checked on decoding. A newtype whose decoding
  * error text is part of the contract keeps a hand-written instance instead (`Citekey.parse`).
  */
object NewtypeCodecs:
  given encoder: [T] => (m: RefinedType.Mirror[T]) => (base: Encoder[m.BaseType]) => Encoder[T] =
    base.asInstanceOf[Encoder[T]]

  given decoder: [T] => (m: RefinedType.Mirror[T]) => (base: Decoder[m.BaseType]) => Decoder[T] =
    base.emap(m.ops.either).asInstanceOf[Decoder[T]]

  given schema: [T] => (m: RefinedType.Mirror[T]) => (base: Schema[m.BaseType]) => Schema[T] =
    base.as[T]
