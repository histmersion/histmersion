package com.histmersion
package domain.library.sources

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.api.Write
import com.histmersion.config.AppConfig
import com.histmersion.domain.library.{LibraryState, LibraryStoreSuite}
import com.histmersion.domain.media.{MediaState, MediaStoreSuite}
import com.histmersion.domain.media.assets.{AssetsControllers, AssetService}
import com.histmersion.util.data.maintenance.WriteGate
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.library.Citekey

class MediaSourceBoundarySuite extends munit.FunSuite:
  private def controllers() =
    val sources = SourceService.InMemory(
      AtomicReference(LibraryState.empty),
      LibraryStoreSuite.Ticking(),
      () => LibraryStoreSuite.Settings
    )
    val assets = AssetService.InMemory(AtomicReference(MediaState.empty), MediaStoreSuite.Ticking())
    AssetsControllers(assets, SourceMedia(sources), () => AppConfig.load(), WriteGate())

  test("invalid source identifiers fail before absence is considered"):
    val c = controllers()
    List("", "Not A Key", "../escape", null).foreach { raw =>
      assertEquals(c.putFiguresAsked(Citekey(raw), Nil).left.map(_.code), Left(Some(Write.CodeInvalid)))
    }

  test("source parser errors reach the media boundary unchanged"):
    assertEquals(
      controllers().putFiguresAsked(Citekey("../escape"), Nil).left.map(_.message).left.map(_.sentence),
      Left("'../escape' is not a Citekey: Should match (?!.*\\.\\.)[a-z][a-z0-9_.-]{1,120}")
    )

  test("a valid absent source is not found"):
    assertEquals(
      controllers().putFigures(com.histmersion.domain.library.Citekey("missing2026_source"), Nil).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )

  test("lookup and missing-source errors retain the original input"):
    val raw = "  missing2026_source  "
    assertEquals(controllers().putFiguresAsked(Citekey(raw), Nil), Left(Write.notFound(Citekey(raw))))
