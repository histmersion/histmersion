package modelinventoryfixture

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.numeric.*
import io.github.iltotore.iron.constraint.string.{Match, Blank}
import io.github.iltotore.iron.constraint.collection.MinLength
import io.github.iltotore.iron.constraint.any.Not

object ConstraintIdentities:
  summon[io.github.iltotore.iron.Pure =:= io.github.iltotore.iron.constraint.any.Pure]
  summon[scala.util.NotGiven[Positive =:= io.github.iltotore.iron.constraint.any.Pure]]
  summon[scala.util.NotGiven[Interval.Closed[-90.0, 90.0] =:= io.github.iltotore.iron.constraint.any.Pure]]

object ExportedPure extends RefinedType[Int, io.github.iltotore.iron.Pure]
object PositiveCount extends RefinedType[Int, Positive]
object NonnegativeCount extends RefinedType[Int, GreaterEqual[0]]
object Latitude extends RefinedType[Double, Interval.Closed[-90.0, 90.0]]
object Code extends RefinedType[String, Match["[a-z]+"]]
object Title extends RefinedType[String, MinLength[3]]
object Nonblank extends RefinedType[String, Not[Blank]]
