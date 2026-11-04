//> using scala 3.10.0
package fixture.controllers {
  final class AgentsHandlers(val value: Int)
  object AgentsHandlers { def result(value: Int): Int = value }
  final class ManagersHandlers(val agent: AgentsHandlers)
  object ManagersHandlers { val label = "ManagersHandlers" }
}
package fixture.consumer {
  import fixture.controllers.{AgentsHandlers as Agent, ManagersHandlers}
  object Use {
    val agent: Agent = new Agent(1)
    val manager: ManagersHandlers = ManagersHandlers(agent)
    val result = Agent.result(2)
    val label = ManagersHandlers.label
    val qualified: fixture.controllers.AgentsHandlers = new fixture.controllers.AgentsHandlers(3)
    val callback: Int => Int = fixture.controllers.AgentsHandlers.result
  }
  object Lookalikes {
    final class AgentsHandlers
    object ManagersHandlers { val label = "unrelated" }
    val agent = new AgentsHandlers
    val label = ManagersHandlers.label
  }
}
