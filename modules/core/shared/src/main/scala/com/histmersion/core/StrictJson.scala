package com.histmersion.core

import io.circe.{Codec, CursorOp, Decoder, DecodingFailure, Json}
import sttp.tapir.{Schema, SchemaType}

/** Strict decoding for the WRITE inputs (#813). A derived decoder ignores keys it does not know, so a
  * patch whose `status` sat at the top level instead of under `metadata`, or whose `tag` was
  * misspelt, decoded to "change nothing" and the write reported success. Every put/patch DTO wraps
  * its derived codec in [[codec]]: an unknown key, or a value that is not an object where an object
  * is expected, is refused with every offending key named and the shape that was expected.
  *
  * The accepted keys come from the type's tapir [[Schema]], which Kindlings derives from the same case
  * class as the codec — so the check cannot drift from what the decoder reads, nor from what the MCP
  * tool schema advertises. Encoding is unchanged: the derived encoder only writes the case class's
  * own fields, so anything the CLI or the web UI sends passes. */
object StrictJson:

  /** `derived`'s decoder behind the key check; its encoder as it is. */
  def codec[A](derived: Codec.AsObject[A])(using s: Schema[A]): Codec.AsObject[A] =
    Codec.AsObject.from(decoder(derived), derived)

  def decoder[A](d: Decoder[A])(using s: Schema[A]): Decoder[A] =
    val known = fieldNames(s)
    Decoder.instance { c =>
      c.value.asObject match
        case None =>
          Left(DecodingFailure(s"${at(c.history)}expected an object with keys ${known.mkString(", ")}; " +
            s"got ${jsonKind(c.value)}", c.history))
        case Some(o) =>
          problems(o.keys.toList, s) match
            case None      => d(c)
            case Some(msg) => Left(DecodingFailure(s"${at(c.history)}$msg", c.history))
    }

  /** The refusal for `keys` sent where `s` is expected, or `None` when every key is one of its fields.
    * A key that is a field of a NESTED object is reported with where it belongs. Pure. */
  def problems(keys: List[String], s: Schema[?]): Option[String] =
    val known   = fieldNames(s)
    val unknown = keys.filterNot(known.contains).distinct
    Option.when(unknown.nonEmpty) {
      val nested = nestedFields(s)
      val hints = unknown.flatMap(k => nested.collectFirst { case (path, f) if f == k =>
        s"'$k' belongs under '$path' ($path.$k)" })
      val noun = if unknown.size == 1 then "unknown key" else "unknown keys"
      s"$noun ${unknown.map(k => s"'$k'").mkString(", ")}" +
        (if hints.isEmpty then "" else s" — ${hints.mkString("; ")}") +
        s"; expected only: ${known.mkString(", ")}"
    }

  /** The JSON keys of a product schema, in declaration order. */
  def fieldNames(s: Schema[?]): List[String] = s.schemaType match
    case p: SchemaType.SProduct[?] => p.fields.map(_.name.encodedName)
    case SchemaType.SOption(e)     => fieldNames(e)
    case _                         => Nil

  /** `(parentPath, fieldName)` for every field of every object nested under `s` (through options and
    * arrays), breadth-first so the shallowest home of a key is found first. Bounded depth: the input
    * DTOs are shallow, and a recursive schema must not loop. */
  private def nestedFields(s: Schema[?]): List[(String, String)] =
    def children(path: String, sc: Schema[?]): List[(String, Schema[?])] = sc.schemaType match
      case p: SchemaType.SProduct[?] =>
        p.fields.map(f => ((if path.isEmpty then "" else s"$path.") + f.name.encodedName, f.schema))
      case SchemaType.SOption(e) => children(path, e)
      case SchemaType.SArray(e)  => children(path, e)
      case _                     => Nil
    val level1 = children("", s)
    val level2 = level1.flatMap((p, sc) => children(p, sc))
    (level1 ++ level2).flatMap((p, sc) => fieldNames(unwrap(sc)).map(p -> _))

  private def unwrap(s: Schema[?]): Schema[?] = s.schemaType match
    case SchemaType.SOption(e) => unwrap(e)
    case SchemaType.SArray(e)  => unwrap(e)
    case _                     => s

  /** `"tags: "` for a nested cursor, `""` at the root. */
  private def at(history: List[CursorOp]): String =
    CursorOp.opsToPath(history).stripPrefix(".") match
      case ""   => ""
      case path => s"$path: "

  private def jsonKind(j: Json): String = j.fold("null", _ => "a boolean", _ => "a number",
    _ => "a string", _ => "an array", _ => "an object")
