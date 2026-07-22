# ---------------------------------------------------------------------------
# Cluster / release targeting (prod EKS only — local is handled by Helm directly)
# ---------------------------------------------------------------------------

variable "kubeconfig_path" {
  description = "Path to the kubeconfig file."
  type        = string
  default     = "~/.kube/config"
}

variable "kube_context" {
  description = "kubeconfig context for the EKS cluster (after `aws eks update-kubeconfig`)."
  type        = string
}

variable "aws_region" {
  description = "AWS region for the cluster and managed services."
  type        = string
  default     = "us-east-1"
}

variable "release_name" {
  description = "Helm release name."
  type        = string
  default     = "lynq"
}

variable "namespace" {
  description = "Target namespace. Must match lynq-<k8s_namespace>-namespace from the prod values file."
  type        = string
  default     = "lynq-prod-namespace"
}

# ---------------------------------------------------------------------------
# Networking / EC2 (self-managed MySQL + Redis host reachable from EKS)
# ---------------------------------------------------------------------------

variable "vpc_id" {
  description = "VPC id where the EC2 lives (same VPC as the EKS cluster for internal reachability)."
  type        = string
}

variable "subnet_id" {
  description = "Subnet id for the EC2 instance (a private subnet in the VPC above)."
  type        = string
}

variable "internal_cidr" {
  description = "Internal network CIDR allowed to reach the DB/Redis ports (typically the VPC CIDR)."
  type        = string
  default     = "10.0.0.0/16"
}

variable "ssh_allowed_cidr" {
  description = "CIDR allowed to SSH (port 22) into the EC2. Set at apply time, e.g. -var=\"ssh_allowed_cidr=1.2.3.4/32\"."
  type        = string
}

variable "ec2_instance_type" {
  description = "EC2 instance type for the MySQL + Redis host."
  type        = string
  default     = "t3.small"
}

variable "ec2_ami_id" {
  description = "AMI id for the EC2 instance. Empty = latest Amazon Linux 2023."
  type        = string
  default     = ""
}

variable "ec2_key_name" {
  description = "EC2 key pair name for SSH access (optional)."
  type        = string
  default     = ""
}

# ---------------------------------------------------------------------------
# Parameterized config injected into the chart (fills the REPLACE_* placeholders
# in k8s_values-prod.yaml).
# ---------------------------------------------------------------------------

variable "ingress_host" {
  description = "Public API host for iam + backend behind the ALB (e.g. api.lynqoficial.com)."
  type        = string
}

variable "cloudflare_api_token" {
  description = "Cloudflare API token with DNS edit permission on the zone."
  type        = string
  sensitive   = true
}

variable "cloudflare_zone_id" {
  description = "Cloudflare zone id for the domain (lynqoficial.com)."
  type        = string
}

variable "s3_bucket_name" {
  description = "S3 bucket name for the backend (created by Terraform)."
  type        = string
}

variable "s3_cors_allowed_origins" {
  description = "Allowed origins for the bucket CORS. Restrict to the frontend origin (e.g. the Cloudflare domain)."
  type        = list(string)
  default     = ["*"]
}

variable "ollama_base_url" {
  description = "External Ollama base URL for lynq-ml."
  type        = string
}

# ---------------------------------------------------------------------------
# Secrets. Provide via TF_VAR_* env vars or a secrets backend — NEVER commit
# real values. All marked sensitive so they are redacted from output.
# ---------------------------------------------------------------------------

variable "dockerhub_server" {
  description = "Docker registry auth server key."
  type        = string
  default     = "https://index.docker.io/v1/"
}

variable "dockerhub_username" {
  description = "Docker Hub username."
  type        = string
  default     = "matlock0o"
}

variable "dockerhub_token" {
  description = "Docker Hub access token."
  type        = string
  sensitive   = true
  default     = ""
}

variable "dockerhub_email" {
  description = "Docker Hub email (optional)."
  type        = string
  default     = ""
}

variable "db_username" {
  description = "Managed DB username (shared by iam + backend)."
  type        = string
  sensitive   = true
  default     = ""
}

variable "db_password" {
  description = "Managed DB password (shared by iam + backend)."
  type        = string
  sensitive   = true
  default     = ""
}

variable "redis_username" {
  description = "Managed Redis username (optional)."
  type        = string
  sensitive   = true
  default     = ""
}

variable "redis_password" {
  description = "Managed Redis password (optional)."
  type        = string
  sensitive   = true
  default     = ""
}

variable "jwt_secret" {
  description = "JWT signing secret for lynq-iam."
  type        = string
  sensitive   = true
  default     = ""
}

variable "aws_access_key_id" {
  description = "AWS access key for the backend. Leave empty when using IRSA."
  type        = string
  sensitive   = true
  default     = ""
}

variable "aws_secret_access_key" {
  description = "AWS secret key for the backend. Leave empty when using IRSA."
  type        = string
  sensitive   = true
  default     = ""
}

variable "openai_api_key" {
  description = "OpenAI API key for lynq-ml (only if LLM_PROVIDER=openai)."
  type        = string
  sensitive   = true
  default     = ""
}
