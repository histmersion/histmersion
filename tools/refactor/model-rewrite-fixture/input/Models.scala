//> using scala 3.10.0
package migrationfixture

final case class Page(offset: Int = 0, total: Int = 0)
final case class Passage(offset: Int)

object Calls:
  def offset(page: Page): Int = page.offset
  def positional: Page = Page(1, 12)
  def named: Page = Page(total = 12, offset = 1)
  def defaults: Page = Page()
  def copied(page: Page): Page = page.copy(offset = 4)
  def forwarded(page: Page): Page = Page(page.offset, page.total)
  def unrelated: Passage = Passage(3)
