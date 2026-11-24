package fixture
package sub

import fixture.Dom.span

object Page:
  def heading(title: Title): String = span(title.value)
