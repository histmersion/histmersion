package consolidationfixture

object Forward:
  final case class Item(title: Item.Title, count: Item.Count)
  object Item:
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Count = Count.T
    object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Builders:
    type MakeTitle = _root_.consolidationfixture.Forward.Item.Title
    val MakeTitle: _root_.consolidationfixture.Forward.Item.Title.type = _root_.consolidationfixture.Forward.Item.Title
    type MakeCount = _root_.consolidationfixture.Forward.Item.Count
    val MakeCount: _root_.consolidationfixture.Forward.Item.Count.type = _root_.consolidationfixture.Forward.Item.Count
    type NamedTitle = _root_.consolidationfixture.Forward.Item.Title
    val NamedTitle: _root_.consolidationfixture.Forward.Item.Title.type = _root_.consolidationfixture.Forward.Item.Title
    type NamedCount = _root_.consolidationfixture.Forward.Item.Count
    val NamedCount: _root_.consolidationfixture.Forward.Item.Count.type = _root_.consolidationfixture.Forward.Item.Count
    def make(title: MakeTitle, count: MakeCount): Item = new Item(title, count)
    def named(title: NamedTitle, count: NamedCount): Item = Item(count = count, title = title)
