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

variable "bedrock_model_id" {
  description = <<-EOT
    Bedrock model id lynq-ml calls through the Converse API (only if
    LLM_PROVIDER=bedrock). Any Converse-capable model works, e.g.
    anthropic.claude-sonnet-4-5-20250929-v1:0, amazon.nova-pro-v1:0 or
    meta.llama3-3-70b-instruct-v1:0.
  EOT
  type        = string
  default     = "amazon.nova-pro-v1:0"
}

variable "bedrock_region" {
  description = "Region whose Bedrock endpoint lynq-ml calls (the model must be enabled there)."
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
  description = "Kubernetes minor version for the control plane. Check it is still supported before applying."
  type        = string
  default     = "1.32"
}

variable "eks_subnet_ids" {
  description = "Subnets for the control plane ENIs and the worker nodes. At least two, in different AZs, in the same VPC as vpc_id."
  type        = list(string)
}

variable "eks_public_access_cidrs" {
  description = "CIDRs allowed to reach the public Kubernetes API endpoint. Narrow this to your IP for a private setup."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "eks_node_instance_type" {
  description = "EC2 instance type for the worker nodes. t3.medium fits the 6 services plus the system pods (17-pod ENI ceiling)."
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
