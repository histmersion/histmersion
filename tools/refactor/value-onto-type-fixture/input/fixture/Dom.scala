package fixture

import com.histmersion.Display

trait Shown[-A]:
  def text(a: A): String

object Shown:
  given Shown[String] = identity(_)

given displayed: [N] => (display: Display[N]) => Shown[N] = display.text(_)

final class Attr(name: String):
  def :=(value: String): String = s"$name=$value"

extension (attr: Attr) def :=[A](shown: A)(using display: Display[A]): String = attr := display.text(shown)

object Dom:
  final class Tag(name: String):
    def apply[A](child: A)(using shown: Shown[A]): String = s"<$name>${shown.text(child)}</$name>"

  val span: Tag = Tag("span")
  val title: Attr = Attr("title")
  val data: Attr = Attr("data")
