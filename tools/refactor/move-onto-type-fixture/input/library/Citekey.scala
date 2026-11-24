package fixture.library

final case class Citekey(value: String)

object Citekey:
  def parse(raw: String): Either[String, Citekey] =
    if raw.matches("[a-z][a-z0-9_]*") then Right(Citekey(raw)) else Left(s"not a citekey: $raw")
