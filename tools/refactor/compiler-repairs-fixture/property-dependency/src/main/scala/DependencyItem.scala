package propertydependency

final case class DependencyItem(title: DependencyItem.Title)
object DependencyItem:
  type Title = Title.T
  object Title extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
