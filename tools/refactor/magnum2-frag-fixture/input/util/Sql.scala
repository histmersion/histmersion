package com.histmersion
package util.database

import com.augustnagro.magnum.Frag

/** The 1.3.1-era fragment of the db module, as small as the rule needs: its symbols are what Magnum2Frag matches. */
final case class Sql(text: Sql.Text, params: List[Param] = Nil):
  def ++(that: Sql): Sql = Sql(Sql.Text(text.value + that.text.value), params ++ that.params)
  def ++(raw: String): Sql = Sql(Sql.Text(text.value + raw), params)
  def ++(that: Frag): Frag = Frag(text.value + that.sqlString, Nil, that.writer)
  def wrap(pre: String, post: String): Sql = Sql(Sql.Text(pre + text.value + post), params)
  def frag: Frag = Frag(text.value, Nil, (_, pos) => pos)

object Sql:
  final case class Text(value: String)
  type RawText = Text
  val RawText: Text.type = Text
  type OfText = Text
  val OfText: Text.type = Text

  val empty: Sql = Sql(Text(""), Nil)
  def raw(text: Text): Sql = Sql(text, Nil)
  def of(text: Text, ps: Param*): Sql = Sql(text, ps.toList)

enum Param:
  case S(v: Param.S.V)
  case L(v: Param.L.V)
  case A(v: List[Param.A.V])

object Param:
  object S:
    final case class V(value: String)
  object L:
    final case class V(value: Long)
  object A:
    final case class V(value: String)
