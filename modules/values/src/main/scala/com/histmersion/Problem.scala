package com.histmersion

import io.github.iltotore.iron.constraint.any.Pure

/** Why an operation failed, as a sentence for a person: the one failure message of the values, api, config and files
  * modules and of the clients built on them (cli, web).
  */
type Problem = Problem.T

object Problem extends Newtype[String, Pure]:
  /** The failure an exception states. */
  def of(t: Throwable): Problem = Problem(t.getMessage)

  extension (p: Problem)
    /** The failure without a leading `prefix` it may start with. */
    def stripPrefix(prefix: String): Problem = Problem(p.value.stripPrefix(prefix))
