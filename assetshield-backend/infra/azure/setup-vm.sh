#!/usr/bin/env bash
# ============================================================================
# AssetShield GH - one-time setup for a fresh Ubuntu VM (Azure).
# Installs Docker Engine + the compose plugin + git, and lets the current user
# run docker without sudo. Run once after first SSH into the VM:
#   bash setup-vm.sh
# Then log out/in (or `newgrp docker`) so the docker group takes effect.
# ============================================================================
set -euo pipefail

echo ">> Updating apt and installing prerequisites..."
sudo apt-get update -y
sudo apt-get install -y ca-certificates curl gnupg git

echo ">> Adding Docker's official apt repository..."
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

echo ">> Installing Docker Engine + compose plugin..."
sudo apt-get update -y
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

echo ">> Allowing the current user to run docker without sudo..."
sudo usermod -aG docker "$USER"

echo ""
docker --version
docker compose version
echo ""
echo ">> Done. Log out and back in (or run: newgrp docker) so the group applies."
echo ">> Then follow infra/azure/README.md to deploy."
