package compilerfixture

object Negative:
  def strict(raw: String): Values.Strict = raw
  def wrongMeaning(text: Values.Text): Values.Other = text
  def wrongBase(count: Int): Values.Text = count
