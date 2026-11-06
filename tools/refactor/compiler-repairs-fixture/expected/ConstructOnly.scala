package compilerfixture

final case class ConstructRecord(text: Values.Text, count: Values.Count, flag: Boolean = false)
object ConstructRecord:
  def apply(text: Values.Text, count: Values.Count): ConstructRecord = new ConstructRecord(text, count, false)

enum ConstructMode(val wire: Values.Text):
  case Finishing extends ConstructMode(_root_.compilerfixture.Values.Text("finish"))

object ConstructOnly:
  def text(raw: String): Values.Text = _root_.compilerfixture.Values.Text(raw)
  def large: Values.Large = _root_.compilerfixture.Values.Large((7).toLong)
  def texts(raw: List[String]): List[Values.Text] = (raw).map(refactorValue => _root_.compilerfixture.Values.Text(refactorValue))
  def record(text: String, count: Int): ConstructRecord = ConstructRecord(count = _root_.compilerfixture.Values.Count(count), text = _root_.compilerfixture.Values.Text(text))
