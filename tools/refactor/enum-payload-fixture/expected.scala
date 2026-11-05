package enumfixture

enum Payload:
  case Named(name: Payload.Named.Name)
  case Number(value: Payload.Number.Value)
  case Empty

object Payload:
  object Named:
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  object Number:
    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

enum Existing(val tag: Existing.Tag):
  case Data(n: Existing.Data.N) extends Existing(???)

object Existing:
  type Tag = Tag.T
  object Tag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Data:
    type N = N.T
    object N extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val marker = 1
