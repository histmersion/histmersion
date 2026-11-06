package com.histmersion.api

import io.circe.{Codec, CursorOp, Decoder, DecodingFailure, Json}
import sttp.tapir.{Schema, SchemaType}

/** A derived decoder ignores keys it does not know, so a misplaced or misspelt key decodes to "change nothing"; write
  * inputs refuse unknown keys instead.
  */
object StrictJson:
  type FieldName = FieldName.T
  object FieldName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FieldPath = FieldPath.T
  object FieldPath extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



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
          problems((o.keys.toList).map(refactorValue => _root_.com.histmersion.api.StrictJson.FieldName(refactorValue)), s) match
            case None      => d(c)
            case Some(msg) => Left(DecodingFailure(s"${at(c.history)}$msg", c.history))
    }

  def problems(keys: List[com.histmersion.api.StrictJson.FieldName], s: Schema[?]): Option[com.histmersion.api.StrictJson.Problem] =
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
      val noun = s"unknown ${Plural.noun(_root_.com.histmersion.api.Plural.Count((unknown.size).toLong), _root_.com.histmersion.api.Plural.Noun.Singular("key"))}"
      _root_.com.histmersion.api.StrictJson.Problem(s"$noun ${unknown.map(k => s"'$k'").mkString(", ")}" +
        (if hints.isEmpty then "" else s" — ${hints.mkString("; ")}") +
        s"; expected only: ${known.mkString(", ")}")
    }

  def fieldNames(s: Schema[?]): List[com.histmersion.api.StrictJson.FieldName] = s.schemaType match
    case p: SchemaType.SProduct[?] => p.fields.map(_root_.com.histmersion.api.StrictJson.FieldName(_.name.encodedName))
    case SchemaType.SOption(e)     => fieldNames(e)
    case _                         => Nil

  private def nestedFields(s: Schema[?]): List[(String, String)] =
    def children(path: com.histmersion.api.StrictJson.FieldPath, sc: Schema[?]): List[(com.histmersion.api.StrictJson.FieldPath, Schema[?])] = sc.schemaType match
      case p: SchemaType.SProduct[?] =>
        p.fields.map(f => (_root_.com.histmersion.api.StrictJson.FieldPath((if (path).value.isEmpty then "" else s"$path.") + f.name.encodedName), f.schema))
      case SchemaType.SOption(e) => children(path, e)
      case SchemaType.SArray(e)  => children(path, e)
      case _                     => Nil
    val level1 = children(_root_.com.histmersion.api.StrictJson.FieldPath(""), s)
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
