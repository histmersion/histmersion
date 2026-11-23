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

### Behind a reverse proxy on this computer

A proxy on the same machine connects from `127.0.0.1` (or `::1`). If loopback stays trusted (so that
your own `histmersion` command needs no key), **declare the proxy** by listing loopback in
`auth.proxy-networks`, and make it mark every request it forwards with `X-Forwarded-For` (Caddy does by
default; in nginx, `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;`):

```hocon
histmersion {
  server {
    host = ["127.0.0.1"]
    public-url = "https://histmersion.example.edu"
  }
  auth {
    api-key = "…"
    proxy-networks = ["127.0.0.0/8", "::1/128"]
    hosts = ["histmersion.example.edu"]
  }
}
```

### Behind a load balancer on another computer

`auth.proxy-networks` names where a proxy connects from, wherever it runs. A load balancer at
`192.168.1.2` in front of a server listening on `192.168.1.5`:

```hocon
histmersion {
  server {
    host = ["192.168.1.5"]
    public-url = "https://histmersion.example.edu"
  }
  auth {
    api-key = "…"
    trusted-networks = []
    authenticated-networks = ["192.168.1.2/32"]
    refused-networks = ["0.0.0.0/0"]
    proxy-networks = ["192.168.1.2/32"]
    hosts = ["histmersion.example.edu"]
  }
}
```

A proxy network lets nothing in by itself: the load balancer's address must also be in
`auth.trusted-networks` or `auth.authenticated-networks`, as here, or its requests are refused like any
other unlisted network's. Listing it in both is no contradiction (a proxy network may overlap any list);
the network lists say what a request gets, the proxy networks only where a forwarded one may come from.

### What counts as forwarded

A request that carries any of `Forwarded`, `X-Forwarded-For`, `X-Forwarded-Host`,
`X-Forwarded-Proto`, `X-Real-IP` or `Via` came through a proxy, and its address is the proxy's, not
the client's. From a peer in `auth.proxy-networks`, the server never trusts such a request, whatever its
address: it is held to the rules of an authenticated network instead, so it needs the key. A proxy that
sends none of these headers would hand every client your trust, which is why the server will not start
in front of one it has not been told about (below). Alternatively take loopback out of
`auth.trusted-networks` and authenticate everyone.

From any other peer — every peer while `auth.proxy-networks` is empty, the default — a request that
carries one of these headers is refused (**403**) whatever else it carries, even `/api/health`: no proxy
is supposed to be there, so something unexpected forwarded it. List the proxy's network to let its
requests in with the key.

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
covers loopback: HTTPS means a proxy serves the page, and it may well connect from loopback. A proxy
network elsewhere (a load balancer) does not count: take loopback out of the trusted networks then.

```text
Histmersion does not start: server.public-url = 'https://histmersion.example.edu' is https, so a reverse
proxy serves it, and auth.trusted-networks trusts loopback, where a proxy on this machine connects from —
declare the proxy by listing loopback in auth.proxy-networks (what it forwards then needs auth.api-key),
or take loopback out of auth.trusted-networks
```

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
these, and so does a request a proxy forwarded from a peer in no proxy network.

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
