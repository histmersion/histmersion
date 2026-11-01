package com.histmersion.domain.media.figures

import com.histmersion.api.FigureLicenceFill
import com.histmersion.db.Importer
import com.histmersion.domain.media.assets.{AssetFixtures, NewFigure}

import com.augustnagro.magnum.*
import java.nio.file.{Files, Path}
import javax.sql.DataSource
import scala.jdk.CollectionConverters.*

/** The figures' repository as the db suites reach it, and the seed's figures read beside its notes as the server
  * reads them.
  */
object FigureFixtures:
  def fillLicences(ds: DataSource, apply: Boolean): FigureLicenceFill = transact(ds)(FigureRepo.fillLicences(apply))

  /** The seed's figures beside a note (`md/<citekey>/figures`, or next to its `md_path`), stored in `ds`. */
  def onDisk(ds: DataSource): Importer.SeedFigures = new Importer.SeedFigures:
    def add(citekey: String, figures: List[NewFigure]): Int = AssetFixtures.add(ds, citekey, figures)
    def of(note: Path, citekey: String, maxBytes: Long): List[NewFigure] =
      val text = scala.util.Try(Files.readString(note)).getOrElse("")
      val root = Option(note.getParent).flatMap(a => Option(a.getParent)).flatMap(a => Option(a.getParent))
      root
        .map(r =>
          Figures.mdPathOf(text).map(rel => r.resolve(rel).getParent.resolve("figures"))
            .getOrElse(r.resolve("md").resolve(citekey).resolve("figures"))
        )
        .filter(Files.isDirectory(_))
        .map { dir =>
          val files = scala.util.Using.resource(Files.list(dir))(_.iterator.asScala.filter(Files.isRegularFile(_)).toList)
          Figures.of(files.map(f => f.getFileName.toString -> Files.readAllBytes(f)), maxBytes)
        }
        .getOrElse(Nil)
