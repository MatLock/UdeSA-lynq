# ---------------------------------------------------------------------------
# Self-managed MySQL + Redis host reachable from the EKS cluster over the
# internal network (same VPC). Two security groups (one per port) allow inbound
# traffic from the internal CIDR; the EC2 instance attaches both.
# ---------------------------------------------------------------------------

# Latest Amazon Linux 2023 AMI, unless an explicit AMI id is provided.
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "state"
    values = ["available"]
  }
}

locals {
  ec2_ami_id = var.ec2_ami_id != "" ? var.ec2_ami_id : data.aws_ami.al2023.id
}

# SG #1 — MySQL (3306) from the internal network.
resource "aws_security_group" "mysql" {
  name        = "lynq-mysql-sg"
  description = "Allow MySQL (3306) from the internal network"
  vpc_id      = var.vpc_id

  ingress {
    description = "MySQL from internal network"
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    cidr_blocks = [var.internal_cidr]
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "lynq-mysql-sg"
  }
}

# SG #3 — SSH (22) from a single parameterizable IP (set at apply time).
resource "aws_security_group" "ssh" {
  name        = "lynq-ssh-sg"
  description = "Allow SSH (22) from a specific admin IP"
  vpc_id      = var.vpc_id

  ingress {
    description = "SSH from admin IP"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_allowed_cidr]
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "lynq-ssh-sg"
  }
}

# SG #2 — Redis (6379) from the internal network.
resource "aws_security_group" "redis" {
  name        = "lynq-redis-sg"
  description = "Allow Redis (6379) from the internal network"
  vpc_id      = var.vpc_id

  ingress {
    description = "Redis from internal network"
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = [var.internal_cidr]
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "lynq-redis-sg"
  }
}

# The MySQL + Redis VM.
resource "aws_instance" "redis_db" {
  ami           = local.ec2_ami_id
  instance_type = var.ec2_instance_type
  subnet_id     = var.subnet_id

  vpc_security_group_ids = [
    aws_security_group.mysql.id,
    aws_security_group.redis.id,
    aws_security_group.ssh.id,
  ]

  key_name = var.ec2_key_name != "" ? var.ec2_key_name : null

  tags = {
    Name = "ec2-lynq-redis-db"
  }
}
