package com.histmersion
package cli

import com.histmersion.api.settings.{ConfigEdit, ConfigEditMarkdown}
import com.github.plokhotnyuk.jsoniter_scala.core.writeToString
import com.sun.net.httpserver.HttpServer

import java.io.{ByteArrayOutputStream, PrintStream}
import java.net.{InetAddress, InetSocketAddress, ServerSocket}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.atomic.AtomicInteger

class SettingsCmdSuite extends munit.FunSuite:

  private val serverWrote = ConfigEdit.Written(
    ConfigEdit.Written.File("/srv/histmersion.conf"),
    changed = List(ConfigEdit.Written.Changed("histmersion.auth.proxy-networks")),
    restart = List(
      ConfigEdit.RestartNote(
        ConfigEdit.RestartNote.Key("histmersion.auth.proxy-networks"),
        ConfigEdit.RestartNote.Why("read when the server starts")
      )
    )
  )

  private val fileWrote = ConfigEdit.Written(
    ConfigEdit.Written.File("/home/histmersion.conf"),
    unchanged = List(ConfigEdit.Written.Unchanged("histmersion.auth.proxy-networks"))
  )

  private def answering[A](body: Client.ServerUrl => A): A =
    val srv = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress, 0), 0)
    srv.createContext(
      "/",
      ex =>
        val bytes = writeToString(serverWrote).getBytes(UTF_8)
        ex.getResponseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(200, bytes.length.toLong)
        ex.getResponseBody.write(bytes)
        ex.close()
    )
    srv.start()
    try body(Loopback.url(srv.getAddress.getPort))
    finally srv.stop(0)

  private def closedPort: Client.ServerUrl =
    val socket = ServerSocket(0, 1, InetAddress.getLoopbackAddress)
    val port = socket.getLocalPort
    socket.close()
    Loopback.url(port)

  private enum Target derives CanEqual:
    case ServerFirst, ThisMachine

  private def set(
      url: Client.ServerUrl,
      target: Target,
      localWrites: AtomicInteger
  ): (Either[Cli.Error, Unit], String) =
    val out = ByteArrayOutputStream()
    given util.console.Console =
      util.console.Console.streams(
        PrintStream(out),
        PrintStream(ByteArrayOutputStream()),
        () => util.console.Console.Line("")
      )
    val writeLocally: ConfigEdit.Write => Either[Cli.Error, ConfigEdit.Written] = _ =>
      localWrites.incrementAndGet()
      Right(fileWrote)
    val result = SettingsCmd.Perform(Client(url), writeLocally)(
      SettingsCmd.Command.SetCmd(
        "auth.proxy-networks",
        "[\"10.0.0.0/8\"]",
        onlyLocal = target == Target.ThisMachine,
        nr = true,
        consent = false,
        json = Cli.JsonOutput(false)
      )
    )
    (result, out.toString(UTF_8))

  test("a setting the server wrote is reported as written, the file on this machine left alone"):
    answering { url =>
      val localWrites = AtomicInteger(0)
      val (result, shown) = set(url, Target.ServerFirst, localWrites)
      assertEquals(result, Right(()))
      assertEquals(localWrites.get, 0, "the server answered: the file on this machine is not written first")
      assertEquals(shown.trim, ConfigEditMarkdown.written(serverWrote).trim)
      assert(!shown.contains("Already so"), shown)
      assert(shown.contains("**Takes effect after a restart:**\n- auth.proxy-networks"), shown)
    }

  test("the file on this machine is written only when the server does not answer, or when asked for"):
    val unreachable = AtomicInteger(0)
    assertEquals(set(closedPort, Target.ServerFirst, unreachable)._1, Right(()))
    assertEquals(unreachable.get, 1)
    answering { url =>
      val asked = AtomicInteger(0)
      val (result, shown) = set(url, Target.ThisMachine, asked)
      assertEquals(result, Right(()))
      assertEquals(asked.get, 1)
      assert(shown.contains("**Already so:**\n- auth.proxy-networks"), shown)
    }
