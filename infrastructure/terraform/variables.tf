# ---------------------------------------------------------------------------
# Cluster / release targeting (prod EKS only — local is handled by Helm directly)
# ---------------------------------------------------------------------------

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
# Networking (vpc.tf) and the MySQL + Redis host the services connect to (data_host.tf).
# ---------------------------------------------------------------------------

variable "vpc_cidr" {
  description = "CIDR of the VPC Terraform creates. Split into two public and two private /20 subnets."
  type        = string
  default     = "10.0.0.0/16"
}

variable "db_port" {
  description = "Port MySQL listens on in the data host."
  type        = number
  default     = 3306
}

variable "redis_port" {
  description = "Port Redis listens on in the data host (plain TCP, no TLS)."
  type        = number
  default     = 6379
}

variable "data_host_instance_type" {
  description = "EC2 instance type of the host that runs MySQL and Redis."
  type        = string
  default     = "t3.small"
}

variable "data_host_disk_size" {
  description = "EBS volume size (GiB) of the data host. MySQL and Redis keep their data on it."
  type        = number
  default     = 20
}

variable "data_host_ssh_cidr" {
  description = "The only CIDR allowed to SSH into the data host, e.g. your public IP as x.x.x.x/32."
  type        = string

  validation {
    condition     = can(cidrhost(var.data_host_ssh_cidr, 0)) && var.data_host_ssh_cidr != "0.0.0.0/0"
    error_message = "data_host_ssh_cidr must be a valid CIDR other than 0.0.0.0/0."
  }
}

variable "data_host_ssh_username" {
  description = "Linux user created on the data host for SSH with password, in the sudo group."
  type        = string
  default     = "lynq-admin"

  validation {
    condition     = can(regex("^[a-z_][a-z0-9_-]{0,31}$", var.data_host_ssh_username))
    error_message = "data_host_ssh_username must be a valid Linux user name."
  }
}

# ---------------------------------------------------------------------------
# Parameterized config injected into the chart (fills the REPLACE_* placeholders
# in k8s_values-prod.yaml).
# ---------------------------------------------------------------------------

variable "ingress_host" {
  description = "Public host of the gateway (lynq-bff) behind the ALB — the only service exposed to the internet (e.g. api.lynqoficial.com)."
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
  description = "External Ollama base URL for lynq-llm and lynq-agent. Unused while both run with LLM_PROVIDER=bedrock."
  type        = string
  default     = ""
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
  description = "Docker Hub access token. Empty skips dockerhub-secret and the images are pulled anonymously, which shares Docker Hub's per-IP rate limit across the whole cluster through the NAT."
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
  description = "MySQL user shared by every service. The data host creates it with all privileges on the five lynq_*_db schemas."
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "Password of the MySQL user."
  type        = string
  sensitive   = true
}

variable "redis_username" {
  description = "Redis ACL user the data host creates for the services."
  type        = string
  sensitive   = true

  validation {
    condition     = can(regex("^[^\\s\"']+$", var.redis_username))
    error_message = "redis_username must not be empty or contain whitespace or quotes."
  }
}

variable "redis_password" {
  description = "Password of the Redis ACL user."
  type        = string
  sensitive   = true

  validation {
    condition     = can(regex("^[^\\s\"']+$", var.redis_password))
    error_message = "redis_password must not be empty or contain whitespace or quotes."
  }
}

variable "data_host_ssh_password_hash" {
  description = "SHA-512 crypt hash of the SSH password of data_host_ssh_username (generate it with: openssl passwd -6). Only the hash reaches the instance."
  type        = string
  sensitive   = true

  validation {
    condition     = can(regex("^\\$6\\$", var.data_host_ssh_password_hash))
    error_message = "data_host_ssh_password_hash must be a SHA-512 crypt hash ($6$...), as printed by: openssl passwd -6."
  }
}

variable "bedrock_model_id" {
  description = <<-EOT
    Bedrock model id lynq-llm calls through the Converse API (only if
    LLM_PROVIDER=bedrock). Any Converse-capable model works, e.g.
    anthropic.claude-sonnet-4-5-20250929-v1:0, amazon.nova-pro-v1:0 or
    meta.llama3-3-70b-instruct-v1:0.
  EOT
  type        = string
  default     = "amazon.nova-pro-v1:0"
}

variable "bedrock_invocable_model_ids" {
  description = "Bedrock models both lynq-llm's and lynq-agent's IAM users may invoke, besides bedrock_model_id and agent_bedrock_model_id, which are always allowed. Lets either service switch between them, or run the agent's intent and judge steps on a cheaper one, without touching IAM."
  type        = list(string)
  default     = ["amazon.nova-lite-v1:0", "amazon.nova-pro-v1:0"]
}

variable "agent_bedrock_model_id" {
  description = "Bedrock model lynq-agent runs its tool-calling loop on. Nova Lite loses the thread on multi-step tool use, so Nova Pro is the default."
  type        = string
  default     = "amazon.nova-pro-v1:0"
}

variable "bedrock_region" {
  description = "Region whose Bedrock endpoint lynq-llm and lynq-agent call (both models must be enabled there)."
  type        = string
  default     = "us-east-1"
}

# ---------------------------------------------------------------------------
# EKS cluster and EC2 worker nodes (eks.tf).
# ---------------------------------------------------------------------------
variable "eks_cluster_name" {
  description = "Name of the EKS cluster (also prefixes its IAM roles and node group)."
  type        = string
  default     = "lynq-eks"
}

variable "eks_kubernetes_version" {
  description = "Kubernetes minor version for the control plane. Out of standard support EKS bills extended support, six times the hourly price: check it before applying."
  type        = string
  default     = "1.35"
}

variable "eks_public_access_cidrs" {
  description = "CIDRs allowed to reach the public Kubernetes API endpoint. Narrow this to your IP for a private setup."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "eks_node_instance_type" {
  description = "EC2 instance type for the worker nodes. Two t3.medium fit the 8 services plus the system pods and the load balancer controller (17-pod ENI ceiling each)."
  type        = string
  default     = "t3.medium"
}

variable "eks_node_capacity_type" {
  description = "ON_DEMAND or SPOT. SPOT is ~70% cheaper but AWS can reclaim the node with two minutes' notice."
  type        = string
  default     = "ON_DEMAND"

  validation {
    condition     = contains(["ON_DEMAND", "SPOT"], var.eks_node_capacity_type)
    error_message = "eks_node_capacity_type must be ON_DEMAND or SPOT."
  }
}

variable "eks_node_disk_size" {
  description = "EBS volume size (GiB) per worker node."
  type        = number
  default     = 20
}

variable "eks_node_desired_size" {
  description = "Worker nodes to run. Two keeps CoreDNS and the ALB controller on separate nodes."
  type        = number
  default     = 2
}

variable "eks_node_min_size" {
  description = "Minimum worker nodes."
  type        = number
  default     = 2
}

variable "eks_node_max_size" {
  description = "Maximum worker nodes the group may scale to."
  type        = number
  default     = 3
}

variable "lbc_version" {
  description = "AWS Load Balancer Controller chart version. Its IAM policy is read from policies/aws-load-balancer-controller-v<version>.json, so bumping it means adding that file."
  type        = string
  default     = "3.5.0"
}
