package com.histmersion
package api.json
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonKeyCodec, JsonValueCodec}
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema

import hearth.kindlings.jsoniterjson.Json
import util.json.{JsonCodec, RawJson}
import api.problem.Text.sentence
import util.problem.{Legacy, Problem}

/** The JSON codec, key codec and tapir schema of every newtype (`type X = X.T; object X extends Newtype[Base, C]`),
  * from its base type's: the same JSON and the same schema, with the constraint checked on decoding. Importing its
  * givens also brings the base types', the containers' and raw JSON's ([[JsonCodec]], [[RawJson]]).
  *
  * An unconstrained (`Pure`) newtype — every one of the wire contract's — decodes as its base type does, with no check:
  * there is nothing to check, and not calling Iron's `either` keeps every companion's runtime constraint out of the web
  * page's bundle.
  *
  * Until the wire carries problems as cases (G60 step 5), a [[Problem]] in a wire record is the string its sentence is,
  * as the String `Problem` newtypes it replaced were: the same JSON and schema, read back as the unclassified text.
  */
object NewtypeCodecs:
  export JsonCodec.given

  inline given codec: [T] => (m: RefinedType.Mirror[T]) => (base: JsonValueCodec[m.BaseType]) => JsonValueCodec[T] =
    compiletime.summonFrom {
      case _: (m.ConstraintType =:= Pure) => base.asInstanceOf[JsonValueCodec[T]]
      case _                              =>
        JsonCodec
          .mapped[m.BaseType, m.BaseType](identity, b => m.ops.either(b).map(_ => b))(using base)
          .asInstanceOf[JsonValueCodec[T]]
    }

  given key: [T] => (m: RefinedType.Mirror[T]) => (base: JsonKeyCodec[m.BaseType]) => JsonKeyCodec[T] =
    base.asInstanceOf[JsonKeyCodec[T]]

  given schema: [T] => (m: RefinedType.Mirror[T]) => (base: Schema[m.BaseType]) => Schema[T] =
    base.as[T]

  given problemCodec: JsonValueCodec[Problem] =
    JsonCodec.text(_.sentence, text => Right(Problem.Unclassified(Legacy.Text(text))))

  given problemSchema: Schema[Problem] = Schema.schemaForString.as[Problem]

  /** Any JSON value (as tapir's circe module described circe's `Json`). */
  given jsonSchema: Schema[Json] = Schema.any
