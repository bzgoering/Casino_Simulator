# Casino Simulator

A full-stack casino simulation: **Java 21 / Spring Boot** backend, **vanilla JavaScript** frontend,
**PostgreSQL** for accounts, all containerised for **Docker Desktop**.

Three games, all decided on the server: **blackjack** (8-deck shoe), **slots** (96% RTP) and
**roulette** (European single zero).

> Play money only. Nothing here handles real currency or real payments.

---

## Quick start

```bash
cp .env.example .env      # then edit it: set a database password and a 32+ byte JWT secret
docker compose up --build
```

Open <http://localhost:8081>.

The API is also published on <http://localhost:8080> for direct inspection
(`curl localhost:8080/api/config`).

### Running without Docker

```bash
# Backend (needs PostgreSQL on localhost:5432, or run just the db service from compose)
cd backend
export CASINO_JWT_SECRET="a-long-random-string-of-at-least-32-bytes"
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend
cd frontend
npm install
npm run dev        # http://localhost:5173, proxies /api to :8080
```

---

## The three kinds of user

| | Guest | Player | Admin |
|---|---|---|---|
| Sign-in required | no | yes | yes |
| Starting balance | **$10,000** | **$100** | $100 |
| Stored in the database | **never** | yes | yes |
| Gets a UID | session id | UUID at sign-up | UUID at sign-up |
| History kept | no | yes | yes |
| Can credit balances | no | no | **yes** |

**Guests leave no trace.** This is enforced by architecture rather than policy: a guest balance
lives in an in-memory session with a 2-hour idle TTL and never touches SQL. The trade-off is that
guest balances reset when the backend restarts, which is the intended reading of "no data will be
kept on them".

A guest balance is deliberately *not* carried in the token. A signed token holding a balance is
still replayable: a player could keep a copy from before they lost and re-present it. The token
carries identity only; the balance always comes from the server.

### Creating an admin

There is intentionally no endpoint that grants ADMIN, because one would be the most attractive
target in the system. Promote an existing account directly:

```bash
docker compose exec db psql -U casino -d casino \
  -c "UPDATE user_account SET role = 'ADMIN' WHERE username = 'your_username';"
```

Sign out and back in to pick up the new role.

---

## The games

### Blackjack

Eight decks in a shoe, shuffled with an unbiased Fisher-Yates pass and reshuffled only when the
cut card at 75% penetration is reached, between rounds and never mid-hand, exactly as a real shoe
behaves.

- Dealer stands on all 17s (configurable to H17)
- Blackjack pays 3:2
- Double on any first two cards; double after split allowed
- Split up to 3 times (4 hands); split aces get one card each and stand
- 21 after a split is an ordinary 21, not a 3:2 natural
- Dealer peeks for a natural behind an ace or a ten

The hole card is genuinely absent from the JSON until the reveal, not merely hidden in the UI.
`GameApiTest` asserts this across many hands.

*Not implemented:* insurance, surrender, and even-money on a natural.

### Slots

Three reels, one payline, 32 stops per reel. That gives exactly 32,768 equally likely
combinations, so the return to player is a computed figure rather than an estimate:

| | |
|---|---|
| **Return to player** | **96.005%** (house edge 3.995%) |
| **Hit frequency** | 22.4% |
| Top jackpot | 200x (three sevens, 1 in 32,768) |

`SlotPaytableTest` enumerates the entire outcome space and asserts the RTP, so changing a reel
strip or a payout without recalculating fails the build. `/api/config` computes the same figure
at request time, so the advertised paytable cannot drift from what the machine actually pays.

### Roulette

A European single-zero wheel: 37 pockets in the real physical sequence
(`0, 32, 15, 19, 4, 21, ...`), correct colours, and all ten standard bet types. 37 pockets against
payouts priced for 36 gives the uniform **2.7027%** house edge on every bet, which the tests
assert bet type by bet type.

**On "real physics":** the winning pocket is drawn uniformly from a CSPRNG rather than simulated
from ball and rotor dynamics. This is the more faithful choice, not a shortcut. Real wheels are
painstakingly balanced precisely so that outcomes are uniform, and casinos track bias and retire
wheels that develop any. A simulation detailed enough to be interesting would also be
*predictable* from its inputs, which is strictly worse than true randomness. What the wheel does
reproduce is the real geometry: authentic pocket order, colours, payouts and edge. The browser
then animates the wheel to the pocket the server already chose.

Inside bets are validated against the actual betting cloth. A "split" must name two genuinely
adjacent numbers; without that check a client could claim a two-number split covering thirty
numbers and collect 17:1 on it.

---

## Architecture

```
Casino_Simulator/
├── backend/                     Spring Boot, layered
│   └── src/main/java/com/casino/
│       ├── game/                pure engines - no Spring, no database
│       │   ├── common/          Card, Shoe, RandomSource, Money
│       │   ├── blackjack/       round state machine
│       │   ├── slots/           reel strips and paytable
│       │   └── roulette/        wheel, layout geometry, bet validation
│       ├── domain/              JPA entities
│       ├── repository/          Spring Data
│       ├── service/             wallet, auth, guest sessions, game orchestration
│       ├── security/            JWT, filters, rate limiting
│       ├── config/              security rules, properties, beans
│       └── web/                 controllers, DTOs, error handling
└── frontend/                    Vite + vanilla JS
    └── src/
        ├── api/client.js        the only place that talks to the API
        ├── lib/                 money, cards, roulette geometry, DOM helpers
        ├── games/               one module per game view
        └── state/store.js       small observable store
```

The game engines are plain Java objects with no framework annotations and a `RandomSource`
injected. That is what makes a rule like "a natural pays 3:2" testable against a scripted deck
instead of hoping the right hand eventually turns up.

### Money

Every amount is `BigDecimal` at 2 decimal places, `NUMERIC(19,2)` in the database. Never a
`double`: `0.1 + 0.2 != 0.3` in binary floating point, and a ledger that drifts a cent per round
is a real defect. `MoneyTest` demonstrates the drift the `double` version would have.

Balances are written only through `WalletService`. Registered accounts are loaded
`SELECT ... FOR UPDATE` so two simultaneous bets serialise at the database rather than racing;
otherwise a player could fire concurrent requests and spend the same balance twice.

Every movement is written to an append-only `ledger_entry`. `AdminApiTest` asserts that the
ledger sums to the account balance.

---

## Security

| Concern | How it is handled |
|---|---|
| Passwords | BCrypt cost 12. Over-72-byte passwords are rejected rather than silently truncated to a weaker secret |
| Account enumeration | Identical response for a wrong password and an unknown user; a hash is verified either way so timing does not disclose which |
| Brute force | Per-account lockout after 5 failures, plus a per-address rate limit on the auth endpoints |
| Tokens | HS256 JWT, identity only, no balance. Secret from `CASINO_JWT_SECRET`; startup fails without it outside the dev profile |
| Authorisation | Default-deny URL rules plus `@PreAuthorize` on the admin service, two independent layers |
| Game integrity | Every outcome server-side. The client submits a stake and a decision, never a result |
| Bet tampering | Roulette bets revalidated against the real layout; blackjack actions checked against the server's own legal-move list |
| Injection | JPA parameter binding throughout; no string-concatenated SQL |
| XSS | The UI sets text via `textContent` only, never `innerHTML`, under a strict CSP |
| CSRF | Not applicable and disabled deliberately: no cookies or ambient credentials, so a cross-site request cannot authenticate |
| Error leakage | Expected errors carry safe messages; everything else is logged server-side and answered generically |
| Privilege audit | Every admin credit written to an immutable `admin_audit` row with actor, target and source address |
| Containers | Backend runs as a non-root user; the database is not published to the host |

**Known limits.** The rate limiter and guest sessions are per-instance and in-memory, so multiple
replicas would each enforce their own limits; move both to Redis before scaling out. The token is
held in `sessionStorage`, which any script on the page could read, so the CSP and the
`textContent` discipline are the actual XSS defences. There is no token revocation list, so a
stolen token stays valid until it expires.

---

## Tests

```bash
cd backend  && ./mvnw test      # 136 tests
cd frontend && npm test         # 56 tests
```

**Backend (136)** covers engine rules against scripted decks (naturals, splits, split aces,
doubling, dealer draw rules, bust handling), exhaustive RTP and house-edge verification, and
full HTTP-level integration tests on H2 spanning auth, the games, the authorisation boundary and
the admin flows.

**Frontend (56)** covers money formatting and bet validation, card parsing, the client-side
roulette geometry including the forged-bet cases the server also rejects, and the API client's
session, error and network handling against a mocked `fetch`.

Two real bugs were found and fixed by these tests during development:

1. After a split, the *original* hand kept `fromSplit = false`, so a two-card 21 on it reported
   as a natural and it escaped the double-after-split rule.
2. A malformed enum in a request body produced a 500 instead of a 400, which both misreported the
   error and logged at ERROR level, letting bad input flood the logs.

---

## API

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/signup` | public | Register; returns a token and $100 |
| POST | `/api/auth/login` | public | Sign in |
| POST | `/api/auth/guest` | public | Start a guest session with $10,000 |
| GET | `/api/config` | public | Limits, paytables, wheel layout |
| GET | `/api/me` | any | Current identity and balance |
| GET | `/api/me/history` | any | Own ledger (empty for guests) |
| POST | `/api/games/blackjack/deal` | any | Start a hand |
| POST | `/api/games/blackjack/action` | any | HIT / STAND / DOUBLE / SPLIT |
| GET | `/api/games/blackjack/current` | any | Reconnect to a hand in progress |
| POST | `/api/games/slots/spin` | any | Spin the reels |
| POST | `/api/games/roulette/spin` | any | Spin against every chip placed |
| POST | `/api/admin/credit` | admin | Credit any account or guest session by UID |
| POST | `/api/admin/credit/self` | admin | Credit your own balance |
| GET | `/api/admin/audit` | admin | Privileged-action audit log |

"any" means any authenticated caller, guests included, since guests hold a real token too.

Example:

```bash
TOKEN=$(curl -sX POST localhost:8080/api/auth/guest | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
curl -sX POST localhost:8080/api/games/slots/spin \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"bet": 5.00}'
```

---

## Configuration

Set through the environment; see `.env.example`.

| Variable | Default | Meaning |
|---|---|---|
| `CASINO_JWT_SECRET` | *(none)* | HS256 key, 32+ bytes. **Required** outside the dev profile |
| `POSTGRES_PASSWORD` | *(none)* | Database password. Required |
| `SPRING_PROFILES_ACTIVE` | `prod` | `dev` adds verbose logging and an ephemeral JWT key |
| `CASINO_SECURITY_ALLOWED_ORIGINS` | localhost:5173, localhost:8081 | CORS allow-list |

Table limits, the guest TTL and the lockout policy live under `casino:` in
`backend/src/main/resources/application.yml`.
