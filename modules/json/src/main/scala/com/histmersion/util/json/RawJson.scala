package com.histmersion
package util.json

import java.nio.charset.StandardCharsets.UTF_8

import com.github.plokhotnyuk.jsoniter_scala.core.{
  readFromStringReentrant,
  writeToStringReentrant,
  JsonReader,
  JsonReaderException,
  JsonValueCodec,
  JsonWriter,
  ReaderConfig
}
import hearth.kindlings.jsoniterjson.{Json, JsonNumber, JsonObject}

/** JSON whose shape no record of ours describes (a model's answer, a tool's input schema, a place's properties, an
  * upstream's reply), as Kindlings' AST. A number keeps the text it was read with, so a document read and written again
  * is the same text, as it was with circe; a string is escaped as circe escaped it.
  */
// Every read and write here is jsoniter's reentrant one: a codec (a record read through raw JSON, a job's detail) calls
// these from inside another read or write, and the plain ones share one reader and writer per thread.
object RawJson:
  given codec: JsonValueCodec[Json] = new JsonValueCodec[Json]:
    def nullValue: Json = Json.Null
    def decodeValue(in: JsonReader, default: Json): Json = read(in)
    def encodeValue(x: Json, out: JsonWriter): Unit = write(x, out)

  private def read(in: JsonReader): Json =
    val b = in.nextToken()
    if b == 'n'.toByte then in.readNullOrError(Json.Null, "expected null")
    else if b == 't'.toByte || b == 'f'.toByte then
      in.rollbackToken()
      Json.Bool(in.readBoolean())
    else if b == '"'.toByte then
      in.rollbackToken()
      Json.Str(in.readString(null))
    else if b == '['.toByte then
      if in.isNextToken(']'.toByte) then Json.Arr(Vector.empty)
      else
        in.rollbackToken()
        val values = Vector.newBuilder[Json]
        values += read(in)
        while in.isNextToken(','.toByte) do values += read(in)
        if !in.isCurrentToken(']'.toByte) then in.decodeError("expected ']' or ','")
        Json.Arr(values.result())
    else if b == '{'.toByte then
      if in.isNextToken('}'.toByte) then Json.Obj(JsonObject.empty)
      else
        in.rollbackToken()
        val fields = Vector.newBuilder[(String, Json)]
        fields += in.readKeyAsString() -> read(in)
        while in.isNextToken(','.toByte) do fields += in.readKeyAsString() -> read(in)
        if !in.isCurrentToken('}'.toByte) then in.decodeError("expected '}' or ','")
        Json.Obj(JsonObject(fields.result()))
    else
      in.rollbackToken()
      val text = String(in.readRawValAsBytes(), UTF_8)
      JsonNumber.fromString(text).fold(in.decodeError(s"illegal number $text"))(Json.Num(_))

  private def write(json: Json, out: JsonWriter): Unit = json match
    case Json.Null    => out.writeNull()
    case Json.Bool(b) => out.writeVal(b)
    case Json.Str(s)  => out.writeVal(s)
    case Json.Num(n)  => out.writeRawVal(n.toString.getBytes(UTF_8))
    case Json.Arr(vs) =>
      out.writeArrayStart()
      vs.foreach(write(_, out))
      out.writeArrayEnd()
    case Json.Obj(o) =>
      out.writeObjectStart()
      o.fields.foreach { (k, v) =>
        out.writeKey(k)
        write(v, out)
      }
      out.writeObjectEnd()

  private val reading: ReaderConfig = ReaderConfig.withAppendHexDumpToParseException(false)

  /** The text as JSON, or why it is not. */
  def parse(text: String): Either[String, Json] = decode[Json](text)

  /** The text as an `A`, or why it is not one, prefixed by where in the text (`findings.add[0]: unknown key 'date'`).
    */
  def decode[A](text: String)(using codec: JsonValueCodec[A]): Either[String, A] =
    decodeAt[A](text).left.map((at, why) => if at.isEmpty then why else s"${Cursor.pathText(at)}: $why")

  /** The text as an `A`, or why it is not one and the fields and indices that lead to where it broke off. */
  def decodeAt[A](text: String)(using codec: JsonValueCodec[A]): Either[(List[Cursor.Step], String), A] =
    try Right(readFromStringReentrant[A](text, reading))
    catch case e: JsonReaderException => Left(failure(text, e.getMessage))

  private val Located = """(?s)^(.*), offset: 0x([0-9a-fA-F]+)$""".r
  private val Pathed = """(?s)^([^\s:.\[\]]+(?:\.[^\s:.\[\]]+|\[\d+\])*): (.*)$""".r
  private val Segment = """([^.\[\]]+)|\[(\d+)\]""".r

  /** jsoniter's error (`why, offset: 0x…`) as the path to where it broke off and why; a `why` that is itself located (a
    * nested read's) continues the path.
    */
  private def failure(text: String, error: String): (List[Cursor.Step], String) = error match
    case Located(message, hex) =>
      val outer = Cursor.path(text, Integer.parseInt(hex, 16))
      message match
        case Pathed(inner, rest) =>
          (outer ++ Segment.findAllMatchIn(inner).map(m => Option(m.group(1)).toLeft(m.group(2).toInt)).toList, rest)
        case _ => (outer, message)
    case other => (Nil, other)

  extension (json: Json)
    /** Compact text. */
    def noSpaces: String = writeToStringReentrant(json)

    /** Indented by two spaces, laid out as circe's `spaces2` laid it out (`"key" : value`, an empty array as `[` and
      * `]` on two lines), so a document printed before and after reads the same.
      */
    def spaces2: String = layout(json, " : ")

    /** Indented by two spaces as people write JSON (`"key": value`), otherwise laid out as [[spaces2]]. */
    def written: String = layout(json, ": ")

    /** The object without its `null` fields (only its own; nested objects keep theirs), as circe's `dropNullValues`. */
    def dropNullValues: Json = json match
      case Json.Obj(o) => Json.Obj(JsonObject(o.fields.filterNot(_._2.isNull)))
      case other       => other

    /** The same value with every object's keys in order, at any depth (what circe's `spaces2SortKeys` printed). */
    def sortedKeys: Json = json match
      case Json.Obj(o)  => Json.Obj(JsonObject(o.fields.sortBy(_._1).map((k, v) => k -> v.sortedKeys)))
      case Json.Arr(vs) => Json.Arr(vs.map(_.sortedKeys))
      case other        => other

    /** The same value with every object's keys sorted and its `null` fields left out, at every depth: a value always
      * prints to the same text (circe's `Printer.noSpaces.copy(sortKeys = true, dropNullValues = true)`).
      */
    def canonical: Json = json match
      case Json.Obj(o) =>
        Json.Obj(JsonObject(o.fields.filterNot(_._2.isNull).sortBy(_._1).map((k, v) => k -> v.canonical)))
      case Json.Arr(vs) => Json.Arr(vs.map(_.canonical))
      case other        => other

    /** The same value without `null` fields at any depth (circe's printer with `dropNullValues`). */
    def withoutNulls: Json = json match
      case Json.Obj(o)  => Json.Obj(JsonObject(o.fields.filterNot(_._2.isNull).map((k, v) => k -> v.withoutNulls)))
      case Json.Arr(vs) => Json.Arr(vs.map(_.withoutNulls))
      case other        => other

    /** The object with `f` applied to its fields; any other value as it is (circe's `mapObject`). */
    def mapObject(f: JsonObject => JsonObject): Json = json.asObject.fold(json)(o => Json.Obj(f(o)))

    /** The array with `f` applied to its elements; any other value as it is (circe's `mapArray`). */
    def mapArray(f: Vector[Json] => Vector[Json]): Json = json.asArray.fold(json)(vs => Json.Arr(f(vs)))

    /** `orElse` unless this is an array or an object (circe's `arrayOrObject`). */
    def arrayOrObject[X](orElse: => X, array: Vector[Json] => X, obj: JsonObject => X): X = json match
      case Json.Arr(vs) => array(vs)
      case Json.Obj(o)  => obj(o)
      case _            => orElse

    /** The JSON as an `A`, or why it is not one. */
    def as[A](using JsonValueCodec[A]): Either[String, A] = decode[A](json.noSpaces)

    /** A cursor at the document's root. */
    def cursor: Cursor = Cursor(Some(json))

    /** This object with `that`'s fields over it, objects merged field by field (circe's `deepMerge`). */
    def deepMerge(that: Json): Json = (json.asObject, that.asObject) match
      case (Some(a), Some(b)) =>
        Json.Obj(b.fields.foldLeft(a) { case (acc, (k, v)) =>
          acc(k) match
            case Some(old) =>
              JsonObject(acc.fields.map((ok, ov) => if ok == k then ok -> ov.deepMerge(v) else ok -> ov))
            case None => acc.add(k, v)
        })
      case _ => that

  extension (o: JsonObject)
    def toList: List[(String, Json)] = o.fields.toList
    def toIterable: Iterable[(String, Json)] = o.fields
    def filterKeys(keep: String => Boolean): JsonObject = JsonObject(o.fields.filter((k, _) => keep(k)))
    def filter(keep: ((String, Json)) => Boolean): JsonObject = JsonObject(o.fields.filter(keep))
    def contains(key: String): Boolean = o.fields.exists(_._1 == key)

    /** The object with `key` set to `value`, in place if it is there, else appended. */
    def updated(key: String, value: Json): JsonObject =
      if o.contains(key) then JsonObject(o.fields.map((k, v) => if k == key then k -> value else k -> v))
      else o.add(key, value)

  extension (n: JsonNumber)
    /** The number as a `Long` if it is a whole one in range, however written (`-546`, `-546.0`, `5e2`), as circe read
      * it; Kindlings' `toLong` reads only an integer literal.
      */
    def asLong: Option[Long] =
      n.toBigDecimal.flatMap(d => scala.util.Try(d.toBigIntExact).toOption.flatten).filter(_.isValidLong).map(_.toLong)

    /** The number as an `Int` if it is a whole one in range, however written (see [[asLong]]). */
    def asInt: Option[Int] = n.asLong.filter(_.isValidInt).map(_.toInt)

  extension (companion: Json.type)
    def fromFields(fields: Iterable[(String, Json)]): Json = Json.Obj(JsonObject.fromIterable(fields))

    /** A number from a double, `null` for a NaN or an infinity. */
    def fromDoubleOrNull(d: Double): Json = Json.fromDouble(d).getOrElse(Json.Null)
    def fromDoubleOrString(d: Double): Json = Json.fromDouble(d).getOrElse(Json.fromString(d.toString))

  extension [A](a: A)(using codec: JsonValueCodec[A])
    /** `a` as JSON. */
    def asJson: Json = readFromStringReentrant[Json](writeToStringReentrant(a), reading)

  private def layout(json: Json, colon: String): String =
    val out = StringBuilder()
    def indent(depth: Int): Unit = out.append("  " * depth)
    def go(j: Json, depth: Int): Unit = j match
      case Json.Arr(vs) if vs.isEmpty =>
        out.append("[\n"); indent(depth); out.append(']')
      case Json.Arr(vs) =>
        out.append("[\n")
        vs.zipWithIndex.foreach { (v, i) =>
          if i > 0 then out.append(",\n")
          indent(depth + 1); go(v, depth + 1)
        }
        out.append('\n'); indent(depth); out.append(']')
      case Json.Obj(o) if o.isEmpty =>
        out.append("{\n"); indent(depth + 1); out.append('\n'); indent(depth); out.append('}')
      case Json.Obj(o) =>
        out.append("{\n")
        o.fields.zipWithIndex.foreach { case ((k, v), i) =>
          if i > 0 then out.append(",\n")
          indent(depth + 1); out.append(Json.fromString(k).noSpaces).append(colon); go(v, depth + 1)
        }
        out.append('\n'); indent(depth); out.append('}')
      case other => out.append(other.noSpaces)
    go(json, 0)
    out.toString
