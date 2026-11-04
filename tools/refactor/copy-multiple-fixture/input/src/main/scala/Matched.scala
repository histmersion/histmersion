package lensfixture

object Matched:
  def update(r: Record): Record = r.copy(
    a = r.a match
      case 0 => 1
      case n => n,
    b = "updated"
  )
