package collisionfixture

object Values:
  type Names = Names.T
  object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  val Names: List[Names] = List(_root_.collisionfixture.Values.Names("one"), _root_.collisionfixture.Values.Names("two"))
  val NamesValue = 17
  type Counts = Counts.T
  object Counts extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  val Counts: List[Counts] = List(_root_.collisionfixture.Values.Counts(3), _root_.collisionfixture.Values.Counts(7))
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  val Label: Label = _root_.collisionfixture.Values.Label("label")
  type Empty = Empty.T
  object Empty extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  val Empty: List[Empty] = Nil
  def identity(value: Names): Names = value

object Consumer:
  @main def checkValueNames(): Unit =
    val named: Values.Names = Values.identity(Values.Names.head)
    assert(named.value == "one")
    assert(Values.Names(1).value == "two")
    assert(Values.Names.map(_.value) == List("one", "two"))
    assert(Values.Counts(0).value == 3 && Values.Counts(1).value == 7)
    assert(Values.Label.value == "label" && Values.NamesValue == 17)
    assert(Values.Empty.isEmpty)
