package helperfixture

object Helpers:
  type Input = Input.T
  object Input extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Output = Output.T
  object Output extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  final case class Row(title: Title)
  def take(value: Input): String = value.value
  def output(value: Output): Output = value
  def bridge(value: Input): Output = output(_root_.helperfixture.Helpers.Output((value).value))
  def title(row: Row): Output = output(_root_.helperfixture.Helpers.Output((row.title).value))
  def many(values: List[Input]): List[Output] = (values).map(refactorValue => _root_.helperfixture.Helpers.Output((refactorValue).value))
  def tuple(factory: () => (Input, Input)): (Output, Output) = { val refactorTuple = factory(); (_root_.helperfixture.Helpers.Output((refactorTuple._1).value), _root_.helperfixture.Helpers.Output((refactorTuple._2).value)) }
  def made(factory: () => (Input => Input)): Output => Output = { val refactorFunction = factory(); (refactorValue2) => _root_.helperfixture.Helpers.Output((refactorFunction(_root_.helperfixture.Helpers.Input((refactorValue2).value))).value) }

  @main def checkHelpers(): Unit =
    assert(bridge(Input("one")).value == "one")
    assert(title(Row(Title("title"))).value == "title")
    assert(many(List(Input("one"), Input("two"))).map(_.value) == List("one", "two"))
    assert(many(Nil).isEmpty)
    var tuples = 0
    val pair = tuple(() => { tuples += 1; (Input("a"), Input("b")) })
    assert(tuples == 1 && pair._1.value == "a" && pair._2.value == "b")
    var created = 0
    var calls = 0
    val f = made(() => { created += 1; value => { calls += 1; Input(value.value + "!") } })
    assert(created == 1 && calls == 0)
    assert(f(Output("one")).value == "one!")
    assert(f(Output("two")).value == "two!")
    assert(created == 1 && calls == 2)
