package com.histmersion
package web

import com.histmersion.api.Html
import org.scalajs.dom

/** The page's only ways to put markup into the document. Each takes an [[Html]], which only escaping, the code's own
  * literal markup or the allow-list makes, so no text from outside (a title, a credit, a fetched page) reaches a DOM
  * sink as markup. `ContractsSuite` rule `html-sink` keeps every other `innerHTML`, `insertAdjacentHTML` and `setHTML`
  * out of the page; a map popup gets its content as a fragment built here ([[fragment]]).
  */
object HtmlSink:
  /** `el`'s content replaced by `html`. */
  def replace(el: dom.Element, html: Html): Unit = el.innerHTML = html.markup

  /** `html` added at the end of `el`'s content. */
  def append(el: dom.Element, html: Html): Unit = el.insertAdjacentHTML("beforeend", html.markup)

  /** `html` as a document fragment (parsed inert, in a `<template>`), for an API that takes nodes. */
  def fragment(html: Html): dom.DocumentFragment =
    val t = dom.document.createElement("template").asInstanceOf[dom.HTMLTemplateElement]
    t.innerHTML = html.markup
    t.content
