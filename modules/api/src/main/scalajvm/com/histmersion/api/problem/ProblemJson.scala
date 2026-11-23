package com.histmersion
package api
package problem

import util.problem.Problem

/** What a bare string is on the JVM, where a problem is otherwise read from its typed entry by the one codec both
  * platforms share (`NewtypeCodecs.problemCodec`, [[Answer.asProblem]]). A bare string is how a master version kept a
  * problem's text in a stored column or record: it reads through [[StoredShapes]] (the shapes a master version wrote;
  * any other text as [[Problem.Stored.Unrecognised]], whole), and is written back as that problem's entry.
  */
private[api] object ProblemJson:

  /** The problem a master version stored as this text. */
  def bare(text: String): Problem = StoredShapes.read(text)

  /** An entry whose case this build does not know, after [[Answer.translations]]: the sentence it was stored with, kept
    * whole (owner decision 7 of row 18, 2026-11-23).
    */
  def unknown(sentence: Problem.Sentence): Option[Problem] = Some(sentence.kept)
