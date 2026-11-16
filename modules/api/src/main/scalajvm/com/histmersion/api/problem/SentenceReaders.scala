package com.histmersion
package api
package problem

import util.problem.Problem

/** The readers [[Column.read]] tries on the JVM: every frozen sentence, so a stored text reads back as its case. */
private[problem] object SentenceReaders:

  /** The cases a column's text may stand for, in the readers' order. */
  def cases(stored: String): Iterator[Problem] = Column.Reading.cases(stored)
