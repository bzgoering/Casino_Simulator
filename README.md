# Casino Simulator

A fun casino web app with three games: **blackjack**, **slots** and **roulette**.

You can play either way:

- **Signed out**, as a guest — hit *Play as guest* and you get **$10,000** in chips to mess about
  with straight away. Nothing about a guest is saved: no account, no history, and the chips reset
  when the session ends.
- **Signed in**, with an account — sign up and you start with **$100**, your balance and full
  history are kept between visits, and you can manage the account from the page behind your
  username.

Signing up asks you to confirm your age first: an account is for **21 and over**, and anyone
younger is seated as a guest instead of being turned away. The date of birth is checked in the
browser and discarded on the spot — it is never sent to the server and never stored, so the only
thing that outlives the question is which of the two doors above you came through.

> **Play money only.** Nothing here touches real currency or real payments. There is no cashier:
> the deposit and withdraw buttons on the account page are deliberately dead, and are there only
> to show where they would sit in a real product.

Every outcome is decided on the server from a cryptographic random source, and the odds are the
real published ones — single-zero roulette at a 2.7% house edge, a 96% return on the slots, and
blackjack paying 3:2 off an eight-deck shoe.

---

## Running it

You need **Docker Desktop**. From the project root:

```bash
cp .env.example .env      # then edit it, see below
docker compose up --build
```

Then open **<http://localhost:8081>**.

Before the first run, open `.env` and set two values:

| Variable | What to put |
|---|---|
| `POSTGRES_PASSWORD` | any password you like — it is only used between the containers |
| `CASINO_JWT_SECRET` | a random string of **at least 32 characters** |

Neither has a working default; the backend refuses to start without a JWT secret. To generate one:

```bash
openssl rand -base64 48
# or, on Windows PowerShell:
powershell -c "[Convert]::ToBase64String((1..48|%{Get-Random -Max 256}))"
```

To stop it, `docker compose down`. Add `-v` to that only if you also want to wipe the database and
start over with no accounts.

The API is published separately on <http://localhost:8080> if you want to poke at it directly:

```bash
curl localhost:8080/api/config
```

### Making an account an admin

Admins can adjust the table limits and credit balances. There is deliberately no button that
grants admin, so promote an existing account from the database:

```bash
docker compose exec db psql -U casino -d casino \
  -c "UPDATE user_account SET role = 'ADMIN' WHERE username = 'your_username';"
```

Sign out and back in to pick up the new role.

---

## Running without Docker

You will need Java 21+, Node 18+, and PostgreSQL on `localhost:5432` (or just start the database
on its own with `docker compose up db`).

```bash
# Backend, on http://localhost:8080
cd backend
export CASINO_JWT_SECRET="a-long-random-string-of-at-least-32-characters"
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend, on http://localhost:5173 (proxies /api to the backend)
cd frontend
npm install
npm run dev
```

---

## Tests

```bash
cd backend  && ./mvnw test      # 180 tests
cd frontend && npm test         # 82 tests
```
