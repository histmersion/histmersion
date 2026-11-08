package com.histmersion
package util.database

object Queries:
  def typed: Sql = Sql.empty ++ Sql.empty
  def byRaw(where: String): Sql = Sql.raw(Sql.Text(where)) // assert: GuidelineInventory
  def byOf(where: String): Sql = Sql.of(Sql.RawText(s"WHERE $where"), 1) // assert: GuidelineInventory.G58raw
  def appended(s: Sql): Sql = s ++ " LIMIT 1" // assert: GuidelineInventory
  def wrapped(s: Sql): Sql = s.wrap("(", ")") // assert: GuidelineInventory
  def literal: Sql = Sql.raw(Sql.Text("SELECT 1")) // assert: GuidelineInventory.G58raw
  def applied(where: String): Sql = Sql.raw(Sql.Text.applyUnsafe(where)) // assert: GuidelineInventory
