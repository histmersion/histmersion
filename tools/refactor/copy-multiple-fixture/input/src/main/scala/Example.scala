package lensfixture

object Effects:
  var events = Vector.empty[String]
  def mark[A](label: String, value: A): A =
    events :+= label
    value

final case class Record(a: Int, b: String):
  def reset: Record = copy(a = 0, b = "")
final case class Observed(a: Int, b: Int):
  Effects.events :+= "construct"
final case class Mutable(var a: Int, b: Int)
trait Parent
final case class Inherited(a: Int, b: Int) extends Parent

object Example:
  def update(r: Record): Record = r.copy(b = Effects.mark("b", "two"), a = Effects.mark("a", 2))
  def nested(r: Record): Record = r.copy(a = r.copy(a = 4, b = "four").a, b = "outer")
  def receiver(): Record = Effects.mark("receiver", Record(1, "one"))
  def effectful(): Record = update(receiver())
  def observed(r: Observed): Observed = r.copy(a = Effects.mark("a", 2), b = Effects.mark("b", 3))
  def mutable(r: Mutable): Mutable = r.copy(a = 2, b = 3)
  def inherited(r: Inherited): Inherited = r.copy(a = 2, b = 3)
  def positional(r: Record): Record = r.copy(2, "two")
  def empty(r: Record): Record = r.copy()
  def fold(r: Record): Record = List(1).foldLeft(r)((s, n) => s.copy(a = n, b = "loop"))

@main def verify(): Unit =
  val r = Record(1, "one")
  assert(Example.effectful() == Record(2, "two"))
  assert(Effects.events == Vector("receiver", "b", "a"))
  assert(r.reset == Record(0, ""))
  assert(Example.nested(r) == Record(4, "outer"))
  val observed = Observed(0, 0)
  Effects.events = Vector.empty
  val result = Example.observed(observed)
  assert(result.a == 2 && result.b == 3)
  assert(Effects.events == Vector("a", "b", "construct"))
