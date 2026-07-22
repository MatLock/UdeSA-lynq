# Prod (AWS EKS). Terraform creates the namespace, external Secrets, and S3
# bucket, then the helm_release. Point kube_context at the EKS context first:
#   aws eks update-kubeconfig --name <cluster> --region <region>
kube_context = "REPLACE_EKS_CONTEXT"
aws_region   = "us-east-1"
namespace    = "lynq-prod-namespace"

# Networking for the self-managed MySQL + Redis EC2 (same VPC as EKS so pods can
# reach it over the internal network).
vpc_id        = "REPLACE_VPC_ID"
subnet_id     = "REPLACE_SUBNET_ID"
internal_cidr = "10.0.0.0/16" # the VPC CIDR that EKS nodes/pods live in
# ssh_allowed_cidr is set at apply time (your current IP), e.g.:
#   terraform apply -var="ssh_allowed_cidr=$(curl -s ifconfig.me)/32" ...
# ec2_instance_type = "t3.small"
# ec2_key_name      = "REPLACE_KEY_PAIR"   # optional, for SSH

# Public API host + Cloudflare DNS. Terraform creates the ACM cert, validates it
# via a Cloudflare DNS record, and points this host at the ALB.
ingress_host       = "api.lynqoficial.com"
cloudflare_zone_id = "REPLACE_CF_ZONE_ID"
# cloudflare_api_token is sensitive -> pass via TF_VAR_cloudflare_api_token

# Parameterized config (fills the REPLACE_* placeholders in k8s_values-prod.yaml).
# NOTE: DB_URL / REDIS_ADDRESS are derived automatically from the EC2's private
# DNS (the MySQL + Redis host), so there are no db_host / redis_host vars here.
# The certificate ARN is created by Terraform, not passed in.
s3_bucket_name  = "REPLACE_BUCKET_NAME"
ollama_base_url = "REPLACE_OLLAMA_BASE_URL"

# Restrict CORS to the real frontend origin (Cloudflare domain).
# s3_cors_allowed_origins = ["https://www.lynqoficial.com"]

# -------------------------------------------------------------------------
# Secrets: DO NOT put real values here (this file is committed). Provide them
# via environment variables (TF_VAR_db_password=... terraform apply) or a
# secrets backend / CI secret store. Listed here only as a reference of what
# must be supplied:
#
#   TF_VAR_cloudflare_api_token
#   TF_VAR_dockerhub_token
#   TF_VAR_db_username
#   TF_VAR_db_password
#   TF_VAR_jwt_secret
#   TF_VAR_redis_username        (only if Redis requires auth)
#   TF_VAR_redis_password        (only if Redis requires auth)
#   TF_VAR_openai_api_key        (only if LLM_PROVIDER=openai)
#
# The backend's AWS S3 access key is created by Terraform (least-privilege IAM
# user scoped to the bucket) and wired into its Secret automatically — you do
# NOT set AWS keys by hand.
# -------------------------------------------------------------------------
