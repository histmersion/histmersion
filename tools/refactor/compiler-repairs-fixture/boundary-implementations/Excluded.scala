package compilerfixture

object OutsideBoundary:
  def raw(value: Values.Text): BoundaryValues.Raw = value
  def encoded(value: BoundaryValues.Encoded): Values.Text = value
  def primitive(value: Values.Text): String = value

trait OtherProtocol[A]:
  def encode(value: A): BoundaryValues.Encoded

object OutsideContract extends OtherProtocol[Int]:
  def encode(value: Int): BoundaryValues.Encoded = Values.Text(value.toString)

abstract class ExtraMember extends Protocol[Int]:
  def helper(value: Values.Text): BoundaryValues.Raw = value

abstract class WrongBase extends Protocol[Int]:
  def decode(raw: Values.Text): Option[Int] = Some(expectsCount(raw))
  def expectsCount(count: Values.Count): Int = count.value

abstract class Constrained extends Protocol[Int]:
  def decode(raw: Values.Text): Option[Int] = Some(expectsStrict(raw))
  def expectsStrict(text: Values.Strict): Int = text.value.length
