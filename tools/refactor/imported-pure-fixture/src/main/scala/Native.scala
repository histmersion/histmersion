package importedfixture

object Native:
  import io.github.iltotore.iron.*
  import io.github.iltotore.iron.constraint.any.Pure
  type Text = Text.T
  object Text extends RefinedType[String, Pure]
  type Count = Count.T
  object Count extends RefinedType[Int, Pure]
  type Flag = Flag.T
  object Flag extends RefinedType[Boolean, Pure]
  type Bytes = Bytes.T
  object Bytes extends RefinedType[Array[Byte], Pure]
  def make(raw: String): Text = raw
  def show(value: Text): String = value
  def many(raw: List[String]): List[Text] = raw
  def count(raw: Int): Count = raw
  def bytes(raw: Array[Byte]): Bytes = raw
  def flag(raw: Boolean): Flag = raw
  def truth(value: Flag): Boolean = value

object Alias:
  import io.github.iltotore.iron.{RefinedType as R}
  import io.github.iltotore.iron.constraint.any.{Pure as P}
  import scala.{Double as NativeDouble}
  type Text = Text.T
  object Text extends R[String, P]
  type Amount = Amount.T
  object Amount extends R[NativeDouble, P]
  def make(raw: String): Text = raw
  def amount(raw: _root_.scala.Double): Amount = raw

object Qualified:
  type Text = Text.T
  object Text extends io.github.iltotore.iron.RefinedType[String, io.github.iltotore.iron.constraint.any.Pure]
  def make(raw: String): Text = raw

object Contract:
  @main def checkImportedPure(): Unit =
    assert(Native.show(Native.make("one")) == "one")
    assert(Native.many(List("one", "two")).map(_.value) == List("one", "two"))
    assert(Native.many(Nil).isEmpty)
    assert(Native.count(Int.MaxValue).value == Int.MaxValue)
    val raw = Array[Byte](1, 2)
    assert(Native.bytes(raw).value eq raw)
    assert(Native.truth(Native.flag(true)) && !Native.truth(Native.flag(false)))
    assert(Alias.make("alias").value == "alias")
    assert(Alias.amount(-3.5).value == -3.5)
    assert(Qualified.make("qualified").value == "qualified")
