package com.histmersion.util.database
import com.augustnagro.magnum.*
import io.circe.syntax.*













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
