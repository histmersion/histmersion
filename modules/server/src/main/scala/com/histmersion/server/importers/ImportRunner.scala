package com.histmersion.server.importers

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.PlaceRepo
import javax.sql.DataSource
import java.io.{ByteArrayOutputStream, InputStream}
import java.net.{HttpURLConnection, URI}
import java.util.zip.ZipInputStream

/** Downloads, transforms and upserts external gazetteer data. Handles zip files (Cliopatria ships
  * as a zip) and plain JSON. The heavy work is in the importer's `transform` — this class
  * orchestrates fetch → unpack → transform → batch upsert. */
object ImportRunner:

  private val BatchSize = 1000

  def fetchAndImport(imp: GazetteerImporter, ds: DataSource, dryRun: Boolean,
      toleranceKm: Double): Either[String, PlaceImportResult] =
    for
      json <- download(imp.fetchUrl)
      items <- imp.transform(json)
      parents = imp match
        case c: CliopatriaImporter.type => c.parentConcepts(items)
        case _ => Nil
      all = parents ++ items
      result <- if dryRun then Right(PlaceImportResult(all.size, 0, 0, List("dry run")))
                else batchUpsert(all, ds, toleranceKm)
    yield result

  private[importers] def download(url: String): Either[String, String] =
    try
      // redirects are followed by THIS connection ([[HttpPolicy]]): the JVM-wide default it used to
      // flip as well changed every other HttpURLConnection in the process
      val conn = URI.create(url).toURL.openConnection().asInstanceOf[HttpURLConnection]
      conn.setInstanceFollowRedirects(true)
      conn.setRequestProperty("User-Agent", HttpPolicy.userAgent(s"Histmersion/${Version.version} (gazetteer import)"))
      conn.setConnectTimeout(30000)
      conn.setReadTimeout(120000)
      val code = conn.getResponseCode
      if code != 200 then Left(s"HTTP $code from $url")
      else
        val bytes = readAll(conn.getInputStream)
        conn.disconnect()
        if url.endsWith(".zip") then unzip(bytes) else Right(new String(bytes, "UTF-8"))
    catch case e: Exception => Left(s"download failed: ${e.getMessage}")

  private def readAll(is: InputStream): Array[Byte] =
    val buf = new ByteArrayOutputStream(1024 * 1024)
    val tmp = new Array[Byte](8192)
    var n = is.read(tmp)
    while n >= 0 do
      buf.write(tmp, 0, n)
      n = is.read(tmp)
    buf.toByteArray

  private def unzip(data: Array[Byte]): Either[String, String] =
    try
      val zis = new ZipInputStream(new java.io.ByteArrayInputStream(data))
      var entry = zis.getNextEntry
      while entry != null && (entry.isDirectory || entry.getName.startsWith("__MACOSX")) do
        entry = zis.getNextEntry
      if entry == null then Left("zip contains no usable file")
      else Right(new String(readAll(zis), "UTF-8"))
    catch case e: Exception => Left(s"unzip failed: ${e.getMessage}")

  private def batchUpsert(items: List[PlaceInput], ds: DataSource,
      toleranceKm: Double): Either[String, PlaceImportResult] =
    try
      val batches = items.grouped(BatchSize).toList
      val nBatches = batches.size
      var i = 0
      val total = batches.foldLeft(PlaceImportResult(0, 0, 0, Nil)) { (acc, batch) =>
        i += 1
        scribe.info(s"[import] upserting batch $i/$nBatches (${batch.size} places)…")
        val r = PlaceRepo.upsertPlaces(ds, batch)
        PlaceImportResult(
          acc.places + r.places, acc.names + r.names,
          acc.relations + r.relations, (acc.failed ++ r.failed).take(20))
      }
      scribe.info(s"[import] rebuilding finding_places…")
      PlaceRepo.rebuildFindingPlaces(ds, toleranceKm)
      Right(total)
    catch case e: Exception => Left(s"upsert failed: ${e.getMessage}")
