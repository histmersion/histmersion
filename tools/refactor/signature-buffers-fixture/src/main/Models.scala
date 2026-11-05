package sigfixture

object Legacy:
  type ReadResult = ReadResult.T
  object ReadResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Keep = Keep.T
  object Keep extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
  def read: Array[sigfixture.Legacy.ReadResult] = ???
  def write(bytes: Array[Byte]): Unit = ???
  def readMaybe: Option[Array[Byte]] = ???
  def mixed(value: sigfixture.Legacy.Keep): Array[sigfixture.Legacy.Keep] = ???
  type CrossResult = CrossResult.T
  object CrossResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]
  def cross: Array[sigfixture.Legacy.CrossResult] = ???
