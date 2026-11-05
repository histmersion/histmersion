package compilerfixture

object Positive:
  def produce(raw: String): Values.Text = raw
  def consume(text: Values.Text): String = text
  def number: Values.Count = 7
  def upper(text: Values.Text): String = text.toUpperCase(java.util.Locale.ROOT)
  def output(stream: java.io.PrintStream, text: Values.Text): Unit = stream.print(text)
  def unchanged(raw: String): String = raw.toUpperCase(java.util.Locale.ROOT)
  def combined(raw: String): Values.Text = raw.toUpperCase(java.util.Locale.ROOT) + "!"
  def optional(raw: String): Option[Values.Text] = Option(raw)
