package propertyfixture

object Flows:
  final case class Item(title: Item.Title, slug: Item.Slug)
  object Item:
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReadTitle = _root_.propertyfixture.Flows.Item.Title
  val ReadTitle: _root_.propertyfixture.Flows.Item.Title.type = _root_.propertyfixture.Flows.Item.Title
  type WriteTitle = _root_.propertyfixture.Flows.Item.Title
  val WriteTitle: _root_.propertyfixture.Flows.Item.Title.type = _root_.propertyfixture.Flows.Item.Title
  type Titles = _root_.propertyfixture.Flows.Item.Title
  val Titles: _root_.propertyfixture.Flows.Item.Title.type = _root_.propertyfixture.Flows.Item.Title
  type ActiveSet = _root_.propertyfixture.Flows.Item.Slug
  val ActiveSet: _root_.propertyfixture.Flows.Item.Slug.type = _root_.propertyfixture.Flows.Item.Slug
  type Listed = _root_.propertyfixture.Flows.Item.Title
  val Listed: _root_.propertyfixture.Flows.Item.Title.type = _root_.propertyfixture.Flows.Item.Title

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
