package com.histmersion
package api.json
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonKeyCodec, JsonValueCodec}
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema
import hearth.kindlings.tapirschemaderivation.KindlingsSchema

import hearth.kindlings.jsoniterjson.Json
import util.json.{JsonCodec, RawJson}
import util.problem.Problem
import api.problem.{Answer, ProblemJson}
import api.problem.ProblemJson.json

/** The JSON codec, key codec and tapir schema of every newtype (`type X = X.T; object X extends Newtype[Base, C]`),
  * from its base type's: the same JSON and the same schema, with the constraint checked on decoding. Importing its
  * givens also brings the base types', the containers' and raw JSON's ([[JsonCodec]], [[RawJson]]).
  *
  * An unconstrained (`Pure`) newtype — every one of the wire contract's — decodes as its base type does, with no check:
  * there is nothing to check, and not calling Iron's `either` keeps every companion's runtime constraint out of the web
  * page's bundle.
  *
  * A [[Problem]] in a wire record is written typed, as a failed request's body lists one ([[Answer.Entry]]).
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

  /** A problem in a wire record: its group, its case, its fields and its English sentence (`{"group", "case", "data",
    * "text"}`, an [[Answer.Entry]]), so a reader keeps its type and a person still reads the sentence. The JVM reads
    * and writes the case; the browser, which only shows the sentence, reads the `text` alone ([[ProblemJson]]).
    *
    * Every problem is written as its entry, in stored columns and records too. A bare string is how a master version
    * kept a problem's text, in stored and checkpointed JSON: it reads through [[api.problem.StoredShapes]] (the shapes
    * a master version wrote; any other text as [[Problem.Stored.Unrecognised]], kept whole).
    */
  given problemCodec: JsonValueCodec[Problem] =
    JsonCodec.mapped[Problem, Json](_.json, ProblemJson.read)

  /** A problem's schema: the typed entry (named `Problem`, apart from the other records called `Entry`). */
  given problemSchema: Schema[Problem] =
    summon[KindlingsSchema[Answer.Entry]].schema.name(Schema.SName("Problem")).as[Problem]

  /** Any JSON value (as tapir's circe module described circe's `Json`). */
  given jsonSchema: Schema[Json] = Schema.any
