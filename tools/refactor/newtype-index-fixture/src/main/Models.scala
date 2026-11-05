package indexfixture

object First:
  type Seconds = Seconds.T
  object Seconds extends _root_.io.github.iltotore.iron.RefinedType[Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Payload = Payload.T
  object Payload extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
object Second:
  type Seconds = Seconds.T
  object Seconds extends _root_.io.github.iltotore.iron.RefinedType[Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Payload = Payload.T
  object Payload extends _root_.io.github.iltotore.iron.RefinedType[Array[Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
object Third:
  type Seconds = Seconds.T
  object Seconds extends _root_.io.github.iltotore.iron.RefinedType[Long, _root_.io.github.iltotore.iron.constraint.numeric.Greater[0L]]
opaque type Label = String
class Token
object Usage:
  import First.Seconds
  val direct: indexfixture.First.Seconds = ???
  val imported: Seconds = ???
  def raw(value: String): Long = ???
