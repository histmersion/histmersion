package consolidationfixture

object Forward:
  final case class Item(title: Item.Title, count: Item.Count)
  object Item:
    type Title = Title.T
    object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Count = Count.T
    object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Builders:
    type MakeTitle = MakeTitle.T
    object MakeTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MakeCount = MakeCount.T
    object MakeCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NamedTitle = NamedTitle.T
    object NamedTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NamedCount = NamedCount.T
    object NamedCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    def make(title: MakeTitle, count: MakeCount): Item = new Item(title, count)
    def named(title: NamedTitle, count: NamedCount): Item = Item(count = count, title = title)
