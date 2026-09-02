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
| Can set table limits | no | no | **yes** (per table game) |

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

An admin can then credit balances and set the table limits from the Admin tab.

---

## The games

### Blackjack

Eight decks in a shoe, shuffled with an unbiased Fisher-Yates pass and reshuffled only when the
cut card at 75% penetration is reached, between rounds and never mid-hand, exactly as a real shoe
behaves.

- Dealer stands on all 17s (configurable to H17)
- Blackjack pays 3:2
- Double on any first two cards; double after split allowed
- Split up to 3 times per box; split aces get one card each and stand
- 21 after a split is an ordinary 21, not a 3:2 natural
- Dealer peeks for a natural behind an ace or a ten
- Up to **4 boxes** in one round, each carrying the same bet

Playing several boxes deals as a live table does: one card to every box, the dealer upcard, a
second card to every box, then the hole card. Dealing each box out in turn would consume the shoe
in a different order and quietly change the game. The whole commitment is debited before a card
is dealt, so a player who can cover three of four boxes is dealt nothing rather than a partly
funded round, and a natural on one box finishes only that box while the rest play on.

The hole card is genuinely absent from the JSON until the reveal, not merely hidden in the UI.
`GameApiTest` asserts this across many hands.

*Not implemented:* insurance, surrender, and even-money on a natural.

### Slots

Three reels showing three rows, 32 stops per reel, five paylines: the three straight-across rows
and both diagonals. The window on each reel is the stop that landed with its neighbour either
side, and the strip is a loop, so the window wraps at the ends exactly as the physical reel does.

**A slot machine is not a table game**, and it is deliberately not covered by the admin-managed
table limits. There is no house minimum: the player dials in whatever denomination they like,
down to a cent, and buys a fixed number of credits. Each credit lights one more payline, in the
order a real cabinet lights them:

| Credits | Lines lit |
|---|---|
| 1 | centre row |
| 3 | all three rows |
| 5 | the rows and both diagonals |

The bet is charged per lit line, so five credits at $0.25 costs $1.25 and each line is scored on
its own. Only lit lines pay, but the **whole window is returned** and drawn, so a near miss on a
diagonal that was not bought is visible exactly as it would be on real glass. The only bound is a
per-spin ceiling in `application.yml`, which is not reachable from the admin console.

| | |
|---|---|
| **Return to player** | **96.005% per line** (house edge 3.995%) |
| **Hit frequency** | 22.4% per line |
| Top jackpot | 200x (three sevens, 1 in 32,768) |

**Lines do not change the odds.** Every payline reads one symbol per reel, each a uniform draw
over the same strip and independent across reels, so every line has exactly the distribution the
old single-line machine had. Expectation adds, so the return is 96.005% whether the player lights
one line or five: more lines buy more chances at the same price per chance, not a better or worse
machine. `SlotPaytableTest` proves this the hard way, enumerating all 32,768 stop combinations
**for each of the five paylines** and asserting every one returns the identical figure. A payline
defined to read two rows off one reel would break that equality and fail the build.

`/api/config` computes the same RTP at request time, so the advertised paytable cannot drift from
what the machine actually pays.

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
stolen token stays valid until it expires — and for the same reason, changing a password does not
end sessions already in progress.

---

## Tests

```bash
cd backend  && ./mvnw test      # 180 tests
cd frontend && npm test         # 82 tests
```

**Backend (180)** covers engine rules against scripted decks (naturals, splits, split aces,
doubling, dealer draw rules, bust handling, multi-box dealing order and settlement), exhaustive
per-payline RTP and house-edge verification, and full HTTP-level integration tests on H2 spanning auth, the
games, the authorisation boundary and the admin flows including limit changes, and self-service password changes and
account deletion.

**Frontend (82)** covers money formatting and bet validation, card parsing, the client-side
roulette geometry including the forged-bet cases the server also rejects, the rendered roulette cloth and slot
machine under jsdom, and the API client's session, error and network handling against a mocked
`fetch`.

Two real bugs were found and fixed by these tests during development:

1. After a split, the *original* hand kept `fromSplit = false`, so a two-card 21 on it reported
   as a natural and it escaped the double-after-split rule.
2. A malformed enum in a request body produced a 500 instead of a 400, which both misreported the
   error and logged at ERROR level, letting bad input flood the logs.
3. Every "2 to 1" box on the roulette cloth was built from the same column selection, so clicking
   one highlighted all twelve and only column 1 was ever really bettable. The layout functions
   were correct throughout; the fault was in how the cloth was drawn, which is why the cloth is
   now tested through the DOM and not only through its geometry.
4. The CORS allow-list held only `http://localhost:8081`, and a browser sends an `Origin` header
   even on a same-origin POST. Anyone who reached the site as `127.0.0.1:8081` therefore had
   every request answered `403 Invalid CORS request`, which surfaced in the UI as the useless
   "Something went wrong." Both spellings of the loopback address are now allowed, and an error
   with no JSON body of its own no longer collapses into the generic message.

---

## API

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/api/auth/signup` | public | Register; returns a token and $100 |
| POST | `/api/auth/login` | public | Sign in |
| POST | `/api/auth/guest` | public | Start a guest session with $10,000 |
| GET | `/api/config` | public | Limits, paytables, wheel layout |
| GET | `/api/me` | any | Current identity and balance |
| GET | `/api/me/history` | any | Own ledger plus lifetime play totals (empty for guests) |
| POST | `/api/me/password` | signed in | Change your own password |
| POST | `/api/me/delete` | any | Close your own account, or end a guest session |
| POST | `/api/games/blackjack/deal` | any | Start a round on 1-4 boxes |
| POST | `/api/games/blackjack/action` | any | HIT / STAND / DOUBLE / SPLIT |
| GET | `/api/games/blackjack/current` | any | Reconnect to a hand in progress |
| POST | `/api/games/slots/spin` | any | Spin the reels with `credits` lines lit |
| POST | `/api/games/roulette/spin` | any | Spin against every chip placed |
| POST | `/api/admin/credit` | admin | Credit any account or guest session by UID |
| POST | `/api/admin/credit/self` | admin | Credit your own balance |
| GET | `/api/admin/limits` | admin | Every game's minimum and maximum bet |
| POST | `/api/admin/limits` | admin | Set one game's minimum and maximum bet |
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

## The account page

Clicking your own name in the top bar opens it. It shows your UID (for a guest, the session id,
which is the same thing an admin credits against), your username, your account type and your
balance, and offers three things.

**Changing your password** requires the current one as well as the new one: a bearer token on its
own must not be enough to take an account over.

`PasswordPolicy` is the only thing that decides whether a password is acceptable, and both
sign-up and a later change call it. That is not just tidiness. The rules had also been restated
as bean-validation annotations on the request records, and an annotation fires *before* the
service does, so the copy on the DTO was the one players actually saw — the same mistake got a
different answer depending on which endpoint you made it on:

```
signup  ->  400 "Some fields need attention."  {"password": "must be between 10 and 72 ..."}
change  ->  400 "Password must be at least 10 characters."
```

The request records now check shape only (`@NotBlank`), the policy owns every rule, and the two
endpoints answer identically. The minimum length is published on `/api/config` as
`passwordMinLength`, so the sign-up and change-password forms state the rule from that one figure
instead of hardcoding it in the markup.

**The cashier does nothing, and only members see it.** Deposit and Withdraw are rendered disabled,
with a line saying so. This is a play-money simulation with no real payments anywhere in it; the
controls are there to show where a cashier would sit, and are inert rather than merely unwired, so
nobody can click one and be left wondering whether something happened. The whole section is hidden
from guests, who have no account to move money to or from, and from admins, who credit balances
from the admin console instead.

**Deleting an account** takes the ledger with it. For a guest there is nothing to delete: the
in-memory session is dropped, and since guests were never written to the database that really is
the end of them. For a registered account the password is required again, the ledger rows are
removed explicitly and then the account row. The admin audit trail deliberately survives, because
it records what an *administrator* did and has to outlive the account it was done to.

The last remaining admin cannot delete themselves. That is not paternalism about the account: it
is the only thing standing between a mistyped click and a deployment nobody can administer again.
An admin may leave once another admin exists.

> The ledger is deleted explicitly rather than left to the foreign key's `ON DELETE CASCADE`. The
> entity maps `user_id` as a plain column rather than a relation, so nothing in the application
> layer guarantees that cascade exists on whatever schema it is pointed at — and it does not exist
> on the H2 schema the tests build. Closing an account now means the same thing everywhere.

---

## Configuration

Set through the environment; see `.env.example`.

| Variable | Default | Meaning |
|---|---|---|
| `CASINO_JWT_SECRET` | *(none)* | HS256 key, 32+ bytes. **Required** outside the dev profile |
| `POSTGRES_PASSWORD` | *(none)* | Database password. Required |
| `SPRING_PROFILES_ACTIVE` | `prod` | `dev` adds verbose logging and an ephemeral JWT key |
| `CASINO_SECURITY_ALLOWED_ORIGINS` | localhost + 127.0.0.1 on :8081 | CORS allow-list. A browser sends `Origin` even same-origin, so every hostname the site is reached by must be listed |

The guest TTL, the lockout policy and the opening table limits live under `casino:` in
`backend/src/main/resources/application.yml`.

The minimum and maximum bet are adjustable at runtime from the admin console **per table game**,
and persisted to a `game_limits` row per game so a change survives a restart. Blackjack and
roulette are different products with different economics, and one house-wide pair forced the same
floor on both; each is now set on its own and binds only on that game. A game with no stored row
falls back to the configured values, so a fresh database needs no seed step.

Slots are absent from that console on purpose: a machine is not a table game, has no house
minimum, and carries its own per-spin ceiling under `casino.slots` instead. `POST
/api/admin/limits` rejects `SLOTS` outright rather than accepting a setting nothing would read.

How high any maximum may go is fixed by `casino.limits.max-configurable-bet` and is deliberately
*not* reachable from the console: an admin account should not be able to open the tables to
arbitrary stakes without a deploy. Every change is written to `admin_audit` alongside the
credits.
