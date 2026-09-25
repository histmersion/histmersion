package histdb.web

class UiStatePreviewSuite extends munit.FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    UiState.setPreview(None)
    UiState.tags.set(Nil)
    UiState.topics.set(Nil)

  test("a preview joins the filter but not the chips") {
    UiState.tags.set(List("bronze"))
    UiState.setPreview(Some(UiState.Preview("tags", "sea_peoples")))
    assertEquals(UiState.toFilter().tags, List("bronze", "sea_peoples"))
    assertEquals(UiState.tags.now(), List("bronze"))
  }

  test("hovering another suggestion replaces the preview") {
    UiState.setPreview(Some(UiState.Preview("topics", "a")))
    UiState.setPreview(Some(UiState.Preview("topics", "b")))
    assertEquals(UiState.toFilter().topics, List("b"))
  }

  test("leaving the hints restores the committed filter") {
    UiState.topics.set(List("a"))
    UiState.setPreview(Some(UiState.Preview("topics", "b")))
    UiState.setPreview(None)
    assertEquals(UiState.toFilter().topics, List("a"))
  }

  test("committing the previewed value does not duplicate it") {
    UiState.setPreview(Some(UiState.Preview("tags", "x")))
    UiState.tags.set(List("x"))
    assertEquals(UiState.toFilter().tags, List("x"))
  }

  test("a preview is dropped when its own field is excluded (suggestion queries)") {
    UiState.setPreview(Some(UiState.Preview("tags", "x")))
    assertEquals(UiState.toFilter(except = Set("tags")).tags, Nil)
    assertEquals(UiState.toFilter(except = Set("topics")).tags, List("x"))
  }
