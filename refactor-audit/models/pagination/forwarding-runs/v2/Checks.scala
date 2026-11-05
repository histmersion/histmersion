package forwardingfixture
import scala.compiletime.testing.typeChecks
@main def checkForwarding(): Unit =
  val wire = Wire(RowCount(7))
  val page = Calls.from(List("a"), wire)
  assert(page.total == RowCount(7))
  assert(Calls.copied(page, Wire(RowCount(8))).total == RowCount(8))
  assert(Calls.empty[String].total == RowCount(0))
  assert(Calls.unrelated.total == 1)
  assert(!typeChecks("forwardingfixture.Page[String](Nil, 7)"))
