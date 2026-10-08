# Infrastructure

Kubernetes deployment for the Lynq platform, packaged as a Helm chart. The same chart runs both a self-contained local cluster (minikube) and the production cluster (AWS EKS); a small set of flags in the values files is what tells the two environments apart. In production the chart is not installed by hand — Terraform coordinates it — while locally you install it directly with Helm.

The chart deploys the application modules (`lynq-iam`, `lynq-bff`, `lynq-app-backend`, `lynq-file-storage`, `lynq-llm`, `lynq-agent`, `lynq-feeders`, `lynq-analytics`, and the frontend) together with their configuration, and — locally only — their infrastructure dependencies (MySQL, Redis, LocalStack). In production MySQL and Redis run on an EC2 instance Terraform creates, S3/SNS/SQS are the real AWS services, the frontend is served from Cloudflare, and secrets are created outside the chart.


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
│   │   ├── cronjobs/         # Scheduled jobs (the daily lynq-feeders run and lynq-analytics snapshot)
│   │   └── infra/            # Local-only MySQL / Redis / LocalStack / Ollama
│   └── values/
│       ├── k8s_values-local.yaml
│       └── k8s_values-prod.yaml
└── terraform/                # Orchestrates the chart in PROD only
    ├── providers.tf
    ├── variables.tf
    ├── main.tf               # Namespace, external Secrets and the helm_release
    ├── vpc.tf                # VPC, public/private subnets, NAT
    ├── eks.tf                # Cluster, node group, OIDC provider
    ├── data_host.tf          # EC2 instance running MySQL + Redis, its security group and Elastic IP
    ├── lbc.tf                # AWS Load Balancer Controller and its IRSA role
    ├── dns.tf                # ACM certificate and Cloudflare records
    ├── s3.tf
    ├── sns.tf                # Domain events topic lynq-app-backend publishes to
    ├── sqs.tf                # lynq-analytics' queue, subscribed to the domain events topic
    ├── bedrock.tf            # Bedrock-only IAM users for lynq-llm and lynq-agent
    ├── outputs.tf
    ├── policies/             # Vendored IAM policy of the load balancer controller
    ├── templates/            # Bootstrap script of the data host (user_data)
    └── environments/
        └── prod.tfvars
```

`helm/` and `terraform/` do not overlap: Helm only reads the chart directory, Terraform only reads its own `.tf`/`.tfvars`. The link between them is a `helm_release` that points at `../helm`. **Local is deployed with Helm directly; Terraform is used only for production.**


## Environments

The chart is driven by per-environment values files. A handful of flags decide what gets rendered:

| Flag | Local | Prod | Effect |
|------|-------|------|--------|
| `manageSecrets` | `true` | `false` | Whether Helm renders the Secrets. In prod they are created outside the chart. |
| `localInfra` | `true` | `false` | Whether MySQL / Redis / LocalStack run in-cluster. In prod they run outside the cluster. |
| `ollamaInCluster` | `false` | `false` | Whether Ollama runs in-cluster. Locally it defaults to the Ollama on your host. |
| `localFrontend` | `true` | `false` | Whether the frontend runs in-cluster. In prod it is served from Cloudflare (Wrangler). |

All credentials live in a single `credentials` block in `k8s_values-local.yaml` (the single source of truth shared by the apps' Secrets and the local infra). Production carries no secret material at all: the required Secrets and their keys are documented at the top of `k8s_values-prod.yaml` and are provisioned by Terraform / External Secrets.


## Scheduled jobs

`lynq-feeders` is the only workload that is not driven by users. It scrapes Bumeran and Computrabajo, asks `lynq-llm` for skills and similarity tags, and hands the batch to `lynq-app-backend`. The Deployment serves the endpoint; a CronJob calls it.

The ingest endpoint answers `202` with no body and does the work in the background, so the CronJob pod is a trigger and nothing more: it is done in milliseconds, whatever the run costs. A run is ~80 postings, each an LLM generation, and used to hold the trigger's connection open for up to 90 minutes — any hiccup on that connection failed the Job even though the ingest itself was fine.

| Setting | Value | Why |
|---------|-------|-----|
| `lynq_feeders.cron.schedule` | `0 6 * * *` | One run a day, off-peak. |
| `lynq_feeders.cron.timeZone` | `Etc/UTC` | Pinned so the run does not drift with DST. |
| `concurrencyPolicy` | `Forbid` | Two triggers no longer overlap in practice — `lynq-feeders` refuses a second run with a `409` — but this costs nothing to keep. |
| `backoffLimit` | `1` | The feed is scraped fresh daily; a failed trigger is better retried tomorrow than hammered at now. |
| `activeDeadlineSeconds` | `120` | The trigger waits for an acknowledgement, not for the run. |
| `requestTimeoutSeconds` | `30` | `curl --max-time` for that acknowledgement. |

Trigger a run by hand without waiting for the schedule:

```bash
kubectl -n lynq-local-namespace create job --from=cronjob/lynq-feeders-cronjob feeders-manual
kubectl -n lynq-local-namespace logs -f job/feeders-manual
```

The Job's log only shows the `202`. The run itself is in the `lynq-feeders` pod, under the request uuid the trigger printed:

```bash
kubectl -n lynq-local-namespace logs -l app=lynq-feeders -f | grep "<request-uuid>"
```

### The lynq-analytics daily snapshot

`lynq-analytics` keeps a daily snapshot of the market and of every candidate's market fit, and the
`lynq-analytics-snapshot-cronjob` is what takes it: a `curl` trigger, like the feeders', that calls
`POST /lynq-analytics/internal/snapshot` with the internal token and a fresh `lynq-request-uuid`. The
endpoint answers `202` and runs in the background, so the Job only waits for the acknowledgement;
a second trigger while a snapshot is running gets a `409`.

| Setting | Value | Why |
|---------|-------|-----|
| `lynq_analytics.cron.enabled` | `true` | The trigger calls `lynq-analytics-service` and reads `lynq-analytics-secret`, both deployed by this chart. |
| `lynq_analytics.cron.schedule` | `0 8 * * *` | 05:00 in Buenos Aires, two hours after the feeders, so the day's snapshot includes what they ingested. |
| `lynq_analytics.cron.timeZone` | `Etc/UTC` | Pinned, as the feeders'. |
| `backoffLimit` | `1` | A missed day is a hole in the daily series, which the charts show as one. |
| `activeDeadlineSeconds` / `requestTimeoutSeconds` | `120` / `30` | The trigger waits for the `202`, not for the snapshot. |

```bash
kubectl -n lynq-local-namespace create job --from=cronjob/lynq-analytics-snapshot-cronjob snapshot-manual
kubectl -n lynq-local-namespace logs -l app=lynq-analytics -f | grep "<request-uuid>"
```

### The internal token

`lynq-app-backend` exposes `/internal/job-posts/ingest` for the feeder. That route is exempt from the bearer-token filters — a cron has no user behind it — and is guarded instead by a shared secret in the `lynq-internal-token` header.

**The same value must be in every Secret that carries it**: `lynq-feeders-secret` (the caller presents it), `lynq-app-backend-secret` and `lynq-llm-secret` (the callees check it). Locally they all come from `credentials.internal.token` in `k8s_values-local.yaml`, so they cannot drift. In prod Terraform generates one value (`random_password.internal_token`) and writes it into all of them; `terraform output -raw internal_token` prints it when you need to call an internal route by hand.

`lynq-analytics` uses the same token both ways: its own `/internal/**` routes check it (the snapshot
trigger presents it), and it presents it to `lynq-app-backend`'s `/internal/score/batch` while it
takes the snapshot. So `lynq-analytics-secret` carries the same `LYNQ_INTERNAL_TOKEN` too, written by
Terraform from the same generated value.

If the values differ, the ingest fails closed: the backend answers `401` and the feeder's run ends with an error logged in its pod — the trigger already got its `202`, so the CronJob is green and the failure is only visible there. The same happens when the token is missing entirely — a deploy that forgets it fails loudly rather than accepting unauthenticated writes.


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
   ollama pull qwen2.5:7b
   ```

   > For Docker Desktop's Kubernetes, set `OLLAMA_BASE_URL` to `http://host.docker.internal:11434` in the values.
   > To run Ollama in the cluster instead, set `ollamaInCluster: true` and point `OLLAMA_BASE_URL` at `http://ollama:11434`.

3. Install the chart:

   ```bash
   helm install lynq ./infrastructure/helm -f infrastructure/helm/values/k8s_values-local.yaml
   ```

   This is the single command that brings the whole platform up: the apps, the
   local infra (MySQL / Redis / LocalStack), and the frontend all pull published
   images — including `lynq-app-frontend`, which is built on each release with the
   local ingress URLs baked in. No manual frontend build is needed.

The platform is then reachable on the shared host: the frontend at `http://lynq.local/` and the gateway at `http://lynq.local/lynq-bff` (path-based routing; the more specific path takes precedence). Everything behind the gateway — `lynq-iam`, `lynq-app-backend`, `lynq-llm`, `lynq-file-storage` — has no Ingress and is reachable only from inside the cluster; the browser's auth calls reach `lynq-iam` relayed by the gateway.

> **macOS (docker driver).** On Mac, minikube runs with the `docker` driver by
> default, and its IP (e.g. `192.168.49.2`) lives inside Docker's internal
> network — it is **not routable from the host**, so pointing `/etc/hosts` at
> `minikube ip` does not work. Instead point the host at loopback and run
> `minikube tunnel`, which exposes the ingress (port 80) on `127.0.0.1`:
>
> ```bash
> echo "127.0.0.1  lynq.local" | sudo tee -a /etc/hosts
> minikube tunnel   # leave running in a dedicated terminal (asks for sudo)
> ```
>
> Keep the tunnel running while you use the platform; closing that terminal
> cuts access. As an alternative without the tunnel, port-forward the ingress
> controller (`kubectl -n ingress-nginx port-forward svc/ingress-nginx-controller 8080:80`)
> and reach it at `http://lynq.local:8080/`.

To preview what Helm will render without installing:

```bash
helm lint ./infrastructure/helm -f infrastructure/helm/values/k8s_values-local.yaml
helm template lynq ./infrastructure/helm -f infrastructure/helm/values/k8s_values-local.yaml
```


## Production

Production runs on AWS EKS and is applied **only with Terraform** (local uses Helm directly). Terraform creates everything the chart needs and then runs the `helm_release`:

- **The network** (`vpc.tf`). A VPC with two public and two private subnets in two AZs, an internet gateway, and a single NAT gateway. The public subnets hold the ALB and the NAT and are tagged `kubernetes.io/role/elb`; the private ones hold the cluster. Every pod leaves the VPC through the NAT's Elastic IP (`terraform output nat_public_ip`).
- **The EKS cluster itself and its EC2 worker nodes** (`eks.tf`), in the private subnets. The control plane is AWS-managed; the workers are a managed node group of `t3.medium` on-demand instances (2 by default, max 3). The cluster's OIDC provider is created too, which is what the load balancer controller's IRSA role trusts.
- **The AWS Load Balancer Controller** (`lbc.tf`), installed with Helm into `kube-system`. Its IAM role is assumed through IRSA, with the official policy vendored under `policies/` for the pinned chart version.
- **MySQL and Redis on their own EC2 instance** (`data_host.tf`), separate from the worker nodes. It is an Ubuntu 24.04 `t3.small` in a public subnet with an Elastic IP. On first boot its `user_data` (`templates/data-host-init.sh.tftpl`) installs MySQL 8 and Redis, creates the five `lynq_*_db` schemas and the MySQL user, the Redis ACL user, and a Linux user for SSH with password. The security group opens MySQL and Redis only to the EKS nodes, which reach them on the instance's private IP — the apps' `DB_URL` / `REDIS_ADDRESS` are derived from it — and SSH only to `data_host_ssh_cidr`. The data lives on the instance's EBS volume, with no backups.
- **S3 bucket** (private, with CORS for pre-signed uploads) for `lynq-file-storage`, the only service that talks to S3.
- **SNS + SQS** (`sns.tf`, `sqs.tf`): the `lynq-domain-events` topic `lynq-app-backend` publishes to, and `lynq-analytics-events`, subscribed to it with raw message delivery and a dead-letter queue after five receives. Same shape as the LocalStack init hook used locally.
- **External Secrets.** `manageSecrets: false` — Helm renders no Secrets; Terraform creates `dockerhub-secret`, `lynq-iam-secret`, `lynq-bff-secret`, `lynq-app-backend-secret`, `lynq-file-storage-secret`, `lynq-llm-secret`, `lynq-agent-secret`, `lynq-feeders-secret`, and `lynq-analytics-secret`, and the deployments consume them by reference. The JWT secret and the internal token are generated by Terraform; the DB/Redis credentials and the Cloudflare and Docker Hub tokens are supplied at apply time via `TF_VAR_*` (never committed).
- **Internet exposure via a shared ALB.** Only `lynq-bff` is exposed: it sits behind a single AWS ALB with a `group.name`, path-based routing on one domain, and TLS terminated at the ALB. It is the entry point for everything, identity included — it relays the auth calls to `lynq-iam`, which like the DMZ services (`lynq-app-backend`, `lynq-llm`, `lynq-file-storage`, `lynq-agent`, `lynq-analytics`) has no Ingress and can only be reached from inside the cluster. The public DNS record is a CNAME to the ALB hostname read off `lynq-bff-ingress`.
- **Certificate + DNS.** Terraform creates the ACM certificate for `api.lynqoficial.com`, validates it via a Cloudflare DNS record, feeds the ARN into the Ingress, and points `api.lynqoficial.com` at the ALB (Cloudflare CNAME, DNS-only). DNS for `lynqoficial.com` lives in Cloudflare.
- **Frontend on Cloudflare.** `localFrontend: false` — the frontend is deployed separately with Wrangler, outside this chart: `npm run deploy` in `lynq-app-frontend` publishes it at `https://app.lynqoficial.com`, the only origin the S3 bucket's CORS allows.


## Production — step by step

Prerequisites on the machine that runs Terraform:

- `terraform` (>= 1.5) and the **AWS CLI v2**. The `kubernetes` and `helm` providers authenticate against the cluster by running `aws eks get-token`, so the CLI must be on the `PATH`.
- AWS credentials allowed to create everything above (VPC, EKS, IAM users/roles/policies, S3, SNS, SQS, ACM). Configure them with `aws configure` or the `AWS_*` env vars; Terraform and the AWS CLI both read them from there. The identity that creates the cluster becomes its admin.
- In the Bedrock console, check that the account can invoke every model in `bedrock_invocable_model_ids` (Nova Lite and Nova Pro) in `bedrock_region`.

Fill in the `REPLACE_*` values in `environments/prod.tfvars` first: `data_host_ssh_cidr` (your public IP as `x.x.x.x/32`, e.g. from `curl -s https://checkip.amazonaws.com`), `cloudflare_zone_id` and `s3_bucket_name` (bucket names are global, pick a unique one).

### 0. Cloudflare token and zone id

Terraform needs a Cloudflare API token (to manage DNS) and the zone id of `lynqoficial.com`.

**API token** — at [dash.cloudflare.com/profile/api-tokens](https://dash.cloudflare.com/profile/api-tokens) → **Create Token**:

- Use the **"Edit zone DNS"** template (or a custom token with `Zone → DNS → Edit`, and optionally `Zone → Zone → Read`).
- **Zone Resources**: `Include → Specific zone → lynqoficial.com` (scopes the token to just that zone).
- Create it and **copy the token — it is shown only once**. Prefer a scoped token over the account-wide *Global API Key*.

**Zone id** — open the domain in the dashboard (Websites → `lynqoficial.com`) → **Overview** → bottom-right **API → Zone ID**.

### 1. Pick the data host passwords

Terraform creates MySQL and Redis on the data host, so there is nothing to prepare there: choose a user and password for each, and a password for SSH. Only the SSH password's SHA-512 hash is handed to Terraform:

```bash
openssl passwd -6
```

The credentials are written into the instance's `user_data` once, on first boot. Terraform ignores later changes to them on the instance (`ignore_changes`), so rotating one means changing it on the host over SSH and then in `TF_VAR_*`, which updates the Secrets.

### 2. Export the secrets

```bash
export TF_VAR_db_username=<DB_USER>
export TF_VAR_db_password=<DB_PASSWORD>
export TF_VAR_redis_username=<REDIS_USER>
export TF_VAR_redis_password=<REDIS_PASSWORD>
export TF_VAR_data_host_ssh_password_hash='<output of openssl passwd -6>'
export TF_VAR_cloudflare_api_token=<cloudflare-dns-token>
export TF_VAR_dockerhub_token=<dockerhub-access-token>
```

The Docker Hub token is optional: without it `dockerhub-secret` is not created and the images are pulled anonymously. All the nodes share the NAT's IP, so they also share Docker Hub's anonymous rate limit — keep the token.

Everything else is generated. The JWT secret and the internal token come from `random_password`, and Terraform creates least-privilege IAM users and writes each access key straight into the Secret of the one service that needs it:

| IAM identity | Permissions | Secret |
| --- | --- | --- |
| `lynq-backend-s3` (`s3.tf`) | `s3:GetObject/PutObject/DeleteObject` + `ListBucket`, scoped to the bucket | `lynq-file-storage-secret` |
| `lynq-llm-bedrock` (`bedrock.tf`) | `bedrock:InvokeModel` on the allowed models (below), `ListFoundationModels` for the health probe | `lynq-llm-secret` |
| `lynq-agent-bedrock` (`bedrock.tf`) | `bedrock:InvokeModel` on the allowed models (below) | `lynq-agent-secret` |
| `lynq-app-backend-sns` (`sns.tf`) | `sns:Publish` on `lynq-domain-events` | `lynq-app-backend-secret` |
| `lynq-analytics-sqs` (`sqs.tf`) | `sqs:ReceiveMessage/DeleteMessage/ChangeMessageVisibility/GetQueueAttributes/GetQueueUrl` on `lynq-analytics-events` | `lynq-analytics-secret` |
| `lynq-eks-aws-load-balancer-controller` role (`lbc.tf`) | the controller's official policy, assumed through IRSA | — (ServiceAccount annotation) |

`lynq-agent` calls Bedrock itself (it does not go through `lynq-llm`), which is why it has its own user. Both Bedrock users may invoke the same models: everything in `bedrock_invocable_model_ids` (Nova Lite and Nova Pro by default) plus `bedrock_model_id` and `agent_bedrock_model_id`, either directly or through a cross-region inference profile (`us.amazon.nova-pro-v1:0`). Switching a service between them — or pointing the agent's `BEDROCK_INTENT_MODEL_ID` / `BEDROCK_JUDGE_MODEL_ID` at Nova Lite — needs no IAM change. A model outside that list, or a guardrail (`bedrock:ApplyGuardrail`), does.

### 3. Create the network and the cluster

The first apply is **two-phase**: the `kubernetes` and `helm` providers are configured from the cluster (`providers.tf`), which does not exist yet.

```bash
cd infrastructure/terraform
terraform init

terraform apply \
  -var-file=environments/prod.tfvars \
  -target=aws_eks_addon.core \
  -target=aws_eip.data_host

terraform output data_host_public_ip
```

The data host is created in this phase so MySQL and Redis are installed by the time the services start. Its bootstrap takes a few minutes; once it is done you can log in with `ssh lynq-admin@<data_host_public_ip>` (the user is `data_host_ssh_username`) and check it with `sudo tail /var/log/cloud-init-output.log`.

### 4. Deploy everything else

```bash
terraform apply -var-file=environments/prod.tfvars
```

This installs the load balancer controller, the namespace, the Secrets, S3, SNS, SQS, the IAM users, and the Helm release, then points the DNS at the ALB. Later applies are this single command — the two-phase split is only needed while the cluster does not exist.

To inspect the cluster with `kubectl`:

```bash
aws eks update-kubeconfig --name "$(terraform output -raw eks_cluster_name)" --region us-east-1
kubectl -n lynq-prod-namespace get pods
```

### 5. DNS

Terraform creates the `api.lynqoficial.com` CNAME pointing at the ALB automatically (Cloudflare, DNS-only). Because the ALB is provisioned asynchronously by the controller, its hostname may not be ready during the first apply — if the `cloudflare_record.api` step errors on an empty hostname, just **re-run the same `terraform apply`** once the release is up (`kubectl -n lynq-prod-namespace get ingress` shows the ALB address).

### Tearing it down

The ALB and its security groups are created by the controller, not by Terraform, so remove the release first and let the controller delete them before the VPC goes:

```bash
terraform destroy -var-file=environments/prod.tfvars -target=helm_release.lynq
# wait until the ALB is gone from the EC2 console (Load Balancers)
terraform destroy -var-file=environments/prod.tfvars
```


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

Rendering the prod values is a quick way to confirm the environment split holds: with `k8s_values-prod.yaml` the output must contain **no** Secret objects and **no** in-cluster infra (MySQL/Redis/LocalStack/Ollama) or frontend — those live outside the cluster in production.
