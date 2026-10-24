package com.histmersion.db

import com.histmersion.api.{Confidence, SourceStatus, EdgeKind, NeighborTarget, PlaceDating, PlaceTimeBasis, LocationPrecision}
import com.augustnagro.magnum.*
import io.circe.{Decoder, Encoder}
import io.circe.syntax.*

/** Magnum DbCodec instances for the domain vocabularies. Each reads/writes the lowercase wire string
  * that Postgres stores. Placed here (db module) because core has no Magnum dependency.
  *
  * ONE rule for a value the vocabulary does not know (#1798 item 11): it reads as the vocabulary's
  * UNKNOWN — the value that claims nothing — never as a confident member. Confidence has no such
  * member, so an unknown confidence reads as NOT STATED (`None`), never as `high`, which is what the
  * store once did. Writes are checked where they enter ([[Write]]: an unknown word is refused). */
object Vocab:
  /** A vocabulary column: `parse` the stored wire string, `unknown` for anything it does not know. */
  def codec[E](parse: String => Option[E], wire: E => String, unknown: E): DbCodec[E] =
    DbCodec[Option[String]].biMap(s => s.flatMap(v => parse(v.trim)).getOrElse(unknown), e => Some(wire(e)))

/** A `jsonb` column holding a wire DTO, crossing through that DTO's own circe codec (#1946) — the
  * Vocab.codec rule for JSON: select the column as `col::text`, bind the value as `$v::jsonb`, and a
  * stored document this build cannot read (NULL, or written by a newer build) reads as `fallback`
  * ([[codec]]) or as absent ([[optional]]), never as an exception. `dropNulls` leaves absent fields
  * out of what is written, for the columns whose readers treat a missing field as its default.
  * [[read]], [[decode]] and [[write]] are the same rules for a repo that holds the text itself. */
object Jsonb:
  def codec[A: {Encoder, Decoder}](fallback: A, dropNulls: Boolean = false): DbCodec[A] =
    DbCodec[Option[String]].biMap(_.flatMap(read[A]).getOrElse(fallback), a => Some(write(a, dropNulls)))

  def optional[A: {Encoder, Decoder}](dropNulls: Boolean = false): DbCodec[Option[A]] =
    DbCodec[Option[String]].biMap(_.flatMap(read[A]), _.map(write(_, dropNulls)))

  /** The stored text as an `A`; `None` when it does not decode. */
  def read[A: Decoder](text: String): Option[A] = decode[A](text).toOption

  /** The stored text as an `A`, or why it does not decode. */
  def decode[A: Decoder](text: String): Either[String, A] =
    io.circe.parser.decode[A](text).left.map(_.getMessage)

  /** What is bound into the column: compact JSON, without null fields when `dropNulls`. */
  def write[A: Encoder](a: A, dropNulls: Boolean = false): String =
    val j = a.asJson
    (if dropNulls then j.dropNullValues else j).noSpaces

/** `findings.confidence`: NULL and an unknown word are both "not stated". There is deliberately no
  * `DbCodec[Confidence]` — a column read as a bare confidence would need a default, and every default
  * claims something. */
given DbCodec[Option[Confidence]] = DbCodec[Option[String]].biMap(
  _.flatMap(s => Confidence.fromString(s.trim)),
  _.map(_.wire)
)

/** An unknown status is `unassessed` — nobody has judged it. */
given DbCodec[SourceStatus] = Vocab.codec(s => SourceStatus.values.find(_.wire.equalsIgnoreCase(s)), _.wire,
  SourceStatus.Unassessed)

/** An unknown edge kind is a plain `references` — the kind that claims no agreement or disagreement. */
given DbCodec[EdgeKind] = Vocab.codec(EdgeKind.fromWire, EdgeKind.wireOf, EdgeKind.References)

given DbCodec[NeighborTarget] = Vocab.codec(s => NeighborTarget.values.find(_.toString.equalsIgnoreCase(s)),
  _.toString.toLowerCase, NeighborTarget.Unresolved)

/** An unknown dating is `unknown` (it used to read as `stated`, a date the place does not have). */
given DbCodec[PlaceDating] = Vocab.codec(PlaceDating.parse, _.wire, PlaceDating.Unknown)

/** The basis is computed in SQL ([[PlaceRepo]]), so every value is known; `dated` is the basis that
  * claims nothing beyond the place's own period. */
given DbCodec[PlaceTimeBasis] = Vocab.codec(PlaceTimeBasis.parse, _.wire, PlaceTimeBasis.Dated)

/** `places.location_precision` (V077): NULL is a point as stated; an unknown value is the LESS
  * precise grid cell — a point no nearness may be read from — not "as stated". */
given DbCodec[LocationPrecision] = DbCodec[Option[String]].biMap(
  s => s.fold(LocationPrecision.Stated)(v => LocationPrecision.parse(v.trim).getOrElse(LocationPrecision.GridCell)),
  p => Option.when(p != LocationPrecision.Stated)(p.wire)
)

/** `DbCodec[List[String]]` for Postgres `text[]` columns — THE way arrays cross the JDBC boundary.
  * Reads via `java.sql.Array`, writes via `createArrayOf("text", ...)`: select the column as-is and
  * bind a list as `$list::text[]`. NULL (a LEFT JOIN miss) reads as `Nil`. */
given DbCodec[List[String]] = new DbCodec[List[String]]:
  val cols: IArray[Int] = IArray(java.sql.Types.ARRAY)
  def readSingle(rs: java.sql.ResultSet, pos: Int): List[String] =
    Option(rs.getArray(pos)).map(_.getArray) match
      // a text[] arrives as an Object[] (String[]); AnyRef elements are Matchable (E165, #879)
      case Some(a: Array[AnyRef]) => a.iterator.collect { case s: String if s.nonEmpty => s }.toList
      case _                 => Nil
  def writeSingle(e: List[String], ps: java.sql.PreparedStatement, pos: Int): Unit =
    val arr = ps.getConnection.createArrayOf("text", e.toArray[AnyRef])
    ps.setArray(pos, arr)
  override def queryRepr: String = "?"
