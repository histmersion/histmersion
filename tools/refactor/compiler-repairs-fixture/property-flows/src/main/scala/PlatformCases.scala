package propertyfixture

object PlatformCases:
  type PlatformTitle = PlatformTitle.T
  object PlatformTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExternalTitle = ExternalTitle.T
  object ExternalTitle extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  def platform(title: PlatformTitle): String = title.value
  def external(title: ExternalTitle): String = title.value
  def fromJs(item: JsItem): String = platform(item.title)
  def fromDependency(item: propertydependency.DependencyItem): String = external(item.title)
