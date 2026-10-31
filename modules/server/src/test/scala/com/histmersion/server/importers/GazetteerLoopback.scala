package com.histmersion.server.importers

import com.sun.net.httpserver.{HttpExchange, HttpServer}

import java.io.ByteArrayOutputStream
import java.net.{InetSocketAddress, URI}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.jdk.CollectionConverters.*

/** A web server on 127.0.0.1 (a free port) that answers a gazetteer download from a fixture and records what the
  * download sent: the request line and every header, as the wire carried them. Never a socket beyond loopback.
  */
final class GazetteerLoopback:
  private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
  // what the next request is answered with (status, body); set by the suite before each download
  @volatile private var answer: (Int, Array[Byte]) = (404, Array.emptyByteArray)
  private val seen = java.util.concurrent.ConcurrentLinkedQueue[GazetteerLoopback.Request]()

  server.createContext(
    "/",
    (ex: HttpExchange) =>
      val headers = ex.getRequestHeaders.asScala.toList.map((k, vs) => k -> vs.asScala.mkString(", "))
      seen.add(GazetteerLoopback.Request(ex.getRequestMethod, ex.getRequestURI.toString, headers))
      val (status, body) = answer
      ex.sendResponseHeaders(status, if body.isEmpty then -1 else body.length.toLong)
      if body.nonEmpty then ex.getResponseBody.write(body)
      ex.close()
  )
  server.start()

  /** Where the server listens: `http://127.0.0.1:<port>`. */
  val origin: String = s"http://127.0.0.1:${server.getAddress.getPort}"

  /** `url` with its scheme, host and port replaced by this server's. */
  def local(url: String): String =
    val u = URI.create(url)
    origin + u.getRawPath + Option(u.getRawQuery).fold("")("?" + _)

  def answerWith(status: Int, body: Array[Byte]): Unit = answer = (status, body)

  /** The requests since the last call, in order. */
  def take(): List[GazetteerLoopback.Request] =
    Iterator.continually(seen.poll()).takeWhile(_ != null).toList

  def stop(): Unit = server.stop(0)

object GazetteerLoopback:
  /** One request as it came: its method, its path and query, and its headers (as the server names them). */
  final case class Request(method: String, target: String, headers: List[(String, String)])

  /** `entries` as a zip archive, in order; a name ending in `/` is a directory. */
  def zip(entries: List[(String, Array[Byte])]): Array[Byte] =
    val out = ByteArrayOutputStream()
    val z = ZipOutputStream(out)
    entries.foreach { (name, bytes) =>
      z.putNextEntry(ZipEntry(name))
      if !name.endsWith("/") then z.write(bytes)
      z.closeEntry()
    }
    z.close()
    out.toByteArray
