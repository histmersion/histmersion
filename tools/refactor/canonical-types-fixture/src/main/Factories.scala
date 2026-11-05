package canonicalfixture

final case class Forwarded(text: Forwarded.Text, maybe: Option[Forwarded.Optional], count: Int = 0)
object Forwarded:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Optional = Optional.T
  object Optional extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyText = ApplyText.T
  object ApplyText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyOptional = ApplyOptional.T
  object ApplyOptional extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  def apply(text: Forwarded.ApplyText, maybe: Option[Forwarded.ApplyOptional]): Forwarded = new Forwarded(text, maybe)

final case class NonForwarded(text: NonForwarded.Text, marker: Boolean = false)
object NonForwarded:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyText = ApplyText.T
  object ApplyText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  def apply(text: NonForwarded.ApplyText): NonForwarded = new NonForwarded(Text(text.value.reverse))

object Vocabulary:
  type Entries = Entries.T
  object Entries extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  val Entries: Set[canonicalfixture.Vocabulary.Entries] = Set(canonicalfixture.Vocabulary.Entries("one"))
