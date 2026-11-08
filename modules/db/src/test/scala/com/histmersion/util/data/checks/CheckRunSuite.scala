package com.histmersion
package util.data.checks

import com.augustnagro.magnum.DbCon

/** One run reads each input once, whichever check asks first; another run reads it again. */
class CheckRunSuite extends munit.FunSuite:
  // the inputs here read no database: the connection is never touched
  private given DbCon = null.asInstanceOf[DbCon]

  test("an input is read once per run, and again by the next run"):
    var reads = 0
    val input = CheckInput { reads += 1; List("a", "b") }
    val run = CheckRun()
    assertEquals(run(input), List("a", "b"))
    assertEquals(run(input), List("a", "b"))
    assertEquals(reads, 1)
    assertEquals(CheckRun()(input), List("a", "b"))
    assertEquals(reads, 2)

  test("two inputs of the same value are two reads"):
    var reads = 0
    val one = CheckInput { reads += 1; 1 }
    val two = CheckInput { reads += 1; 1 }
    val run = CheckRun()
    assertEquals(run(one) + run(two), 2)
    assertEquals(reads, 2)
