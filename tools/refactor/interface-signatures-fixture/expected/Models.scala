package interfacefixture

object Values:
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

trait Port:
  def read(): Values.Text
  def echo(value: Values.Text): Option[Values.Text]

object Live extends Port:
  def read(): _root_.interfacefixture.Values.Text = throw new UnsupportedOperationException
  def echo(value: _root_.interfacefixture.Values.Text): _root_.scala.Option[_root_.interfacefixture.Values.Text] = throw new UnsupportedOperationException

object Testing:
  val fake: interfacefixture.Port = new interfacefixture.Port:
    def read(): _root_.interfacefixture.Values.Text = throw new UnsupportedOperationException
    def echo(value: _root_.interfacefixture.Values.Text): _root_.scala.Option[_root_.interfacefixture.Values.Text] = throw new UnsupportedOperationException

trait OtherPort:
  def read(): String
object Other extends OtherPort:
  def read(): String = "unchanged"

abstract class WrongOverload extends Port:
  def echo(value: Int): Option[String] = None
