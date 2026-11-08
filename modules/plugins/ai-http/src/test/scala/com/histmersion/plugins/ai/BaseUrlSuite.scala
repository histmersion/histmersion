package com.histmersion
package plugins.ai

import com.histmersion.testsupport.ComparedByValue.given

/** A provider's base URL and its endpoints. */
class BaseUrlSuite extends munit.FunSuite:

  private def endpoint(base: ProviderSettings.BaseUrl, segments: String*) =
    BaseUrl.parse(base, BaseUrl.Setting("k")).fold(e => fail(e.value), _.endpoint(segments*))

  test("an endpoint's segments follow the base path, a trailing slash or not, a query kept after them"):
    assertEquals(
      endpoint(ProviderSettings.BaseUrl("https://api.anthropic.com"), "v1", "messages"),
      "https://api.anthropic.com/v1/messages"
    )
    assertEquals(
      endpoint(ProviderSettings.BaseUrl("http://127.0.0.1:8080/x/"), "v1", "messages"),
      "http://127.0.0.1:8080/x/v1/messages"
    )
    assertEquals(
      endpoint(ProviderSettings.BaseUrl("https://h.example/openai/v1?api-version=1"), "chat", "completions"),
      "https://h.example/openai/v1/chat/completions?api-version=1"
    )

  test("a base URL that is not one names the setting"):
    assertEquals(
      BaseUrl
        .parse(
          ProviderSettings.BaseUrl("http://127.0.0.1:port/x"),
          BaseUrl.Setting("agents.providers.anthropic.base-url")
        )
        .left
        .toOption,
      Some(
        "agents.providers.anthropic.base-url: Invalid port: port. Is the port a number? Is the path separated with a / " +
          "(directly in the string, not embedded)?"
      )
    )
