//> using scala 3.10.0
package fixture.controllers {
  final class AgentsControllers(val value: Int)
  object AgentsControllers { def result(value: Int): Int = value }
  final class ManagersControllers(val agent: AgentsControllers)
  object ManagersControllers { val label = "ManagersHandlers" }
}
package fixture.consumer {
  import fixture.controllers.{AgentsControllers as Agent, ManagersControllers}
  object Use {
    val agent: Agent = new Agent(1)
    val manager: ManagersControllers = ManagersControllers(agent)
    val result = Agent.result(2)
    val label = ManagersControllers.label
    val qualified: fixture.controllers.AgentsControllers = new fixture.controllers.AgentsControllers(3)
    val callback: Int => Int = fixture.controllers.AgentsControllers.result
  }
  object Lookalikes {
    final class AgentsHandlers
    object ManagersHandlers { val label = "unrelated" }
    val agent = new AgentsHandlers
    val label = ManagersHandlers.label
  }
}
