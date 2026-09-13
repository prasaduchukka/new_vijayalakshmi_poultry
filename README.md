# Vijayalakshmi Poultry Farm — Management System

A simple, practical digitization of the farm's paper ledger: customers, suppliers,
orders, deliveries, purchases, payments, expenses, running ledgers, and PDF reports.

Built exactly to the confirmed requirements — no GST, no driver/vehicle/trip modules,
no weight-difference percentage. Customer billing always uses **Received Weight × Rate**,
never dispatch weight.

```
vpf/
├── backend/     Spring Boot API (Java 17, PostgreSQL)
└── frontend/    Plain HTML/CSS/JS admin console (no build step needed)
```

---

## 1. Prerequisites

- **Java 17+** and **Maven** — for the backend
- **PostgreSQL 13+** — database
- A way to serve static files for the frontend (any static host, or even just opening
  the HTML files — see Section 4)

## 2. Database setup

```sql
CREATE DATABASE vpf_db;
CREATE USER vpf_user WITH ENCRYPTED PASSWORD 'change_me';
GRANT ALL PRIVILEGES ON DATABASE vpf_db TO vpf_user;
```

Tables are created automatically on first run (`ddl-auto: update` in
`application.yml`) — no manual migration scripts needed.

## 3. Running the backend

```bash
cd backend
# Set these before running (or edit application.yml directly):
export DB_URL=jdbc:postgresql://localhost:5432/vpf_db
export DB_USERNAME=vpf_user
export DB_PASSWORD=change_me
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD=choose_a_strong_password
export ALLOWED_ORIGINS=http://localhost:5500,http://127.0.0.1:5500

mvn clean install
mvn spring-boot:run
```

The API starts on `http://localhost:8080`. A default admin user is created
automatically on first run (username/password from the env vars above, or
`admin` / `admin123` if you don't set them — **change this immediately** via
the "Change Password" option, or by setting `ADMIN_PASSWORD` before the very
first run).

> **Note on compiling:** this environment couldn't reach Maven Central to do a
> full build-and-verify pass (no internet access to the public repo). The code
> has been written and manually reviewed carefully, following standard Spring
> Boot patterns throughout, but please run `mvn clean install` yourself before
> deploying, and let me know if anything doesn't compile — happy to fix it fast.

## 4. Running the frontend

The frontend is plain HTML/CSS/JS — no npm install, no build step.

**For local development**, serve the `frontend/` folder with any static server, e.g.:
```bash
cd frontend
python3 -m http.server 5500
```
Then open `http://localhost:5500`.

**Important:** open `frontend/js/config.js` and set `window.API_BASE` to wherever
your backend is running, e.g.:
```js
window.API_BASE = 'http://localhost:8080';
```
If you deploy frontend and backend on the exact same domain (recommended for
production — see below), leave `API_BASE` as an empty string.

## 5. Recommended hosting setup (cheap & simple)

Since you said budget/hosting isn't decided yet, here's the simplest reliable path:

1. **One small VPS** (e.g. a ₹400–800/month plan from DigitalOcean, Hetzner, or an
   Indian provider) running:
   - PostgreSQL (or use a managed free-tier Postgres like Supabase/Neon to start)
   - The Spring Boot JAR (`java -jar vpf.jar`), kept alive with `systemd` or `pm2`
   - Nginx in front, serving the `frontend/` folder as static files AND reverse-proxying
     `/api/*` to the Spring Boot app on the same domain — this avoids CORS entirely
     and lets you leave `API_BASE = ''`.
2. Point a domain (or subdomain) at the VPS, add free HTTPS via Let's Encrypt/Certbot.
3. Back up the Postgres database on a daily cron (`pg_dump`) — this is the owner's
   real business data, treat it like the paper ledger and don't lose it.

A minimal `nginx` example once you're ready to deploy:
```nginx
server {
    listen 80;
    server_name yourdomain.com;

    root /var/www/vpf/frontend;
    index index.html;

    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
    }
}
```

I'm happy to walk through the actual deployment step by step once you've picked
a host — it's a short list of commands either way.

## 6. First login

1. Go to the login page, sign in with the admin credentials from Section 3.
2. Add your suppliers (Suguna, etc.) under **Suppliers**.
3. Add your customers (Sri Durga SR Chicken Point, etc.) under **Customers** —
   enter their current outstanding balance as the **Opening Balance**. This seeds
   the ledger so history isn't lost when you switch off paper.
4. If staff (gumasta) need their own logins, go to **Staff Accounts** (visible only
   to Admins, in the top nav) and create a Gumasta account for them. Gumasta accounts
   can add new deliveries, payments, purchases, expenses, customers, and suppliers,
   but cannot edit or permanently delete anything — only Admin accounts can.
5. Start recording deliveries → payments day to day. Sales Amount, running balances,
   dashboard, and PDFs are all automatic.

## 7. What's implemented

- Customers, Suppliers (full CRUD for Admin; add-only for Gumasta; account pages
  with running balance)
- Deliveries — Sales Amount = Dispatch Weight \u00d7 Admin-entered Selling Rate
  (simplified single-weigh-in workflow; Received Weight/Boxes/Weight Difference
  are still supported at the database/API level if you ever want to re-enable
  the original received-weight billing flow, just not exposed in the current UI)
- Feed sales — record when a chicken center buys chicken feed from the farm;
  adds to their outstanding balance the same way a delivery does
- Customer & Supplier payments — every payment is its own row, never overwritten.
  The Customer Payments modal supports **Save & Next** for quickly entering many
  customers' payments in one sitting without reopening the form each time
- Customer & Supplier ledgers — append-only running balance. The Customer Account
  page's Ledger tab (shown first) merges deliveries, feed sales, and payments into
  one chronological table in the paper-ledger format: Date / Birds / Kg / Rate /
  Amount / O.Bal / T.Bal / Paid / Balance
- Expenses (9 categories from the spec)
- Admin dashboard — Today's Sales, Supplier Outstanding, Today's Expenses, plus
  Recent Deliveries and Recent Payments
- 6 PDF reports: Customer Statement (single customer, or **All Customers** combined
  into one PDF), Supplier Purchase, Daily Sales, Expense, Profit/Loss
- **Two account roles**: Admin (full access, including permanently deleting
  records/customers/suppliers) and Gumasta/Staff (can only add new records, no
  edit or delete). Enforced on the backend (not just hidden in the UI), so a
  Gumasta account can't call the API directly to bypass the restriction either.
- Deleting a customer/supplier cascades to all their deliveries, payments, purchases,
  and ledger history. Deleting a single delivery/payment/purchase automatically
  recalculates that customer or supplier's running ledger balance afterward.
- Session-based login (no GST, no driver/vehicle/trip modules, no weight-difference
  percentage, no separate Orders feature)

## 8. Suggested next steps (not built yet, only if you want them later)

- Automated daily Postgres backups
- SMS/WhatsApp reminders for outstanding balances
- A settings screen for Admins to change other users' passwords
