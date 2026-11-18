package com.histmersion.spike.cremabench

import java.util.function.LongUnaryOperator

/** Collatz step counts for 1..n: a CPU loop with no allocation, loaded from a jar at run time. */
final class Bench extends LongUnaryOperator:
  def applyAsLong(n: Long): Long =
    var acc = 0L
    var i = 1L
    while i <= n do
      var x = i
      var steps = 0L
      while x != 1L do
        x = if (x & 1L) == 0L then x >>> 1 else 3L * x + 1L
        steps += 1
      acc += steps
      i += 1
    acc
