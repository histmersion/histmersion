package importedfixture

object ParentShadow:
  import io.github.iltotore.iron.*
  import io.github.iltotore.iron.constraint.any.Pure
  final class Box[A](val payload: A)
  abstract class RefinedType[A, C]:
    type T = Box[A]
  type Text = Text.T
  object Text extends RefinedType[String, Pure]
  def native(value: Text): String = value

object ConstraintShadow:
  import io.github.iltotore.iron.*
  type Pure = io.github.iltotore.iron.constraint.string.Match["ok"]
  type Text = Text.T
  object Text extends RefinedType[String, Pure]
  def native(value: Text): String = value

object BaseShadow:
  import io.github.iltotore.iron.*
  import io.github.iltotore.iron.constraint.any.Pure
  type Double = String
  type Amount = Amount.T
  object Amount extends RefinedType[Double, Pure]
  def make(raw: _root_.scala.Double): Amount = raw

object UncertainImports:
  import io.github.iltotore.iron.*
  import io.github.iltotore.iron.constraint.any.Pure
  import scala.concurrent.*
  type Text = Text.T
  object Text extends RefinedType[String, Pure]
  def make(raw: String): Text = raw

trait InheritedBase:
  type Double = String
object Inherited extends InheritedBase:
  import io.github.iltotore.iron.*
  import io.github.iltotore.iron.constraint.any.Pure
  type Amount = Amount.T
  object Amount extends RefinedType[Double, Pure]
  def make(raw: _root_.scala.Double): Amount = raw

object NamespaceShadow:
  object io:
    object github:
      object iltotore:
        object iron:
          final class Box[A](val payload: A)
          abstract class RefinedType[A, C]:
            type T = Box[A]
          object constraint:
            object any:
              trait Pure
  import io.github.iltotore.iron.*
  import io.github.iltotore.iron.constraint.any.Pure
  type Text = Text.T
  object Text extends RefinedType[String, Pure]
  def native(value: Text): String = value
