package com.histmersion
package db
import com.histmersion.util.problem.Problem
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson

import org.scalacheck.Gen

import com.histmersion.util.data.exchange.BundleFormat
import com.histmersion.util.database.Jsonb
import com.histmersion.lint.{tolerated, LintRule}

/** How [[StoredShapesSuite]] writes and reads a stored document: through the store's own functions (`Jsonb`,
  * `BundleFormat`) and the codecs they take.
  */
object StoredCodecs:
  final class Codec[A](val write: (A, Boolean) => String, val decode: String => Either[String, A])
  object Codec:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    given [A: JsonValueCodec] => Codec[A] = Codec(
      (a, drop) => s"${Jsonb.write(a, if drop then Jsonb.DropNulls.DropNulls else Jsonb.DropNulls.KeepNulls)}",
      t => Jsonb.decode[A](Jsonb.JsonText(s"$t")).left.map(e => s"$e")
    )

  final class BundleCodec[A](
      val stamp: A => String,
      val line: A => String,
      val decode: (String, Boolean) => Either[String, A]
  )
  object BundleCodec:
    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    given [A: JsonValueCodec] => BundleCodec[A] = BundleCodec(
      a => BundleFormat.stamp(a).line,
      a => BundleFormat.line(a),
      (l, stamped) => BundleFormat.decodeLine[A](l, stamped, Problem.Where("the line")).left.map(e => s"$e")
    )

  type JsonAst = Json
  object JsonAst:
    def parse(text: String): Option[Json] = RawJson.parse(text).toOption
    def print(j: Json): String = j.noSpaces

  /** The same JSON value: numbers compared by value, objects by their fields in any order. */
  def sameJson(a: String, b: String): Boolean =
    (RawJson.parse(a), RawJson.parse(b)) match
      case (Right(x), Right(y)) => same(x, y)
      case _                    => false

  private def same(x: Json, y: Json): Boolean =
    (x.asObject, y.asObject, x.asArray, y.asArray, x.asNumber, y.asNumber) match
      case (Some(o), Some(p), _, _, _, _) =>
        o.keys.toSet == p.keys.toSet && o.toMap.forall((k, v) => same(v, p(k).get))
      case (_, _, Some(a), Some(b), _, _) => a.size == b.size && a.zip(b).forall(same)
      case (_, _, _, _, Some(m), Some(n)) => m.toBigDecimal == n.toBigDecimal
      case _                              => x.noSpaces == y.noSpaces

  object JsonSamples:
    private val leaf: Gen[Json] = Gen.oneOf(
      Gen.const(Json.Null),
      Gen.oneOf(true, false).map(Json.fromBoolean),
      Gen.choose(-1000000L, 1000000L).map(Json.fromLong),
      Gen.choose(-1000.0, 1000.0).map(Json.fromDoubleOrNull),
      Gen.alphaNumStr.map(Json.fromString),
      Gen.oneOf("é \"quoted\" \\ back", "line\nbreak\ttab", "\u0001ctl", "Ἀθῆναι").map(Json.fromString)
    )
    private def tree(depth: Int): Gen[Json] =
      if depth <= 0 then leaf
      else
        Gen.frequency(
          3 -> leaf,
          1 -> Gen.listOfN(3, tree(depth - 1)).map(Json.fromValues),
          1 -> Gen.listOfN(3, Gen.zip(Gen.identifier.map(_.take(6)), tree(depth - 1))).map(Json.fromFields)
        )

    /** A document: an object or an array, as a column or a record field holds one (never a bare null). */
    val gen: Gen[Json] = Gen.oneOf(
      Gen.listOfN(3, tree(1)).map(Json.fromValues),
      Gen.listOfN(3, Gen.zip(Gen.identifier.map(_.take(6)), tree(1))).map(Json.fromFields)
    )
