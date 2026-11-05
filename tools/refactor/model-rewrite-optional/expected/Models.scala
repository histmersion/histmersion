//> using scala 3.10.0
package migrationfixture

final case class Page(next: Option[migrationfixture.Offset] = None)
final case class Results(next: Option[migrationfixture.Offset] = None)
final case class Passage(offset: Option[Int])

object Calls:
  def next(page: Page): Option[migrationfixture.Offset] = page.next
  def raw(value: Int): Option[migrationfixture.Offset] = (Some(value)).map(migrationfixture.Offset.apply)
  def positional: Page = Page((Some(3)).map(migrationfixture.Offset.apply))
  def named: Page = Page(next = (Some(4)).map(migrationfixture.Offset.apply))
  def defaults: Page = Page()
  def copied(page: Page): Page = page.copy(next = (Some(5)).map(migrationfixture.Offset.apply))
  def forwarded(page: Page): Results = Results(next(page))
  def fromRaw: Results = Results(raw(6))
  def unrelated: Passage = Passage(Some(7))
