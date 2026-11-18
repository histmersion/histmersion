package com.histmersion.spike.crema

import com.histmersion.plugin.{Doi, Plugin, WorkKey}
import com.histmersion.plugin.testkit.{HttpMode, PluginHarness}
import com.histmersion.util.problem.Text.sentences

import java.net.URLClassLoader
import java.nio.file.Path
import java.util.function.{LongUnaryOperator, Supplier}

/** Spike S7 (notes/crema-spike-s7.md): the plugin SPI in a native image with and without
  * `-H:+RuntimeClassLoading`. NOT production code; never merged.
  *
  *   - `ready` prints the startup marker and exits (start-time baseline)
  *   - `hello <jar>|static` loads the `hello` fixture through the server's own PluginHost (via the testkit's
  *     PluginHarness): ServiceLoader discovery, defaults, typed config decode, register, a fetch
  *   - `bench <jar>|aot <n> <rounds>` times a CPU loop in a class loaded from a jar, or the same loop compiled in
  *   - `lazy <jar> <class>` reads a class's lazy vals from 8 threads at once
  */
object CremaMain:
  private val t0 = System.nanoTime()
  private def ms(from: Long): String = f"${(System.nanoTime() - from) / 1e6}%.1f ms"

  def main(args: Array[String]): Unit =
    args.toList match
      case "ready" :: Nil => println(s"crema: ready (${ms(t0)} after main)")
      case "hello" :: where :: Nil => hello(where)
      case "bench" :: where :: n :: rounds :: Nil => bench(where, n.toLong, rounds.toInt)
      case "lazy" :: jar :: cls :: Nil => lazies(jar, cls)
      case _ =>
        System.err.println("usage: crema ready | hello <jar>|static | bench <jar>|aot <n> <rounds> | lazy <jar> <class>")
        sys.exit(2)

  private def loaderFor(jar: String): ClassLoader =
    URLClassLoader(Array(Path.of(jar).toAbsolutePath.toUri.toURL), getClass.getClassLoader)

  private def hello(where: String): Unit =
    val loader = if where == "static" then getClass.getClassLoader else loaderFor(where)
    Thread.currentThread.setContextClassLoader(loader)
    val t = System.nanoTime()
    PluginHarness.load(
      Plugin.Info.Id("hello"),
      "enabled = true\ngreeter = LoudGreeter\nmirrors = [\"https://example.org/a\", \"https://example.org/b\"]",
      HttpMode.Offline,
      env = Map.empty
    ) match
      case Left(problems) =>
        println(s"crema: hello FAILED to load: ${problems.sentences.mkString("; ")}")
        sys.exit(1)
      case Right(h) =>
        val loaded = ms(t)
        h.log.foreach(l => println(s"crema: plugin log: $l"))
        println(s"crema: plugin class ${h.plugin.getClass.getName} from ${h.plugin.getClass.getClassLoader}")
        println(s"crema: fetchers ${h.contributions}")
        val report = h.fetch(WorkKey(doi = Some(Doi("10.1234/crema"))))
        println(s"crema: fetch outcome ${report.outcome}")
        println(s"crema: hello loaded+registered in $loaded; done ${ms(t0)} after main")

  private def bench(where: String, n: Long, rounds: Int): Unit =
    val op: LongUnaryOperator =
      if where == "aot" then AotBench()
      else
        loaderFor(where)
          .loadClass("com.histmersion.spike.cremabench.Bench")
          .getDeclaredConstructor()
          .newInstance()
          .asInstanceOf[LongUnaryOperator]
    (1 to rounds).foreach { r =>
      val t = System.nanoTime()
      val x = op.applyAsLong(n)
      println(s"crema: bench $where round $r n=$n -> $x in ${ms(t)}")
    }

  private def lazies(jar: String, cls: String): Unit =
    val s = loaderFor(jar).loadClass(cls).getDeclaredConstructor().newInstance().asInstanceOf[Supplier[String]]
    val pool = java.util.concurrent.Executors.newFixedThreadPool(8)
    try
      val fs = (1 to 8).map(_ => pool.submit(() => s.get()))
      val got = fs.map(_.get()).distinct
      println(s"crema: lazy $cls -> ${got.mkString(" | ")}")
    finally pool.shutdown()
