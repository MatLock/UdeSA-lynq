locals {
  lbc_namespace       = "kube-system"
  lbc_service_account = "aws-load-balancer-controller"
  lbc_oidc_issuer     = replace(aws_iam_openid_connect_provider.eks.url, "https://", "")
}

data "aws_iam_policy_document" "lbc_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.eks.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.lbc_oidc_issuer}:sub"
      values   = ["system:serviceaccount:${local.lbc_namespace}:${local.lbc_service_account}"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.lbc_oidc_issuer}:aud"
      values   = ["sts.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "lbc" {
  name               = "${var.eks_cluster_name}-aws-load-balancer-controller"
  assume_role_policy = data.aws_iam_policy_document.lbc_assume.json
}

resource "aws_iam_policy" "lbc" {
  name   = "${var.eks_cluster_name}-aws-load-balancer-controller"
  policy = file("${path.module}/policies/aws-load-balancer-controller-v${var.lbc_version}.json")
}

resource "aws_iam_role_policy_attachment" "lbc" {
  role       = aws_iam_role.lbc.name
  policy_arn = aws_iam_policy.lbc.arn
}

resource "helm_release" "lbc" {
  name       = "aws-load-balancer-controller"
  namespace  = local.lbc_namespace
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  version    = var.lbc_version

  set {
    name  = "clusterName"
    value = aws_eks_cluster.lynq.name
  }

  set {
    name  = "region"
    value = var.aws_region
  }

  set {
    name  = "vpcId"
    value = aws_vpc.lynq.id
  }

  set {
    name  = "serviceAccount.name"
    value = local.lbc_service_account
  }

  set {
    name  = "serviceAccount.annotations.eks\\.amazonaws\\.com/role-arn"
    value = aws_iam_role.lbc.arn
  }

  depends_on = [
    aws_eks_addon.core,
    aws_iam_role_policy_attachment.lbc,
  ]
}
