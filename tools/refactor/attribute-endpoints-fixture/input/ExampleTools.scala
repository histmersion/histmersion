package fixture

object ExampleTools:
  given Creator.New = Creator.New.Unattributed
  val tool = Endpoint[Tool]().handle { in =>
    Controllers.put(in.text).merge
  }
  val shared = Endpoint[Put]().handle(in => Controllers.put(in.text).merge)
  val partial = Endpoint[Put]().handle { case Put(text, _, _) => Controllers.put(text).merge }
