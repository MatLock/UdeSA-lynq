# ---------------------------------------------------------------------------
# Bedrock access for lynq-ml.
#
# Mirrors the S3 user in s3.tf: a dedicated IAM user whose only permission is
# invoking the configured model, with its access key wired into
# kubernetes_secret.ml. Swap this block for an IRSA role when the cluster has
# an OIDC provider — the service reads the standard AWS credential chain, so
# nothing in the code changes.
#
# ListFoundationModels is what GET /lynq-ml/health probes: it costs nothing and
# proves credentials, region and reachability without spending tokens.
# ---------------------------------------------------------------------------
resource "aws_iam_user" "ml_bedrock" {
  name = "lynq-ml-bedrock"
}

locals {
  bedrock_model_bare = replace(var.bedrock_model_id, "/^(us|eu|apac|global)\\./", "")
}

data "aws_iam_policy_document" "ml_bedrock" {
  statement {
    sid    = "InvokeConfiguredModel"
    effect = "Allow"
    actions = [
      "bedrock:InvokeModel",
      "bedrock:InvokeModelWithResponseStream",
    ]
    resources = [
      "arn:aws:bedrock:*::foundation-model/${local.bedrock_model_bare}",
      "arn:aws:bedrock:${var.bedrock_region}:*:inference-profile/${local.bedrock_model_bare}",
      "arn:aws:bedrock:${var.bedrock_region}:*:inference-profile/*.${local.bedrock_model_bare}",
    ]
  }

  statement {
    sid       = "HealthProbe"
    effect    = "Allow"
    actions   = ["bedrock:ListFoundationModels"]
    resources = ["*"]
  }
}

resource "aws_iam_user_policy" "ml_bedrock" {
  name   = "lynq-ml-bedrock-access"
  user   = aws_iam_user.ml_bedrock.name
  policy = data.aws_iam_policy_document.ml_bedrock.json
}

resource "aws_iam_access_key" "ml_bedrock" {
  user = aws_iam_user.ml_bedrock.name
}
