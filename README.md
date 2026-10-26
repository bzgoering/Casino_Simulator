# Casino Simulator

A full-stack casino web app with **blackjack**, **slots** and **roulette**, and more games to come. Play money only: nothing touches real currency.

## Tech
- **Backend:** Java 21, Spring Boot 3.4, PostgreSQL 16
- **Frontend:** JavaScript (Vite 5), served by nginx
- **Runs with:** Docker Compose

## How to run
You need **Docker Desktop**. From the project root:
```bash
cp .env.example .env      # then set POSTGRES_PASSWORD and CASINO_JWT_SECRET
docker compose up --build
```
Open **http://localhost:8081**. Stop it with `docker compose down` (add `-v` to also wipe the database).

`CASINO_JWT_SECRET` must be at least 32 characters. You can generate one with `openssl rand -base64 48`.

<details>
<summary>Running without Docker</summary>

You need Java 21+, Node 18+, and PostgreSQL on `localhost:5432` (or run just the database with `docker compose up db`).

```bash
# Backend, on http://localhost:8080
cd backend
export CASINO_JWT_SECRET="a-long-random-string-of-at-least-32-characters"
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend, on http://localhost:5173
cd frontend
npm install
npm run dev
```
</details>

**Tests:** `cd backend && ./mvnw test` and `cd frontend && npm test`

## Features
- **Guest mode:** play right away with $10,000 in chips. Nothing is saved.
- **Accounts (21+):** start with $100, and your balance and history are kept between visits.
- **Real odds, decided on the server:** American double-zero roulette, slots with a 96% return, and blackjack paying 3:2 from an eight-deck shoe.
- **Admin role:** adjust table limits and credit balances. To make an account an admin:
  ```bash
  docker compose exec db psql -U casino -d casino \
    -c "UPDATE user_account SET role = 'ADMIN' WHERE username = 'your_username';"
  ```
