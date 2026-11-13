package com.histmersion
package api
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.api.problem.Text.sentence
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonReader, JsonValueCodec, JsonWriter}
import hearth.kindlings.jsoniterjson.Json
import sttp.tapir.{Schema, SchemaType}
import io.github.iltotore.iron.constraint.any.Pure

import util.json.{JsonCodec, RawJson}

/** A write input's JSON codec (`derives StrictJson`): a derived decoder ignores keys it does not know, so a misplaced
  * or misspelt key decodes to "change nothing"; a write input refuses unknown keys instead, naming the keys it takes.
  */
trait StrictJson[A] extends JsonCodec[A]

object StrictJson:
  /** The record's own derived codec, refusing unknown keys; its schema names the keys it takes. */
  inline def derived[A](using Schema[A]): StrictJson[A] = codec(JsonCodec.derived[A])

  type FieldName = FieldName.T
  object FieldName extends Newtype[String, Pure]
  type FieldPath = FieldPath.T
  object FieldPath extends Newtype[String, Pure]

  def codec[A](derived: JsonValueCodec[A])(using s: Schema[A]): StrictJson[A] =
    val known = fieldNames(s)
    new StrictJson[A]:
      def nullValue: A = derived.nullValue
      def encodeValue(x: A, out: JsonWriter): Unit = derived.encodeValue(x, out)
      def decodeValue(in: JsonReader, default: A): A =
        val json = RawJson.codec.decodeValue(in, Json.Null)
        json.asObject match
          case None =>
            in.decodeError(s"expected an object with keys ${known.mkString(", ")}; got ${jsonKind(json)}")
          case Some(o) =>
            problems(o.keys.toList.map(k => FieldName(k)), s) match
              case Some(msg) => in.decodeError(msg.sentence)
              case None      => json.as[A](using derived).fold(in.decodeError, identity)

  def problems(keys: List[FieldName], s: Schema[?]): Option[Problem] =
    val known = fieldNames(s)
    val unknown = keys.filterNot(known.contains).distinct
    Option.when(unknown.nonEmpty) {
      val nested = nestedFields(s)
      val hints = unknown.flatMap(k =>
        nested.collectFirst {
          case (path, f) if f == k =>
            s"'$k' belongs under '$path' ($path.$k)"
        }
      )
      val noun = s"unknown ${Plural.noun(Plural.Count((unknown.size).toLong), Plural.Noun.Singular("key"))}"
      Problem.Unclassified(
        Legacy.Text(
          s"$noun ${unknown.map(k => s"'$k'").mkString(", ")}" +
            (if hints.isEmpty then "" else s" — ${hints.mkString("; ")}") +
            s"; expected only: ${known.mkString(", ")}"
        )
      )
    }

  def fieldNames(s: Schema[?]): List[FieldName] = s.schemaType match
    case p: SchemaType.SProduct[?] => p.fields.map(f => FieldName(f.name.encodedName))
    case SchemaType.SOption(e)     => fieldNames(e)
    case _                         => Nil

  private def nestedFields(s: Schema[?]): List[(FieldPath, FieldName)] =
    def children(path: FieldPath, sc: Schema[?]): List[(FieldPath, Schema[?])] = sc.schemaType match
      case p: SchemaType.SProduct[?] =>
        p.fields.map(f => (FieldPath((if (path).value.isEmpty then "" else s"$path.") + f.name.encodedName), f.schema))
      case SchemaType.SOption(e) => children(path, e)
      case SchemaType.SArray(e)  => children(path, e)
      case _                     => Nil
    val level1 = children(FieldPath(""), s)
    val level2 = level1.flatMap((p, sc) => children(p, sc))
    (level1 ++ level2).flatMap((p, sc) => fieldNames(unwrap(sc)).map(p -> _))

  private def unwrap(s: Schema[?]): Schema[?] = s.schemaType match
    case SchemaType.SOption(e) => unwrap(e)
    case SchemaType.SArray(e)  => unwrap(e)
    case _                     => s

  private def jsonKind(j: Json): String =
    j.fold(("null"), _ => ("a boolean"), _ => ("a number"), _ => ("a string"), _ => ("an array"), _ => ("an object"))
