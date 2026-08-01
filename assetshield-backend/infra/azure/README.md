# Hosting the backend on an Azure VM

Lift-and-shift: one Linux VM runs the existing `docker compose` stack against the
already-hosted **Neon** (database) and **Supabase** (storage). Because services
talk to each other by compose service name, nothing about the app changes — this
is the same stack you run locally, just on a public host.

Prereq: DB on Neon and storage on Supabase are already live (see
`infra/neon/README.md` and the Supabase setup). This VM only runs the services.

---

## Step 1 — Create the VM (Azure portal)

Azure Portal -> **Virtual machines** -> **Create** -> Azure virtual machine.

- **Region:** `Germany West Central` (Frankfurt) - same city as the Neon project,
  so app<->DB latency stays low.
- **Image:** `Ubuntu Server 24.04 LTS`.
- **Size:** `Standard_B2ms` (2 vCPU, 8 GiB) recommended - 7 JVMs + image builds
  need the headroom. `Standard_B2s` (4 GiB) is the budget option but risks OOM;
  you can resize up later if needed.
- **Authentication:** SSH public key (create a new key pair; download the `.pem`).
- **Disk:** default 30 GB Standard SSD is fine.
- **Inbound ports (NSG):** allow **SSH (22)** and **8080** (the gateway). Add
  **80/443** later when you attach a domain.

After it boots, note the VM's **public IP**.

## Step 2 — First SSH + install Docker

```bash
ssh -i /path/to/key.pem azureuser@<VM_PUBLIC_IP>
```

Clone the repo and run the setup script:

```bash
git clone https://github.com/kbbortei1/Asset_Shield.git
cd Asset_Shield/assetshield-backend
bash infra/azure/setup-vm.sh
newgrp docker   # or log out/in so the docker group applies
```

> If the repo is private, authenticate the clone first: install `gh`
> (`sudo apt-get install -y gh`), run `gh auth login`, then `gh repo clone
> kbbortei1/Asset_Shield`. A read-only deploy key or a PAT also works.

## Step 3 — Copy the two secret files (they are gitignored)

From your **Windows** machine (PowerShell), copy `.env` and `secrets.prod.local`
into the cloned backend dir on the VM:

```powershell
scp -i C:\path\to\key.pem `
  C:\Users\offic\Desktop\Asset_Shield\assetshield-backend\.env `
  C:\Users\offic\Desktop\Asset_Shield\assetshield-backend\secrets.prod.local `
  azureuser@<VM_PUBLIC_IP>:~/Asset_Shield/assetshield-backend/
```

`.env` already contains everything the stack needs: the Neon host + passwords,
Supabase keys, Brevo mail creds, JWT/internal secrets, and the
`COMPOSE_FILE`/`COMPOSE_PROFILES` defaults that target Neon.

## Step 4 — Build and start

```bash
cd ~/Asset_Shield/assetshield-backend
docker compose up -d --build      # first run builds all 7 images (takes a while)
docker compose ps                 # all should become healthy
docker compose logs -f gateway    # watch it come up
```

Because `.env` sets `COMPOSE_FILE=docker-compose.yml;docker-compose.neon.yml`,
this automatically runs against Neon - no extra flags needed.

## Step 5 — Point the mobile app at the VM

Set the app's API base URL to `http://<VM_PUBLIC_IP>:8080` and rebuild the app.
(Once a domain + HTTPS are attached, switch it to `https://api.yourdomain`.)

Smoke test from anywhere:

```bash
curl -s -X POST http://<VM_PUBLIC_IP>:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+233200000000","password":"SuperAdmin#2026"}'
```

A JSON response with `"status":"success"` means the whole chain
(app -> VM -> Neon/Supabase) works.

---

## Later: domain + HTTPS

Put a small reverse proxy (Caddy or nginx) in front of the gateway to terminate
TLS on 443 and get an auto Let's Encrypt cert, then point your domain's A record
at the VM IP. Documented separately when we attach the domain.

## Operating notes

- **Restart after reboot:** the containers restart automatically (compose default
  `restart` policy) unless you `docker compose down`.
- **Update deploy:** `git pull && docker compose up -d --build`.
- **Cost:** a B2ms is ~USD 60/mo against the $100 student credit; stop the VM
  when idle to conserve credit. Resize (B2s <-> B2ms) via the portal if needed.
- **The local `postgres` container still starts** (a service depends on it) but
  holds no data now - the app uses Neon. Harmless; ~150 MB idle.
