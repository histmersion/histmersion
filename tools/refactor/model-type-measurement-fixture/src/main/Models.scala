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
