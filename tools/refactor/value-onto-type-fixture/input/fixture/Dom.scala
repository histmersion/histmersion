package fixture

import com.histmersion.Display

trait Shown[-A]:
  def text(a: A): String

object Shown:
  given Shown[String] = identity(_)

given displayed: [N] => (display: Display[N]) => Shown[N] = display.text(_)

object Dom:
  final class Tag(name: String):
    def apply[A](child: A)(using shown: Shown[A]): String = s"<$name>${shown.text(child)}</$name>"

  val span: Tag = Tag("span")
