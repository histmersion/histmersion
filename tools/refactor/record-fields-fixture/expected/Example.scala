//> using scala 3.10.1
package fixture

import fixture.provenance
object provenance:
  final case class By(plugin: Option[String] = None)
  trait Named:
    def createdBy: Option[By]

trait Shown

final case class Put(
    text: String,
    tags: List[String] = Nil, createdBy: Option[provenance.By] = None
) extends provenance.Named derives CanEqual

final case class Patch(text: Option[String] = None, createdBy: Option[provenance.By] = None) extends Shown with provenance.Named

final case class View(id: Int, title: String, createdAt: Option[java.time.Instant] = None)

final case class Untouched(text: String)
