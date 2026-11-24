package fixture.library

final case class Citekey(value: String)

object Citekey:
  def parse(raw: String): Either[String, Citekey] =
    if raw.matches("[a-z][a-z0-9_]*") then Right(Citekey(raw)) else Left(s"not a citekey: $raw")

  extension (oldCk: Citekey) def renameTo(newRaw: String): Either[String, Citekey] =
    Citekey.parse(newRaw.trim).flatMap { ck =>
      if ck == oldCk then Left("unchanged") else Right(ck)
    }

  extension (ck: Citekey) def renamedTwice(first: String, second: String): Either[String, Citekey] =
    ck.renameTo(first).flatMap(_.renameTo(second))
