package lensfixture

final case class Numeric(id: Long, start: Option[Long], amount: Double, values: List[Long])
object NumericExamples:
  def update(n: Numeric): Numeric = n.copy(id = 2, start = Some(-1300), amount = 5, values = List(1, 2))
