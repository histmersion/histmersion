package lensfixture

final case class Stamped(at: Long, count: Int)
object ReservedExamples:
  def time(r: Stamped): Stamped = r.copy(at = 2L)
  def count(r: Stamped): Stamped = r.copy(count = 2)
