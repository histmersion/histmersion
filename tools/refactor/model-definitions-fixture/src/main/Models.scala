package fixture

final case class Entry(label: String, size: Option[Int], existing: Token)
object Entry:
  val sentinel = 1

object Group:
  final case class Row(keys: Map[String, Long], active: Boolean)

final case class Token(value: String)

final case class Collision(kind: String)
object Collision:
  object Kind
