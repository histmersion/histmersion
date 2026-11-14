package com.histmersion
package util.problem

import Text.sentences

/** Problems carried through a library that passes on only exceptions or text (a config reader's failure, which keeps a
  * `Throwable` as its cause): the layer that catches it reads [[problems]] back, typed, instead of the library's text.
  * Its message is the problems' sentences, for whoever prints the exception as it is.
  */
final class Raised(val problems: Problems)
    extends RuntimeException(problems.sentences.mkString("; "), null, false, false)
