package methodfixture
final case class Page(total: methodfixture.RowCount)
final case class Wire(total: methodfixture.WireCount)
trait Service:
  def page(total: methodfixture.RowCount): Page
object Service extends Service:
  def page(total: methodfixture.RowCount = methodfixture.RowCount(0)): Page = Page(total)
  def optional(total: Option[methodfixture.RowCount] = None): Option[methodfixture.RowCount] = total
object Other:
  def page(total: Int): Int = total
object Calls:
  def positional: Page = Service.page(methodfixture.RowCount(5))
  def named: Page = Service.page(total = methodfixture.RowCount(7))
  def defaulted: Page = Service.page()
  def abstractCall(s: Service): Page = s.page(methodfixture.RowCount(9))
  def forwarded(p: Page): Page = Service.page(p.total)
  def converted(w: Wire): Page = Service.page(methodfixture.RowCount(w.total.value))
  def optional: Option[methodfixture.RowCount] = Service.optional((Some(4)).map(methodfixture.RowCount.apply))
  def unrelated: Int = Other.page(11)
