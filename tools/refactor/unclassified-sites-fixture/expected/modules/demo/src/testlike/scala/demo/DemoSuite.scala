package demo

import com.histmersion.util.problem.{Legacy, Problem}

// U12 a test's site has scope `test` (the fixture's test marker is /src/testlike/: scala-cli compiles it as main code)
object DemoSuite:
  val made: Problem = Problem.Unclassified(Legacy.Text("in a test")) // assert: UnclassifiedSites.Unclassified
