package com.histmersion
package util.database

import com.augustnagro.magnum.DbCodec
import io.github.iltotore.iron.RefinedType

import java.sql.{PreparedStatement, ResultSet, SQLException}

/** The column codec of every newtype, from its base type's. A value its constraint refuses fails the read, naming the
  * column and the value: the stored data breaks a rule the code relies on, and that should be loud. NULL passes
  * through, for `Option`'s codec to read as `None`.
  */
object NewtypeColumns:
  given column: [T] => (m: RefinedType.Mirror[T]) => (base: DbCodec[m.BaseType]) => DbCodec[T] = new DbCodec[T]:
    val cols: IArray[Int] = base.cols
    def queryRepr: String = base.queryRepr
    def readSingle(rs: ResultSet, pos: Int): T =
      val raw = base.readSingle(rs, pos)
      if Option(raw).isEmpty then raw.asInstanceOf[T]
      else
        m.ops
          .either(raw)
          .fold(
            e => throw SQLException(s"column ${rs.getMetaData.getColumnLabel(pos)}: '$raw' is not valid: $e"),
            _.asInstanceOf[T]
          )
    def writeSingle(entity: T, ps: PreparedStatement, pos: Int): Unit =
      base.writeSingle(entity.asInstanceOf[m.BaseType], ps, pos)

  given listColumn: [T] => (m: RefinedType.Mirror[T]) => (base: DbCodec[List[m.BaseType]]) => DbCodec[List[T]] = new DbCodec[List[T]]:
    val cols: IArray[Int] = base.cols
    def queryRepr: String = base.queryRepr
    def readSingle(rs: ResultSet, pos: Int): List[T] =
      base.readSingle(rs, pos).map { raw =>
        m.ops.either(raw).fold(
          e => throw SQLException(s"column ${rs.getMetaData.getColumnLabel(pos)}: '$raw' is not valid: $e"),
          _.asInstanceOf[T]
        )
      }
    def writeSingle(entity: List[T], ps: PreparedStatement, pos: Int): Unit =
      base.writeSingle(entity.map(_.asInstanceOf[m.BaseType]), ps, pos)
