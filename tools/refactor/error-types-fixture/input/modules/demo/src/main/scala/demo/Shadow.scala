package demo

object Shadow:
  // C3 a caller that writes ExitCode for something else: E11 stays
  val ExitCode = 5
  def call(s: String) = Refusals.refused(s).map(_ + ExitCode)
