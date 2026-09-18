package histdb.web

import com.raquo.laminar.api.L.*
import org.scalajs.dom

object Main:

  def main(args: Array[String]): Unit =
    val app = div(
      cls := "histdb-app",
      AppState.appElement
    )
    renderOnDomContentLoaded(dom.document.getElementById("app"), app)
