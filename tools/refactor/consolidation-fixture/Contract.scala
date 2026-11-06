package consolidationfixture

object Contract:
  summon[Client.Seconds =:= Shared.Seconds]
  summon[Run.Step.Seconds =:= Run.Seconds]
  summon[Run.Summary.Seconds =:= Run.Seconds]
  summon[Run.Step.Seconds.T =:= Run.Seconds]
  summon[scala.util.NotGiven[Outside.Seconds =:= Run.Seconds]]
  summon[scala.util.NotGiven[Run.OtherUnit.Milliseconds =:= Run.Seconds]]

  @main def verifyConsolidation(): Unit =
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
