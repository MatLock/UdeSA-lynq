# ---------------------------------------------------------------------------
# lynq-analytics consumes the domain events lynq-app-backend publishes to
# lynq-domain-events (sns.tf) through its own queue, with a dead-letter queue
# behind it. Mirrors localstack-init/02-init-domain-events.sh: raw message
# delivery, so the queue receives the event envelope as published, and five
# receives before a message is parked in the dead-letter queue.
# ---------------------------------------------------------------------------
resource "aws_sqs_queue" "analytics_events_dlq" {
  name                      = "lynq-analytics-events-dlq"
  message_retention_seconds = 1209600
}

resource "aws_sqs_queue" "analytics_events" {
  name           = "lynq-analytics-events"
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.analytics_events_dlq.arn
    maxReceiveCount     = 5
  })
}

data "aws_iam_policy_document" "analytics_events_from_topic" {
  statement {
    sid     = "DomainEventsTopicOnly"
    effect  = "Allow"
    actions = ["sqs:SendMessage"]
    principals {
      type        = "Service"
      identifiers = ["sns.amazonaws.com"]
    }
    resources = [aws_sqs_queue.analytics_events.arn]
    condition {
      test     = "ArnEquals"
      variable = "aws:SourceArn"
      values   = [aws_sns_topic.domain_events.arn]
    }
  }
}

resource "aws_sqs_queue_policy" "analytics_events" {
  queue_url = aws_sqs_queue.analytics_events.id
  policy    = data.aws_iam_policy_document.analytics_events_from_topic.json
}

resource "aws_sns_topic_subscription" "analytics_events" {
  topic_arn            = aws_sns_topic.domain_events.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.analytics_events.arn
  raw_message_delivery = true
  depends_on           = [aws_sqs_queue_policy.analytics_events]
}

# Least-privilege consumer, wired into kubernetes_secret.analytics. Swap it for
# an IRSA role when the cluster's OIDC provider is used: the service reads the
# standard AWS credential chain, so nothing in the code changes.
resource "aws_iam_user" "analytics_sqs" {
  name = "lynq-analytics-sqs"
}

data "aws_iam_policy_document" "analytics_sqs" {
  statement {
    sid    = "ConsumeAnalyticsEvents"
    effect = "Allow"
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:ChangeMessageVisibility",
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl",
    ]
    resources = [aws_sqs_queue.analytics_events.arn]
  }
}

resource "aws_iam_user_policy" "analytics_sqs" {
  name   = "lynq-analytics-sqs-consume"
  user   = aws_iam_user.analytics_sqs.name
  policy = data.aws_iam_policy_document.analytics_sqs.json
}

resource "aws_iam_access_key" "analytics_sqs" {
  user = aws_iam_user.analytics_sqs.name
}
