package domain

trait Codec[A]
object Codec:
  def derived[A]: Codec[A] = new Codec[A] {}

enum Mode derives CanEqual:
  case Fast, Slow

enum Family(val wire: String) derives CanEqual:
  case Cc0 extends Family("cc0")
  case Gfdl extends Family("gfdl")

  def isFree: Boolean = this == Cc0

enum Shape[+A] derives Codec, CanEqual:
  case Point
  case Box(a: A)

enum Kept derives CanEqual:
  case One, Two

final case class Span(start: Int, end: Int) derives CanEqual

final case class Pair[A](left: A, right: A) extends Product with Serializable derives Codec, CanEqual

final case class Checked private (n: Int) extends Serializable derives CanEqual:
  def twice: Int = n * 2

sealed trait Outcome derives CanEqual
object Outcome:
  case object Done extends Outcome
  final case class Failed(reason: String) extends Outcome

final case class Unlisted(n: Int)

object Holder:
  final case class Inner(id: String) derives CanEqual { def show: String = id }
  class Plain(val n: Int)
