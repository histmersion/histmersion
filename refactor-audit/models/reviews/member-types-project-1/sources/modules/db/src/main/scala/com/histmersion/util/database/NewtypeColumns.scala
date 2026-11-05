package com.histmersion.util.database

import com.augustnagro.magnum.DbCodec
import io.github.iltotore.iron.RefinedType

import java.sql.{PreparedStatement, ResultSet, SQLException}

/** The column codec of every newtype, from its base type's. A value its constraint refuses fails the read, naming the
  * column and the value: the stored data breaks a rule the code relies on, and that should be loud. NULL passes
  * through, for `Option`'s codec to read as `None`.
  */
object NewtypeColumns:
  type Cols = Cols.T
  object Cols extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueryReprResult = QueryReprResult.T
  object QueryReprResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadSinglePos = ReadSinglePos.T
  object ReadSinglePos extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteSinglePos = WriteSinglePos.T
  object WriteSinglePos extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  given column: [T] => (m: RefinedType.Mirror[T]) => (base: DbCodec[m.BaseType]) => DbCodec[T] = new DbCodec[T]:
    val cols: IArray[com.histmersion.util.database.NewtypeColumns.Cols] = base.cols
    def queryRepr: com.histmersion.util.database.NewtypeColumns.QueryReprResult = base.queryRepr
    def readSingle(rs: ResultSet, pos: com.histmersion.util.database.NewtypeColumns.ReadSinglePos): T =
      val raw = base.readSingle(rs, pos)
      if raw == null then raw.asInstanceOf[T]
      else
        m.ops
          .either(raw)
          .fold(
            e => throw SQLException(s"column ${rs.getMetaData.getColumnLabel(pos)}: '$raw' is not valid: $e"),
            _.asInstanceOf[T]
          )
    def writeSingle(entity: T, ps: PreparedStatement, pos: com.histmersion.util.database.NewtypeColumns.WriteSinglePos): Unit =
      base.writeSingle(entity.asInstanceOf[m.BaseType], ps, pos)

  given listColumn: [T] => (m: RefinedType.Mirror[T]) => (base: DbCodec[List[m.BaseType]]) => DbCodec[List[T]] = new DbCodec[List[T]]:
    val cols: IArray[com.histmersion.util.database.NewtypeColumns.Cols] = base.cols
    def queryRepr: com.histmersion.util.database.NewtypeColumns.QueryReprResult = base.queryRepr
    def readSingle(rs: ResultSet, pos: com.histmersion.util.database.NewtypeColumns.ReadSinglePos): List[T] =
      base.readSingle(rs, pos).map { raw =>
        m.ops.either(raw).fold(
          e => throw SQLException(s"column ${rs.getMetaData.getColumnLabel(pos)}: '$raw' is not valid: $e"),
          _.asInstanceOf[T]
        )
      }
    def writeSingle(entity: List[T], ps: PreparedStatement, pos: com.histmersion.util.database.NewtypeColumns.WriteSinglePos): Unit =
      base.writeSingle(entity.map(_.asInstanceOf[m.BaseType]), ps, pos)
