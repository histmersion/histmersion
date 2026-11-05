package com.histmersion.pluginhost

import com.histmersion.plugin.{LocalFiles, Processes}
import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.duration.*

object PluginCapabilitiesCheck:
  def main(args: Array[String]): Unit =
    if args.headOption.contains("child") then child(args.drop(1).head)
    else check()

  private def child(mode: String): Unit = mode match
    case "capture" =>
      val input = String(System.in.readAllBytes(), UTF_8)
      System.out.print(input + "|" + Option(System.getenv("CAPABILITY_CHECK")).getOrElse("none"))
      System.err.print("diagnostic")
    case "interactive" =>
      System.out.println("ready")
      System.out.flush()
      val reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in, UTF_8))
      var line = reader.readLine()
      while line != null do
        System.out.println("answer:" + line)
        System.out.flush()
        line = reader.readLine()
    case "wait" => Thread.sleep(30000)
    case other => throw IllegalArgumentException(other)

  private def check(): Unit =
    val files = DiskLocalFiles()
    val bytes = "staged content".getBytes(UTF_8)
    var staged: Option[Path] = None
    assert(files.staged("document.pdf", bytes) { path =>
      staged = Some(path)
      assert(files.isFile(path) && files.exists(path) && files.isDirectory(path.getParent))
      assert(files.read(path).sameElements(bytes))
      assert(String(files.readPrefix(path, 6), UTF_8) == "staged")
      Right("ok")
    } == Right("ok"))
    assert(!Files.exists(staged.get) && !Files.exists(staged.get.getParent))
    assert(files.staged("document.pdf", bytes) { path => staged = Some(path); Left("callback failed") } == Left("callback failed"))
    assert(!Files.exists(staged.get.getParent))
    val thrown = scala.util.Try(files.staged("document.pdf", bytes) { path => staged = Some(path); throw IllegalStateException("callback failed") })
    assert(thrown.isFailure && !Files.exists(staged.get.getParent))
    List("", ".", "..", "../escape", "/absolute", "nested/file", "nested\\file").foreach { name =>
      assert(files.staged(name, bytes)(_ => throw AssertionError("invalid name reached callback")).isLeft)
    }
    assert(LocalFiles.Unavailable.staged("a", bytes)(_ => Right(())).isLeft)
    assert(Processes.Unavailable.capture(List("unused")).isLeft)
    val processes = SystemProcesses()
    val java = Path.of(System.getProperty("java.home"), "bin", "java").toString
    def command(mode: String) = List(java, "-cp", System.getProperty("java.class.path"), getClass.getName.stripSuffix("$"), "child", mode)
    val captured = processes.run(command("capture"), Map("CAPABILITY_CHECK" -> "value"), Some("input"), 10.seconds).toOption.get
    assert(captured == Processes.Result(0, "input|value", "diagnostic"))
    val unbounded = processes.capture(command("capture")).toOption.get
    assert(unbounded == Processes.Result(0, "|none", "diagnostic"))
    assert(processes.run(command("wait"), Map.empty, None, 100.millis).left.exists(_.contains("did not answer")))
    val session = processes.start(command("interactive"), Map.empty).toOption.get
    try
      assert(session.awaitOut(10.seconds)(_.contains("ready")))
      assert(session.send("secret input") == Right(()))
      assert(session.awaitOut(10.seconds)(_.contains("answer:secret input")))
      assert(session.err.isEmpty)
    finally assert(session.close(10.seconds).contains(0))
    assert(!session.alive)
    println("PASS: staged cleanup on success/Left/throw; filename confinement; file reads; refused defaults; separate process streams; stdin/environment; timeout; interactive lifecycle")
