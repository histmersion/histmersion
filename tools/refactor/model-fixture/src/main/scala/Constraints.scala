package modelinventoryfixture

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.Greater

object StrictCount extends RefinedType[Int, Greater[0]]

object AliasedCount extends RefinedType[Int, Unrestricted]

type Pure = Greater[0]
object Lookalike extends RefinedType[Int, Pure]
