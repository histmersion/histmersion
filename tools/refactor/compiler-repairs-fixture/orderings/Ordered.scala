package compilerfixture

object Ordered:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Alias = Count
  type Ratio = Ratio.T
  object Ratio extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Flag = Flag.T
  object Flag extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Bytes = Bytes.T
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WithBody = WithBody.T
  object WithBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    def zero: T = apply(0)
  type Braced = Braced.T
  object Braced extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure] {}
  type Custom = Custom.T
  object Custom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    given Ordering[T] = Ordering.by[T, Int](_.value).reverse

object OrderedChecks:
  def texts(values: List[Ordered.Text]): List[Ordered.Text] = values.sorted
  def counts(values: List[Ordered.Count]): List[Ordered.Count] = values.sorted
  def alias(values: List[Ordered.Alias]): List[Ordered.Alias] = values.sorted
  def ratios(values: List[Ordered.Ratio]): List[Ordered.Ratio] = values.sorted
  def flags(values: List[Ordered.Flag]): List[Ordered.Flag] = values.sorted
  def tuples(values: List[(Int, Ordered.Text)]): List[(Int, Ordered.Text)] = values.sorted
  def custom(values: List[Ordered.Custom]): List[Ordered.Custom] = values.sorted

  def check(): Unit =
    assert(texts(List(Ordered.Text("z"), Ordered.Text("a"), Ordered.Text("a"))).map(_.value) == List("a", "a", "z"))
    assert(counts(List(Ordered.Count(Int.MaxValue), Ordered.Count(-1), Ordered.Count(Int.MinValue))).map(_.value) == List(Int.MinValue, -1, Int.MaxValue))
    assert(alias(List(Ordered.Count(2), Ordered.Count(1))).map(_.value) == List(1, 2))
    assert(flags(List(Ordered.Flag(true), Ordered.Flag(false))).map(_.value) == List(false, true))
    val raw = List(Double.NaN, Double.PositiveInfinity, -0.0, 0.0, Double.NegativeInfinity, 1.0)
    assert(ratios(raw.map(Ordered.Ratio.apply)).map(v => java.lang.Double.doubleToRawLongBits(v.value)) == raw.sorted.map(java.lang.Double.doubleToRawLongBits))
    assert(tuples(List(2 -> Ordered.Text("a"), 1 -> Ordered.Text("z"), 1 -> Ordered.Text("a"))).map((n, t) => n -> t.value) == List(1 -> "a", 1 -> "z", 2 -> "a"))
    assert(custom(List(Ordered.Custom(1), Ordered.Custom(3), Ordered.Custom(2))).map(_.value) == List(3, 2, 1))
