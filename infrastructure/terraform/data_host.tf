data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

locals {
  data_host_init = templatefile("${path.module}/templates/data-host-init.sh.tftpl", {
    db_port            = var.db_port
    redis_port         = var.redis_port
    db_username_b64    = base64encode(replace(replace(var.db_username, "\\", "\\\\"), "'", "\\'"))
    db_password_b64    = base64encode(replace(replace(var.db_password, "\\", "\\\\"), "'", "\\'"))
    redis_username_b64 = base64encode(var.redis_username)
    redis_password_b64 = base64encode(var.redis_password)
    ssh_username       = var.data_host_ssh_username
    ssh_hash_b64       = base64encode(var.data_host_ssh_password_hash)
  })
}

resource "aws_security_group" "data_host" {
  name        = "lynq-data-host"
  description = "MySQL and Redis for the EKS nodes, SSH for the operator"
  vpc_id      = aws_vpc.lynq.id

  tags = {
    Name = "lynq-data-host"
  }
}

resource "aws_vpc_security_group_ingress_rule" "data_host_ssh" {
  security_group_id = aws_security_group.data_host.id
  description       = "SSH from the operator"
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
  cidr_ipv4         = var.data_host_ssh_cidr
}

resource "aws_vpc_security_group_ingress_rule" "data_host_mysql" {
  security_group_id            = aws_security_group.data_host.id
  description                  = "MySQL from the EKS nodes"
  ip_protocol                  = "tcp"
  from_port                    = var.db_port
  to_port                      = var.db_port
  referenced_security_group_id = aws_eks_cluster.lynq.vpc_config[0].cluster_security_group_id
}

resource "aws_vpc_security_group_ingress_rule" "data_host_redis" {
  security_group_id            = aws_security_group.data_host.id
  description                  = "Redis from the EKS nodes"
  ip_protocol                  = "tcp"
  from_port                    = var.redis_port
  to_port                      = var.redis_port
  referenced_security_group_id = aws_eks_cluster.lynq.vpc_config[0].cluster_security_group_id
}

resource "aws_vpc_security_group_egress_rule" "data_host_all" {
  security_group_id = aws_security_group.data_host.id
  description       = "Package installs and updates"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_instance" "data_host" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.data_host_instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.data_host.id]
  user_data              = local.data_host_init

  root_block_device {
    volume_type = "gp3"
    volume_size = var.data_host_disk_size
    encrypted   = true
  }

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  tags = {
    Name = "lynq-data-host"
  }

  depends_on = [
    aws_vpc_security_group_egress_rule.data_host_all,
    aws_vpc_security_group_ingress_rule.data_host_ssh,
    aws_vpc_security_group_ingress_rule.data_host_mysql,
    aws_vpc_security_group_ingress_rule.data_host_redis,
  ]

  lifecycle {
    ignore_changes = [ami, user_data]
  }
}

resource "aws_eip" "data_host" {
  domain   = "vpc"
  instance = aws_instance.data_host.id

  tags = {
    Name = "lynq-data-host-eip"
  }

  depends_on = [aws_internet_gateway.lynq]
}
