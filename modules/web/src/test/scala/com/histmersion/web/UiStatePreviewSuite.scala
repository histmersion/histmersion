package com.histmersion.web

import com.histmersion.testsupport.ComparedByValue.given

class UiStatePreviewSuite extends munit.FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    UiState.setPreview(None)
    UiState.tags.set(Nil)
    UiState.topics.set(Nil)

  test("a preview joins the filter but not the chips") {
    UiState.tags.set(List("bronze"))
    UiState.setPreview(Some(UiState.Preview(com.histmersion.web.UiState.Preview.Key("tags"), com.histmersion.web.UiState.Preview.Value("sea_peoples"))))
    assertEquals(UiState.toFilter().tags, List("bronze", "sea_peoples"))
    assertEquals(UiState.tags.now(), List("bronze"))
  }

  test("hovering another suggestion replaces the preview") {
    UiState.setPreview(Some(UiState.Preview(com.histmersion.web.UiState.Preview.Key("topics"), com.histmersion.web.UiState.Preview.Value("a"))))
    UiState.setPreview(Some(UiState.Preview(com.histmersion.web.UiState.Preview.Key("topics"), com.histmersion.web.UiState.Preview.Value("b"))))
    assertEquals(UiState.toFilter().topics, List("b"))
  }

  test("leaving the hints restores the committed filter") {
    UiState.topics.set(List("a"))
    UiState.setPreview(Some(UiState.Preview(com.histmersion.web.UiState.Preview.Key("topics"), com.histmersion.web.UiState.Preview.Value("b"))))
    UiState.setPreview(None)
    assertEquals(UiState.toFilter().topics, List("a"))
  }

  test("committing the previewed value does not duplicate it") {
    UiState.setPreview(Some(UiState.Preview(com.histmersion.web.UiState.Preview.Key("tags"), com.histmersion.web.UiState.Preview.Value("x"))))
    UiState.tags.set(List("x"))
    assertEquals(UiState.toFilter().tags, List("x"))
  }

  test("a preview is dropped when its own field is excluded (suggestion queries)") {
    UiState.setPreview(Some(UiState.Preview(com.histmersion.web.UiState.Preview.Key("tags"), com.histmersion.web.UiState.Preview.Value("x"))))
    assertEquals(UiState.toFilter(except = Set(com.histmersion.web.UiState.ToFilterExcept("tags"))).tags, Nil)
    assertEquals(UiState.toFilter(except = Set(com.histmersion.web.UiState.ToFilterExcept("topics"))).tags, List("x"))
  }

  test("a reload after a preview waits preview-ms; after a committed change, the normal delay") {
    UiState.mirrorHash = false
    UiState.store = Store.Memory()
    UiState.previewMs = com.histmersion.api.instance.DebounceConfig.PreviewMs(60)
    UiState.setPreview(Some(UiState.Preview(com.histmersion.web.UiState.Preview.Key("tags"), com.histmersion.web.UiState.Preview.Value("x"))))
    assertEquals(UiState.reloadDelay(com.histmersion.web.UiState.ReloadDelayNormalMs(250)), 60)
    assertEquals(UiState.reloadDelay(com.histmersion.web.UiState.ReloadDelayNormalMs(40)), 40, "never longer than the normal delay")
    UiState.set(UiState.topics := List("y"))()
    assertEquals(UiState.reloadDelay(com.histmersion.web.UiState.ReloadDelayNormalMs(250)), 250)
  }
