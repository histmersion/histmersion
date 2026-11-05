package com.histmersion.domain.automation.worklists

/** What an import does with the list it imports into: empty it first (importing the same file twice is idempotent), or
  * add to it.
  */
enum ImportMode:
  case Replace, Append

object ImportMode:
  type OfReplace = OfReplace.T
  object OfReplace extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def of(replace: com.histmersion.domain.automation.worklists.ImportMode.OfReplace): ImportMode = if replace then Replace else Append
