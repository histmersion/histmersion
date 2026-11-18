package demo

import com.histmersion.cli.{Cli, ExitCode}
object Elsewhere:
  // C2 a caller that does not see the wrapper's names gets their imports
  def twice(s: String) = Parse.number(s).left.map(Cli.Error(_, exitCode = ExitCode.Usage)).map(_ * 2)
