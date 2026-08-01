# Hosting the backend on an Oracle Cloud Always-Free VM

Same lift-and-shift as the Azure guide, but on Oracle Cloud's **Always Free**
Ampere (ARM) VM — free forever, no student verification, generous resources
(up to 4 CPU / 24 GB RAM). One Linux VM runs the existing `docker compose` stack
against the already-hosted **Neon** (database) and **Supabase** (storage).

The shared installer `infra/azure/setup-vm.sh` works here too (it's just Ubuntu).

---

## Step 1 — Sign up

1. Go to <https://www.oracle.com/cloud/free/> -> **Start for free**.
2. You'll need email, phone, and a **credit/debit card for identity only** —
   Always-Free resources are **never charged**. (Choose "Always Free" resources
   and you won't be billed.)
3. **Home region: choose `Germany Central (Frankfurt)`** — same city as your Neon
   database, so app<->DB latency stays low. **This cannot be changed later**, so
   pick it at signup.
4. Account activation can take a few minutes to a couple of hours. Wait for the
   "your account is ready" email before continuing.

## Step 2 — Create the VM instance

Console -> **Compute -> Instances -> Create instance**.

- **Image:** Canonical **Ubuntu 24.04** (change from the default Oracle Linux).
- **Shape:** click **Change shape -> Ampere** -> `VM.Standard.A1.Flex`, set
  **4 OCPU / 24 GB** (all within Always Free). If you see "out of capacity", try
  2 OCPU / 12 GB, or retry later / another availability domain — free ARM
  capacity in popular regions comes and goes.
- **SSH keys:** upload your public key (or let it generate and download the key).
- Leave the default VCN/public subnet (it gives the VM a public IP).
- **Create**, then note the instance's **public IP**.

## Step 3 — Open the ports (TWO places — this is the classic Oracle gotcha)

Oracle blocks ports at both the network layer **and** inside the Ubuntu image.

**(a) Network — VCN Security List:**
Networking -> Virtual Cloud Networks -> your VCN -> the public subnet ->
its Security List -> **Add Ingress Rules**:
- Source `0.0.0.0/0`, IP Protocol TCP, Destination port **8080** (the gateway).
- (Port 22 is usually already open.)

**(b) OS firewall — run once on the VM after SSH:**
Oracle's Ubuntu image drops non-SSH traffic via iptables. Open 8080 and persist:
```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 8080 -j ACCEPT
sudo netfilter-persistent save
```

## Step 4 — Install Docker, get the code, deploy

```bash
ssh -i /path/to/key ubuntu@<PUBLIC_IP>
git clone https://github.com/kbbortei1/Asset_Shield.git
cd Asset_Shield/assetshield-backend
bash infra/azure/setup-vm.sh      # shared installer — Docker + compose
newgrp docker
```

Copy the two gitignored secret files from your Windows machine (PowerShell):
```powershell
scp -i C:\path\to\key `
  C:\Users\offic\Desktop\Asset_Shield\assetshield-backend\.env `
  C:\Users\offic\Desktop\Asset_Shield\assetshield-backend\secrets.prod.local `
  ubuntu@<PUBLIC_IP>:~/Asset_Shield/assetshield-backend/
```

Then build + start (images build natively for ARM — Java/Spring run fine on ARM;
first build is a bit slower):
```bash
cd ~/Asset_Shield/assetshield-backend
docker compose up -d --build
docker compose ps
```

`.env` already targets Neon (`COMPOSE_FILE`/`COMPOSE_PROFILES`), so no flags needed.

## Step 5 — Point the app + smoke test

Set the mobile app's API base URL to `http://<PUBLIC_IP>:8080`. Then:
```bash
curl -s -X POST http://<PUBLIC_IP>:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+233200000000","password":"SuperAdmin#2026"}'
```
`"status":"success"` means app -> VM -> Neon/Supabase all works.

---

## Notes

- **ARM (Ampere) is fine:** the service Dockerfiles build on `eclipse-temurin`,
  which has arm64 images; `docker compose build` on the VM builds arm64 natively.
- **Domain + HTTPS:** add a Caddy/nginx reverse proxy on 443 later, then point a
  domain's A record at the public IP.
- **Free forever:** as long as you stay on Always-Free shapes (A1.Flex up to
  4 OCPU / 24 GB, plus the boot volume), there's no charge and no expiry.
