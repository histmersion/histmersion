//> using scala 3.10.0
//> using dep io.github.iltotore::iron:3.4.1
package boundaryfixture

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

type Offset = Offset.T
object Offset extends RefinedType[Int, Pure]
type RowCount = RowCount.T
object RowCount extends RefinedType[Int, Pure]
final case class Page(offset: Int, total: Int)
final case class Passage(offset: Int)

object Paging:
  def next(p: Page): Option[Int] = Option.when(p.total > 0)(p.offset + 1)
  def empty(p: Page): Boolean = p.offset match
    case 0 => true
    case _ => false
  def render(p: Page): String = Render.count(p.total)
  def forward(p: Page): Page = Page(p.offset, p.total)
  def text(p: Passage): Int = p.offset + 1
object Render:
  def count(n: Long): String = n.toString

@main def checkBoundaries(): Unit =
  val p = Page(1, 3)
  assert(Paging.next(p).contains(Offset(2)))
  assert(Paging.render(p) == "3")
  assert(Paging.empty(Page(0, 0)))
  assert(Paging.forward(p) == p)
  assert(Paging.text(Passage(2)) == 3)
