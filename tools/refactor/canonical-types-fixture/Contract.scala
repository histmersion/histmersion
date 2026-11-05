package canonicalfixture

object Contract:
  summon[Original.Argument =:= Canonical.Argument]
  summon[Original.Error =:= Canonical.Error]
  @main def verifyCanonicalTypes(): Unit =
    val argument: Canonical.Argument = Original.Argument("resolve")
    assert(argument.value == "resolve")
    assert(result(() => Left(Original.Error("bad"))).left.map(_.value) == Left("bad"))
