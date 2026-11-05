package canonicalfixture

final case class Forwarded(text: Forwarded.Text, maybe: Option[Forwarded.Optional], count: Int = 0)
object Forwarded:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Optional = Optional.T
  object Optional extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyText = _root_.canonicalfixture.Forwarded.Text
  val ApplyText: _root_.canonicalfixture.Forwarded.Text.type = _root_.canonicalfixture.Forwarded.Text
  type ApplyOptional = _root_.canonicalfixture.Forwarded.Optional
  val ApplyOptional: _root_.canonicalfixture.Forwarded.Optional.type = _root_.canonicalfixture.Forwarded.Optional
  def apply(text: Forwarded.ApplyText, maybe: Option[Forwarded.ApplyOptional]): Forwarded = new Forwarded(text, maybe)

final case class NonForwarded(text: NonForwarded.Text, marker: Boolean = false)
object NonForwarded:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyText = ApplyText.T
  object ApplyText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  def apply(text: NonForwarded.ApplyText): NonForwarded = new NonForwarded(Text(text.value.reverse))

object Vocabulary:
  type Entries = _root_.canonicalfixture.Canonical.Argument
  val Entries: Set[canonicalfixture.Vocabulary.Entries] = Set(_root_.canonicalfixture.Canonical.Argument("one"))
