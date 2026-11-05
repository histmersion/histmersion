package com.histmersion.domain.library.sources

import com.histmersion.api.Write
import com.histmersion.config.AppConfig
import com.histmersion.domain.library.{LibraryState, LibraryStoreSuite}
import com.histmersion.domain.media.{MediaState, MediaStoreSuite}
import com.histmersion.domain.media.assets.{AssetService, AssetsControllers}
import com.histmersion.util.data.maintenance.WriteGate
import java.util.concurrent.atomic.AtomicReference

class MediaSourceBoundarySuite extends munit.FunSuite:
  private def controllers() =
    val sources = SourceService.InMemory(AtomicReference(LibraryState.empty), LibraryStoreSuite.Ticking(), () => LibraryStoreSuite.Settings)
    val assets = AssetService.InMemory(AtomicReference(MediaState.empty), MediaStoreSuite.Ticking())
    AssetsControllers(assets, SourceMedia(sources), () => AppConfig.load(), WriteGate())

  test("invalid source identifiers fail before absence is considered"):
    val c = controllers()
    List("", "Not A Key", "../escape", null).foreach { raw =>
      assertEquals(c.putFigures(raw, Nil).left.map(_.code), Left(Some(Write.CodeInvalid)))
    }

  test("source parser errors reach the media boundary unchanged"):
    assertEquals(controllers().putFigures("../escape", Nil).left.map(_.message),
      Left("invalid citekey '../escape' — it may not contain '..'"))

  test("a valid absent source is not found"):
    assertEquals(controllers().putFigures("missing2026_source", Nil).left.map(_.code), Left(Some(Write.CodeNotFound)))

  test("lookup and missing-source errors retain the original input"):
    val raw = "  missing2026_source  "
    assertEquals(controllers().putFigures(raw, Nil), Left(Write.notFound(raw)))
