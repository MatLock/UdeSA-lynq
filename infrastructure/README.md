# Infrastructure

Kubernetes deployment for the Lynq platform, packaged as a Helm chart. The same chart runs both a self-contained local cluster (minikube) and the production cluster (AWS EKS); a small set of flags in the values files is what tells the two environments apart. In production the chart is not installed by hand — Terraform coordinates it — while locally you install it directly with Helm.

The chart deploys the application modules (`lynq-iam`, `lynq-app-backend`, `lynq-ml`, and the frontend) together with their configuration, and — locally only — their infrastructure dependencies (MySQL, Redis, LocalStack). In production those dependencies come from managed services (RDS, ElastiCache, S3), the frontend is served from Cloudflare, and secrets are created outside the chart.


## Layout

```
infrastructure/
├── helm/                     # The chart (what gets deployed)
│   ├── Chart.yaml
│   ├── templates/            # Rendered recursively by Helm; grouped by resource type
│   │   ├── namespace/
│   │   ├── deployments/
│   │   ├── services/
│   │   ├── ingress/
│   │   ├── configmaps/
│   │   ├── secrets/
│   │   └── infra/            # Local-only MySQL / Redis / LocalStack / Ollama
│   └── values/
│       ├── k8s_values-local.yaml
│       └── k8s_values-prod.yaml
└── terraform/                # Orchestrates the chart in PROD only
    ├── providers.tf
    ├── variables.tf
    ├── main.tf
    ├── s3.tf
    ├── outputs.tf
    └── environments/
        └── prod.tfvars
```

`helm/` and `terraform/` do not overlap: Helm only reads the chart directory, Terraform only reads its own `.tf`/`.tfvars`. The link between them is a `helm_release` that points at `../helm`. **Local is deployed with Helm directly; Terraform is used only for production.**


## Environments

The chart is driven by per-environment values files. A handful of flags decide what gets rendered:

| Flag | Local | Prod | Effect |
|------|-------|------|--------|
| `manageSecrets` | `true` | `false` | Whether Helm renders the Secrets. In prod they are created outside the chart. |
| `localInfra` | `true` | `false` | Whether MySQL / Redis / LocalStack run in-cluster. In prod these are managed services. |
| `ollamaInCluster` | `false` | `false` | Whether Ollama runs in-cluster. Locally it defaults to the Ollama on your host. |
| `localFrontend` | `true` | `false` | Whether the frontend runs in-cluster. In prod it is served from Cloudflare (Wrangler). |

All credentials live in a single `credentials` block in `k8s_values-local.yaml` (the single source of truth shared by the apps' Secrets and the local infra). Production carries no secret material at all: the required Secrets and their keys are documented at the top of `k8s_values-prod.yaml` and are provisioned by Terraform / External Secrets.


## Running locally

Prerequisites: a running minikube cluster, `helm`, and (by default) an Ollama server on your host.

1. Enable the ingress controller and map the shared host:

   ```bash
   minikube addons enable ingress
   echo "$(minikube ip)  lynq.local" | sudo tee -a /etc/hosts
   ```

2. Start Ollama on your host, listening on all interfaces, and pull the model:

   ```bash
   OLLAMA_HOST=0.0.0.0 ollama serve   # in a dedicated terminal
   ollama pull llama3.1
   ```

   > For Docker Desktop's Kubernetes, set `OLLAMA_BASE_URL` to `http://host.docker.internal:11434` in the values.
   > To run Ollama in the cluster instead, set `ollamaInCluster: true` and point `OLLAMA_BASE_URL` at `http://ollama:11434`.

3. Build the frontend image with the local ingress URLs baked in (Vite bakes them at build time) and load it into the cluster:

   ```bash
   docker build -t lynq-app-frontend:local \
     --build-arg LYNQ_IAM_BASE_URL=http://lynq.local/lynq-iam \
     --build-arg LYNQ_BACKEND_BASE_URL=http://lynq.local/lynq-backend-app \
     ./lynq-app-frontend
   minikube image load lynq-app-frontend:local
   ```

4. Install the chart:

   ```bash
   helm install lynq ./infrastructure/helm -f infrastructure/helm/values/k8s_values-local.yaml
   ```

The platform is then reachable on the shared host: the frontend at `http://lynq.local/`, IAM at `http://lynq.local/lynq-iam`, and the backend at `http://lynq.local/lynq-backend-app` (path-based routing; the more specific paths take precedence).

To preview what Helm will render without installing:

```bash
helm lint ./infrastructure/helm -f infrastructure/helm/values/k8s_values-local.yaml
helm template lynq ./infrastructure/helm -f infrastructure/helm/values/k8s_values-local.yaml
```


## Production

Production runs on AWS EKS and is applied **only with Terraform** (local uses Helm directly). Terraform creates everything the chart needs and then runs the `helm_release`:

- **MySQL + Redis on an EC2 instance** (`ec2-lynq-redis-db`), reachable from EKS over the internal network. Two security groups open `3306` and `6379` to the VPC CIDR, and a third opens `22` to a single admin IP. The apps' `DB_URL` / `REDIS_ADDRESS` are derived automatically from the instance's private DNS.
- **S3 bucket** (private, with CORS for pre-signed uploads) for the backend.
- **External Secrets.** `manageSecrets: false` — Helm renders no Secrets; Terraform creates `dockerhub-secret`, `lynq-iam-secret`, `lynq-app-backend-secret`, and `lynq-ml-secret`, and the deployments consume them by reference. Sensitive values are supplied at apply time via `TF_VAR_*` (never committed).
- **Internet exposure via a shared ALB.** `lynq-iam` and `lynq-app-backend` sit behind a single AWS ALB (AWS Load Balancer Controller) with a shared `group.name`, path-based routing on one domain, and TLS terminated at the ALB.
- **Certificate + DNS.** Terraform creates the ACM certificate for `api.lynqoficial.com`, validates it via a Cloudflare DNS record, feeds the ARN into the Ingress, and points `api.lynqoficial.com` at the ALB (Cloudflare CNAME, DNS-only). DNS for `lynqoficial.com` lives in Cloudflare.
- **Frontend on Cloudflare.** `localFrontend: false` — the frontend is deployed separately with Wrangler, outside this chart.


## Production — step by step

Prerequisites: an EKS cluster with the AWS Load Balancer Controller, a VPC (note its id, a subnet id, and its CIDR), a Cloudflare zone for `lynqoficial.com` (note its zone id) plus an API token with DNS edit permission, `terraform`, and AWS credentials for the provider (`aws configure` or `AWS_*` env vars). Fill in the `REPLACE_*` values in `environments/prod.tfvars` first. The ACM certificate and the `api.lynqoficial.com` DNS record are created by Terraform.

### 0. Cloudflare token and zone id

Terraform needs a Cloudflare API token (to manage DNS) and the zone id of `lynqoficial.com`.

**API token** — at [dash.cloudflare.com/profile/api-tokens](https://dash.cloudflare.com/profile/api-tokens) → **Create Token**:

- Use the **"Edit zone DNS"** template (or a custom token with `Zone → DNS → Edit`, and optionally `Zone → Zone → Read`).
- **Zone Resources**: `Include → Specific zone → lynqoficial.com` (scopes the token to just that zone).
- Create it and **copy the token — it is shown only once**. Prefer a scoped token over the account-wide *Global API Key*.

**Zone id** — open the domain in the dashboard (Websites → `lynqoficial.com`) → **Overview** → bottom-right **API → Zone ID**.

```bash
export TF_VAR_cloudflare_api_token=<token>   # used at apply time
# and set in environments/prod.tfvars:
#   cloudflare_zone_id = "<zone-id>"

# optional — verify the token works:
curl -s -H "Authorization: Bearer <token>" \
  https://api.cloudflare.com/client/v4/user/tokens/verify
```

### 1. Create the EC2 instance (MySQL + Redis host)

The apps depend on the database, so the VM is created first, on its own:

```bash
cd infrastructure/terraform
terraform init

terraform apply \
  -target=aws_instance.redis_db \
  -var-file=environments/prod.tfvars \
  -var="ssh_allowed_cidr=$(curl -s ifconfig.me)/32"

terraform output redis_db_public_ip   # for SSH
```

### 2. Install MySQL and Redis on the instance

SSH in and install both, **defining the user and password** you will feed to Terraform in step 3 (Amazon Linux 2023):

```bash
ssh -i <your-key.pem> ec2-user@<redis_db_public_ip>

# ---- MySQL ----
sudo dnf install -y https://dev.mysql.com/get/mysql80-community-release-el9-1.noarch.rpm
sudo dnf install -y mysql-community-server
sudo systemctl enable --now mysqld
sudo grep 'temporary password' /var/log/mysqld.log   # initial root password

# create databases + app user (choose <DB_USER> / <DB_PASSWORD>)
mysql -u root -p <<'SQL'
CREATE DATABASE IF NOT EXISTS lynq_iam_db;
CREATE DATABASE IF NOT EXISTS lynq_backend_db;
CREATE USER '<DB_USER>'@'%' IDENTIFIED BY '<DB_PASSWORD>';
GRANT ALL PRIVILEGES ON lynq_iam_db.*     TO '<DB_USER>'@'%';
GRANT ALL PRIVILEGES ON lynq_backend_db.* TO '<DB_USER>'@'%';
FLUSH PRIVILEGES;
SQL

# allow remote connections: set `bind-address = 0.0.0.0` in /etc/my.cnf, then
sudo systemctl restart mysqld

# ---- Redis ----
sudo dnf install -y redis6
# in /etc/redis6/redis.conf: set `bind 0.0.0.0` and add an ACL user matching the app:
#   user <REDIS_USER> on ><REDIS_PASSWORD> ~* &* +@all
#   user default off
sudo systemctl enable --now redis6
```

### 3. Define the AWS keys and passwords

Export the secrets as `TF_VAR_*` so Terraform writes them into the Kubernetes Secrets — the DB/Redis values **must match** what you set on the VM in step 2:

```bash
export TF_VAR_db_username=<DB_USER>
export TF_VAR_db_password=<DB_PASSWORD>
export TF_VAR_redis_username=<REDIS_USER>
export TF_VAR_redis_password=<REDIS_PASSWORD>
export TF_VAR_jwt_secret=<jwt-signing-secret>
export TF_VAR_dockerhub_token=<dockerhub-access-token>
export TF_VAR_cloudflare_api_token=<cloudflare-dns-token>
# Backend AWS access — prefer IRSA; otherwise:
export TF_VAR_aws_access_key_id=<...>
export TF_VAR_aws_secret_access_key=<...>
# Only if lynq-ml uses OpenAI:
export TF_VAR_openai_api_key=<...>
```

### 4. Deploy everything else

Point the kubeconfig at EKS and apply the full config (namespace, Secrets, S3 bucket, and the Helm release):

```bash
aws eks update-kubeconfig --name <cluster> --region <region>

terraform apply \
  -var-file=environments/prod.tfvars \
  -var="ssh_allowed_cidr=$(curl -s ifconfig.me)/32"
```

### 5. DNS

Terraform creates the `api.lynqoficial.com` CNAME pointing at the ALB automatically (Cloudflare, DNS-only). Because the ALB is provisioned asynchronously by the controller, its hostname may not be ready during the first apply — if the `cloudflare_record.api` step errors on an empty hostname, just **re-run the same `terraform apply`** once the release is up (`kubectl -n lynq-prod-namespace get ingress` shows the ALB address).


## Validating

Before installing or applying, check that the chart renders and the Terraform is well-formed:

```bash
# Helm — lint and preview the rendered manifests for each environment
helm lint ./infrastructure/helm -f infrastructure/helm/values/k8s_values-local.yaml
helm lint ./infrastructure/helm -f infrastructure/helm/values/k8s_values-prod.yaml
helm template lynq ./infrastructure/helm -f infrastructure/helm/values/k8s_values-local.yaml
helm template lynq ./infrastructure/helm -f infrastructure/helm/values/k8s_values-prod.yaml

# Terraform — format and validate
cd infrastructure/terraform
terraform fmt -recursive -check
terraform init -backend=false
terraform validate
```

Rendering the prod values is a quick way to confirm the environment split holds: with `k8s_values-prod.yaml` the output must contain **no** Secret objects and **no** in-cluster infra (MySQL/Redis/LocalStack/Ollama) or frontend — those are external in production.
