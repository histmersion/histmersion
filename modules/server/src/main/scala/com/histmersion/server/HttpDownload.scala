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
final class HttpDownload(userAgent: HttpDownload.UserAgent):

  private def open(url: com.histmersion.server.HttpDownload.OpenUrl, readTimeoutMs: com.histmersion.server.HttpDownload.OpenReadTimeoutMs): HttpURLConnection =
    val conn = URI.create(url).toURL.openConnection().asInstanceOf[HttpURLConnection]
    conn.setInstanceFollowRedirects(true)
    conn.setRequestProperty("User-Agent", userAgent)
    conn.setConnectTimeout(30000)
    conn.setReadTimeout(readTimeoutMs)
    conn

  /** `url`'s text; a `.zip` is opened and its first file read. */
  def text(url: com.histmersion.server.HttpDownload.TextUrl): Either[com.histmersion.server.HttpDownload.TextResultPart1, com.histmersion.server.HttpDownload.TextResultPart2] =
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
  type UserAgent = UserAgent.T
  object UserAgent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type OpenUrl = OpenUrl.T
  object OpenUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenReadTimeoutMs = OpenReadTimeoutMs.T
  object OpenReadTimeoutMs extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextResultPart1 = TextResultPart1.T
  object TextResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextResultPart2 = TextResultPart2.T
  object TextResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextUrl = TextUrl.T
  object TextUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnzipResultPart1 = UnzipResultPart1.T
  object UnzipResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnzipResultPart2 = UnzipResultPart2.T
  object UnzipResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

  private def unzip(data: Array[Byte]): Either[com.histmersion.server.HttpDownload.UnzipResultPart1, com.histmersion.server.HttpDownload.UnzipResultPart2] =
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
