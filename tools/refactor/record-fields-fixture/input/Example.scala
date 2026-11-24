//> using scala 3.10.1
package fixture

object provenance:
  final case class By(plugin: Option[String] = None)
  trait Named:
    def createdBy: Option[By]

trait Shown

final case class Put(
    text: String,
    tags: List[String] = Nil
) derives CanEqual

final case class Patch(text: Option[String] = None) extends Shown

final case class View(id: Int, title: String)

final case class Untouched(text: String)
