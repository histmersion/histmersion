import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

/** The Maven repository a Histmersion install ships for plugin authors (`<install>/maven`): the plugin API and the
  * plugin platform, the POM whose dependencies are the libraries the server provides to plugins at the versions it
  * ships. Pure: build.sbt reads and writes the files.
  */
object PluginRepo {

  /** `group:artifact` (the artifact with its `_3` suffix, as Maven names it) and a version. */
  final case class Library(group: String, artifact: String, version: String) {
    def key: String = s"$group:$artifact"
    override def toString: String = s"$group:$artifact:$version"
  }

  /** The directory of one version in the Maven layout: `com/histmersion/histmersion-plugin-api_3/0.1.0`. */
  def versionDir(group: String, artifact: String, version: String): String =
    s"${group.replace('.', '/')}/$artifact/$version"

  /** The checked-in platform contract: one `group:artifact:version` per line, `#` comments and blank lines ignored. A
    * line that is not three colon-separated parts is an error naming it.
    */
  def parseContract(lines: Seq[String]): Either[String, Seq[Library]] = {
    val entries = lines.map(_.trim).filter(l => l.nonEmpty && !l.startsWith("#"))
    val parsed = entries.map(l => l -> l.split(':').toList)
    parsed.collectFirst { case (l, parts) if parts.size != 3 || parts.exists(_.isEmpty) => l } match {
      case Some(bad) => Left(s"not group:artifact:version: '$bad'")
      case None      => Right(parsed.map { case (_, List(g, a, v)) => Library(g, a, v); case (l, _) => sys.error(l) })
    }
  }

  /** Pure: the curated libraries at the versions the server resolves (`shipped`: `group:artifact` → version); Left
    * names every curated library the server does not ship.
    */
  def shippedVersions(curated: Seq[Library], shipped: Map[String, String]): Either[String, Seq[Library]] = {
    val missing = curated.filterNot(l => shipped.contains(l.key))
    if missing.nonEmpty then Left(s"not on the server's class path: ${missing.map(_.key).mkString(", ")}")
    else Right(curated.map(l => l.copy(version = shipped(l.key))))
  }

  private def xml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

  /** The platform's POM: packaging `pom`, a dependency per library at its shipped version. */
  def platformPom(
      group: String,
      artifact: String,
      version: String,
      description: String,
      licence: (String, String),
      libraries: Seq[Library]
  ): String = {
    val deps = libraries
      .map { l =>
        s"""    <dependency>
         |      <groupId>${xml(l.group)}</groupId>
         |      <artifactId>${xml(l.artifact)}</artifactId>
         |      <version>${xml(l.version)}</version>
         |    </dependency>""".stripMargin
      }
      .mkString("\n")
    s"""<?xml version="1.0" encoding="UTF-8"?>
       |<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       |    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
       |  <modelVersion>4.0.0</modelVersion>
       |  <groupId>${xml(group)}</groupId>
       |  <artifactId>${xml(artifact)}</artifactId>
       |  <version>${xml(version)}</version>
       |  <packaging>pom</packaging>
       |  <description>${xml(description)}</description>
       |  <licenses>
       |    <license>
       |      <name>${xml(licence._1)}</name>
       |      <url>${xml(licence._2)}</url>
       |    </license>
       |  </licenses>
       |  <dependencies>
       |$deps
       |  </dependencies>
       |</project>
       |""".stripMargin
  }

  private def hex(algorithm: String, bytes: Array[Byte]): String =
    MessageDigest.getInstance(algorithm).digest(bytes).map(b => f"$b%02x").mkString

  /** The checksum files Maven repositories keep beside every file: extension → content. */
  def checksums(bytes: Array[Byte]): Seq[(String, String)] =
    Seq("sha1" -> hex("SHA-1", bytes), "md5" -> hex("MD5", bytes), "sha256" -> hex("SHA-256", bytes))

  def checksums(text: String): Seq[(String, String)] = checksums(text.getBytes(UTF_8))
}
