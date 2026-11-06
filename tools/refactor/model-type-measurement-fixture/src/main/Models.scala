package measurefixture

class Token
type Alias = String
final case class Model(resolve: (String, Option[Int]) => Boolean, named: Token)
object Ops:
  lazy val activeSet: Set[String] = Set.empty
  val typed: Token = new Token
  val inferred = 42
  def seconds(s: Token): Long = 0L
  def collect(xs: List[String], f: Int => String): Option[Long] = None

object Named:
  final class String
final case class Qualified(
    rootString: _root_.java.lang.String,
    rootBoolean: _root_.scala.Boolean,
    rootByte: _root_.scala.Byte,
    rootShort: _root_.scala.Short,
    rootInt: _root_.scala.Int,
    rootLong: _root_.scala.Long,
    rootFloat: _root_.scala.Float,
    rootDouble: _root_.scala.Double,
    rootChar: _root_.scala.Char,
    named: _root_.measurefixture.Named.String
)
