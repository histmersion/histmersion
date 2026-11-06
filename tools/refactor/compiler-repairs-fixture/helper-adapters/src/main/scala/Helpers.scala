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
  def bridge(value: Input): Output = output(value)
  def title(row: Row): Output = output(row.title)
  def many(values: List[Input]): List[Output] = values
  def tuple(factory: () => (Input, Input)): (Output, Output) = factory()
  def made(factory: () => (Input => Input)): Output => Output = factory()

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
