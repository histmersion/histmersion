package compilerfixture

object OrderingNegative:
  def bytes(values: List[Ordered.Bytes]): List[Ordered.Bytes] = values.sorted
  def existingBody(values: List[Ordered.WithBody]): List[Ordered.WithBody] = values.sorted
  def braced(values: List[Ordered.Braced]): List[Ordered.Braced] = values.sorted
  def outsideRoots(values: List[Values.Count]): List[Values.Count] = values.sorted

class InstanceOrdered:
  type Count = Count.T
  object Count extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  def counts(values: List[Count]): List[Count] = values.sorted
