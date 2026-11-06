package collisionfixture

object Values:
  type Names = NamesValue2
  type NamesValue2 = NamesValue2.T
  object NamesValue2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  val Names: List[Names] = List(_root_.collisionfixture.Values.NamesValue2("one"), _root_.collisionfixture.Values.NamesValue2("two"))
  val NamesValue = 17
  type Counts = CountsValue
  type CountsValue = CountsValue.T
  object CountsValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  val Counts: List[Counts] = List(_root_.collisionfixture.Values.CountsValue(3), _root_.collisionfixture.Values.CountsValue(7))
  type Label = LabelValue
  type LabelValue = LabelValue.T
  object LabelValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  val Label: Label = _root_.collisionfixture.Values.LabelValue("label")
  type Empty = EmptyValue
  type EmptyValue = EmptyValue.T
  object EmptyValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
