package com.histmersion
package util.database
import com.augustnagro.magnum.Frag
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A composable SQL fragment: text with `?` placeholders plus the parameters that fill them, in order.
  *
  * Magnum's `sql""` interpolator cannot splice one query into another — an interpolated `Frag` is treated as a bind
  * *parameter* (`setObject`), not as SQL — so composition happens here instead, and the finished fragment is handed to
  * Magnum as a single [[Frag]] via [[frag]]. Parameters are always bound, never inlined, so a fragment cannot be an
  * injection vector.
  *
  * This is the ONE fragment type of the `db` module: [[FilterSql]] (`Filter → SQL`), [[Search]] (FTS) and
  * [[AggregateRepo]] all build and splice these, which is what lets `Filter.q` be an FTS predicate instead of an
  * `ILIKE`.
  */
final case class Sql(text: Sql.Text, params: List[Param] = Nil):
  def ++(that: Sql): Sql = Sql(Sql.Text(text.value + that.text.value), params ++ that.params)
  def ++(raw: String): Sql = Sql(Sql.Text(text.value + raw), params)
  def wrap(pre: String, post: String): Sql = Sql(Sql.Text(pre + text.value + post), params)

  /** This fragment, then a query written with Magnum's `sql""` — how a shared fragment (the window of a time-filtered
    * place view, `PlaceRepo.timeWindowCte`) heads such a query. Parameters bind in text order: this fragment's, then
    * the query's.
    */
  def ++(that: Frag): Frag =
    val ps = params
    Frag(
      text.value + that.sqlString,
      ps.map(Sql.value) ++ that.params,
      (stmt: java.sql.PreparedStatement, pos: Int) => that.writer.write(stmt, Sql.bind(stmt, pos, ps))
    )

  /** Hand off to Magnum. `Frag.params` is only used for logging; the writer does the binding. */
  def frag: Frag =
    val ps = params
    Frag(text.value, ps.map(Sql.value), (stmt: java.sql.PreparedStatement, pos: Int) => Sql.bind(stmt, pos, ps))

object Sql:
  type RawText = Text
  val RawText: Text.type = Text
  type OfText = Text
  val OfText: Text.type = Text

  type Text = Text.T
  object Text extends RefinedType[String, Pure]

  val empty: Sql = Sql(Text(""), Nil)
  def raw(text: Text): Sql = Sql(text, Nil)
  def of(text: Text, ps: Param*): Sql = Sql(text, ps.toList)

  private def value(p: Param): Any = p match
    case Param.S(v) => v.value
    case Param.L(v) => v.value
    case Param.I(v) => v.value
    case Param.D(v) => v.value
    case Param.A(v) => v.map(_.value)

  private def bind(stmt: java.sql.PreparedStatement, pos: Int, ps: List[Param]): Int =
    var i = pos
    ps.foreach { p =>
      p match
        case Param.S(v) => stmt.setString(i, v.value)
        case Param.L(v) => stmt.setLong(i, v.value)
        case Param.I(v) => stmt.setInt(i, v.value)
        case Param.D(v) => stmt.setDouble(i, v.value)
        case Param.A(v) => stmt.setArray(i, stmt.getConnection.createArrayOf("text", v.map(_.value).toArray[AnyRef]))
      i += 1
    }
    i
