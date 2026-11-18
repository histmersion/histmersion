package com.histmersion
package api
package problem

import util.problem.Problem

/** What a bare string is in the browser, where a problem is otherwise read from its typed entry by the one codec both
  * platforms share (`NewtypeCodecs.problemCodec`, [[Answer.asProblem]]), so a problem nested in another's fields keeps
  * its case and the page writes every problem as the server does. The server sends every problem typed; a bare string
  * the page receives is outside text, kept as it came: a [[Problem.Relayed]] of it. The page reads no stored shape
  * ([[StoredShapes]] is the JVM's and the database's).
  */
private[api] object ProblemJson:

  /** The outside text, as it came. */
  def bare(text: String): Problem = Problem.Relayed(Problem.Sentence(s"$text"))
