//> using scala 3.10.0
//> using dep io.scalaland::chimney:2.1.0
package com.histmersion.api.automation {
  enum AgentRole { case Probe, Writer }
}
package com.histmersion.domain.automation {
  enum AgentRole { case Probe, Writer }
}
package fixtures {
  import io.scalaland.chimney.dsl.*
  object ModelPicker {
    def accept(role: com.histmersion.domain.automation.AgentRole): String = role.toString
    def pick(role: com.histmersion.api.automation.AgentRole): String = accept(role.transformInto[com.histmersion.domain.automation.AgentRole])
    def alreadyDomain(role: com.histmersion.api.automation.AgentRole): String = accept(role.transformInto[com.histmersion.domain.automation.AgentRole])
    def ordinaryValue(role: String): String = role.trim
  }
}
