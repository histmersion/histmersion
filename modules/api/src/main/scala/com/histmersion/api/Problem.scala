package com.histmersion
package api

import io.github.iltotore.iron.constraint.any.Pure

/** Why an operation failed, as a sentence for a person: the one failure message of the wire contract. */
type Problem = Problem.T

object Problem extends Newtype[String, Pure]
