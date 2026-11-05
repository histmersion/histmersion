//> using scala 3.10.0
package migrationfixture

final case class Page(offset: migrationfixture.Offset = migrationfixture.Offset(0), total: migrationfixture.RowCount = migrationfixture.RowCount(0))
final case class Passage(offset: Int)

object Calls:
  def offset(page: Page): migrationfixture.Offset = page.offset
  def positional: Page = Page(migrationfixture.Offset(1), migrationfixture.RowCount(12))
  def named: Page = Page(total = migrationfixture.RowCount(12), offset = migrationfixture.Offset(1))
  def defaults: Page = Page()
  def copied(page: Page): Page = page.copy(offset = migrationfixture.Offset(4))
  def forwarded(page: Page): Page = Page(page.offset, page.total)
  def unrelated: Passage = Passage(3)
