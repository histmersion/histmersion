package com.histmersion
package api
package problem

import util.problem.Problem
import com.histmersion.lint.{LintRule, tolerated}

/** What a bare string is in the browser, where a problem is otherwise read from its typed entry by the one codec both
  * platforms share (`NewtypeCodecs.problemCodec`, [[Answer.asProblem]]), so a problem nested in another's fields keeps
  * its case and the page writes every problem as the server does. The server sends every problem typed; a bare string
  * the page receives is outside text, kept as it came: a [[Problem.Relayed]] of it. The page reads no stored shape
  * ([[StoredShapes]] is the JVM's and the database's).
  */
private[api] object ProblemJson:

  /** The outside text, as it came. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def bare(text: String): Problem = Problem.Relayed(Problem.Sentence(s"$text"))

  /** An entry whose case this page does not know (owner decision E19): a tab left open across an upgrade meets a case
    * the newer server added, or one whose fields changed. It is shown by the sentence the server wrote for it, never as
    * a failure to read the whole answer.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def unknown(sentence: Problem.Sentence): Option[Problem] = Some(Problem.Relayed(sentence))
