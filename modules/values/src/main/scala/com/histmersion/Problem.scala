package com.histmersion

import io.github.iltotore.iron.constraint.any.Pure

/** Why an operation failed, as a sentence for a person: the one failure message of the values, api, config and files
  * modules and of the clients built on them (cli, web).
  */
type Problem = Problem.T

object Problem extends Newtype[String, Pure]
