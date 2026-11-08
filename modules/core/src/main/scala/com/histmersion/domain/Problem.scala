package com.histmersion
package domain

import com.histmersion.Problem as ValuesProblem
import com.histmersion.api.Problem as ApiProblem
import io.github.iltotore.iron.constraint.any.Pure

/** Why a core operation failed, as a sentence for a person: the one failure message of the domain layer. */
type Problem = Problem.T

object Problem extends Newtype[String, Pure]:

  /** A failure of the values layer (a value that does not parse), as the domain's. */
  def fromValues(p: ValuesProblem): Problem = apply(p.value)

  /** A failure of the wire contract, as the domain's. */
  def fromApi(p: ApiProblem): Problem = apply(p.value)

  /** The domain's failure as the wire contract's, where a record of the api carries it. */
  def wire(p: Problem): ApiProblem = ApiProblem(p.value)

  /** The domain's failures as the wire contract's. */
  def wireAll(ps: List[Problem]): List[ApiProblem] = ps.map(wire)
