package com.histmersion.web.library

/** The arrow keys in a list: which row is chosen after ↑ or ↓. Pure. */
object TableKeys:

  /** A key the list answers by moving its choice: ↓ one row on, ↑ one row back. */
  def delta(key: String): Option[Int] = key match
    case "ArrowDown" => Some(1)
    case "ArrowUp"   => Some(-1)
    case _           => None

  /** The row chosen after moving `delta` rows from `current` among `rows`, held at the ends; with no choice on this
    * page yet, ↓ takes the first row and ↑ the last. `None` for an empty page.
    */
  def step(rows: List[String], current: Option[String], delta: Int): Option[String] =
    if rows.isEmpty then None
    else
      current.map(rows.indexOf).filter(_ >= 0) match
        case Some(i) => Some(rows(math.max(0, math.min(rows.size - 1, i + delta))))
        case None    => Some(if delta >= 0 then rows.head else rows.last)
