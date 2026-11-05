package com.histmersion.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

/** One page of a tab's answer: its rows, the server's total, and the count line's extras for it (`200 concepts`, the
  * filter's time window, …).
  */
final case class TabPage[Row](rows: List[Row], total: com.histmersion.api.RowCount, extras: List[String] = Nil)
