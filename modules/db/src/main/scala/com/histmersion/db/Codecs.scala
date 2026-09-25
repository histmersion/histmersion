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

/** `DbCodec[List[String]]` for Postgres `text[]` columns. Reads via `java.sql.Array`, writes via
  * `createArrayOf("text", ...)`. Used when a DTO carries a proper list instead of a comma-joined
  * string and the SQL projection returns the column as-is (no `array_to_string`). */
given DbCodec[List[String]] = new DbCodec[List[String]]:
  val cols: IArray[Int] = IArray(java.sql.Types.ARRAY)
  def readSingle(rs: java.sql.ResultSet, pos: Int): List[String] =
    val arr = rs.getArray(pos)
    if arr == null then Nil
    else
      val a = arr.getArray.asInstanceOf[Array[?]]
      a.iterator.collect { case s: String if s.nonEmpty => s }.toList
  def writeSingle(e: List[String], ps: java.sql.PreparedStatement, pos: Int): Unit =
    val arr = ps.getConnection.createArrayOf("text", e.toArray[AnyRef])
    ps.setArray(pos, arr)
  override def queryRepr: String = "?"
