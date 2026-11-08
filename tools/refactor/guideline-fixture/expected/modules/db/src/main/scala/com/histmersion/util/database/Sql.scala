package com.histmersion
package util.database

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

final class Sql(val text: String, val params: List[Int]):
  def ++(that: Sql): Sql = new Sql(text + that.text, params ++ that.params)
  def ++(raw: String): Sql = new Sql(text + raw, params)
  def wrap(pre: String, post: String): Sql = new Sql(pre + text + post, params)

object Sql:
  type Text = Text.T
  object Text extends RefinedType[String, Pure]
  val RawText: Text.type = Text
  val empty: Sql = new Sql("", Nil)
  def raw(text: Text): Sql = new Sql(text.value, Nil) // assert: GuidelineInventory.G44
  def of(text: Text, params: Int*): Sql = new Sql(text.value, params.toList) // assert: GuidelineInventory.G44
