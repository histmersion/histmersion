package com.histmersion
package api
package problem

import util.problem.Problem

/** The sentence of a problem, as the api renders it. */
object Text:
  extension (problem: Problem)
    def sentence: String = problem match
      case Problem.Missing(field) => s"field $field is required"
      case _                      => "something failed"
