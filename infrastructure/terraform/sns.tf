resource "aws_sns_topic" "domain_events" {
  name = "lynq-domain-events"
}

resource "aws_iam_user" "backend_sns" {
  name = "lynq-app-backend-sns"
}

data "aws_iam_policy_document" "backend_sns" {
  statement {
    sid       = "PublishDomainEvents"
    effect    = "Allow"
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.domain_events.arn]
  }
}

resource "aws_iam_user_policy" "backend_sns" {
  name   = "lynq-app-backend-sns-publish"
  user   = aws_iam_user.backend_sns.name
  policy = data.aws_iam_policy_document.backend_sns.json
}

resource "aws_iam_access_key" "backend_sns" {
  user = aws_iam_user.backend_sns.name
}
