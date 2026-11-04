package lensfixture

import hearth.kindlings.optics.syntax.*

final case class Numeric(id: Long, start: Option[Long], amount: Double, values: List[Long])
object NumericExamples:
  def update(n: Numeric): Numeric = n.modify(_.id).setTo((2: _root_.scala.Long)).modify(_.start).setTo((Some(-1300): _root_.scala.Option[_root_.scala.Long])).modify(_.amount).setTo((5: _root_.scala.Double)).modify(_.values).setTo((List(1, 2): _root_.scala.collection.immutable.List[_root_.scala.Long]))
