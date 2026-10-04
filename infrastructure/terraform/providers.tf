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
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }
}

provider "kubernetes" {
  host                   = aws_eks_cluster.lynq.endpoint
  cluster_ca_certificate = base64decode(aws_eks_cluster.lynq.certificate_authority[0].data)

  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    args        = ["eks", "get-token", "--cluster-name", aws_eks_cluster.lynq.name, "--region", var.aws_region]
  }
}

provider "helm" {
  kubernetes {
    host                   = aws_eks_cluster.lynq.endpoint
    cluster_ca_certificate = base64decode(aws_eks_cluster.lynq.certificate_authority[0].data)

    exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "aws"
      args        = ["eks", "get-token", "--cluster-name", aws_eks_cluster.lynq.name, "--region", var.aws_region]
    }
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
