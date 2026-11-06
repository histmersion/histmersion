package compilerfixture

object Excluded:
  def unwrap(value: Values.Text): String = value
  def member(value: Values.Text): String = value.trim
  def rawOverload(value: Values.Text): java.nio.file.Path = java.nio.file.Path.of(value)
  def same(value: Values.Text): Boolean = "one" == value
  def retype(value: Values.Text): Values.Other = value
