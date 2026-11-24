package fixture

import io.github.iltotore.iron.RefinedType

trait Shown[-A]:
  def text(a: A): String

object Shown:
  given Shown[String] = identity(_)

given lifted: [N] => (m: RefinedType.Mirror[N] { type BaseType = String }) => Shown[N] = _.asInstanceOf[String]

object Dom:
  final class Tag(name: String):
    def apply[A](child: A)(using shown: Shown[A]): String = s"<$name>${shown.text(child)}</$name>"

  val span: Tag = Tag("span")
