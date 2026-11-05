package constructorfixture

final class Connection(val host: Connection.Host, val port: Connection.Port)

object Connection:
  type Host = Host.T
  object Host extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Port = Port.T
  object Port extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

enum Mode(val wire: Mode.Wire):
  case Off extends Mode(???)

object Mode:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

final class Binary(val bytes: Array[Byte])
