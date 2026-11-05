package fixture

final case class Entry(label: Token, size: Option[Entry.Size], existing: Token)
object Entry:
  type Size = Size.T
  object Size extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val sentinel = 1

object Group:
  final case class Row(keys: Map[Row.KeysKey, Row.KeysValue], active: Row.Active)

  object Row:
    type KeysKey = KeysKey.T
    object KeysKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KeysValue = KeysValue.T
    object KeysValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Active = Active.T
    object Active extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

final case class Token(value: Token.Value)

object Token:
  type Value = Value.T
  object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

final case class Collision(kind: String)
object Collision:
  object Kind

final case class Choice(kind: fixture.Choice.Kind)
object Choice:
  enum Kind:
    case One

final case class Payload(bytes: Array[Byte])

final case class Packet(bytes: Packet.Bytes, pixels: Option[Packet.Pixels])

object Packet:
  type Bytes = Bytes.T
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Pixels = Pixels.T
  object Pixels extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Int], _root_.io.github.iltotore.iron.constraint.any.Pure]

final case class Legacy(bytes: Legacy.Bytes)
object Legacy:
  type Bytes = Bytes.T
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Array[_root_.scala.Byte], _root_.io.github.iltotore.iron.constraint.any.Pure]
