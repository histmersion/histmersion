package consolidationfixture

object Client:
  type Seconds = _root_.consolidationfixture.Shared.Seconds
  val Seconds: _root_.consolidationfixture.Shared.Seconds.type = _root_.consolidationfixture.Shared.Seconds

object Run:
  type Seconds = Seconds.T
  object Seconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Step:
    type Seconds = _root_.consolidationfixture.Run.Seconds
    val Seconds: _root_.consolidationfixture.Run.Seconds.type = _root_.consolidationfixture.Run.Seconds
  object Summary:
    type Seconds = _root_.consolidationfixture.Run.Seconds
    val Seconds: _root_.consolidationfixture.Run.Seconds.type = _root_.consolidationfixture.Run.Seconds
  object OtherUnit:
    type Milliseconds = Milliseconds.T
    object Milliseconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

object Outside:
  type Seconds = Seconds.T
  object Seconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

object BaseMismatch:
  type Seconds = Seconds.T
  object Seconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Other:
    type Seconds = Seconds.T
    object Seconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

object ConstraintMismatch:
  type Seconds = Seconds.T
  object Seconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Other:
    type Seconds = Seconds.T
    object Seconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.numeric.Greater[0L]]

object Behavior:
  type Seconds = Seconds.T
  object Seconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Other:
    type Seconds = Seconds.T
    object Seconds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]:
      def zero: Seconds = Seconds(0L)
