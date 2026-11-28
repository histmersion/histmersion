package com.histmersion
package util.database

import com.augustnagro.magnum.DbCodec
import io.github.iltotore.iron.RefinedType

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import java.sql.{PreparedStatement, ResultSet, SQLException, Types}

import util.problem.Problem
import util.nonempty.NonEmptyVector
import util.json.RawJson
import com.histmersion.api.json.TwoStates
import util.json.RawJson.*
import com.histmersion.api.json.NewtypeCodecs.{problemCodec, vector}
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

/** The column codec of every newtype, from its base type's. A value its constraint refuses fails the read, naming the
  * column and the value: the stored data breaks a rule the code relies on, and that should be loud. NULL passes
  * through, for `Option`'s codec to read as `None`.
  *
  * A [[Problem]] column (`jsonb`) holds every problem's derived entry, `{"group", "case", "data", "text"}`, in an
  * array: readable in SQL through `text` (`error -> 0 ->> 'text'`), and read back as the cases they were written from.
  * A bare string (the text a master version kept) reads through `api.problem.StoredShapes`.
  */
@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
@atBoundary(BoundaryKind.Sql, "reads and writes a newtype column from its base type's codec")
object NewtypeColumns:
  given twoStates: [E <: reflect.Enum] => (states: TwoStates[E]) => DbCodec[E] =
    summon[DbCodec[Boolean]].biMap(b => states.values(if b then 0 else 1), _.ordinal == 0)

  given column: [T] => (m: RefinedType.Mirror[T]) => (base: DbCodec[m.BaseType]) => DbCodec[T] = new DbCodec[T]:
    val cols: IArray[Int] = base.cols
    def queryRepr: String = base.queryRepr
    def readSingle(rs: ResultSet, pos: Int): T =
      val raw = base.readSingle(rs, pos)
      if Option(raw).isEmpty then raw.asInstanceOf[T] else checked(m, rs, pos, raw)
    def readSingleOption(rs: ResultSet, pos: Int): Option[T] =
      base.readSingleOption(rs, pos).map(raw => checked(m, rs, pos, raw))
    def writeSingle(entity: T, ps: PreparedStatement, pos: Int): Unit =
      base.writeSingle(entity.asInstanceOf[m.BaseType], ps, pos)

  given listColumn: [T] => (m: RefinedType.Mirror[T]) => (base: DbCodec[List[m.BaseType]]) => DbCodec[List[T]] =
    new DbCodec[List[T]]:
      val cols: IArray[Int] = base.cols
      def queryRepr: String = base.queryRepr
      def readSingle(rs: ResultSet, pos: Int): List[T] = base.readSingle(rs, pos).map(raw => checked(m, rs, pos, raw))
      def readSingleOption(rs: ResultSet, pos: Int): Option[List[T]] =
        base.readSingleOption(rs, pos).map(_.map(raw => checked(m, rs, pos, raw)))
      def writeSingle(entity: List[T], ps: PreparedStatement, pos: Int): Unit =
        base.writeSingle(entity.map(_.asInstanceOf[m.BaseType]), ps, pos)

  /** The stored value as the newtype, or the read fails naming the column and the value. */
  private def checked[T](m: RefinedType.Mirror[T], rs: ResultSet, pos: Int, raw: m.BaseType): T =
    m.ops
      .either(raw)
      .fold(
        e => throw SQLException(s"column ${rs.getMetaData.getColumnLabel(pos)}: '$raw' is not valid: $e"),
        _.asInstanceOf[T]
      )

  /** A problems' column (a job's, a step's, a watch's, a plan pool's): every problem's entry, in order, in a JSON
    * array.
    */
  given problemsColumn: DbCodec[NonEmptyVector[Problem]] =
    jsonbColumn[Vector[Problem]].biMap(
      all =>
        Option(all)
          .map(a => NonEmptyVector.from(a).getOrElse(throw SQLException("a stored problem list is empty")))
          .getOrElse(null.asInstanceOf[NonEmptyVector[Problem]]),
      problems => NonEmptyVector.toVector(problems)
    )

  /** A `jsonb` column of `A`'s JSON: bound as an untyped parameter (the column's own type takes it, so no `::jsonb` is
    * needed at the call), read as its text. A document that does not decode fails the read, naming the column.
    */
  private def jsonbColumn[A](using codec: JsonValueCodec[A]): DbCodec[A] = new DbCodec[A]:
    val cols: IArray[Int] = IArray(Types.OTHER)
    def queryRepr: String = "?"
    def readSingle(rs: ResultSet, pos: Int): A = readSingleOption(rs, pos).getOrElse(null.asInstanceOf[A])
    def readSingleOption(rs: ResultSet, pos: Int): Option[A] =
      Option(rs.getString(pos)).map(text =>
        RawJson
          .decode[A](text)
          .fold(
            e => throw SQLException(s"column ${rs.getMetaData.getColumnLabel(pos)}: '$text' does not read: $e"),
            identity
          )
      )
    def writeSingle(entity: A, ps: PreparedStatement, pos: Int): Unit =
      ps.setObject(pos, entity.asJson.noSpaces, Types.OTHER)
