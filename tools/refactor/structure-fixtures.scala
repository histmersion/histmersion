package com.histmersion.db { // expect: bucket
  object DatabaseThing
}
package com.histmersion.files { // expect: bucket
  object FileThing
}
package com.histmersion.values { // expect: bucket
  object ValueThing
}
package com.histmersion.upstreams { // expect: bucket
  object UpstreamThing
}
package com.histmersion.upstreams.core {
  object Parser
}
package com.histmersion {
  package db { // expect: bucket
    object NestedDatabaseThing
  }
}
package com.histmersion.domain.audit {
  final class FeatureHandlers // expect: handlers
  trait FeatureTraitHandlers // expect: handlers
  object FeatureObjectHandlers // expect: handlers
  type AliasHandlers = FeatureHandlers // expect: handlers
  enum EventHandlers: // expect: handlers
    case One
  class FeatureControllers
  object FeatureHandlersText
  object Entry:
    def main(args: Array[String]): Unit = () // expect: object-main
    class Nested:
      def main(args: Array[String]): Unit = ()
  object AnotherEntry:
    def main(args: scala.Array[java.lang.String]) = () // expect: object-main
  object KindSources:
    def main(kind: String): String = kind
  @main def launch(args: String*): Unit = ()
  val sample = "object FakeHandlers { def main(args: Array[String]): Unit = () }"
}
package com.histmersion.web.map {
  trait MarkerHandlers
}
