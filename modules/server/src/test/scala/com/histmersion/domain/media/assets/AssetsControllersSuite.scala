package com.histmersion
package domain.media.assets

import com.histmersion.api.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.Write
import com.histmersion.config.AppConfig
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.media.{MediaSources, MediaState, MediaStoreSuite}
import com.histmersion.util.data.maintenance.WriteGate

import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.api.media.{AssetInput, AssetPatch}
import com.histmersion.api.exchange.Bundle
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.catalogue.Place
import com.histmersion.api.media.MediaItem

/** The pictures' controllers over the in-memory service: a figure's URL under an old citekey, an upload to a source
  * that is not there or that does not decode, a picture's uid that is not one, and every write refused while the
  * maintenance lock is held.
  */
class AssetsControllersSuite extends munit.FunSuite:

  private val sources = new MediaSources:
    def current(citekey: Citekey): Option[MediaSources.Current] = if citekey == Citekey("zqold") then
      Some(MediaSources.Current("zqnew2001_x"))
    else Some(MediaSources.Current(citekey.value))
    def lookup(citekey: Citekey): Either[MediaSources.Error, MediaSources.Key] =
      if citekey == Citekey("zqnew2001_x") then Right(MediaSources.Key(citekey.value))
      else Left(MediaSources.Error.NotFound)
    def markdown(citekey: Citekey): MediaSources.Markdown = MediaSources.Markdown("")

  private def controllers(gate: WriteGate = WriteGate()) =
    val assets = AssetService.InMemory(AtomicReference(MediaState.empty), MediaStoreSuite.Ticking())
    (AssetsControllers(assets, sources, () => AppConfig.load(), gate), assets)

  private val png = AssetInput(
    Bundle.Asset.FileName("a.png"),
    Bundle.Asset.MimeText("image/png"),
    AssetInput.Base64(Base64.getEncoder.encodeToString(Array[Byte](1, 2, 3)))
  )

  test("a figure uploaded to its source, read back under an old citekey; refused for a source not there"):
    val (c, _) = controllers()
    assertEquals(c.putFigures(Citekey("zqnew2001_x"), List(png)).map(_.stored), Right(1))
    assertEquals(c.putFigures(Citekey("zqgone2001_x"), List(png)).left.map(_.code), Left(Some(Write.CodeNotFound)))
    assert(
      c.putFigures(Citekey("zqnew2001_x"), List(png.modify(_.base64).setTo(AssetInput.Base64("%%%"))))
        .left
        .exists(_.message.sentence.contains("not valid base64"))
    )
    assertEquals(c.figures(Citekey("zqold")).map(_.name), List("a.png"))
    assertEquals(c.figure(Citekey("zqold"), AssetService.AssetName("a.png")).map(_._2), Right("image/png"))
    assertEquals(
      c.figure(Citekey("zqold"), AssetService.AssetName("b.png")).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )

  test("a picture's uid that is not one is not found; the cards of what holds none are empty"):
    val (c, _) = controllers()
    assertEquals(
      c.show(AssetService.AssetUid("not-a-uid")).left.map(_.message).left.map(_.sentence),
      Left("No picture 'not-a-uid'")
    )
    assertEquals(
      c.thumb(AssetService.AssetUid("00000000-0000-0000-0000-000000000000")).left.map(_.code),
      Left(Some(Write.CodeNotFound))
    )
    assertEquals(
      (c.eventThumbs(Event.Id("zq_e")), c.sourceThumb(Citekey("zq")), c.placeThumbs(Place.Id("zq_p"))),
      (Nil, None, Nil)
    )

  test("a held lock refuses every write"):
    val gate = WriteGate()
    val (c, _) = controllers(gate)
    val held = gate.exclusively(WriteGate.OperationName("restore"))(
      Right(
        (
          c.putFigures(Citekey("zqnew2001_x"), List(png)),
          c.patch(
            Citekey("zqnew2001_x"),
            AssetService.AssetName("a.png"),
            AssetPatch(caption = Some(MediaItem.Caption("x")))
          )
        )
      )
    )
    val (a, b) = held.fold(e => fail(e.message.sentence), identity)
    assert(a.isLeft && b.isLeft, (a, b))
