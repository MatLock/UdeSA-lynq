# ---------------------------------------------------------------------------
# Bedrock access for lynq-llm.
#
# Mirrors the S3 user in s3.tf: a dedicated IAM user whose only permission is
# invoking the configured model, with its access key wired into
# kubernetes_secret.llm. Swap this block for an IRSA role when the cluster has
# an OIDC provider — the service reads the standard AWS credential chain, so
# nothing in the code changes.
#
# ListFoundationModels is what GET /lynq-llm/health probes: it costs nothing and
# proves credentials, region and reachability without spending tokens.
# ---------------------------------------------------------------------------
resource "aws_iam_user" "llm_bedrock" {
  name = "lynq-llm-bedrock"
}

locals {
  bedrock_model_bare = replace(var.bedrock_model_id, "/^(us|eu|apac|global)\\./", "")
}

data "aws_iam_policy_document" "llm_bedrock" {
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

resource "aws_iam_user_policy" "llm_bedrock" {
  name   = "lynq-llm-bedrock-access"
  user   = aws_iam_user.llm_bedrock.name
  policy = data.aws_iam_policy_document.llm_bedrock.json
}

resource "aws_iam_access_key" "llm_bedrock" {
  user = aws_iam_user.llm_bedrock.name
}

moved {
  from = aws_iam_user.ml_bedrock
  to   = aws_iam_user.llm_bedrock
}

moved {
  from = aws_iam_user_policy.ml_bedrock
  to   = aws_iam_user_policy.llm_bedrock
}

moved {
  from = aws_iam_access_key.ml_bedrock
  to   = aws_iam_access_key.llm_bedrock
}
