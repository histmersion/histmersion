package compilerfixture

object Negative:
  def ambiguous: Choice = Choice("one")
  def strictOverload: StrictOverload = StrictOverload("one")
  def nullCount: Values.Count = null
  def narrowing(value: Long): Values.Count = value
  def strict(raw: String): Values.Strict = raw
  def wrongMeaning(text: Values.Text): Values.Other = text
  def wrongBase(count: Int): Values.Text = count
