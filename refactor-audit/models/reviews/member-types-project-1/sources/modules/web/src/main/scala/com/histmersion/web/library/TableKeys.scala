package com.histmersion.web.library

/** The arrow keys in a list: which row is chosen after ↑ or ↓. Pure. */
object TableKeys:

  /** A key the list answers by moving its choice: ↓ one row on, ↑ one row back. */
  type DeltaResult = DeltaResult.T
  object DeltaResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeltaKey = DeltaKey.T
  object DeltaKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepResult = StepResult.T
  object StepResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepRows = StepRows.T
  object StepRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepCurrent = StepCurrent.T
  object StepCurrent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StepDelta = StepDelta.T
  object StepDelta extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def delta(key: com.histmersion.web.library.TableKeys.DeltaKey): Option[com.histmersion.web.library.TableKeys.DeltaResult] = key match
    case "ArrowDown" => Some(1)
    case "ArrowUp"   => Some(-1)
    case _           => None

  /** The row chosen after moving `delta` rows from `current` among `rows`, held at the ends; with no choice on this
    * page yet, ↓ takes the first row and ↑ the last. `None` for an empty page.
    */
  def step(rows: List[com.histmersion.web.library.TableKeys.StepRows], current: Option[com.histmersion.web.library.TableKeys.StepCurrent], delta: com.histmersion.web.library.TableKeys.StepDelta): Option[com.histmersion.web.library.TableKeys.StepResult] =
    if rows.isEmpty then None
    else
      current.map(rows.indexOf).filter(_ >= 0) match
        case Some(i) => Some(rows(math.max(0, math.min(rows.size - 1, i + delta))))
        case None    => Some(if delta >= 0 then rows.head else rows.last)
