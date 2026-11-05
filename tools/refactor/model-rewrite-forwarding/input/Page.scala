package forwardingfixture
final case class Page[A](rows: List[A], total: Int = 0)
final case class Other(total: Int)
object Calls:
  def from[A](rows: List[A], wire: Wire): Page[A] = Page[A](rows, wire.total)
  def copied[A](page: Page[A], wire: Wire): Page[A] = page.copy(total = wire.total)
  def empty[A]: Page[A] = Page[A](Nil)
  def unrelated: Other = Other(1)
