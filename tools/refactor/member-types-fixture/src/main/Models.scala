package memberfixture

class Token
final case class Policy(resolve: (String, Token) => Boolean):
  def label: String = ???

object Service:
  lazy val keys: Set[String] = ???
  def seconds(s: Token): Long = ???
  def choose(query: String): Option[Int] = ???

class Store:
  def load(key: String): Option[Long] = ???
  override def toString: String = "store"
