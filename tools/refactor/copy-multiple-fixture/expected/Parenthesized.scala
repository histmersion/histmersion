package lensfixture

import hearth.kindlings.optics.syntax.*

object Parenthesized:
  def update(r: Record): Record = r.modify(_.a).setTo((r.a + 1)).modify(_.b).setTo((r.b + "!"))
