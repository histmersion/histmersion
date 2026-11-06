package scalarfixture

object Excluded:
  type Positive = Positive.T
  object Positive extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.numeric.Positive]
  def constrained(x: Positive): Int = x + 1
  def regularCall(x: Numbers.Count): Int = java.lang.Math.abs(x)
  def callback(xs: List[Numbers.Count]): String = new java.lang.StringBuilder().append(xs.map(_.value))
  def arbitraryReceiver(x: Numbers.Count, other: Custom): Int = other * x
  def infixCallback: Numbers.Count => Int = 3 * (_: Numbers.Count)
  def unprovenReceiver(x: Numbers.Count): Int =
    val n = 3
    n * x
  final class Custom:
    def *(n: Int): Int = n
    def *(n: Long): Int = n.toInt

object Spoof:
  final class Int:
    def *(n: _root_.scala.Int): _root_.scala.Int = n
    def *(n: _root_.scala.Long): _root_.scala.Int = n.toInt
  def lookalike(x: Numbers.Count, other: Int): _root_.scala.Int = other * x
