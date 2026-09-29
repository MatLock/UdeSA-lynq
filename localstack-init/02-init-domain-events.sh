#!/bin/bash
# LocalStack init hook (runs from /etc/localstack/init/ready.d once SNS and SQS are ready).
# Creates the lynq-domain-events topic lynq-app-backend publishes to, and the
# lynq-analytics-events queue lynq-analytics consumes, with its dead-letter queue.
# The subscription uses raw message delivery: the queue receives the event
# envelope as published, not wrapped in an SNS notification.
set -euo pipefail

TOPIC="${DOMAIN_EVENTS_TOPIC:-lynq-domain-events}"
QUEUE="${ANALYTICS_EVENTS_QUEUE:-lynq-analytics-events}"
DLQ="${QUEUE}-dlq"
MAX_RECEIVE_COUNT="${ANALYTICS_EVENTS_MAX_RECEIVE_COUNT:-5}"

TOPIC_ARN=$(awslocal sns create-topic --name "${TOPIC}" --query TopicArn --output text)

DLQ_URL=$(awslocal sqs create-queue --queue-name "${DLQ}" \
  --attributes MessageRetentionPeriod=1209600 --query QueueUrl --output text)
DLQ_ARN=$(awslocal sqs get-queue-attributes --queue-url "${DLQ_URL}" \
  --attribute-names QueueArn --query Attributes.QueueArn --output text)

QUEUE_URL=$(awslocal sqs create-queue --queue-name "${QUEUE}" --query QueueUrl --output text)
awslocal sqs set-queue-attributes --queue-url "${QUEUE_URL}" --attributes "{
  \"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"${MAX_RECEIVE_COUNT}\\\"}\"
}"
QUEUE_ARN=$(awslocal sqs get-queue-attributes --queue-url "${QUEUE_URL}" \
  --attribute-names QueueArn --query Attributes.QueueArn --output text)

if ! awslocal sns list-subscriptions-by-topic --topic-arn "${TOPIC_ARN}" \
    --query "Subscriptions[?Endpoint=='${QUEUE_ARN}'].SubscriptionArn" --output text | grep -q .; then
  awslocal sns subscribe --topic-arn "${TOPIC_ARN}" --protocol sqs \
    --notification-endpoint "${QUEUE_ARN}" --attributes RawMessageDelivery=true
fi

echo "LocalStack SNS/SQS ready: topic '${TOPIC}' delivers raw to '${QUEUE}', dead letters in '${DLQ}' after ${MAX_RECEIVE_COUNT} receives."
