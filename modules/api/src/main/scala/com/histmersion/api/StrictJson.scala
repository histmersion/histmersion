package com.histmersion
package api

import io.circe.{Codec, CursorOp, Decoder, DecodingFailure, Json}
import sttp.tapir.{Schema, SchemaType}
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A derived decoder ignores keys it does not know, so a misplaced or misspelt key decodes to "change nothing"; write
  * inputs refuse unknown keys instead.
  */
object StrictJson:
  type FieldName = FieldName.T
  object FieldName extends RefinedType[String, Pure]
  type FieldPath = FieldPath.T
  object FieldPath extends RefinedType[String, Pure]
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]



  def codec[A](derived: Codec.AsObject[A])(using s: Schema[A]): Codec.AsObject[A] =
    Codec.AsObject.from(decoder(derived), derived)

  def decoder[A](d: Decoder[A])(using s: Schema[A]): Decoder[A] =
    val known = fieldNames(s)
    Decoder.instance { c =>
      c.value.asObject match
        case None =>
          Left(
            DecodingFailure(
              s"${at(c.history)}expected an object with keys ${known.mkString(", ")}; " +
                s"got ${jsonKind(c.value)}",
              c.history
            )
          )
        case Some(o) =>
          problems((o.keys.toList).map(refactorValue => FieldName(refactorValue)), s) match
            case None      => d(c)
            case Some(msg) => Left(DecodingFailure(s"${at(c.history)}$msg", c.history))
    }

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
      Problem(s"$noun ${unknown.map(k => s"'$k'").mkString(", ")}" +
        (if hints.isEmpty then "" else s" — ${hints.mkString("; ")}") +
        s"; expected only: ${known.mkString(", ")}")
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

  private def at(history: List[CursorOp]): String =
    CursorOp.opsToPath(history).stripPrefix(".") match
      case ""   => ("")
      case path => (s"$path: ")

  private def jsonKind(j: Json): String =
    j.fold(("null"), _ => ("a boolean"), _ => ("a number"), _ => ("a string"), _ => ("an array"), _ => ("an object"))
