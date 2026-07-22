# ---------------------------------------------------------------------------
# TLS certificate (ACM) + DNS (Cloudflare) for the public API host.
#
# Flow: ACM issues a cert for var.ingress_host -> DNS validation creates the
# CNAME(s) in Cloudflare -> once validated, the ARN is fed into the chart's
# Ingress -> after the release, api.<domain> is pointed at the ALB.
# ---------------------------------------------------------------------------

resource "aws_acm_certificate" "lynq" {
  domain_name       = var.ingress_host
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

# One Cloudflare CNAME per ACM validation option.
resource "cloudflare_record" "acm_validation" {
  for_each = {
    for dvo in aws_acm_certificate.lynq.domain_validation_options : dvo.domain_name => {
      name  = dvo.resource_record_name
      type  = dvo.resource_record_type
      value = dvo.resource_record_value
    }
  }

  zone_id = var.cloudflare_zone_id
  name    = each.value.name
  type    = each.value.type
  value   = each.value.value
  ttl     = 60
  proxied = false

  # ACM validation records are recreated if the cert changes.
  allow_overwrite = true
}

# Waits until ACM sees the validation records and issues the certificate.
resource "aws_acm_certificate_validation" "lynq" {
  certificate_arn         = aws_acm_certificate.lynq.arn
  validation_record_fqdns = [for r in cloudflare_record.acm_validation : r.hostname]
}

# ---------------------------------------------------------------------------
# Point the public host at the ALB created by the AWS Load Balancer Controller.
# The ALB hostname only exists after the release reconciles, so this reads it
# from the Ingress status. NOTE: ALB provisioning is async — if the hostname is
# not ready on the first apply, re-run `terraform apply`.
#
# proxied = false (DNS-only): TLS is terminated at the ALB with the ACM cert,
# so Cloudflare must not proxy/re-terminate this record.
# ---------------------------------------------------------------------------
data "kubernetes_ingress_v1" "backend" {
  metadata {
    name      = "lynq-app-backend-ingress"
    namespace = var.namespace
  }
  depends_on = [helm_release.lynq]
}

resource "cloudflare_record" "api" {
  zone_id = var.cloudflare_zone_id
  name    = var.ingress_host
  type    = "CNAME"
  value   = data.kubernetes_ingress_v1.backend.status[0].load_balancer[0].ingress[0].hostname
  ttl     = 300
  proxied = false
}
