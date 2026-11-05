package relocation.client
import relocation.library.{Title, Other}

object Client:
  val grouped: Title = Title("grouped")
  val other = Other("unchanged")
  val qualified: relocation.library.Title = relocation.library.Title("qualified")
  def renamed: String =
    import relocation.library.{Title as Label}
    val value: Label = Label("renamed")
    value.value
  def wildcard: String =
    import relocation.library.*
    Title("wildcard").value
  val unchanged = relocation.api.Title("api")
  def shadow: String =
    object Title:
      def apply(s: String): String = s
    Title("shadow")

@main def verifyRelocation(): Unit =
  assert(Client.grouped.value == "grouped")
  assert(Client.qualified.value == "qualified")
  assert(Client.renamed == "renamed")
  assert(Client.wildcard == "wildcard")
  assert(Client.other.value == "unchanged")
  assert(Client.unchanged.value == "api")
  assert(Client.shadow == "shadow")
  assert(relocation.library.SamePackage.value.value == "same-package")
