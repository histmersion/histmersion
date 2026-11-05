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

final case class Choice(kind: String)
object Choice:
  enum Kind:
    case One

final case class Payload(bytes: Array[Byte])

final case class Packet(bytes: Array[Byte], pixels: Option[Array[Int]])

final case class Legacy(bytes: Array[Legacy.Bytes])
object Legacy:
  type Bytes = Bytes.T
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Byte, _root_.io.github.iltotore.iron.constraint.any.Pure]

object Sampling:
  def sample(captured: Int): Unit =
    final case class Local(x: Int):
      def snapshot: Int = captured
    ()
