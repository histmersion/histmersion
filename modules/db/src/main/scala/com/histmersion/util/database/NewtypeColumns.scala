package com.histmersion
package util.database

import com.augustnagro.magnum.DbCodec
import io.github.iltotore.iron.RefinedType

import java.sql.{PreparedStatement, ResultSet, SQLException}

import util.problem.Problem
import com.histmersion.api.problem.Column
import com.histmersion.api.problem.Column.stored
import com.histmersion.api.problem.Answer.relayed

/** The column codec of every newtype, from its base type's. A value its constraint refuses fails the read, naming the
  * column and the value: the stored data breaks a rule the code relies on, and that should be loud. NULL passes
  * through, for `Option`'s codec to read as `None`.
  *
  * A [[Problem]] column holds the frozen wording of the api's `Column` renderer and reads back through it (G60).
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

  given listColumn: [T] => (m: RefinedType.Mirror[T]) => (base: DbCodec[List[m.BaseType]]) => DbCodec[List[T]] =
    new DbCodec[List[T]]:
      val cols: IArray[Int] = base.cols
      def queryRepr: String = base.queryRepr
      def readSingle(rs: ResultSet, pos: Int): List[T] =
        base.readSingle(rs, pos).map { raw =>
          m.ops
            .either(raw)
            .fold(
              e => throw SQLException(s"column ${rs.getMetaData.getColumnLabel(pos)}: '$raw' is not valid: $e"),
              _.asInstanceOf[T]
            )
        }
      def writeSingle(entity: List[T], ps: PreparedStatement, pos: Int): Unit =
        base.writeSingle(entity.map(_.asInstanceOf[m.BaseType]), ps, pos)

  /** A problem column holds the problem's frozen stored sentence ([[Column]]) and reads back through it: a text no case
    * writes (an older version's) is `Stored.Unrecognised` and writes back bare. An unclassified problem is kept as its
    * words alone, so it reads back as the text it was; so is the unclassified why of an index left out of a sweep (a
    * watch's `last_error`: `not asked this sweep — <why>`, as the versions before the typed problem stored it).
    */
  given problemColumn: DbCodec[Problem] = DbCodec.StringCodec.biMap(
    text => Option(text).map(Column.read).orNull,
    problem =>
      Option(problem).map {
        case Problem.Unclassified(text)                          => s"$text"
        case Problem.Upstream.Unasked(why: Problem.Unclassified) => Problem.Upstream.Unasked(why.relayed).stored
        case other                                               => other.stored
      }.orNull
  )
