package compilerfixture

trait Protocol[A]:
  def encode(value: A): Values.Text
  def decode(raw: Values.Text): Option[A]
