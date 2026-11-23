# 0028. A declared proxy's reported client address decides, read from the right and failing closed

- Status: accepted (owner 2026-11-23, queue row 41: "yes, I want a proper fix")
- Date: 2026-11-23

## Context

Queue row 2 (S1c follow-up) made `auth.proxy-networks` the list of networks a reverse proxy connects from. A request
carrying `Forwarded`, `X-Forwarded-For`, `X-Forwarded-Host`, `X-Forwarded-Proto`, `X-Real-IP` or `Via` is forwarded:
from a peer outside those networks it is refused (403 `UndeclaredProxy`), and from a peer inside them it was held to
the authenticated rules, so it always needed `auth.api-key`.

That made HTTPS cost the owner the key on their own devices. `tailscale serve --https=8443 http://127.0.0.1:8000`
connects from loopback and marks every request with the tailnet client's address in `X-Forwarded-For`. Undeclared,
loopback's forwarded requests were refused; declared, each one needed the key, although the device that sent it is in
`100.64.0.0/10`, which the owner trusts when it connects directly. Serving plain HTTP was the only way to keep the key
off their devices.

The standard answer is the trusted-proxy pattern (nginx `set_real_ip_from` with `real_ip_recursive on`, Rails'
trusted proxies): the address a proxy reports is believed only from a proxy the operator declared, and the chain is
read from the end the nearest proxy wrote.

## Decision

**Only from a declared proxy is the report read.** A forwarded request from a peer outside `auth.proxy-networks` is
still refused (403 `UndeclaredProxy`) whatever it says. A request with none of the six headers is judged by its peer,
as before. The peer of a forwarded request must itself still be in a trusted or authenticated network (a proxy
network lets nothing in by itself, row 2), and a refused or unlisted peer keeps its own 403.

**Where the address comes from.** In precedence order: RFC 7239 `Forwarded`, each element's `for=`; else
`X-Forwarded-For`; else `X-Real-IP` (one address in one header line). Several lines of one header are one
comma-separated list, in order (RFC 9110 §5.3). A node may carry a port (`192.0.2.60:5678`, `"[2001:db8::1]:4711"`,
an obfuscated `_port`); the port is dropped. An IPv4-mapped IPv6 address is its IPv4 address, both as a reported node
and as a peer (`Cidr.numbered` normalises `Inet6Address`es the JDK keeps in the mapped form, too). Only literal
addresses are read; no text is ever looked up in DNS. A `Forwarded` header whose elements carry no `for=` at all is
no address source; an element without `for=` within one that has some is a hidden hop.

**Which entry is the client.** The chain is walked from the right, past every address inside `auth.proxy-networks`;
the first address outside them is the client. A chain of declared proxies only names its leftmost entry (a client on
the proxy's own machine). Entries left of the client are never read, so what a client prepends to the header
(`X-Forwarded-For: 100.64.0.1` sent to a proxy that appends) is never reached.

**Every present source must agree.** Precedence alone is spoofable whenever a proxy writes one header and passes
another on untouched: `tailscale serve` sets `X-Forwarded-For` and forwards a client's own `Forwarded`; an nginx that
only sets `X-Real-IP $remote_addr` forwards a client's own `X-Forwarded-For`. So each address source present must
parse and must name the same client as the first, or an address inside `auth.proxy-networks` (nginx's `X-Real-IP`
behind a declared balancer names the balancer, which says nothing of the client). Two sources naming two clients are
`Conflicting`.

**How the address is judged.** Exactly as a direct connection's peer would be: the longest prefix of the trusted,
authenticated and refused networks decides; trusted is trusted with no key; authenticated needs the key (or a run
token on the quota path, or a public read); refused or unlisted is refused (403 `NetworkRefused` /
`NetworkNotAccepted`). `Host`, the origin check, the agent endpoint and `trusted-as` are unchanged.

**Fail closed: an unusable report needs the key.** When the declared proxy reports no usable address — no address
source (only `Via`, `X-Forwarded-Proto` or `-Host`), `unknown` or an obfuscated identifier reached before a client, an
entry that is no address, an empty entry or header, an unterminated quote, `for=` twice in one element, more than 16
hops, or conflicting sources — the request is held to the authenticated rules: the key, never trust. Refusing (403)
was the alternative. The key is chosen because it is exactly what every forwarded request from a declared proxy got
before this decision: no deployment that works today stops working (a proxy that sends only `Via` keeps working with
the key), the outcome never grants trust, and a key holder is admitted from the proxy's own network anyway. An
attacker can only make their own request fall back to the key.

**`X-Forwarded-Proto` and `X-Forwarded-Host`** still only mark a request as forwarded. The server reads neither; this
decision does not widen that.

**The start-up check stays.** `server.public-url` https with trusted loopback and no proxy network covering loopback
still stops the start: an undeclared proxy on loopback either has every request refused (it marks them) or hands
every client loopback's trust (it does not), and declaring loopback is what makes its report read. Its sentence
("what it forwards then needs auth.api-key") is quoted by `docs/user-guide/how-to/access.md` through
`architecture/quoted-sentences.tsv`, which has no update mode, so it is left as is here; access.md says beside it that
a trusted reported client needs no key.

## Consequences

- `tailscale serve` HTTPS on any port in front of a loopback server, with loopback declared and the tailnet trusted,
  gives the owner's devices the owner's trust with no key.
- A reported client in no listed network is now refused, where before it needed the key. A reverse proxy or load
  balancer serving clients from the internet must list their networks (`authenticated-networks = ["0.0.0.0/0",
  "::/0"]` with the key); listing them also admits direct connections from them, so a server only a balancer should
  reach is kept out of others' reach by the network, not by this check. access.md's recipes were changed accordingly.
- The proxy must mark every request it forwards, and must append to (or overwrite) the address header the server
  reads. A declared proxy that passes all three address headers through untouched and adds none lets a client choose
  its own address; nothing in the request can detect that, which is why the owner declares the proxy explicitly.
- A different precedence for a particular proxy (nginx's `real_ip_header`) is not configurable; a setting would be
  added if a proxy needs it.

## Code

- `modules/server/src/main/scala/com/histmersion/util/access/Forwarding.scala`: the six headers, and the three address
  headers' raw values (`Forwarding.of`, read by `AccessGate` through netty's `getAll`).
- `modules/server/src/main/scala/com/histmersion/util/access/ForwardedChain.scala`: parsing `Forwarded`,
  `X-Forwarded-For` and `X-Real-IP` into hops (`node`, the quote-aware `split`), the 16-hop cap (`MaxHops`, `from`) and
  the walk from the right (`client`).
- `modules/server/src/main/scala/com/histmersion/util/access/ClientAddress.scala`: `Direct | Forwarded | Unusable |
  Undeclared`, the precedence and the agreement of the sources (`of`).
- `modules/server/src/main/scala/com/histmersion/util/access/Admission.scala`: `decide` and its `Standing`.
- `modules/server/src/main/scala/com/histmersion/util/access/Cidr.scala`: `numbered` and `sameAddress` (mapped
  addresses).
- Tests: `ForwardedChainSuite`, `ClientAddressSuite`, `AdmissionSuite`, `CidrSuite`, `AuthHttpSuite`.
