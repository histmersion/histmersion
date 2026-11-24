package fixture
package sub

import fixture.Dom.span
import fixture.given

object Page:
  def heading(title: Title): String = span(title)
