output "namespace" {
  description = "Namespace the release was deployed into."
  value       = var.namespace
}

output "release_name" {
  description = "Helm release name."
  value       = helm_release.lynq.name
}

output "release_status" {
  description = "Status of the Helm release."
  value       = helm_release.lynq.status
}

output "chart_version" {
  description = "Version of the deployed chart."
  value       = helm_release.lynq.version
}

output "ingress_host" {
  description = "Public host for iam + backend (prod)."
  value       = var.ingress_host
}

output "s3_bucket_name" {
  description = "Name of the S3 bucket created by Terraform."
  value       = aws_s3_bucket.lynq.bucket
}

output "redis_db_public_ip" {
  description = "Public IP of the MySQL + Redis EC2 (for SSH; empty if the subnet assigns none)."
  value       = aws_instance.redis_db.public_ip
}

output "redis_db_private_ip" {
  description = "Private IP of the MySQL + Redis EC2 (reachable from EKS over the internal network)."
  value       = aws_instance.redis_db.private_ip
}

output "redis_db_private_dns" {
  description = "Private DNS of the MySQL + Redis EC2. Use it for db_host / redis_host."
  value       = aws_instance.redis_db.private_dns
}

output "redis_db_security_group_ids" {
  description = "Security group ids attached to the MySQL + Redis EC2."
  value       = [aws_security_group.mysql.id, aws_security_group.redis.id]
}
