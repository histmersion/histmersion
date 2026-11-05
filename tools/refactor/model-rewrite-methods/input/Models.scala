package methodfixture
final case class Page(total: Int)
final case class Wire(total: Int)
trait Service:
  def page(total: Int): Page
object Service extends Service:
  def page(total: Int = 0): Page = Page(total)
  def optional(total: Option[Int] = None): Option[Int] = total
object Other:
  def page(total: Int): Int = total
object Calls:
  def positional: Page = Service.page(5)
  def named: Page = Service.page(total = 7)
  def defaulted: Page = Service.page()
  def abstractCall(s: Service): Page = s.page(9)
  def forwarded(p: Page): Page = Service.page(p.total)
  def converted(w: Wire): Page = Service.page(w.total)
  def optional: Option[Int] = Service.optional(Some(4))
  def unrelated: Int = Other.page(11)
