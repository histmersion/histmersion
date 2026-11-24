package fixture.library

object Citekeys:
  private val Shape = "^[a-z][a-z0-9_]*$".r

  def valid(raw: String): Boolean = Shape.matches(raw)

  def renameTo(oldCk: Citekey, newRaw: String): Either[String, Citekey] =
    Citekey.parse(newRaw.trim).flatMap { ck =>
      if ck == oldCk then Left("unchanged") else Right(ck)
    }

  def renamedTwice(ck: Citekey, first: String, second: String): Either[String, Citekey] =
    renameTo(ck, first).flatMap(renameTo(_, second))
