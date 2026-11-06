package interfacefixture

object Concrete extends Registry[Int]:
  def add(id: _root_.interfacefixture.Values.Text, a: Int): Either[_root_.interfacefixture.Values.Text, Unit] = Right(())

object Alternate:
  trait Registry[A]:
    def add(id: String, a: A): Boolean

object ImportedUsing:
  import Alternate.Registry
  object Live extends Registry[Int]:
    def add(id: String, a: Int): Boolean = true

object LocalUsing:
  trait Registry[A]:
    def add(id: String, a: A): Boolean
  object Live extends Registry[Int]:
    def add(id: String, a: Int): Boolean = true
