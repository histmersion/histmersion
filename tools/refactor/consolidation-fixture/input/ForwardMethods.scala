package consolidationfixture

object ForwardMethods:
  type LeafN = LeafN.T
  object LeafN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MiddleN = MiddleN.T
  object MiddleN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OuterN = OuterN.T
  object OuterN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FullN = FullN.T
  object FullN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeafLabel = LeafLabel.T
  object LeafLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MiddleLabel = MiddleLabel.T
  object MiddleLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OuterLabel = OuterLabel.T
  object OuterLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FullLabel = FullLabel.T
  object FullLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeftCount = LeftCount.T
  object LeftCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RightCount = RightCount.T
  object RightCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LeafTag = LeafTag.T
  object LeafTag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListTag = ListTag.T
  object ListTag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def leaf(n: LeafN, label: LeafLabel): String = s"$n:$label"
  def middle(n: MiddleN, label: MiddleLabel): String = leaf(n, label)
  def outer(n: OuterN, label: OuterLabel): String = middle(label = label, n = n)
  def fullyQualified(n: FullN, label: FullLabel): String = _root_.consolidationfixture.ForwardMethods.leaf(n, label)
  def left(count: LeftCount): Int = if count.value <= 0 then 0 else right(count)
  def right(count: RightCount): Int = if count.value < 0 then left(count) else count.value
  def leaves(tags: List[LeafTag]): List[LeafTag] = tags
  def lists(tags: List[ListTag]): List[LeafTag] = leaves(tags)
