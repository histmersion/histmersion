package interfacefixture

object Values:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

trait Port:
  def read(): Values.Text
  def echo(value: Values.Text): Option[Values.Text]

object Live extends Port:
  def read(): String = throw new UnsupportedOperationException
  def echo(value: String): Option[String] = throw new UnsupportedOperationException

object Testing:
  val fake: interfacefixture.Port = new interfacefixture.Port:
    def read(): String = throw new UnsupportedOperationException
    def echo(value: String): Option[String] = throw new UnsupportedOperationException

trait OtherPort:
  def read(): String
object Other extends OtherPort:
  def read(): String = "unchanged"

abstract class WrongOverload extends Port:
  def echo(value: Int): Option[String] = None

trait Registry[A]:
  def add(id: Values.Text, a: A): Either[Values.Text, Unit]

object Generic:
  def create[A](): Registry[A] = new Registry[A]:
    def add(id: String, a: A) = Right(())

abstract class WrongGeneric extends Registry[String]:
  def add(id: Int, a: String) = Right(())
