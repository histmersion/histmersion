package com.histmersion
package domain

final class SourceSuite
final class SourceOracleSuite
final class SourceFixture
final class SourceOrder

object Constants:
  final val key = "kept"
  inline val year = 1200
  val unsafe: Citekey = Citekey.applyUnsafe("smith2020")
  val assumed: Shelf = Shelf.assume("top")
  val joined: Citekey = Citekey.assume("smith" + "2020")
  val named: Citekey = Citekey(key)
  val inlined: Year = Year.applyUnsafe(year)
  val spliced: Citekey = Citekey.applyUnsafe(s"smith${2020}")
