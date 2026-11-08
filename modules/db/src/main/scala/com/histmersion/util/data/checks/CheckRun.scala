package com.histmersion
package util.data.checks

import com.augustnagro.magnum.DbCon
import scala.collection.mutable

/** One run of the checks: every [[CheckInput]] a check asks for is read once and handed to the next check that asks. */
final class CheckRun:
  private val inputs = mutable.HashMap.empty[CheckInput[?], Any]

  def apply[A](input: CheckInput[A])(using DbCon): A =
    inputs.getOrElseUpdate(input, input.read).asInstanceOf[A]
