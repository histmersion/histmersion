package consolidationfixture

object Contract:
  summon[ForwardMethods.OuterN =:= ForwardMethods.LeafN]
  summon[ForwardMethods.MiddleN =:= ForwardMethods.LeafN]
  summon[ForwardMethods.FullN =:= ForwardMethods.LeafN]
  summon[ForwardMethods.OuterLabel =:= ForwardMethods.LeafLabel]
  summon[ForwardMethods.MiddleLabel =:= ForwardMethods.LeafLabel]
  summon[ForwardMethods.FullLabel =:= ForwardMethods.LeafLabel]
  summon[ForwardMethods.RightCount =:= ForwardMethods.LeftCount]
  summon[ForwardMethods.ListTag =:= ForwardMethods.LeafTag]
  summon[Forward.Builders.MakeTitle =:= Forward.Item.Title]
  summon[Forward.Builders.MakeCount =:= Forward.Item.Count]
  summon[Forward.Builders.NamedTitle =:= Forward.Item.Title]
  summon[Forward.Builders.NamedCount =:= Forward.Item.Count]
  summon[Client.Seconds =:= Shared.Seconds]
  summon[Run.Step.Seconds =:= Run.Seconds]
  summon[Run.Summary.Seconds =:= Run.Seconds]
  summon[Run.Step.Seconds.T =:= Run.Seconds]
  summon[scala.util.NotGiven[Outside.Seconds =:= Run.Seconds]]
  summon[scala.util.NotGiven[Run.OtherUnit.Milliseconds =:= Run.Seconds]]

  @main def verifyConsolidation(): Unit =
    assert(ForwardMethods.outer(ForwardMethods.OuterN(7L), ForwardMethods.OuterLabel("one")) == "7:one")
    assert(ForwardMethods.fullyQualified(ForwardMethods.FullN(9L), ForwardMethods.FullLabel("two")) == "9:two")
    assert(ForwardMethods.left(ForwardMethods.LeftCount(2)) == 2)
    assert(ForwardMethods.lists(List(ForwardMethods.ListTag("tag"))).map(_.value) == List("tag"))
    val made = Forward.Builders.make(Forward.Builders.MakeTitle("first"), Forward.Builders.MakeCount(7))
    assert(made.title.value == "first" && made.count.value == 7)
    val named = Forward.Builders.named(Forward.Builders.NamedTitle("second"), Forward.Builders.NamedCount(9))
    assert(named.title.value == "second" && named.count.value == 9)
    val shared: Shared.Seconds = Client.Seconds(13L)
    assert(shared.value == 13L)
    assert(Client.Seconds.either(17L).map(_.value) == Right(17L))
    val first: Run.Step.Seconds = Run.Step.Seconds(4L)
    val second: Run.Summary.Seconds = Run.Summary.Seconds(7L)
    val values: List[Run.Seconds] = List(first, second)
    val callback: Run.Step.Seconds => Option[Run.Summary.Seconds] = x => Some(x)
    assert(values.map(_.value).sum == 11L)
    assert(callback(first).map(_.value).contains(4L))
    assert(Run.Step.Seconds.either(9L).map(_.value) == Right(9L))
