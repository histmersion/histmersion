//> using scala 3.10.0
package migrationfixture

final case class Page(next: Option[Int] = None)
final case class Results(next: Option[Int] = None)
final case class Passage(offset: Option[Int])

object Calls:
  def next(page: Page): Option[Int] = page.next
  def raw(value: Int): Option[Int] = Some(value)
  def positional: Page = Page(Some(3))
  def named: Page = Page(next = Some(4))
  def defaults: Page = Page()
  def copied(page: Page): Page = page.copy(next = Some(5))
  def forwarded(page: Page): Results = Results(next(page))
  def fromRaw: Results = Results(raw(6))
  def unrelated: Passage = Passage(Some(7))
