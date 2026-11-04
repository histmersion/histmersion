package lensfixture

import hearth.kindlings.optics.syntax.*

object Matched:
  def update(r: Record): Record = r.modify(_.a).setTo(r.a match
    case 0 => 1
    case n => n
  ).modify(_.b).setTo("updated")
