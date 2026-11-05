package sigfixture

object Legacy:
  type WriteBytes = WriteBytes.T
  object WriteBytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadMaybeResult = ReadMaybeResult.T
  object ReadMaybeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ReadResult = ReadResult.T
  object ReadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Keep = Keep.T
  object Keep extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
  def read: sigfixture.Legacy.ReadResult = ???
  def write(bytes: sigfixture.Legacy.WriteBytes): Unit = ???
  def readMaybe: Option[sigfixture.Legacy.ReadMaybeResult] = ???
  def mixed(value: sigfixture.Legacy.Keep): Array[sigfixture.Legacy.Keep] = ???
  type CrossResult = CrossResult.T
  object CrossResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
  def cross: Array[sigfixture.Legacy.CrossResult] = ???
