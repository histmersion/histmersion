package domain

object Uses:
  def a(x: Mode, y: Mode): Boolean = x == y
  def b(x: Span, y: Span): Boolean = x != y
  def c(x: Pair[Int], y: Pair[Int]): Boolean = x == y
  def d(x: Outcome): Boolean = x == Outcome.Done
  def e(x: Holder.Inner, y: Holder.Inner): Boolean = x == y
  def f(x: Checked, y: Checked): Boolean = x == y
  def g(x: Shape[Int], y: Shape[Int]): Boolean = x == y
  def h(x: Kept, y: Kept): Boolean = x == y
  def i(x: Family, y: Family): Boolean = x == y
