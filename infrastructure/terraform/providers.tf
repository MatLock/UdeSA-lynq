terraform {
  required_version = ">= 1.5"

  required_providers {
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.17"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.30"
    }
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 4.0"
    }
  }
}

# The kubernetes/helm providers target the EKS cluster via the kubeconfig
# context (set it with `aws eks update-kubeconfig` first).
provider "kubernetes" {
  config_path    = var.kubeconfig_path
  config_context = var.kube_context
}

provider "helm" {
  kubernetes {
    config_path    = var.kubeconfig_path
    config_context = var.kube_context
  }
}

provider "aws" {
  region = var.aws_region
}

# DNS for lynq.com is managed in Cloudflare (the frontend also lives there).
# Used for ACM DNS validation and the api.lynq.com record pointing at the ALB.
provider "cloudflare" {
  api_token = var.cloudflare_api_token
}
