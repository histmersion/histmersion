package fixture.library

object Citekeys:
  private val Shape = "^[a-z][a-z0-9_]*$".r

  def valid(raw: String): Boolean = Shape.matches(raw)
