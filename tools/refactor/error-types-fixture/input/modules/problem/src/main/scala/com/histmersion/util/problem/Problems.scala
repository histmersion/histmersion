package com.histmersion
package util.problem

/** The shape of the real problem module that ErrorTypesToProblems relies on: `Problem`, and `Problems` as a top-level
  * alias (its symbol is `Problems$package.Problems#`).
  */
sealed trait Problem

object Problem:
  final case class Unclassified(text: String) extends Problem

type Problems = Problem | Vector[Problem]
