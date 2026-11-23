package com.histmersion
package db

import com.histmersion.domain.{Citekey, Status}

object SourceRows:
  extension (sc: StringContext) def sql(args: Any*): String = sc.s(args*)
  def toParam(c: Citekey): String = c.value + raw"/$c" // assert: GuidelineInventory
  def fromRow(s: String): Citekey = Citekey.assume(s)
  def bind(x: String): String = x
  def q(st: Status): String = sql"UPDATE t SET status = $st" // assert: GuidelineInventory
