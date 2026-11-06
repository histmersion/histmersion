package compilerfixture

final case class Record(text: Values.Text, count: Values.Count, flag: Boolean = Record.defaultFlag())
object Record:
  var legacyCalls = 0
  var defaultCalls = 0
  def defaultFlag(): Boolean = { defaultCalls += 1; true }
  def apply(text: Values.Text, count: Values.Count): Record =
    legacyCalls += 1
    new Record(text, count, false)

final case class Envelope(text: Values.Text, values: List[Values.Text], optional: Option[Values.Text], flag: Boolean = false)
object Envelope:
  def apply(text: Values.Text, values: List[Values.Text], optional: Option[Values.Text]): Envelope = new Envelope(text, values, optional, false)

final case class Choice(value: Values.Text)
object Choice:
  @scala.annotation.targetName("other")
  def apply(value: Values.Other): Choice = new Choice(Values.Text(value.value))

final case class StrictOverload(value: Values.Strict, flag: Boolean = false)
object StrictOverload:
  def apply(value: Values.Strict): StrictOverload = new StrictOverload(value, false)

object Overloads:
  def multilineFlag(): Unit =
    assert(
      Values.Flag(
        true
      )
    )
  def named(text: String, count: Int): Record = Record(count = count, text = text)
  def positional(text: String, count: Int): Record = Record(text, count)
  def primary(text: String, count: Int): Record = Record(text, count, flag = true)
  def empty: Envelope = Envelope("one", Nil, None)
  def texts(values: Set[String]): Set[Values.Text] = values
  def bytes(values: Array[Byte]): Values.Bytes = values
  def populated: Envelope = Envelope("one", Nil, Some("raw"))
