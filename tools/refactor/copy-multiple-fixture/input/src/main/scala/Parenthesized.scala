package lensfixture

object Parenthesized:
  def update(r: Record): Record = r.copy(a = (r.a + 1), b = (r.b + "!"))
