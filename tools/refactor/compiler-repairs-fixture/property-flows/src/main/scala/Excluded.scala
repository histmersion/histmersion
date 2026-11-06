package propertyfixture

object Excluded:
  import Flows.Item
  final case class Other(title: Other.Title)
  object Other:
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Mixed = Mixed.T
  object Mixed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Unknown = Unknown.T
  object Unknown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Caption = Caption.T
  object Caption extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Changed = Changed.T
  object Changed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Custom = Custom.T
  object Custom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    def normalize(raw: String): T = assume(raw.trim)
  type Existing = Existing.T
  object Existing extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  final case class Protected(title: Existing)

  def mixed(title: Mixed): String = title.value
  def first(item: Item): String = mixed(item.title)
  def second(other: Other): String = mixed(other.title)
  def unknown(item: Item): Unknown = item.title
  def caption(caption: Caption): String = caption.value
  def wrong(item: Item): String = caption(item.title)
  def changed(item: Item): Changed = Item.Title(item.title.value.reverse)
  def custom(title: Custom): String = title.value
  def customCaller(item: Item): String = custom(item.title)
  def existing(item: Item): Existing = item.title
