import java.nio.charset.StandardCharsets.UTF_8

object LedgerSitesPlan {
  val Config = "tools/refactor/ledger-sites.conf"
  val TimeoutEnv = "HISTMERSION_LEDGER_SITES_TIMEOUT"
  val DefaultTimeoutSeconds: Long = 15L * 60

  def command(roots: Seq[String], scala: String, scalafixScala: String, scalafix: String): Seq[String] =
    Seq(
      "scala-cli",
      "run",
      "--server=false",
      "-q",
      "-S",
      scalafixScala,
      "--java-opt",
      "-Xmx6g",
      "--dep",
      s"ch.epfl.scala:scalafix-cli_$scalafixScala:$scalafix",
      "--main-class",
      "scalafix.cli.Cli",
      "--",
      "--config",
      Config,
      "--scala-version",
      scala,
      "--sourceroot",
      ".",
      "--auto-classpath",
      "--auto-classpath-roots",
      "target/out"
    ) ++ roots.flatMap(r => Seq("--semanticdb-targetroots", r)) ++ Seq("--files", "modules")

  def key(sourcesDigest: String, versions: String): String = {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    digest.update(s"$sourcesDigest\n$versions".getBytes(UTF_8))
    digest.digest.map(b => f"${b & 0xff}%02x").mkString
  }

  def timeoutSeconds(env: Option[String]): Long =
    env.map(_.trim).flatMap(_.toLongOption).filter(_ > 0).getOrElse(DefaultTimeoutSeconds)
}
