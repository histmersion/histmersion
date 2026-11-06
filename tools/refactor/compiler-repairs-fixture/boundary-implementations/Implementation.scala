package compilerfixture

object BoundaryValues:
  type Raw = Raw.T
  object Raw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Encoded = Encoded.T
  object Encoded extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

object BoundaryImplementation extends Protocol[Int]:
  def encode(value: Int): Values.Text = BoundaryValues.Encoded(value.toString)
  def decode(raw: Values.Text): Option[Int] = parse(raw)
  def parse(raw: BoundaryValues.Raw): Option[Int] = raw.value.toIntOption
