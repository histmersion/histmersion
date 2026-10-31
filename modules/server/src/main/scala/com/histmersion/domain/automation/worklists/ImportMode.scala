package com.histmersion.domain.automation.worklists

/** What an import does with the list it imports into: empty it first (importing the same file twice is idempotent), or
  * add to it.
  */
enum ImportMode:
  case Replace, Append

object ImportMode:
  def of(replace: Boolean): ImportMode = if replace then Replace else Append
