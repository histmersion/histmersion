package com.histmersion
package util.database
import com.augustnagro.magnum.*

import java.sql.{PreparedStatement, ResultSet, Types}
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** Magnum DbCodec instances for the domain vocabularies. Each reads/writes the lowercase wire string that Postgres
  * stores. Placed here (db module) because core has no Magnum dependency.
  *
  * ONE rule for a value the vocabulary does not know (#1798 item 11): it reads as the vocabulary's UNKNOWN — the value
  * that claims nothing — never as a confident member. Confidence has no such member, so an unknown confidence reads as
  * NOT STATED (`None`), never as `high`, which is what the store once did. Writes are checked where they enter
  * ([[Write]]: an unknown word is refused).
  */
@atBoundary(BoundaryKind.Sql, "reads and writes vocabularies as their wire strings in columns")
object Vocab:
  /** A vocabulary column: `parse` the stored wire string, `unknown` for anything it does not know. A NULL column reads
    * as `unknown`, and as `None` where the column is read as an `Option` (an outer join's missing row).
    */

  def codec[E](parse: WireEnumValues.Wire => Option[E], wire: E => WireEnumValues.Wire, unknown: E): DbCodec[E] =
    new DbCodec[E]:
      val cols: IArray[Int] = IArray(Types.VARCHAR)
      def queryRepr: String = "?"
      def readSingle(rs: ResultSet, pos: Int): E = readSingleOption(rs, pos).getOrElse(unknown)
      def readSingleOption(rs: ResultSet, pos: Int): Option[E] =
        Option(rs.getString(pos)).map(v => parse(WireEnumValues.Wire(v.trim)).getOrElse(unknown))
      @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
      def writeSingle(e: E, ps: PreparedStatement, pos: Int): Unit = ps.setString(pos, wire(e).value)
