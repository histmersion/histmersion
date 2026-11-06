package propertyfixture

object Flows:
  final case class Item(title: Item.Title, slug: Item.Slug)
  object Item:
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadTitle = ReadTitle.T
  object ReadTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteTitle = WriteTitle.T
  object WriteTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Titles = Titles.T
  object Titles extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ActiveSet = ActiveSet.T
  object ActiveSet extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Listed = Listed.T
  object Listed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val items: List[Item] = List(Item(Item.Title("one"), Item.Slug("first")))
  def read(item: Item): ReadTitle = item.title
  def write(title: WriteTitle): String = title.value
  def caller(item: Item): String = write(item.title)
  val titles: List[Titles] = items.map(_.title)
  val activeSet: Set[ActiveSet] = items.map(_.slug).toSet
  def listed(items: List[Item]): List[Listed] = items.map(_.title)

  def check(): Unit =
    summon[ReadTitle =:= Item.Title]
    summon[WriteTitle =:= Item.Title]
    summon[Titles =:= Item.Title]
    summon[ActiveSet =:= Item.Slug]
    summon[Listed =:= Item.Title]
    assert(read(items.head).value == "one" && caller(items.head) == "one")
    assert(titles.map(_.value) == List("one"))
    assert(activeSet.map(_.value) == Set("first"))
    assert(listed(items).map(_.value) == List("one"))
    assert(WriteTitle.either("two").map(_.value) == Right("two"))
