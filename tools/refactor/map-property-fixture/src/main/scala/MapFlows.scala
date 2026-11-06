package mapfixture

object MapFlows:
  final case class Item(slug: Item.Slug, title: Item.Title)
  object Item:
    type Slug = Slug.T
    object Slug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Keys = Keys.T
  object Keys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Values = Values.T
  object Values extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BothKeys = BothKeys.T
  object BothKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BothValues = BothValues.T
  object BothValues extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResultKeys = ResultKeys.T
  object ResultKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  val items = List(Item(Item.Slug("first"), Item.Title("one")), Item(Item.Slug("second"), Item.Title("two")))
  val bySlug: Map[Keys, Item] = items.map(i => i.slug -> i).toMap
  val titles: Map[String, Values] = items.map(i => (i.slug.value, i.title)).toMap
  val both: Map[BothKeys, BothValues] = items.map(i => (i.slug, i.title)).toMap
  def index(rows: List[Item]): Map[ResultKeys, Item] = rows.map(i => i.slug -> i).toMap

  @main def checkMapFlows(): Unit =
    summon[Keys =:= Item.Slug]
    summon[Values =:= Item.Title]
    summon[BothKeys =:= Item.Slug]
    summon[BothValues =:= Item.Title]
    summon[ResultKeys =:= Item.Slug]
    assert(bySlug(Item.Slug("first")).title.value == "one")
    assert(titles("second").value == "two")
    assert(both(Item.Slug("first")).value == "one")
    assert(index(Nil).isEmpty)
    var reads = 0
    def rows(): List[Item] = { reads += 1; items }
    assert(index(rows()).size == 2 && reads == 1)
    val duplicate = Item(Item.Slug("first"), Item.Title("last"))
    assert(index(items :+ duplicate)(Item.Slug("first")).title.value == "last")
