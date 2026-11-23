# Who can reach your server

Histmersion decides who it lets in by **where a request comes from**. Its settings list networks, and
for each one what a request from it gets:

- a **trusted** network acts as you, the owner, with no sign-in. On your own computer this is the
  default: you open `http://localhost:8000` and nothing asks for a password;
- an **authenticated** network must send the server's API key (`auth.api-key`) with every request,
  reads as well as changes;
- a **refused** network is turned away (403), whatever it sends;
- a request from a network that no list names is refused too.

When several listed networks hold an address, the narrowest one (the longest prefix) decides. So
`127.0.0.0/8` can be trusted inside an authenticated or refused `0.0.0.0/0`.

The server listens on every address in `server.host`: a list (`["127.0.0.1", "100.101.102.103"]`), or
one address or several comma-separated as text (`"127.0.0.1"`, `"127.0.0.1,100.101.102.103"`), which
is how `HISTMERSION_BIND` gives it (`HISTMERSION_BIND=127.0.0.1,100.101.102.103`). The bundled app keeps that setting: loopback only,
unless your config file or `HISTMERSION_BIND` lists more.

## Pick one of these setups

### On this computer only (the default)

Nothing to set. The server listens on `127.0.0.1`, and only programs on your computer can reach it.
The browser, the `histmersion` command and your AI assistant all work without a key.

```hocon
histmersion {
  server.host = ["127.0.0.1"]
  auth.trusted-networks = ["127.0.0.0/8", "::1/128"]
}
```

### On this computer and your tailnet (Tailscale)

[Tailscale](https://tailscale.com) gives each of your devices an address in `100.64.0.0/10` (and
`fd7a:115c:a1e0::/48`). Listen on loopback and on this computer's tailnet address only (find it with
`tailscale ip -4`), and trust the tailnet: your phone or laptop reach the server as you do, with no key,
and nothing on the local network you are on can connect.

```hocon
histmersion {
  server.host = ["127.0.0.1", "::1", "100.101.102.103"]     # this computer's tailnet address
  auth {
    trusted-networks = ["127.0.0.0/8", "::1/128", "100.64.0.0/10", "fd7a:115c:a1e0::/48"]
    refused-networks = ["0.0.0.0/0", "::/0"]
    hosts = ["mymac.tail1234.ts.net"]                         # the name you type in the browser
  }
}
```

Why the refused networks: the operating system delivers a packet for an address to any interface
it arrives on, so the server counts a non-loopback address as reachable from its whole address family.
Naming the rest as refused accounts for it, so the server starts with no key; a stray connection that
reaches the tailnet address from elsewhere gets 403.

Trusting the tailnet trusts **every device on it**. If you share your tailnet with others, list only
your own devices' addresses (`100.101.102.103/32`) instead of the whole range.

### Every interface, no key, everyone else refused

The same trust, listening on every interface (`0.0.0.0`): the networks you trust get in and every
other network is refused, so no key is needed. `0.0.0.0` listens for IPv6 as well (Java binds it as
`::`, the wildcard of both families), so the IPv6 networks are named too.

```hocon
histmersion {
  server.host = ["0.0.0.0"]
  auth {
    trusted-networks = ["127.0.0.0/8", "::1/128", "100.64.0.0/10"]
    refused-networks = ["0.0.0.0/0", "::/0"]
  }
}
```

List a wildcard (`0.0.0.0` or `::`) alone: it already listens on every address, so the server does not
start when `server.host` names another address beside it (below). List each address once, too.

### HTTPS on your tailnet with `tailscale serve`

`tailscale serve` gives the server an HTTPS address on your tailnet, with a certificate for this
computer's `ts.net` name, and forwards each request to the server on loopback. Keep the server on
loopback and run (add `--bg` to keep it serving after the command exits):

```sh
tailscale serve --https=8443 http://127.0.0.1:8000
```

Then declare loopback as the proxy and trust the tailnet:

```hocon
histmersion {
  server {
    host = ["127.0.0.1"]
    public-url = "https://mymac.tail1234.ts.net:8443"            # this computer's ts.net name and the port
  }
  auth {
    trusted-networks = ["127.0.0.0/8", "::1/128", "100.64.0.0/10", "fd7a:115c:a1e0::/48"]
    proxy-networks = ["127.0.0.0/8", "::1/128"]                   # tailscale serve connects from loopback
    hosts = ["mymac.tail1234.ts.net"]
  }
}
```

`tailscale serve` marks every request it forwards with `X-Forwarded-For`, the tailnet address of the
device that connected. Because loopback is a declared proxy, the server judges that address, not
loopback's: a device in `100.64.0.0/10` or `fd7a:115c:a1e0::/48` is trusted and needs no key, over
HTTPS. The `histmersion` command and a browser on this computer still reach `http://localhost:8000`
directly, trusted as before. `public-url` names the HTTPS address with its port, so the pages served
there may send changes (the port is part of a page's origin); no key is needed for any of it.

Trusting the tailnet trusts **every device on it** (see the tailnet setup above). The recipe is for
`tailscale serve`, which only your tailnet reaches; Tailscale Funnel puts the same port on the public
internet, so do not turn it on for this port without checking what it reports.

### An institution's or a public server

Trust no network: every request authenticates. Put the server behind a reverse proxy on the same
machine that serves HTTPS, declare the network it connects from (what it forwards is refused otherwise,
see below), and tell the server the name it is reached by:

```hocon
histmersion {
  server {
    host = ["0.0.0.0"]
    public-url = "https://histmersion.example.edu"
  }
  auth {
    api-key = "…"
    trusted-networks = []
    authenticated-networks = ["0.0.0.0/0", "::/0"]
    proxy-networks = ["127.0.0.0/8", "::1/128"]
    hosts = ["histmersion.example.edu"]
  }
}
```

### A reverse proxy on this computer

A proxy on the same machine (Caddy, nginx) connects from `127.0.0.1` (or `::1`). If loopback stays
trusted (so that your own `histmersion` command needs no key), **declare the proxy** by listing loopback
in `auth.proxy-networks`, make it mark every request it forwards with `X-Forwarded-For` (Caddy does by
default; in nginx, `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;`), and list the
networks its clients come from, which are judged as if they connected directly — here everyone, with
the key:

```hocon
histmersion {
  server {
    host = ["127.0.0.1"]
    public-url = "https://histmersion.example.edu"
  }
  auth {
    api-key = "…"
    authenticated-networks = ["0.0.0.0/0", "::/0"]
    proxy-networks = ["127.0.0.0/8", "::1/128"]
    hosts = ["histmersion.example.edu"]
  }
}
```

Without `authenticated-networks`, a client the proxy reports from the internet would be in no listed
network, and refused (403) as a direct connection from it would be.

### Behind a load balancer on another computer

`auth.proxy-networks` names where a proxy connects from, wherever it runs. A load balancer at
`192.168.1.2` in front of a server listening on `192.168.1.5`, its clients anywhere, with the key:

```hocon
histmersion {
  server {
    host = ["192.168.1.5"]
    public-url = "https://histmersion.example.edu"
  }
  auth {
    api-key = "…"
    trusted-networks = []
    authenticated-networks = ["0.0.0.0/0"]
    proxy-networks = ["192.168.1.2/32"]
    hosts = ["histmersion.example.edu"]
  }
}
```

A proxy network lets nothing in by itself: the load balancer's address must also be in
`auth.trusted-networks` or `auth.authenticated-networks`, as here, or its requests are refused like any
other unlisted network's. Listing it in both is no contradiction (a proxy network may overlap any list);
the network lists say what a request gets, the proxy networks only where a forwarded one may come from.
A network listed for the clients the balancer reports admits direct connections from it as well, so
keep `192.168.1.5` out of their reach (a firewall) when only the balancer should connect.

### Behind a reverse proxy

A request that carries any of `Forwarded`, `X-Forwarded-For`, `X-Forwarded-Host`,
`X-Forwarded-Proto`, `X-Real-IP` or `Via` came through a proxy, and its connection's address is the
proxy's, not the client's.

**From a peer in `auth.proxy-networks`** (a declared proxy), the server reads the client's address
from what the proxy reports and judges it **exactly as it would judge a direct connection from that
address**: in a trusted network, it is trusted with no key; in an authenticated one, it needs the key;
in a refused one, or in none, it is refused (403). `Host`, the page a change comes from, and
`trusted-as` are checked as for any request.

- **Where the address comes from.** `Forwarded`'s `for=` (RFC 7239) first, else `X-Forwarded-For`,
  else `X-Real-IP`. Several lines of one header are read as one list, in order. Ports are dropped
  (`192.0.2.60:5678`, `"[2001:db8::1]:4711"`), and an IPv4-mapped address (`::ffff:127.0.0.1`) is its
  IPv4 address.
- **Which entry is the client.** The list is walked from the right — the end the nearest proxy
  wrote — past every address that is itself in `auth.proxy-networks`; the first address that is not
  is the client. Entries further left are never read, so a client cannot choose its own address by
  sending the header itself. A list of proxies only names its leftmost entry: a client on the proxy's
  own computer.
- **Every header must agree.** When more than one of those three headers is present, each must name
  the same client, or a declared proxy. A proxy that writes `X-Forwarded-For` but passes a client's own
  `Forwarded` on untouched (or the other way round) therefore cannot be tricked into vouching for
  another address.
- **Fail closed.** When the proxy reports no usable address — none of the three headers, `for=unknown`
  or an obfuscated `for=_hidden` where the client would be, an entry that is no address, an empty one,
  more than 16 entries, or headers that disagree — the request is held to the rules of an authenticated
  network: it needs the key, and never gets trust. That is what every forwarded request got before the
  proxy's report was read, so a proxy that sends only `Via` or `X-Forwarded-Proto` keeps working with
  the key.

The proxy must mark every request it forwards. A proxy that sends none of these headers would hand
every client loopback's trust, which is why the server will not start in front of one it has not been
told about (below). Alternatively take loopback out of `auth.trusted-networks` and authenticate
everyone. `X-Forwarded-Proto` and `X-Forwarded-Host` mark a request as forwarded and change nothing
else: the server reads neither.

**From any other peer** — every peer while `auth.proxy-networks` is empty, the default — a request that
carries one of these headers is refused (**403**, `UndeclaredProxy`) whatever else it carries, even
`/api/health`: no proxy is supposed to be there, so something unexpected forwarded it, and what it
reports is not believed. List the proxy's network to let its requests in.

Signing in with personal accounts (and with an institution's single sign-on) is planned; until then
everyone who authenticates uses the one API key.

## The server refuses an open setup

The server does not start when an address it listens on can be reached from a network that is neither
trusted, authenticated with a key, nor refused. Each address in `server.host` is checked on its own,
and every open one is named. For example, `server.host = ["0.0.0.0"]` with only loopback trusted stops
with:

```text
Histmersion does not start: server.host lists '0.0.0.0', which can be reached from 0.0.0.0/0; none of
auth.trusted-networks, auth.authenticated-networks and auth.refused-networks names that network — bind to
127.0.0.1 only (HISTMERSION_BIND), or list the network as trusted, as authenticated (with auth.api-key
set) or as refused
```

Loopback (`127.0.0.1`, `::1`, `localhost`) is reached from loopback only. Any other address of one
family (`192.168.1.5`, `100.101.102.103`) is reached from that whole family; `0.0.0.0`, `::` and a
host name from both IPv4 and IPv6 (Java binds `0.0.0.0` as `::`, which takes IPv4 and IPv6 alike).

It does not start either when `server.host` lists a wildcard (`0.0.0.0` or `::`) beside another
address, `127.0.0.1` or `::1` included. The wildcard already listens on every address of the port, so
the second listener would fail to bind (Linux) or quietly take that address's connections (macOS):

```text
Histmersion does not start: server.host lists '0.0.0.0' and '127.0.0.1' on one port, but '0.0.0.0'
already listens on every address of this computer — list '0.0.0.0' alone, or only specific addresses
(HISTMERSION_BIND)
```

Nor when `server.host` lists one address twice, however it is written (`::1` and `[::1]`,
`127.0.0.1` and `::ffff:127.0.0.1`, a name in two cases): the second listener cannot bind it.

```text
Histmersion does not start: server.host lists the address '::1' twice (as '::1' and as '[::1]'), and a
second listener cannot bind an address the first already holds — list each address once
(HISTMERSION_BIND)
```

It also does not start when `server.public-url` is `https`, loopback is trusted, and no proxy network
covers loopback: HTTPS means a proxy serves the page, and it may well connect from loopback. Undeclared,
such a proxy's requests would either be refused (it marks them) or all be trusted as loopback (it does
not); declared, the address each one reports decides. A proxy network elsewhere (a load balancer) does
not count: take loopback out of the trusted networks then. The setups above that serve HTTPS from this
computer (`tailscale serve`, a reverse proxy) declare loopback, so they start.

```text
Histmersion does not start: server.public-url = 'https://histmersion.example.edu' is https, so a reverse
proxy serves it, and auth.trusted-networks trusts loopback, where a proxy on this machine connects from —
declare the proxy by listing loopback in auth.proxy-networks (what it forwards then needs auth.api-key),
or take loopback out of auth.trusted-networks
```

What a declared proxy forwards needs `auth.api-key` unless the client address it reports is trusted
(see *Behind a reverse proxy*).

A network named in two lists (the same CIDR as trusted and refused, say) stops the start as well.

## What protects a trusted computer

Because trust comes from the address, a web page you visit could try to use it: it can send requests
to `localhost` from your own browser. Two checks stop that, on every setup:

- **The host name.** A request must name the server by one of its own names: `localhost`,
  `127.0.0.1`, `[::1]`, an address in `server.host`, the host of `server.public-url`, or one listed
  in `auth.hosts`. Any other name is answered **421**. A page whose own name was pointed at your
  computer (DNS rebinding) is turned away here.
- **The page a change comes from.** A change (`POST`, `PUT`, `PATCH`, `DELETE`) sent by a browser page
  must come from the server's own page — one of those names, on the server's port — or from an origin
  listed in `auth.origins`. Any other page's change is answered **403**. A program that is no browser
  page (the `histmersion` command, an MCP client) sends no `Origin` or `Referer` and passes: the
  server sets no cookie that another page could ride on.

A read (`GET`, `HEAD`) from another site's page is not checked: the browser does not let that page see
the answer, and a read changes nothing. A check over the compiled routes holds every `GET` and `HEAD`
endpoint to that; the few that still change something (caches, markers, lazily created rows) are listed
for the developers in `architecture/get-writes.tsv`.

## What answers without a key

From an authenticated network, without the key, the server answers only:

- `GET /api/health` and `GET /api/version`;
- the web page itself (`/`, `/explore`, `/library…`, …) and its files under `/web/`, so the page can
  ask you for the key.

Everything else — the sources, the settings, the backups, the agents' state and transcripts — answers
**401** until the request carries `Authorization: Bearer <key>`. A refused network gets **403** even for
these, and so does a request a proxy forwarded from a peer in no proxy network. A request a declared
proxy forwarded is answered as its reported client's network says.

## Sending the key

- **The web page** asks for the key once and keeps it in the browser's storage; it is sent with every
  request, never in a link. Opened from a trusted network it never asks.
- **The `histmersion` command** reads it from `HISTMERSION_TOKEN`, or else from the first line of the
  file `cli.token-file` names (keep that file readable by you alone). There is no command-line option:
  another user of the computer could read it in the process list.
- **An MCP client** sends it as a header, for example in `.mcp.json`:
  `{"type": "http", "url": "https://histmersion.example.edu/mcp", "headers": {"Authorization": "Bearer …"}}`.

The addresses, the networks, `proxy-networks`, `trusted-as`, `hosts` and `origins` are read when the server
starts; the key itself is read again by `histmersion config reload`.
