package com.histmersion
package domain

import com.histmersion.Problem as ValuesProblem
import com.histmersion.api.Problem as ApiProblem

/** The domain's failure message and the other layers' ones, converted where core meets them: a value that does not
  * parse (values), a record of the wire contract (api). Core has no Chimney, and the layers' messages are different
  * types on purpose.
  */
object ProblemTransformers:

  /** A failure of the values layer as the domain's. */
  def ofValues(p: ValuesProblem): Problem = Problem(p.value)

  /** A failure of the wire contract as the domain's. */
  def ofApi(p: ApiProblem): Problem = Problem(p.value)

  /** The domain's failure as the wire contract's, for a record of the api that carries it. */
  def toApi(p: Problem): ApiProblem = ApiProblem(p.value)
