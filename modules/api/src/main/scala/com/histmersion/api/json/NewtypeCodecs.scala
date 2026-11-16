package com.histmersion
package api.json
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonKeyCodec, JsonValueCodec}
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.{Schema, SchemaType}
import hearth.kindlings.tapirschemaderivation.KindlingsSchema

import hearth.kindlings.jsoniterjson.Json
import util.json.{JsonCodec, RawJson}
import util.problem.Problem
import util.json.RawJson.*
import api.problem.{Answer, Column}
import api.problem.Answer.{entry, problem}
import api.problem.Column.stored

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
    * "text"}`, an [[Answer.Entry]]), so a reader keeps its type and a person still reads the sentence.
    *
    * A bare string is how records carried a problem before (G60 step 4), in stored and checkpointed JSON too: it reads
    * as the case whose frozen column sentence it is ([[Column.read]]), any other text as
    * [[Problem.Stored.Unrecognised]], which is written back as the same bare string, so such a record keeps its bytes.
    */
  given problemCodec: JsonValueCodec[Problem] =
    JsonCodec.mapped[Problem, Json](
      {
        case unrecognised: Problem.Stored.Unrecognised => Json.fromString(unrecognised.stored)
        case typed                                     => typed.entry.asJson
      },
      json => json.asString.fold(json.as[Answer.Entry].flatMap(_.problem))(text => Right(Column.read(text)))
    )

  /** A problem's schema: the typed entry (named `ProblemEntry`, apart from the other records called `Entry`), or the
    * bare string an unrecognised stored text is written as.
    */
  given problemSchema: Schema[Problem] =
    Schema(
      SchemaType.SCoproduct[Problem](
        List(Schema.schemaForString, summon[KindlingsSchema[Answer.Entry]].schema.name(Schema.SName("ProblemEntry"))),
        None
      )(_ => None),
      Some(Schema.SName("Problem"))
    )

  /** Any JSON value (as tapir's circe module described circe's `Json`). */
  given jsonSchema: Schema[Json] = Schema.any
