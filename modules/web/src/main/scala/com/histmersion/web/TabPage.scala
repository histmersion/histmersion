package com.histmersion
package web

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import com.histmersion.api.RowCount
import io.github.iltotore.iron.constraint.any.Pure

/** One page of a tab's answer: its rows, the server's total, and the count line's extras for it (`200 concepts`, the
  * filter's time window, …).
  */
final case class TabPage[Row](rows: List[Row], total: RowCount, extras: List[TabPage.Extras] = Nil)

object TabPage:
  type Extras = Extras.T
  object Extras extends Newtype[String, Pure]
