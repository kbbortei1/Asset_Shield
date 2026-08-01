# Hosting the backend on an AWS EC2 VM

Same lift-and-shift as the other guides: one EC2 Linux VM runs the existing
`docker compose` stack against the already-hosted **Neon** (database) and
**Supabase** (storage). EC2 t3 instances are x86_64 — the same architecture as
your dev machine — so images build exactly as they do locally.

Shared installer: `infra/azure/setup-vm.sh` (it's just Ubuntu; works here too).

---

## Step 0 — Region

Top-right region selector: prefer **Europe (Frankfurt) `eu-central-1`** — same
city as your Neon project, so app<->DB latency stays low. (Europe (Stockholm)
`eu-north-1` also works and is a bit cheaper; it just adds ~20 ms per DB call.)
Your $100 credit is account-wide, so the region choice doesn't affect it.

## Step 1 — Launch the instance

EC2 -> **Launch instance**:
- **Name:** `assetshield`
- **AMI:** Ubuntu Server 24.04 LTS (x86_64)
- **Instance type:** `t3.large` (2 vCPU, **8 GB**) — 7 JVMs + image builds need
  the room. `t3.medium` (4 GB) is cheaper but risks OOM during builds.
- **Key pair:** create one, download the `.pem` (keep it safe — it's your SSH key)
- **Network / Security group:** create one with inbound rules:
  - SSH **22** — source *My IP*
  - Custom TCP **8080** — source *Anywhere* `0.0.0.0/0` (the gateway)
  - (add 80/443 later for a domain)
- **Storage:** 30 GB gp3 (default 8 GB is too small for 7 images)
- **Launch**, then note the instance's **Public IPv4 address**.

> AWS's security group *is* the firewall — unlike Oracle, the Ubuntu AMI does not
> block ports internally, so there's no second OS-firewall step.

## Step 2 — (Recommended) Elastic IP for a stable address

If you'll **stop/start** the instance to save credit, its public IP changes each
start. Allocate an **Elastic IP** (EC2 -> Elastic IPs -> Allocate) and associate
it with the instance so the app's API URL stays constant. (Free while attached to
a running instance.)

## Step 3 — Connect + install Docker

```bash
chmod 400 /path/to/key.pem            # (mac/linux; on Windows set file perms once)
ssh -i /path/to/key.pem ubuntu@<PUBLIC_IP>
git clone https://github.com/kbbortei1/Asset_Shield.git
cd Asset_Shield/assetshield-backend
bash infra/azure/setup-vm.sh          # shared installer: Docker + compose
newgrp docker
```

## Step 4 — Copy the two gitignored secret files

From your **Windows** machine (PowerShell):
```powershell
scp -i C:\path\to\key.pem `
  C:\Users\offic\Desktop\Asset_Shield\assetshield-backend\.env `
  C:\Users\offic\Desktop\Asset_Shield\assetshield-backend\secrets.prod.local `
  ubuntu@<PUBLIC_IP>:~/Asset_Shield/assetshield-backend/
```

## Step 5 — Build + start

```bash
cd ~/Asset_Shield/assetshield-backend
docker compose up -d --build     # first build compiles all 7 images (a few min)
docker compose ps                # wait for healthy
```

`.env` already targets Neon (`COMPOSE_FILE`/`COMPOSE_PROFILES`), so no flags needed.

## Step 6 — Point the app + smoke test

Set the mobile app's API base URL to `http://<PUBLIC_IP>:8080`, then:
```bash
curl -s -X POST http://<PUBLIC_IP>:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+233200000000","password":"SuperAdmin#2026"}'
```
`"status":"success"` = app -> EC2 -> Neon/Supabase all working.

---

## Cost / credit notes

- `t3.large` is ~USD 60/mo on-demand; your **$100 credit** covers the demo window.
  **Stop the instance when idle** (EC2 -> Instance state -> Stop) to conserve it —
  you're billed per second only while running. An Elastic IP keeps the address.
- EBS storage (30 GB gp3) is a small monthly cost (~USD 2-3), billed even when the
  instance is stopped — negligible against the credit.
- Watch spend in **Billing and Cost Management**; the credit meter is on Console Home.

## Later: domain + HTTPS

Add a Caddy/nginx reverse proxy on 443 (auto Let's Encrypt), point a domain's A
record at the Elastic IP, then switch the app to `https://api.yourdomain`.
