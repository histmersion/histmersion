package com.histmersion
package domain

import io.github.iltotore.iron.constraint.any.Pure

/** Why a core operation failed, as a sentence for a person: the one failure message of the domain layer. */
type Problem = Problem.T

object Problem extends Newtype[String, Pure]
