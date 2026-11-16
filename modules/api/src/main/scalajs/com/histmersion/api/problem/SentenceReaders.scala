package com.histmersion
package api
package problem

import util.problem.Problem

/** The readers [[Column.read]] tries in the browser: none. The server sends every problem typed, so the page never
  * reads a frozen sentence back, and leaving the readers out keeps them out of the page's bundle: a bare text is kept
  * as it was ([[Problem.Stored.Unrecognised]]).
  */
private[problem] object SentenceReaders:

  /** No case: the browser reads no column. */
  def cases(stored: String): Iterator[Problem] = Iterator.empty
