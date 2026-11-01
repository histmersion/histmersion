package com.histmersion.db

import com.histmersion.api.{PlaceDating, PlaceTimeBasis}
import com.augustnagro.magnum.*
import io.circe.syntax.*
import com.histmersion.values.{Confidence, EdgeKind, LocationPrecision, SourceStatus}

/** `findings.confidence`: NULL and an unknown word are both "not stated". There is deliberately no
  * `DbCodec[Confidence]` — a column read as a bare confidence would need a default, and every default claims something.
  */
given DbCodec[Option[Confidence]] = DbCodec[Option[String]].biMap(
  _.flatMap(s => Confidence.fromString(s.trim)),
  _.map(_.wire)
)

/** An unknown status is `unassessed` — nobody has judged it. */
given DbCodec[SourceStatus] =
  Vocab.codec(s => SourceStatus.values.find(_.wire.equalsIgnoreCase(s)), _.wire, SourceStatus.Unassessed)

/** An unknown edge kind is a plain `references` — the kind that claims no agreement or disagreement. */
given DbCodec[EdgeKind] = Vocab.codec(EdgeKind.fromWire, EdgeKind.wireOf, EdgeKind.References)

/** An unknown dating is `unknown` (it used to read as `stated`, a date the place does not have). */
given DbCodec[PlaceDating] = Vocab.codec(PlaceDating.parse, _.wire, PlaceDating.Unknown)

/** The basis is computed in SQL ([[PlaceRepo]]), so every value is known; `dated` is the basis that claims nothing
  * beyond the place's own period.
  */
given DbCodec[PlaceTimeBasis] = Vocab.codec(PlaceTimeBasis.parse, _.wire, PlaceTimeBasis.Dated)

/** `places.location_precision` (V077): NULL is a point as stated; an unknown value is the LESS precise grid cell — a
  * point no nearness may be read from — not "as stated".
  */
given DbCodec[LocationPrecision] = DbCodec[Option[String]].biMap(
  s => s.fold(LocationPrecision.Stated)(v => LocationPrecision.parse(v.trim).getOrElse(LocationPrecision.GridCell)),
  p => Option.when(p != LocationPrecision.Stated)(p.wire)
)

/** `DbCodec[List[String]]` for Postgres `text[]` columns — THE way arrays cross the JDBC boundary. Reads via
  * `java.sql.Array`, writes via `createArrayOf("text", ...)`: select the column as-is and bind a list as
  * `$list::text[]`. NULL (a LEFT JOIN miss) reads as `Nil`.
  */
given DbCodec[List[String]] = new DbCodec[List[String]]:
  val cols: IArray[Int] = IArray(java.sql.Types.ARRAY)
  def readSingle(rs: java.sql.ResultSet, pos: Int): List[String] =
    Option(rs.getArray(pos)).map(_.getArray) match
      // a text[] arrives as an Object[] (String[]); AnyRef elements are Matchable (E165, #879)
      case Some(a: Array[AnyRef]) => a.iterator.collect { case s: String if s.nonEmpty => s }.toList
      case _                      => Nil
  def writeSingle(e: List[String], ps: java.sql.PreparedStatement, pos: Int): Unit =
    val arr = ps.getConnection.createArrayOf("text", e.toArray[AnyRef])
    ps.setArray(pos, arr)
  override def queryRepr: String = "?"
