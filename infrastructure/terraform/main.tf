locals {
  db_address = "${var.db_host}:${var.db_port}"

  db_url_iam          = "jdbc:mysql://${local.db_address}/lynq_iam_db"
  db_url_backend      = "jdbc:mysql://${local.db_address}/lynq_backend_db"
  db_url_file_storage = "jdbc:mysql://${local.db_address}/lynq_file_storage_db"
  db_url_analytics    = "jdbc:mysql://${local.db_address}/lynq_analytics_db"
  db_url_agent        = "mysql+aiomysql://${urlencode(var.db_username)}:${urlencode(var.db_password)}@${local.db_address}/lynq_agent_db"

  # Chart value overrides that fill the REPLACE_* placeholders in k8s_values-prod.yaml.
  # The bucket name goes to lynq-file-storage: it is the only service that talks to
  # S3, and lynq-app-backend delegates every file operation to it.
  chart_overrides = {
    "ingress.host"                                         = var.ingress_host
    "ingress.certificateArn"                               = aws_acm_certificate_validation.lynq.certificate_arn
    "lynq_iam.config.DB_URL"                               = local.db_url_iam
    "lynq_iam.config.REDIS_ADDRESS"                        = var.redis_host
    "lynq_iam.config.REDIS_PORT"                           = tostring(var.redis_port)
    "lynq_app_backend.config.DB_URL"                       = local.db_url_backend
    "lynq_app_backend.config.AWS_REGION"                   = var.aws_region
    "lynq_app_backend.config.LYNQ_DOMAIN_EVENTS_TOPIC_ARN" = aws_sns_topic.domain_events.arn
    "lynq_file_storage.config.DB_URL"                      = local.db_url_file_storage
    "lynq_file_storage.config.AWS_REGION"                  = var.aws_region
    "lynq_file_storage.config.AWS_BUCKET_NAME"             = var.s3_bucket_name
    "lynq_llm.config.OLLAMA_BASE_URL"                      = var.ollama_base_url
    "lynq_llm.config.BEDROCK_MODEL_ID"                     = var.bedrock_model_id
    "lynq_llm.config.BEDROCK_REGION"                       = var.bedrock_region
    "lynq_agent.config.OLLAMA_BASE_URL"                    = var.ollama_base_url
    "lynq_agent.config.BEDROCK_MODEL_ID"                   = var.agent_bedrock_model_id
    "lynq_agent.config.BEDROCK_REGION"                     = var.bedrock_region
    "lynq_analytics.config.DB_URL"                         = local.db_url_analytics
    "lynq_analytics.config.REDIS_ADDRESS"                  = var.redis_host
    "lynq_analytics.config.REDIS_PORT"                     = tostring(var.redis_port)
    "lynq_analytics.config.AWS_REGION"                     = var.aws_region
    "lynq_analytics.config.LYNQ_ANALYTICS_EVENTS_QUEUE"    = aws_sqs_queue.analytics_events.name
  }
}

resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

resource "random_password" "internal_token" {
  length  = 48
  special = false
}

# ---------------------------------------------------------------------------
# Namespace — created by Terraform so the external Secrets can be placed in it
# before the release. The chart's createNamespace is false in prod.
# ---------------------------------------------------------------------------
resource "kubernetes_namespace" "lynq" {
  metadata {
    name = var.namespace
  }
}

# ---------------------------------------------------------------------------
# External Secrets — created outside the chart (the chart's manageSecrets is
# false in prod). Names/keys match what the deployments reference.
# ---------------------------------------------------------------------------
resource "kubernetes_secret" "dockerhub" {
  count = var.dockerhub_token == "" ? 0 : 1

  metadata {
    name      = "dockerhub-secret"
    namespace = var.namespace
  }
  type = "kubernetes.io/dockerconfigjson"
  data = {
    ".dockerconfigjson" = jsonencode({
      auths = {
        (var.dockerhub_server) = {
          username = var.dockerhub_username
          password = var.dockerhub_token
          email    = var.dockerhub_email
          auth     = base64encode("${var.dockerhub_username}:${var.dockerhub_token}")
        }
      }
    })
  }
  depends_on = [kubernetes_namespace.lynq]
}

resource "kubernetes_secret" "iam" {
  metadata {
    name      = "lynq-iam-secret"
    namespace = var.namespace
  }
  type = "Opaque"
  data = {
    DB_USERNAME    = var.db_username
    DB_PASSWORD    = var.db_password
    REDIS_USERNAME = var.redis_username
    REDIS_PASSWORD = var.redis_password
    JWT_SECRET     = random_password.jwt_secret.result
  }
  depends_on = [kubernetes_namespace.lynq]
}

resource "kubernetes_secret" "bff" {
  metadata {
    name      = "lynq-bff-secret"
    namespace = var.namespace
  }
  type = "Opaque"
  data = {
    JWT_SECRET = random_password.jwt_secret.result
  }
  depends_on = [kubernetes_namespace.lynq]
}

# No bucket credentials here: lynq-app-backend delegates all file handling to
# lynq-file-storage and holds only the file ids it returns.
resource "kubernetes_secret" "backend" {
  metadata {
    name      = "lynq-app-backend-secret"
    namespace = var.namespace
  }
  type = "Opaque"
  data = {
    DB_USERNAME           = var.db_username
    DB_PASSWORD           = var.db_password
    AWS_ACCESS_KEY_ID     = aws_iam_access_key.backend_sns.id
    AWS_SECRET_ACCESS_KEY = aws_iam_access_key.backend_sns.secret
    # Checked on /internal/**; lynq-analytics presents it to /internal/score/batch.
    LYNQ_INTERNAL_TOKEN = random_password.internal_token.result
  }
  depends_on = [kubernetes_namespace.lynq]
}

# lynq-analytics has its own schema (lynq_analytics_db) on the same MySQL host,
# reads Redis for its cache, and consumes lynq-analytics-events with the
# least-privilege SQS key from sqs.tf.
resource "kubernetes_secret" "analytics" {
  metadata {
    name      = "lynq-analytics-secret"
    namespace = var.namespace
  }
  type = "Opaque"
  data = {
    DB_USERNAME           = var.db_username
    DB_PASSWORD           = var.db_password
    REDIS_USERNAME        = var.redis_username
    REDIS_PASSWORD        = var.redis_password
    LYNQ_INTERNAL_TOKEN   = random_password.internal_token.result
    AWS_ACCESS_KEY_ID     = aws_iam_access_key.analytics_sqs.id
    AWS_SECRET_ACCESS_KEY = aws_iam_access_key.analytics_sqs.secret
  }
  depends_on = [kubernetes_namespace.lynq]
}

# lynq-file-storage owns the bucket, so the least-privilege S3 access key is
# wired into this Secret and nowhere else.
resource "kubernetes_secret" "file_storage" {
  metadata {
    name      = "lynq-file-storage-secret"
    namespace = var.namespace
  }
  type = "Opaque"
  data = {
    DB_USERNAME = var.db_username
    DB_PASSWORD = var.db_password
    # Generated by Terraform — the least-privilege S3 user's access key.
    AWS_ACCESS_KEY_ID     = aws_iam_access_key.backend_s3.id
    AWS_SECRET_ACCESS_KEY = aws_iam_access_key.backend_s3.secret
  }
  depends_on = [kubernetes_namespace.lynq]
}

# lynq-llm calls Bedrock, so it gets its own least-privilege access key —
# scoped to InvokeModel, separate from the S3 one lynq-file-storage uses.
resource "kubernetes_secret" "llm" {
  metadata {
    name      = "lynq-llm-secret"
    namespace = var.namespace
  }
  type = "Opaque"
  data = {
    AWS_ACCESS_KEY_ID     = aws_iam_access_key.llm_bedrock.id
    AWS_SECRET_ACCESS_KEY = aws_iam_access_key.llm_bedrock.secret
    LYNQ_INTERNAL_TOKEN   = random_password.internal_token.result
  }
  depends_on = [kubernetes_namespace.lynq]
}

moved {
  from = kubernetes_secret.ml
  to   = kubernetes_secret.llm
}

resource "kubernetes_secret" "agent" {
  metadata {
    name      = "lynq-agent-secret"
    namespace = var.namespace
  }
  type = "Opaque"
  data = {
    DB_URL                = local.db_url_agent
    AWS_ACCESS_KEY_ID     = aws_iam_access_key.agent_bedrock.id
    AWS_SECRET_ACCESS_KEY = aws_iam_access_key.agent_bedrock.secret
  }
  depends_on = [kubernetes_namespace.lynq]
}

resource "kubernetes_secret" "feeders" {
  metadata {
    name      = "lynq-feeders-secret"
    namespace = var.namespace
  }
  type = "Opaque"
  data = {
    LYNQ_INTERNAL_TOKEN = random_password.internal_token.result
  }
  depends_on = [kubernetes_namespace.lynq]
}

# ---------------------------------------------------------------------------
# The Lynq chart, using the prod values and overriding the REPLACE_*
# placeholders. Depends on the namespace, secrets, and bucket so ordering is
# correct (they exist before the pods that use them).
# ---------------------------------------------------------------------------
resource "helm_release" "lynq" {
  name      = var.release_name
  namespace = var.namespace
  # Namespace is created by Terraform above, not by Helm.
  create_namespace = false
  timeout          = 900

  chart  = "${path.module}/../helm"
  values = [file("${path.module}/../helm/values/k8s_values-prod.yaml")]

  dynamic "set" {
    for_each = local.chart_overrides
    content {
      name  = set.key
      value = set.value
    }
  }

  depends_on = [
    kubernetes_namespace.lynq,
    kubernetes_secret.dockerhub,
    kubernetes_secret.iam,
    kubernetes_secret.bff,
    kubernetes_secret.backend,
    kubernetes_secret.file_storage,
    kubernetes_secret.llm,
    kubernetes_secret.agent,
    kubernetes_secret.feeders,
    kubernetes_secret.analytics,
    aws_s3_bucket.lynq,
    aws_sns_topic.domain_events,
    aws_sns_topic_subscription.analytics_events,
    helm_release.lbc,
  ]
}
