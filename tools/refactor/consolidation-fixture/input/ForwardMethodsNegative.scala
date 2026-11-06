package consolidationfixture

object ForwardMethodsNegative:
  type Original = Original.T
  object Original extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Unknown = Unknown.T
  object Unknown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Other = Other.T
  object Other extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Helper = Helper.T
  object Helper extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Custom = Custom.T
  object Custom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    def normalize(raw: String): T = assume(raw.trim)
  type Number = Number.T
  object Number extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Entity(text: Other)
  def sink(text: Original): String = text.value
  def unknown(text: Unknown): String = sink(text)
  def property(text: Other): String = sink(text)
  def changed(text: Helper): String = sink(Original(text.value.reverse))
  def purpose(caption: Helper): String = sink(caption)
  def custom(text: Custom): String = sink(text)
  def numeric(text: Number): String = sink(text)
  def generic[A](text: Helper): String = sink(text)
  def shadowed(text: Helper): List[String] = List("raw").map(text => sink(text))
  def shadowedMethod(text: Helper, sink: Helper => String): String = sink(text)
  def overloaded(text: Original): String = text.value
  def overloaded(text: Original, n: Int): String = text.value
  def overload(text: Helper): String = overloaded(text)

  object Neighbor:
    def external(text: Original): String = text.value
  def crossOwner(text: Helper): String = Neighbor.external(text)
  def imported(text: Helper): String =
    import Neighbor.external as sink
    sink(text)
