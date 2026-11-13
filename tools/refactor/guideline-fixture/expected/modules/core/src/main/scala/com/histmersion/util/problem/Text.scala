package com.histmersion
package util.problem

/** The sentence of a problem, as the problem module renders it. */
object Text:
  extension (problem: Problem)
    def sentence: String = problem match
      case Problem.Missing(field) => s"field $field is required"
      case _                      => "something failed"
