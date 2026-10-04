# ---------------------------------------------------------------------------
# Bedrock access for lynq-llm and, below it, for lynq-agent.
#
# Mirrors the S3 user in s3.tf: a dedicated IAM user whose only permission is
# invoking the models in local.bedrock_invocable_models, with its access key
# wired into kubernetes_secret.llm. Swap this block for an IRSA role when the
# cluster has an OIDC provider — the service reads the standard AWS credential
# chain, so nothing in the code changes.
#
# ListFoundationModels is what GET /lynq-llm/health probes: it costs nothing and
# proves credentials, region and reachability without spending tokens.
# ---------------------------------------------------------------------------
resource "aws_iam_user" "llm_bedrock" {
  name = "lynq-llm-bedrock"
}

locals {
  bedrock_invocable_models = distinct([
    for model in concat(var.bedrock_invocable_model_ids, [var.bedrock_model_id, var.agent_bedrock_model_id]) :
    replace(model, "/^(us|eu|apac|global)\\./", "")
  ])

  bedrock_invocable_model_arns = flatten([
    for model in local.bedrock_invocable_models : [
      "arn:aws:bedrock:*::foundation-model/${model}",
      "arn:aws:bedrock:${var.bedrock_region}:*:inference-profile/${model}",
      "arn:aws:bedrock:${var.bedrock_region}:*:inference-profile/*.${model}",
    ]
  ])
}

data "aws_iam_policy_document" "llm_bedrock" {
  statement {
    sid    = "InvokeAllowedModels"
    effect = "Allow"
    actions = [
      "bedrock:InvokeModel",
      "bedrock:InvokeModelWithResponseStream",
    ]
    resources = local.bedrock_invocable_model_arns
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

resource "aws_iam_user" "agent_bedrock" {
  name = "lynq-agent-bedrock"
}

data "aws_iam_policy_document" "agent_bedrock" {
  statement {
    sid    = "InvokeAllowedModels"
    effect = "Allow"
    actions = [
      "bedrock:InvokeModel",
      "bedrock:InvokeModelWithResponseStream",
    ]
    resources = local.bedrock_invocable_model_arns
  }
}

resource "aws_iam_user_policy" "agent_bedrock" {
  name   = "lynq-agent-bedrock-access"
  user   = aws_iam_user.agent_bedrock.name
  policy = data.aws_iam_policy_document.agent_bedrock.json
}

resource "aws_iam_access_key" "agent_bedrock" {
  user = aws_iam_user.agent_bedrock.name
}
