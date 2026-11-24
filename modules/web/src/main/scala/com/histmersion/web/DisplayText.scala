package com.histmersion
package web

import com.raquo.laminar.keys.{HtmlAttr, HtmlProp}
import com.raquo.laminar.modifiers.{KeySetter, RenderableText}
import com.raquo.laminar.nodes.ReactiveHtmlElement
import org.scalajs.dom

given displayedText: [A] => (display: Display[A]) => RenderableText[A] = RenderableText(display.text)

extension (attr: HtmlAttr[String])
  def :=[A](shown: A)(using
      display: Display[A]
  ): KeySetter[HtmlAttr[String], String, ReactiveHtmlElement[dom.HTMLElement]] =
    attr := display.text(shown)

extension [DomV](prop: HtmlProp[String, DomV])
  def :=[A](shown: A)(using
      display: Display[A]
  ): KeySetter[HtmlProp[String, DomV], String, ReactiveHtmlElement[dom.HTMLElement]] =
    prop := display.text(shown)
