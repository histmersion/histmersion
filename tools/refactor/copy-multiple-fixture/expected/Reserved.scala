package lensfixture

import hearth.kindlings.optics.syntax.*

final case class Stamped(at: Long, count: Int)
object ReservedExamples:
  def time(r: Stamped): Stamped = r.copy(at = 2L)
  def count(r: Stamped): Stamped = r.modify(_.count).setTo(2)
