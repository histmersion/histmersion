package consolidationfixture

object ForwardNegative:
  final case class One(title: One.Title)
  object One:
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  final case class Two(title: Two.Title)
  object Two:
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ConflictTitle = ConflictTitle.T
  object ConflictTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnknownTitle = UnknownTitle.T
  object UnknownTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UpperTitle = UpperTitle.T
  object UpperTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Caption = Caption.T
  object Caption extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  def both(title: ConflictTitle): List[One | Two] = List(new One(title), new Two(title))
  def unknown(title: UnknownTitle): One = new One(title)
  def upper(title: UpperTitle): One = new One(One.Title(title.value.toUpperCase(java.util.Locale.ROOT)))
  def wrong(caption: Caption): One = new One(caption)
  def property(title: One.Title): Two = new Two(title)
  def shadowed(title: Caption): One = List("raw").map(title => new One(title)).head
  final case class Custom(title: One.Title)
  object Custom:
    @scala.annotation.targetName("fromCaption")
    def apply(title: Caption): Custom = new Custom(One.Title(title.value.reverse))
  def custom(title: Caption): Custom = Custom(title)
