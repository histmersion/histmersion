package com.histmersion.db

import com.histmersion.core.{Confidence, SourceStatus, EdgeKind}
import com.augustnagro.magnum.*

/** Magnum DbCodec instances for the domain enums. Each reads/writes the lowercase wire string
  * that Postgres stores. Placed here (db module) because core has no Magnum dependency. */

given DbCodec[Confidence] = DbCodec[String].biMap(
  s => Confidence.fromString(s).getOrElse(Confidence.High),
  c => c.toString.toLowerCase
)

given DbCodec[SourceStatus] = DbCodec[String].biMap(
  s => SourceStatus.fromString(s),
  st => st.toString.toLowerCase
)

given DbCodec[EdgeKind] = DbCodec[String].biMap(
  s => EdgeKind.fromWire(s).getOrElse(EdgeKind.References),
  e => EdgeKind.wireOf(e)
)

/** `DbCodec[List[String]]` for Postgres `text[]` columns — THE way arrays cross the JDBC boundary.
  * Reads via `java.sql.Array`, writes via `createArrayOf("text", ...)`: select the column as-is and
  * bind a list as `$list::text[]`. NULL (a LEFT JOIN miss) reads as `Nil`. */
given DbCodec[List[String]] = new DbCodec[List[String]]:
  val cols: IArray[Int] = IArray(java.sql.Types.ARRAY)
  def readSingle(rs: java.sql.ResultSet, pos: Int): List[String] =
    Option(rs.getArray(pos)).map(_.getArray) match
      case Some(a: Array[?]) => a.iterator.collect { case s: String if s.nonEmpty => s }.toList
      case _                 => Nil
  def writeSingle(e: List[String], ps: java.sql.PreparedStatement, pos: Int): Unit =
    val arr = ps.getConnection.createArrayOf("text", e.toArray[AnyRef])
    ps.setArray(pos, arr)
  override def queryRepr: String = "?"
