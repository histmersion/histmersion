package lensfixture

object Effects:
  var events = Vector.empty[String]

final case class Record(a: Int, b: String):
  def renamed: Record = copy(b = "renamed")

final case class Box[A](value: A)
final class Fake:
  def copy(a: Int): Int = a

object Example:
  def plain(r: Record): Record = r.copy(a = 2)
  def nested(r: Record): Record = r.copy(a = r.copy(a = 3).a)
  def multiple(r: Record): Record = r.copy(a = 2, b = "two")
  def generic(r: Box[Int]): Box[Int] = r.copy(value = 2)
  def unrelated(f: Fake): Int = f.copy(2)
  def fold(r: Record): Record = List(1, 2).foldLeft(r)((s, n) => s.copy(a = s.a + n))
  def receiver(): Record =
    Effects.events :+= "receiver"
    Record(1, "one")
  def argument(): Int =
    Effects.events :+= "argument"
    7
  def effectful(): Record = receiver().copy(a = argument())

@main def verify(): Unit =
  val r = Record(1, "one")
  assert(Example.plain(r) == Record(2, "one"))
  assert(Example.nested(r) == Record(3, "one"))
  assert(r.renamed == Record(1, "renamed"))
  assert(Example.multiple(r) == Record(2, "two"))
  assert(Example.generic(Box(1)) == Box(2))
  assert(Example.unrelated(new Fake) == 2)
  assert(Example.fold(r) == Record(4, "one"))
  assert(Example.effectful() == Record(7, "one"))
  assert(Effects.events == Vector("receiver", "argument"))
