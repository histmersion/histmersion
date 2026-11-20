package com.histmersion
package cli

import java.io.InputStream
import java.net.{InetAddress, ServerSocket}
import java.nio.charset.StandardCharsets.US_ASCII

/** G111: what the CLI says when the connection fails, against a stub on the loopback interface (no real network). A
  * connection that ends after a write was sent leaves the outcome unknown; one never made is "unreachable".
  */
class ClientTransportSuite extends munit.FunSuite:

  // a hang is a failure, not a stuck run
  override val munitTimeout = scala.concurrent.duration.Duration(60, "s")

  /** A server that reads each request whole (its headers and the body its Content-Length gives), then closes the
    * connection without answering — every connection, since the JDK's client sends a GET a second time. Its URL; it
    * stops when the socket is closed.
    */
  private def hangUpAfterReading(): (ServerSocket, Client.ServerUrl) =
    val socket = new ServerSocket(0, 4, InetAddress.getLoopbackAddress)
    @annotation.tailrec def serve(): Unit =
      val served = scala.util.Try {
        val s = socket.accept()
        val in = s.getInputStream
        val head = readHead(in)
        val length = head.linesIterator
          .collectFirst { case l if l.toLowerCase.startsWith("content-length:") => l.drop(15).trim.toInt }
          .getOrElse(0)
        val _ = in.readNBytes(length)
        s.close()
      }
      if served.isSuccess then serve()
    val t = new Thread(() => serve())
    t.setDaemon(true)
    t.start()
    (socket, Client.ServerUrl(s"http://127.0.0.1:${socket.getLocalPort}"))

  private def readHead(in: InputStream): String =
    val out = new java.io.ByteArrayOutputStream
    @annotation.tailrec def loop(last: List[Int]): Unit =
      val b = in.read()
      if b >= 0 then
        out.write(b)
        val seen = (b :: last).take(4)
        if seen != List('\n', '\r', '\n', '\r').map(_.toInt) then loop(seen)
    loop(Nil)
    new String(out.toByteArray, US_ASCII)

  test("a write whose connection ends after it was sent: outcome unknown, with the request named"):
    val (socket, url) = hangUpAfterReading()
    try
      val r = new Client(url).backup
      assertEquals(r.left.map(_.exitCode), Left(ExitCode.OutcomeUnknown))
      val said = r.left.toOption.map(_.problems.toString).getOrElse("")
      assert(said.contains("/api/backup"), said)
    finally socket.close()

  test("a read whose connection ends: 'cannot reach', nothing changed"):
    val (socket, url) = hangUpAfterReading()
    try assertEquals(new Client(url).health.left.map(_.exitCode), Left(ExitCode.Unreachable))
    finally socket.close()

  test("a server that is not there: 'unreachable', for a write too"):
    val socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress)
    val url = Client.ServerUrl(s"http://127.0.0.1:${socket.getLocalPort}")
    socket.close()
    assertEquals(new Client(url).backup.left.map(_.exitCode), Left(ExitCode.Unreachable))
