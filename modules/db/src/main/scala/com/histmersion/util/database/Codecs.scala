package com.histmersion
package util.database
import com.augustnagro.magnum.*
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** `DbCodec[List[String]]` for Postgres `text[]` columns — THE way arrays cross the JDBC boundary. Reads via
  * `java.sql.Array`, writes via `createArrayOf("text", ...)`: select the column as-is and bind a list as
  * `$list::text[]`. NULL (a LEFT JOIN miss) reads as `Nil`.
  */
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
@atBoundary(BoundaryKind.Sql, "reads and writes text[] columns")
given DbCodec[List[String]] = new DbCodec[List[String]]:
  val cols: IArray[Int] = IArray(java.sql.Types.ARRAY)
  def readSingle(rs: java.sql.ResultSet, pos: Int): List[String] = readSingleOption(rs, pos).getOrElse(Nil)
  def readSingleOption(rs: java.sql.ResultSet, pos: Int): Option[List[String]] =
    Option(rs.getArray(pos)).map(_.getArray).map {
      // a text[] arrives as an Object[] (String[]); AnyRef elements are Matchable (E165, #879)
      case a: Array[AnyRef] => a.iterator.collect { case s: String if s.nonEmpty => s }.toList
      case _                => Nil
    }
  def writeSingle(e: List[String], ps: java.sql.PreparedStatement, pos: Int): Unit =
    val arr = ps.getConnection.createArrayOf("text", e.toArray[AnyRef])
    ps.setArray(pos, arr)
  override def queryRepr: String = "?"
