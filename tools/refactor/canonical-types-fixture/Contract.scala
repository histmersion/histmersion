package canonicalfixture

object Contract:
  summon[Original.Argument =:= Canonical.Argument]
  summon[Original.Error =:= Canonical.Error]
  summon[Forwarded.ApplyText =:= Forwarded.Text]
  summon[Forwarded.ApplyOptional =:= Forwarded.Optional]
  summon[scala.util.NotGiven[NonForwarded.ApplyText =:= NonForwarded.Text]]
  @main def verifyCanonicalTypes(): Unit =
    val argument: Canonical.Argument = Original.Argument("resolve")
    assert(argument.value == "resolve")
    assert(result(() => Left(Original.Error("bad"))).left.map(_.value) == Left("bad"))
    val forwarded = Forwarded(Forwarded.ApplyText("text"), Some(Forwarded.ApplyOptional("maybe")))
    assert(forwarded.text.value == "text" && forwarded.maybe.map(_.value).contains("maybe") && forwarded.count == 0)
    assert(NonForwarded(NonForwarded.ApplyText("abc")).text.value == "cba")
    assert(Vocabulary.Entries.map(_.value) == Set("one"))
