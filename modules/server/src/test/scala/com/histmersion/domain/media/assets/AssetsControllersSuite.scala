package com.histmersion.domain.media.assets

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Write
import com.histmersion.config.AppConfig
import com.histmersion.domain.media.{MediaSources, MediaState, MediaStoreSuite}
import com.histmersion.util.data.maintenance.WriteGate

import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.media.{AssetInput, AssetPatch}

/** The pictures' controllers over the in-memory service: a figure's URL under an old citekey, an upload to a source
  * that is not there or that does not decode, a picture's uid that is not one, and every write refused while the
  * maintenance lock is held.
  */
class AssetsControllersSuite extends munit.FunSuite:

  private val sources = new MediaSources:
    def current(citekey: String): Option[String] = if citekey == "zqold" then Some("zqnew2001_x") else Some(citekey)
    def lookup(citekey: String): Either[MediaSources.Error, MediaSources.Key] =
      if citekey == "zqnew2001_x" then Right(MediaSources.Key(citekey))
      else Left(MediaSources.Error.NotFound)
    def markdown(citekey: String): String = ""

  private def controllers(gate: WriteGate = WriteGate()) =
    val assets = AssetService.InMemory(AtomicReference(MediaState.empty), MediaStoreSuite.Ticking())
    (AssetsControllers(assets, sources, () => AppConfig.load(), gate), assets)

  private val png = AssetInput(com.histmersion.api.exchange.Bundle.Asset.FileName("a.png"), com.histmersion.api.exchange.Bundle.Asset.MimeText("image/png"), Base64.getEncoder.encodeToString(Array[Byte](1, 2, 3)))

  test("a figure uploaded to its source, read back under an old citekey; refused for a source not there"):
    val (c, _) = controllers()
    assertEquals(c.putFigures(com.histmersion.domain.library.Citekey("zqnew2001_x"), List(png)).map(_.stored), Right(1))
    assertEquals(c.putFigures(com.histmersion.domain.library.Citekey("zqgone2001_x"), List(png)).left.map(_.code), Left(Some(Write.CodeNotFound)))
    assert(
      c.putFigures(com.histmersion.domain.library.Citekey("zqnew2001_x"), List(png.modify(_.base64).setTo("%%%"))).left.exists(_.message.contains("not valid base64"))
    )
    assertEquals(c.figures(com.histmersion.domain.library.Citekey("zqold")).map(_.name), List("a.png"))
    assertEquals(c.figure(com.histmersion.domain.library.Citekey("zqold"), com.histmersion.domain.media.assets.AssetService.AssetName("a.png")).map(_._2), Right("image/png"))
    assertEquals(c.figure(com.histmersion.domain.library.Citekey("zqold"), com.histmersion.domain.media.assets.AssetService.AssetName("b.png")).left.map(_.code), Left(Some(Write.CodeNotFound)))

  test("a picture's uid that is not one is not found; the cards of what holds none are empty"):
    val (c, _) = controllers()
    assertEquals(c.show(com.histmersion.domain.media.assets.AssetService.AssetUid("not-a-uid")).left.map(_.message), Left("No picture 'not-a-uid'"))
    assertEquals(c.thumb(com.histmersion.domain.media.assets.AssetService.AssetUid("00000000-0000-0000-0000-000000000000")).left.map(_.code), Left(Some(Write.CodeNotFound)))
    assertEquals((c.eventThumbs(com.histmersion.domain.catalogue.Event.Id("zq_e")), c.sourceThumb(com.histmersion.domain.library.Citekey("zq")), c.placeThumbs(com.histmersion.domain.catalogue.Place.Id("zq_p"))), (Nil, None, Nil))

  test("a held lock refuses every write"):
    val gate = WriteGate()
    val (c, _) = controllers(gate)
    val held = gate.exclusively(com.histmersion.util.data.maintenance.WriteGate.OperationName("restore"))(
      Right((c.putFigures(com.histmersion.domain.library.Citekey("zqnew2001_x"), List(png)), c.patch(com.histmersion.domain.library.Citekey("zqnew2001_x"), com.histmersion.domain.media.assets.AssetService.AssetName("a.png"), AssetPatch(caption = Some(com.histmersion.api.media.MediaItem.Caption("x"))))))
    )
    val (a, b) = held.fold(e => fail(e.message), identity)
    assert(a.isLeft && b.isLeft, (a, b))
