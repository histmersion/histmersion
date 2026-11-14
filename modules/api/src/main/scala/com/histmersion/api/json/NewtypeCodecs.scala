package com.histmersion
package api.json
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonKeyCodec, JsonValueCodec}
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema

import hearth.kindlings.jsoniterjson.Json
import util.json.{JsonCodec, RawJson}
import util.problem.Text.sentence
import util.problem.{Legacy, NonEmptyVector, Problem}
import util.json.RawJson.*

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

  /** One value or several (`Problems` is one problem or several): the one value itself, several as a list — so a field
    * that held one value (as an earlier version wrote it) reads, and writes, the same.
    */
  given oneOrMore: [A] => (one: JsonValueCodec[A]) => JsonValueCodec[A | NonEmptyVector[A]] =
    // at run time several values are the vector itself, and one value is never a vector
    def all(values: A | NonEmptyVector[A]): Vector[A] = values.asInstanceOf[Matchable] match
      case several: Vector[?] => several.asInstanceOf[Vector[A]]
      case single             => Vector(single.asInstanceOf[A])
    def of(read: Vector[A]): Option[A | NonEmptyVector[A]] =
      if read.size == 1 then read.headOption else NonEmptyVector.from(read)
    JsonCodec.mapped[A | NonEmptyVector[A], Json](
      values =>
        all(values) match
          case Vector(single) => single.asJson(using one)
          case several        => Json.Arr(several.map(_.asJson(using one))),
      json =>
        json.asArray.fold(json.as(using one).map(a => a: A | NonEmptyVector[A])) { elements =>
          val read = elements.map(_.as(using one))
          read
            .collectFirst { case Left(failure) => failure }
            .toLeft(read.collect { case Right(a) => a })
            .flatMap(as => of(as).toRight("an empty list"))
        }
    )

  given oneOrMoreSchema: [A] => (one: Schema[A]) => Schema[A | NonEmptyVector[A]] =
    Schema.schemaForIterable[A, Vector].as[A | NonEmptyVector[A]]

  /** Any JSON value (as tapir's circe module described circe's `Json`). */
  given jsonSchema: Schema[Json] = Schema.any
