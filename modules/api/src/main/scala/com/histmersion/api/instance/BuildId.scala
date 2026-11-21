package com.histmersion
package api.instance

import com.histmersion.api.Html
import io.github.iltotore.iron.constraint.any.Pure

/** The build of the web bundle the server ships (G109): the first 32 hex digits of the linked bundle's SHA-256, written
  * by the build into the bundle and beside it. The server sends it in [[UiConfig]], in the bundle's URL and in the
  * [[BuildId.Header]] of every answer; the page compares it with its own and asks for a reload when they differ. Empty
  * from a server without a built bundle (or an older one): nothing to compare.
  */
type BuildId = BuildId.T

object BuildId extends Newtype[String, Pure]:
  /** How long an id is: the first 32 hex digits of the bundle's SHA-256. */
  private val Length = 32

  /** The response header that carries the server's build on every answer, so an open page notices an upgrade at its
    * next request, not only when it loads.
    */
  val Header: String = "X-Histmersion-Build"

  /** How a page's build compares with the server's. */
  enum Verdict derives CanEqual:
    /** Both name the same build. */
    case Same

    /** One side names none (a development bundle, a server without a built bundle or an older one). */
    case Unknown

    /** The server runs another build than the page's. */
    case Updated

  /** 32 lower-case hex digits: an id as the build writes it. */
  private def written(text: String): Boolean =
    text.length == Length && text.forall(c => c >= '0' && c <= '9' || c >= 'a' && c <= 'f')

  /** The build a text names (an answer's [[Header]]): none when it is no id as the build writes it. */
  def parse(text: String): Option[BuildId] = Option.when(written(text))(BuildId(text))

  extension (id: BuildId)
    /** An id as the build writes it. Empty, or the page's placeholder, is none. */
    def known: Boolean = written(id.value)

    /** This page's build against the server's. */
    def against(server: BuildId): Verdict =
      if !id.known || !server.known then Verdict.Unknown
      else if id == server then Verdict.Same
      else Verdict.Updated

    /** The id as it goes into an address or a header, percent-encoded (an id as the build writes it is unchanged). */
    def encoded: String = Html.Atoms.segment(id.value)
