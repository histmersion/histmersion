package com.histmersion
package chimneyiron

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*

import java.util.concurrent.atomic.AtomicInteger

type CitekeyRule = Match["[a-z][a-z0-9_-]*"]

final class NonBlankCounted
object NonBlankCounted:
  val checksRun = AtomicInteger(0)

  def recordCheck(passed: Boolean): Boolean =
    checksRun.incrementAndGet()
    passed

  given Constraint[String, NonBlankCounted]:
    override inline def test(inline value: String): Boolean = recordCheck(value.trim.nonEmpty)
    override inline def message: String = "must not be blank"
