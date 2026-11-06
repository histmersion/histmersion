package scalarfixture

object Numbers:
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Ratio = Ratio.T
  object Ratio extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Offset = Offset.T
  object Offset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Letter = Letter.T
  object Letter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Char, _root_.io.github.iltotore.iron.constraint.any.Pure]

object Operations:
  def add(x: Numbers.Count): Int = (x).value + 1
  def multiply(x: Numbers.Ratio): Double = 3.0 * (x).value
  def compare(x: Numbers.Ratio): Boolean = 0.0 < (x).value
  def upper(x: Numbers.Count): Boolean = (x).value <= 10
  def divide(x: Numbers.Count): Int = 10 / (x).value
  def bits(x: Numbers.Count): Int = 3 & (x).value
  def offset(x: Numbers.Offset): Long = (x).value + 2L
  def letter(x: Numbers.Letter): Int = (x).value.toInt
  def ownedReceiver(x: Numbers.Ratio, y: Numbers.Ratio): Boolean = x.value >= (y).value
  def evaluatedOnce(next: () => Numbers.Count): Int = (next()).value + 1
  final case class Row(count: Numbers.Count)
  def selected(row: Row): Int = (row.count).value + 1
  def positive(xs: List[Numbers.Count]): List[Numbers.Count] = xs.filter((_).value > 0)
  def negate(x: Numbers.Count): Int = -(x).value

object Contract:
  @main def checkNativeScalar(): Unit =
    assert(Operations.add(Numbers.Count(2)) == 3)
    assert(Operations.add(Numbers.Count(Int.MaxValue)) == Int.MinValue)
    assert(Operations.multiply(Numbers.Ratio(-2.5)) == -7.5)
    assert(Operations.compare(Numbers.Ratio(0.5)))
    assert(!Operations.compare(Numbers.Ratio(Double.NaN)))
    assert(Operations.upper(Numbers.Count(10)))
    assert(!Operations.upper(Numbers.Count(11)))
    assert(Operations.divide(Numbers.Count(2)) == 5)
    assert(scala.util.Try(Operations.divide(Numbers.Count(0))).failed.get.isInstanceOf[ArithmeticException])
    assert(Operations.bits(Numbers.Count(5)) == 1)
    assert(Operations.offset(Numbers.Offset(Long.MaxValue)) == Long.MinValue + 1)
    assert(Operations.letter(Numbers.Letter('A')) == 65)
    assert(Operations.ownedReceiver(Numbers.Ratio(2.0), Numbers.Ratio(1.0)))
    var calls = 0
    assert(Operations.evaluatedOnce(() => { calls += 1; Numbers.Count(4) }) == 5)
    assert(calls == 1)
    assert(Operations.selected(Operations.Row(Numbers.Count(4))) == 5)
    assert(Operations.positive(List(Numbers.Count(-1), Numbers.Count(0), Numbers.Count(2))).map(_.value) == List(2))
    assert(Operations.negate(Numbers.Count(Int.MinValue)) == Int.MinValue)
