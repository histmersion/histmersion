package consolidationfixture

object ForwardMethods:
  type LeafN = LeafN.T
  object LeafN extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MiddleN = _root_.consolidationfixture.ForwardMethods.LeafN
  val MiddleN: _root_.consolidationfixture.ForwardMethods.LeafN.type = _root_.consolidationfixture.ForwardMethods.LeafN
  type OuterN = _root_.consolidationfixture.ForwardMethods.LeafN
  val OuterN: _root_.consolidationfixture.ForwardMethods.LeafN.type = _root_.consolidationfixture.ForwardMethods.LeafN
  type FullN = _root_.consolidationfixture.ForwardMethods.LeafN
  val FullN: _root_.consolidationfixture.ForwardMethods.LeafN.type = _root_.consolidationfixture.ForwardMethods.LeafN
  type LeafLabel = LeafLabel.T
  object LeafLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MiddleLabel = _root_.consolidationfixture.ForwardMethods.LeafLabel
  val MiddleLabel: _root_.consolidationfixture.ForwardMethods.LeafLabel.type = _root_.consolidationfixture.ForwardMethods.LeafLabel
  type OuterLabel = _root_.consolidationfixture.ForwardMethods.LeafLabel
  val OuterLabel: _root_.consolidationfixture.ForwardMethods.LeafLabel.type = _root_.consolidationfixture.ForwardMethods.LeafLabel
  type FullLabel = _root_.consolidationfixture.ForwardMethods.LeafLabel
  val FullLabel: _root_.consolidationfixture.ForwardMethods.LeafLabel.type = _root_.consolidationfixture.ForwardMethods.LeafLabel
  type LeftCount = LeftCount.T
  object LeftCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RightCount = _root_.consolidationfixture.ForwardMethods.LeftCount
  val RightCount: _root_.consolidationfixture.ForwardMethods.LeftCount.type = _root_.consolidationfixture.ForwardMethods.LeftCount
  type LeafTag = LeafTag.T
  object LeafTag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListTag = _root_.consolidationfixture.ForwardMethods.LeafTag
  val ListTag: _root_.consolidationfixture.ForwardMethods.LeafTag.type = _root_.consolidationfixture.ForwardMethods.LeafTag

  def leaf(n: LeafN, label: LeafLabel): String = s"$n:$label"
  def middle(n: MiddleN, label: MiddleLabel): String = leaf(n, label)
  def outer(n: OuterN, label: OuterLabel): String = middle(label = label, n = n)
  def fullyQualified(n: FullN, label: FullLabel): String = _root_.consolidationfixture.ForwardMethods.leaf(n, label)
  def left(count: LeftCount): Int = if count.value <= 0 then 0 else right(count)
  def right(count: RightCount): Int = if count.value < 0 then left(count) else count.value
  def leaves(tags: List[LeafTag]): List[LeafTag] = tags
  def lists(tags: List[ListTag]): List[LeafTag] = leaves(tags)
