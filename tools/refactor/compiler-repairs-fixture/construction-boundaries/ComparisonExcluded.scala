package compilerfixture

object ComparisonExcluded:
  def meaning(value: Values.Text, other: Values.Other): Boolean = value == other
  def base(value: Values.Count, raw: String): Boolean = value == raw
  def constrained(value: Values.Strict, raw: String): Boolean = value == raw
  def pattern(value: Values.Text): Boolean = value match
    case "one" => true
    case _ => false
