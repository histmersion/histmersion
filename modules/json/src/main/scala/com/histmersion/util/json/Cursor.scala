package com.histmersion
package util.json

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.lint.{BoundaryKind, atBoundary}

/** A position in a JSON document that may not exist: how a reader walks a document whose shape no record describes
  * (`doc.cursor.downField("a").downArray.focus`).
  */
@atBoundary(BoundaryKind.Json, "walks JSON no record describes and finds where a text broke off")
final case class Cursor(focus: Option[Json], at: Vector[Either[String, Int]] = Vector.empty):
  import Cursor.{pathText, Read, Step}

  /** The value at `key`, if this is an object that has it. */
  def downField(key: String): Cursor = Cursor(focus.flatMap(_.asObject).flatMap(_(key)), at :+ Left(key))

  /** The first element, if this is a non-empty array. */
  def downArray: Cursor = downN(0)

  /** The `n`th element, if this is an array that long. */
  def downN(n: Int): Cursor = Cursor(focus.flatMap(_.asArray).flatMap(_.lift(n)), at :+ Right(n))

  /** The value here as an `A`, or why it is not one, named where it is (`choices[0].message.role: …`); an absent value
    * reads as `null` (an `Option` as `None`, anything else as a missing required field).
    */
  def as[A](using JsonValueCodec[A]): Either[String, A] =
    RawJson.decodeAt[A](focus.fold("null")(RawJson.noSpaces)).left.map { (inner, why) =>
      if focus.isEmpty then where(Nil, "missing required field") else where(inner, why)
    }

  /** A failure here: why, named where it is. */
  def fail(why: String): Left[String, Nothing] = Left(where(Nil, why))

  /** Nothing, if this is an object; else why not (absent: a missing required field), named where it is. */
  def anObject: Either[String, Unit] = focus match
    case Some(j) if j.isObject => Right(())
    case Some(_)               => fail("expected an object")
    case None                  => fail("missing required field")

  private def where(inner: List[Step], why: String): String =
    val steps = at.toList ++ inner
    if steps.isEmpty then why else s"${pathText(steps)}: $why"

  /** The value at `key` as an `A` (circe's `get`). */
  def get[A](key: String)(using JsonValueCodec[A]): Either[String, A] = downField(key).as[A]

  /** The value down the fields `keys`, one after another. */
  def down(keys: String*): Cursor = keys.foldLeft(this)(_.downField(_))

  // ── lenient readers: what an upstream's answer says here, however it spells it, or nothing ──

  /** The string here, as it is. */
  def text: Option[String] = focus.flatMap(_.asString)

  /** The string here trimmed, if it is not blank. */
  def filled: Option[String] = text.map(_.trim).filter(_.nonEmpty)

  /** The string here, or the number here as written; trimmed, if it is not blank. */
  def literal: Option[String] =
    focus.flatMap(j => j.asString.orElse(j.asNumber.map(_.toString))).map(_.trim).filter(_.nonEmpty)

  /** The strings here: an array's (others skipped) or a lone string; trimmed, the blank ones left out. */
  def strings: List[String] =
    focus.toList
      .flatMap(j => j.asArray.map(_.toList.flatMap(_.asString)).getOrElse(j.asString.toList))
      .map(_.trim)
      .filter(_.nonEmpty)

  /** The whole number here, written as a number or as a string. */
  def integer: Option[Long] =
    focus.flatMap(j => j.asNumber.flatMap(_.toLong).orElse(j.asString.flatMap(_.trim.toLongOption)))

  /** The whole number here, if it is an `Int` (see [[integer]]). */
  def whole: Option[Int] = integer.filter(_.isValidInt).map(_.toInt)

  /** The number here, written as a number or as a string. */
  def double: Option[Double] =
    focus.flatMap(j => j.asNumber.flatMap(_.toDouble).orElse(j.asString.flatMap(_.trim.toDoubleOption)))

  /** The boolean here, or a string here that says `true` (any case) or not. */
  def boolean: Option[Boolean] = focus.flatMap(j => j.asBoolean.orElse(j.asString.map(_.trim.equalsIgnoreCase("true"))))

  /** The elements here, if this is an array; else none. */
  def elements: List[Json] = values.toList.flatten

  /** The value here, read by `r`. */
  def read[A](r: Read[A]): Either[String, A] = r.at(this)

  /** Whether there is a value here. */
  def succeeded: Boolean = focus.isDefined

  /** The elements, if this is an array. */
  def values: Option[Vector[Json]] = focus.flatMap(_.asArray)

  /** The keys, if this is an object. */
  def keys: Option[Vector[String]] = focus.flatMap(_.asObject).map(_.keys)

object Cursor:
  /** One step down a document: an object's field or an array's index. */
  type Step = Either[String, Int]

  /** A path as text: `findings.add[0].date`. */
  def pathText(steps: List[Step]): String =
    steps.zipWithIndex.map {
      case (Left(key), 0) => key
      case (Left(key), _) => s".$key"
      case (Right(i), _)  => s"[$i]"
    }.mkString

  /** The fields and indices of the values open at `offset` of a JSON text (the byte there read too: jsoniter's error
    * offset is the last byte it read, unless that byte opens the value that was refused), outermost first.
    */
  def path(text: String, offset: Int): List[Step] =
    @scala.annotation.tailrec
    def go(i: Int, open: List[Either[Option[String], Int]], key: Boolean): List[Either[Option[String], Int]] =
      if i > offset || i >= text.length || (i == offset && (text(i) == '{' || text(i) == '[')) then open
      else
        text(i) match
          case '{'       => go(i + 1, Left(None) :: open, true)
          case '['       => go(i + 1, Right(0) :: open, false)
          case '}' | ']' => go(i + 1, open.drop(1), false)
          case ','       =>
            open match
              case Right(n) :: rest => go(i + 1, Right(n + 1) :: rest, false)
              case _                => go(i + 1, open, true)
          case ':' => go(i + 1, open, false)
          case '"' =>
            val end = stringEnd(text, i + 1)
            open match
              case Left(_) :: rest if key => go(end + 1, Left(Some(text.substring(i + 1, end))) :: rest, false)
              case _                      => go(end + 1, open, key)
          case _ => go(i + 1, open, key)
    go(0, Nil, false).reverse.flatMap {
      case Left(Some(k)) => List(Left(k))
      case Left(None)    => Nil
      case Right(n)      => List(Right(n))
    }

  @scala.annotation.tailrec
  private def stringEnd(text: String, i: Int): Int =
    if i >= text.length then text.length
    else if text(i) == '\\' then stringEnd(text, i + 2)
    else if text(i) == '"' then i
    else stringEnd(text, i + 1)

  /** How a value is read where a cursor stands, or why it cannot be: a decoder for JSON whose shape no record describes
    * (an upstream's reply read field by field).
    */
  final class Read[A](val at: Cursor => Either[String, A]):
    /** The document read from its root. */
    def apply(json: Json): Either[String, A] = at(Cursor(Some(json)))

  object Read:
    /** An array, each element read by `each`. */
    def list[A](each: Read[A]): Read[List[A]] = Read { c =>
      c.focus.flatMap(_.asArray) match
        case None         => c.fail("expected an array")
        case Some(values) =>
          values.indices
            .foldLeft[Either[String, List[A]]](Right(Nil))((acc, i) =>
              acc.flatMap(as => each.at(c.downN(i)).map(_ :: as))
            )
            .map(_.reverse)
    }

    /** A value that may be absent or `null`. */
    def option[A](each: Read[A]): Read[Option[A]] =
      Read(c => if c.focus.forall(_.isNull) then Right(None) else each.at(c).map(Some(_)))
