package com.histmersion.server

import com.histmersion.api.instance.Version
import com.histmersion.domain.HttpPolicy
import com.histmersion.domain.catalogue.places.GazetteerLoad

import java.io.{ByteArrayOutputStream, InputStream}
import java.net.{HttpURLConnection, URI}
import java.util.zip.ZipInputStream
import scala.annotation.tailrec
import scala.util.Using

/** The plain downloads the gazetteer, the land and PeriodO use: a generic User-Agent ([[HttpPolicy]]), never a personal
  * one; redirects followed by THIS connection (the JVM-wide default it used to flip as well changed every other
  * `HttpURLConnection` in the process).
  */
final class HttpDownload(userAgent: String):

  private def open(url: String, readTimeoutMs: Int): HttpURLConnection =
    val conn = URI.create(url).toURL.openConnection().asInstanceOf[HttpURLConnection]
    conn.setInstanceFollowRedirects(true)
    conn.setRequestProperty("User-Agent", userAgent)
    conn.setConnectTimeout(30000)
    conn.setReadTimeout(readTimeoutMs)
    conn

  /** `url`'s text; a `.zip` is opened and its first file read. */
  def text(url: String): Either[String, String] =
    try
      val conn = open(url, 120000)
      val code = conn.getResponseCode
      if code != 200 then Left(s"HTTP $code from $url")
      else
        val bytes = HttpDownload.readAll(conn.getInputStream)
        conn.disconnect()
        if url.endsWith(".zip") then HttpDownload.unzip(bytes) else Right(new String(bytes, "UTF-8"))
    catch case e: Exception => Left(s"download failed: ${e.getMessage}")

  /** `url` into the file reserved for it. */
  val file: GazetteerLoad.Download = (url, into, timeoutSeconds) =>
    try
      val conn = open(url, timeoutSeconds * 1000)
      val code = conn.getResponseCode
      if code != 200 then Left(s"HTTP $code from $url")
      else
        Using.resource(conn.getInputStream)(in => Using.resource(into.output())(out => in.transferTo(out)))
        Right(())
    catch case e: Exception => Left(s"download of $url failed: ${e.getMessage}")

object HttpDownload:
  def apply(): HttpDownload = new HttpDownload(
    HttpPolicy.userAgent(s"Histmersion/${Version.version} (gazetteer import)")
  )

  private def readAll(is: InputStream): Array[Byte] =
    val buf = new ByteArrayOutputStream(1024 * 1024)
    val tmp = new Array[Byte](8192)
    @tailrec def next(): Unit =
      val n = is.read(tmp)
      if n >= 0 then
        buf.write(tmp, 0, n)
        next()
    next()
    buf.toByteArray

  private def unzip(data: Array[Byte]): Either[String, String] =
    try
      val zis = new ZipInputStream(new java.io.ByteArrayInputStream(data))
      @tailrec def usable(e: java.util.zip.ZipEntry): Option[java.util.zip.ZipEntry] =
        if e == null then None
        else if e.isDirectory || e.getName.startsWith("__MACOSX") then usable(zis.getNextEntry)
        else Some(e)
      usable(zis.getNextEntry) match
        case None    => Left("zip contains no usable file")
        case Some(_) => Right(new String(readAll(zis), "UTF-8"))
    catch case e: Exception => Left(s"unzip failed: ${e.getMessage}")
