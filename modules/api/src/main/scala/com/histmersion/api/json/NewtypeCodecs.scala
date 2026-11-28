package com.histmersion
package api.json
import com.histmersion.api.given
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonKeyCodec, JsonReader, JsonValueCodec, JsonWriter}
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.{Codec, CodecFormat, Schema}
import hearth.kindlings.tapirschemaderivation.KindlingsSchema

import hearth.kindlings.jsoniterjson.Json
import util.json.{JsonCodec, RawJson}
import util.problem.Problem
import util.json.RawJson.asJson
import api.problem.Answer
import api.problem.Answer.{asProblem, entry}
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
@atBoundary(BoundaryKind.Json, "gives every newtype its JSON codec from its base type")
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

  def twoStates[E <: reflect.Enum](values: Array[E]): JsonValueCodec[E] = new JsonValueCodec[E]:
    def nullValue: E = null.asInstanceOf[E]
    def decodeValue(in: JsonReader, default: E): E = if in.readBoolean() then values(0) else values(1)
    def encodeValue(x: E, out: JsonWriter): Unit = out.writeVal(x.ordinal == 0)

  def twoStatesSchema[E]: Schema[E] = Schema.schemaForBoolean.as[E]

  /** A two-case enum of the layers below the api (whose companions extend the codec-free TwoCases) gets its JSON here,
    * at the boundary, the same as a wire enum's: `true` its first case, `false` its second.
    */
  given twoCases: [E <: reflect.Enum] => (cases: TwoCases[E]) => JsonValueCodec[E] = twoStates(cases.values)

  given twoCasesSchema: [E <: reflect.Enum] => TwoCases[E] => Schema[E] = twoStatesSchema

  given key: [T] => (m: RefinedType.Mirror[T]) => (base: JsonKeyCodec[m.BaseType]) => JsonKeyCodec[T] =
    base.asInstanceOf[JsonKeyCodec[T]]

  given schema: [T] => (m: RefinedType.Mirror[T]) => (base: Schema[m.BaseType]) => Schema[T] =
    base.as[T]

  given plain: [T] => (m: RefinedType.Mirror[T] { type ConstraintType = Pure })
    => (base: Codec[String, m.BaseType, CodecFormat.TextPlain]) => Codec[String, T, CodecFormat.TextPlain] =
    base.asInstanceOf[Codec[String, T, CodecFormat.TextPlain]]

  /** A problem in a wire record: its group, its case, its fields and its English sentence (`{"group", "case", "data",
    * "text"}`, an [[Answer.Entry]]), so a reader keeps its type and a person still reads the sentence. One codec for
    * the JVM (server, CLI, MCP) and the browser: each writes and reads the case, a problem nested in another's fields
    * too.
    *
    * Every problem is written as its entry, in stored columns and records too. A bare string is read by the platform
    * ([[api.problem.ProblemJson.bare]]): on the JVM it is how a master version kept a problem's text, in stored and
    * checkpointed JSON, read through [[api.problem.StoredShapes]] (the shapes a master version wrote; any other text as
    * [[Problem.Stored.Unrecognised]], kept whole); in the browser it is outside text, [[Problem.Relayed]].
    */
  given problemCodec: JsonValueCodec[Problem] =
    JsonCodec.mapped[Problem, Json](_.entry.asJson, _.asProblem)

  /** A problem's schema: the typed entry (named `Problem`, apart from the other records called `Entry`). */
  given problemSchema: Schema[Problem] =
    summon[KindlingsSchema[Answer.Entry]].schema.name(Schema.SName("Problem")).as[Problem]

  /** Any JSON value (as tapir's circe module described circe's `Json`). */
  given jsonSchema: Schema[Json] = Schema.any
