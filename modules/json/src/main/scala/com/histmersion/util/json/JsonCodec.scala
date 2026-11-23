package com.histmersion
package util.json

import java.time.Instant
import java.util.UUID

import scala.collection.immutable.ListMap

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonKeyCodec, JsonReader, JsonValueCodec, JsonWriter}
import hearth.kindlings.jsoniterderivation.{JsoniterConfig, KindlingsJsonValueCodec}

/** A record's JSON codec, derived by Kindlings (`derives JsonCodec`): every field written, `None` as `null`, an enum
  * whose cases all lack fields as its case's name (`"Case"`), any other sum type wrapped in its case's name
  * (`{"Case":{…}}`, a case without fields as `{"Case":{}}`). An enum of words also reads the wrapped form a former
  * version stored.
  *
  * Reading is lenient where the old decoders were: an absent `Option` field is `None`, an absent field with a default
  * takes it. Any other absent field fails as missing (`missing required field "name"`), a collection without a default
  * too.
  */
trait JsonCodec[A] extends JsonValueCodec[A]

object JsonCodec:
  export RawJson.given

  /** How a derived codec reads a key its record does not have: skipped (the default), or refused — an MCP tool's
    * arguments, where a misspelt argument must not mean "change nothing".
    */
  enum Unknown derives CanEqual:
    case Skipped, Refused

  object Unknown:
    given default: Unknown = Skipped

  /** How a derived codec reads and writes: every field written, `None` as `null`; an absent collection without a
    * default is missing, as any other required field; an empty collection (`[]`, `{}`) is read as empty even where the
    * field's default is not, so that what is written reads back.
    */
  def config(unknown: Unknown): JsoniterConfig =
    JsoniterConfig.default.withCirceLikeObjectEncoding.withEnumAsStrings.withRequireCollectionFields.withEmptyCollectionsAsEmpty
      .withSkipUnexpectedFields(unknown == Unknown.Skipped)

  /** What a schema derived beside a codec is told of it (Kindlings' Tapir schemas read it): the codecs' own config, so
    * a field is optional exactly where the decoder accepts it absent (an `Option`, a field with a default) and required
    * where it does not (any other field, a collection without a default included).
    */
  val schemas: JsoniterConfig = config(Unknown.Skipped)

  inline def derived[A](using unknown: Unknown): JsonCodec[A] =
    of(KindlingsJsonValueCodec.derived[A](using config(unknown)))

  /** The codec of a type that cannot say `derives JsonCodec` (the problems' cases, in a module without JSON). */
  inline def derivedWords[A]: JsonCodec[A] =
    of(KindlingsJsonValueCodec.derived[A](using config(Unknown.Skipped)))

  private def of[A](codec: JsonValueCodec[A]): JsonCodec[A] =
    new JsonCodec[A]:
      def nullValue: A = codec.nullValue
      def decodeValue(in: JsonReader, default: A): A = codec.decodeValue(in, default)
      def encodeValue(x: A, out: JsonWriter): Unit = codec.encodeValue(x, out)

  /** A codec over another type's: `to` writes, `from` reads or says why it cannot. */
  def mapped[A, B](to: A => B, from: B => Either[String, A])(using base: JsonValueCodec[B]): JsonCodec[A] =
    new JsonCodec[A]:
      // jsoniter's placeholder for a value not read yet: never written, replaced once read
      private var empty: A = compiletime.uninitialized
      def nullValue: A = empty
      def decodeValue(in: JsonReader, default: A): A =
        from(base.decodeValue(in, base.nullValue)).fold(in.decodeError, identity)
      def encodeValue(x: A, out: JsonWriter): Unit = base.encodeValue(to(x), out)

  /** A codec of text: `to` writes the string, `from` reads it or says why it cannot. */
  def text[A](to: A => String, from: String => Either[String, A]): JsonCodec[A] = mapped(to, from)

  /** A key codec of text. */
  def key[A](to: A => String, from: String => Either[String, A]): JsonKeyCodec[A] = new JsonKeyCodec[A]:
    def decodeKey(in: JsonReader): A = from(in.readKeyAsString()).fold(in.decodeError, identity)
    def encodeKey(x: A, out: JsonWriter): Unit = out.writeKey(to(x))

  // ---- the base types, written as circe wrote them ---------------------------------------------------------------

  given string: JsonValueCodec[String] = new JsonValueCodec[String]:
    def nullValue: String = null
    def decodeValue(in: JsonReader, default: String): String = in.readString(default)
    def encodeValue(x: String, out: JsonWriter): Unit = out.writeVal(x)

  given int: JsonValueCodec[Int] = new JsonValueCodec[Int]:
    def nullValue: Int = 0
    def decodeValue(in: JsonReader, default: Int): Int = in.readInt()
    def encodeValue(x: Int, out: JsonWriter): Unit = out.writeVal(x)

  given long: JsonValueCodec[Long] = new JsonValueCodec[Long]:
    def nullValue: Long = 0L
    def decodeValue(in: JsonReader, default: Long): Long = in.readLong()
    def encodeValue(x: Long, out: JsonWriter): Unit = out.writeVal(x)

  given short: JsonValueCodec[Short] = new JsonValueCodec[Short]:
    def nullValue: Short = 0
    def decodeValue(in: JsonReader, default: Short): Short = in.readShort()
    def encodeValue(x: Short, out: JsonWriter): Unit = out.writeVal(x)

  /** circe wrote a double as Java prints it, and a NaN or an infinity as `null`. */
  given double: JsonValueCodec[Double] = new JsonValueCodec[Double]:
    def nullValue: Double = 0.0
    def decodeValue(in: JsonReader, default: Double): Double = in.readDouble()
    def encodeValue(x: Double, out: JsonWriter): Unit =
      if x.isNaN || x.isInfinite then out.writeNull() else out.writeVal(x)

  given float: JsonValueCodec[Float] = new JsonValueCodec[Float]:
    def nullValue: Float = 0f
    def decodeValue(in: JsonReader, default: Float): Float = in.readFloat()
    def encodeValue(x: Float, out: JsonWriter): Unit =
      if x.isNaN || x.isInfinite then out.writeNull() else out.writeVal(x)

  given boolean: JsonValueCodec[Boolean] = new JsonValueCodec[Boolean]:
    def nullValue: Boolean = false
    def decodeValue(in: JsonReader, default: Boolean): Boolean = in.readBoolean()
    def encodeValue(x: Boolean, out: JsonWriter): Unit = out.writeVal(x)

  /** Every digit kept, read and written as its text. */
  given bigDecimal: JsonValueCodec[BigDecimal] = new JsonValueCodec[BigDecimal]:
    def nullValue: BigDecimal = null
    def decodeValue(in: JsonReader, default: BigDecimal): BigDecimal =
      in.readBigDecimal(default, java.math.MathContext.UNLIMITED, Int.MaxValue, Int.MaxValue)
    def encodeValue(x: BigDecimal, out: JsonWriter): Unit = out.writeVal(x)

  given instant: JsonValueCodec[Instant] =
    text(_.toString, s => scala.util.Try(Instant.parse(s)).toEither.left.map(_.getMessage))

  given uuid: JsonValueCodec[UUID] =
    text(_.toString, s => scala.util.Try(UUID.fromString(s)).toEither.left.map(_.getMessage))

  given stringKey: JsonKeyCodec[String] = key(identity, Right(_))

  // ---- the containers ----------------------------------------------------------------------------------------------

  given option: [A] => (element: JsonValueCodec[A]) => JsonValueCodec[Option[A]] = new JsonValueCodec[Option[A]]:
    def nullValue: Option[A] = None
    def decodeValue(in: JsonReader, default: Option[A]): Option[A] =
      if in.isNextToken('n'.toByte) then in.readNullOrError(None, "expected a value or null")
      else
        in.rollbackToken()
        Some(element.decodeValue(in, element.nullValue))
    def encodeValue(x: Option[A], out: JsonWriter): Unit = x match
      case Some(a) => element.encodeValue(a, out)
      case None    => out.writeNull()

  private def sequence[A, C](build: Vector[A] => C, each: C => IterableOnce[A], empty: C)(using
      element: JsonValueCodec[A]
  ): JsonValueCodec[C] = new JsonValueCodec[C]:
    def nullValue: C = empty
    def decodeValue(in: JsonReader, default: C): C =
      if !in.isNextToken('['.toByte) then in.decodeError("expected '['")
      else if in.isNextToken(']'.toByte) then empty
      else
        in.rollbackToken()
        val b = Vector.newBuilder[A]
        b += element.decodeValue(in, element.nullValue)
        while in.isNextToken(','.toByte) do b += element.decodeValue(in, element.nullValue)
        if !in.isCurrentToken(']'.toByte) then in.decodeError("expected ']' or ','")
        build(b.result())
    def encodeValue(x: C, out: JsonWriter): Unit =
      out.writeArrayStart()
      each(x).iterator.foreach(element.encodeValue(_, out))
      out.writeArrayEnd()

  given list: [A: JsonValueCodec] => JsonValueCodec[List[A]] = sequence[A, List[A]](_.toList, identity, Nil)
  given vector: [A: JsonValueCodec] => JsonValueCodec[Vector[A]] =
    sequence[A, Vector[A]](identity, identity, Vector.empty)
  given seq: [A: JsonValueCodec] => JsonValueCodec[Seq[A]] = sequence[A, Seq[A]](identity, identity, Seq.empty)
  given set: [A: JsonValueCodec] => JsonValueCodec[Set[A]] = sequence[A, Set[A]](_.toSet, identity, Set.empty)

  private def entries[K, V, M](build: Vector[(K, V)] => M, each: M => IterableOnce[(K, V)], empty: M)(using
      keys: JsonKeyCodec[K],
      values: JsonValueCodec[V]
  ): JsonValueCodec[M] = new JsonValueCodec[M]:
    def nullValue: M = empty
    def decodeValue(in: JsonReader, default: M): M =
      if !in.isNextToken('{'.toByte) then in.decodeError("expected '{'")
      else if in.isNextToken('}'.toByte) then empty
      else
        in.rollbackToken()
        val b = Vector.newBuilder[(K, V)]
        b += keys.decodeKey(in) -> values.decodeValue(in, values.nullValue)
        while in.isNextToken(','.toByte) do b += keys.decodeKey(in) -> values.decodeValue(in, values.nullValue)
        if !in.isCurrentToken('}'.toByte) then in.decodeError("expected '}' or ','")
        build(b.result())
    def encodeValue(x: M, out: JsonWriter): Unit =
      out.writeObjectStart()
      each(x).iterator.foreach { (k, v) =>
        keys.encodeKey(k, out)
        values.encodeValue(v, out)
      }
      out.writeObjectEnd()

  given map: [K: JsonKeyCodec, V: JsonValueCodec] => JsonValueCodec[Map[K, V]] =
    entries[K, V, Map[K, V]](_.toMap, identity, Map.empty)
  given listMap: [K: JsonKeyCodec, V: JsonValueCodec] => JsonValueCodec[ListMap[K, V]] =
    entries[K, V, ListMap[K, V]](ListMap.from, identity, ListMap.empty)
