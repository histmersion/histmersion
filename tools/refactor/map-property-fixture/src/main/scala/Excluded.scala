package mapfixture

object Excluded:
  import MapFlows.{Item, items}
  final case class Other(slug: Other.Slug)
  object Other:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Mixed = Mixed.T
  object Mixed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Unknown = Unknown.T
  object Unknown extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Changed = Changed.T
  object Changed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Custom = Custom.T
  object Custom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]:
    def empty: T = apply("")
  type Nested = Nested.T
  object Nested extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Protected = Protected.T
  object Protected extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  final case class Holder(key: Protected)
  type Conflict = Conflict.T
  object Conflict extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Raw = Raw.T
  object Raw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  val mixedFirst: Map[Mixed, Item] = items.map(i => i.slug -> i).toMap
  val mixedSecond: Map[Mixed, Other] = List(Other(Other.Slug("other"))).map(i => i.slug -> i).toMap
  val unknown: Map[Unknown, Item] = items.map(i => i.slug -> i).toMap
  val changed: Map[Changed, Item] = items.map(i => Item.Slug(i.slug.value.reverse) -> i).toMap
  val custom: Map[Custom, Item] = items.map(i => i.slug -> i).toMap
  val nested: List[Map[Nested, Item]] = List(items.map(i => i.slug -> i).toMap)
  val protectedKeys: Map[Protected, Item] = items.map(i => i.slug -> i).toMap
  val bothRoles: Map[Conflict, Conflict] = items.map(i => i.slug -> i.title).toMap
  val validKey: Map[Conflict, Item] = items.map(i => i.slug -> i).toMap
  val raw: Map[Raw, Item] = items.map(i => i.slug.value -> i).toMap
