package com.histmersion.util.database
import com.augustnagro.magnum.Frag

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
  def ++(that: Sql): Sql = Sql(text + that.text, params ++ that.params)
  def ++(raw: String): Sql = Sql(text + raw, params)
  def wrap(pre: String, post: String): Sql = Sql(pre + text + post, params)

  /** This fragment, then a query written with Magnum's `sql""` — how a shared fragment (the window of a time-filtered
    * place view, `PlaceRepo.timeWindowCte`) heads such a query. Parameters bind in text order: this fragment's, then
    * the query's.
    */
  def ++(that: Frag): Frag =
    val ps = params
    Frag(
      text + that.sqlString,
      ps.map(Sql.value) ++ that.params,
      (stmt: java.sql.PreparedStatement, pos: Int) => that.writer.write(stmt, Sql.bind(stmt, pos, ps))
    )

  /** Hand off to Magnum. `Frag.params` is only used for logging; the writer does the binding. */
  def frag: Frag =
    val ps = params
    Frag(text, ps.map(Sql.value), (stmt: java.sql.PreparedStatement, pos: Int) => Sql.bind(stmt, pos, ps))

object Sql:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val empty: Sql = Sql("", Nil)
  def raw(text: String): Sql = Sql(text, Nil)
  def of(text: String, ps: Param*): Sql = Sql(text, ps.toList)

  private def value(p: Param): Any = p match
    case Param.S(v) => v
    case Param.L(v) => v
    case Param.I(v) => v
    case Param.D(v) => v
    case Param.A(v) => v

  private def bind(stmt: java.sql.PreparedStatement, pos: Int, ps: List[Param]): Int =
    var i = pos
    ps.foreach { p =>
      p match
        case Param.S(v) => stmt.setString(i, v)
        case Param.L(v) => stmt.setLong(i, v)
        case Param.I(v) => stmt.setInt(i, v)
        case Param.D(v) => stmt.setDouble(i, v)
        case Param.A(v) => stmt.setArray(i, stmt.getConnection.createArrayOf("text", v.toArray[AnyRef]))
      i += 1
    }
    i
