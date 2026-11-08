package com.histmersion.pluginresolver

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import scala.jdk.CollectionConverters.*

/** The plugins directory's `lib/`: the dependency jars of the installed plugins, and three files. `classpath` lists the
  * jars in class-path order, `inputs` the plugin jars and POMs they were resolved from (`<sha256> <file>`), and
  * `repositories` the repositories they were resolved from.
  */
object LibDirectory:
  export com.histmersion.pluginresolver.Resolver.{Problem as StoredRepositoriesResultPart1, FileName as WriteInputsPart1, Sha256 as WriteInputsPart2, Sha256 as Sha256Result}

  val Classpath = "classpath"
  val Inputs = "inputs"
  val Repositories = "repositories"

  def storedRepositories(lib: Path): Either[com.histmersion.pluginresolver.LibDirectory.StoredRepositoriesResultPart1, List[RepositoryUrl]] =
    val f = lib.resolve(Repositories)
    if !Files.isRegularFile(f) then Right(Nil)
    else
      traverse(Files.readAllLines(f, UTF_8).asScala.toList.map(_.trim).filter(_.nonEmpty))(r =>
        RepositoryUrl.either(r).left.map(e => _root_.com.histmersion.pluginresolver.Resolver.Problem(s"$f: '$r': $e"))
      )

  /** Replace what `lib/` holds with `deps`: a jar already there with the same content is kept, every other jar removed.
    */
  def write(
      lib: Path,
      deps: List[Dependency],
      inputs: List[(com.histmersion.pluginresolver.LibDirectory.WriteInputsPart1, com.histmersion.pluginresolver.LibDirectory.WriteInputsPart2)],
      repositories: List[RepositoryUrl]
  ): Unit =
    Files.createDirectories(lib)
    val wanted = deps.map(_.libName).toSet
    jarsIn(lib).filterNot(p => wanted(_root_.com.histmersion.pluginresolver.Dependency.LibNameResult(p.getFileName.toString))).foreach(Files.delete)
    deps.foreach { d =>
      val target = lib.resolve((d.libName).value)
      if !Files.isRegularFile(target) || sha256(target) != sha256(d.file) then
        val partial = lib.resolve((d.libName).value + ".part")
        Files.copy(d.file, partial, StandardCopyOption.REPLACE_EXISTING)
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
    Files.writeString(lib.resolve(Classpath), (Resolution.classpathFile(deps)).value, UTF_8)
    Files.writeString(lib.resolve(Inputs), (Resolution.inputsFile(inputs)).value, UTF_8)
    if repositories.nonEmpty then
      Files.writeString(lib.resolve(Repositories), repositories.map(_.value + "\n").mkString, UTF_8)

  private def jarsIn(dir: Path): List[Path] =
    val s = Files.list(dir)
    try s.iterator().asScala.filter(p => Files.isRegularFile(p) && p.getFileName.toString.endsWith(".jar")).toList
    finally s.close()

  def sha256(p: Path): com.histmersion.pluginresolver.LibDirectory.Sha256Result =
    val md = MessageDigest.getInstance("SHA-256")
    val in = Files.newInputStream(p)
    try
      val buf = new Array[Byte](1 << 16)
      Iterator.continually(in.read(buf)).takeWhile(_ >= 0).foreach(n => md.update(buf, 0, n))
    finally in.close()
    _root_.com.histmersion.pluginresolver.LibDirectory.Sha256Result(md.digest().map(b => f"$b%02x").mkString)
