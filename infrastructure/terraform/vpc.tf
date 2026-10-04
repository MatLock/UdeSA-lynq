data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  availability_zones = slice(data.aws_availability_zones.available.names, 0, 2)
}

resource "aws_vpc" "lynq" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "lynq-vpc"
  }
}

resource "aws_internet_gateway" "lynq" {
  vpc_id = aws_vpc.lynq.id

  tags = {
    Name = "lynq-igw"
  }
}

resource "aws_subnet" "public" {
  count = length(local.availability_zones)

  vpc_id            = aws_vpc.lynq.id
  availability_zone = local.availability_zones[count.index]
  cidr_block        = cidrsubnet(var.vpc_cidr, 4, count.index)

  tags = {
    Name                                            = "lynq-public-${local.availability_zones[count.index]}"
    "kubernetes.io/role/elb"                        = "1"
    "kubernetes.io/cluster/${var.eks_cluster_name}" = "shared"
  }
}

resource "aws_subnet" "private" {
  count = length(local.availability_zones)

  vpc_id            = aws_vpc.lynq.id
  availability_zone = local.availability_zones[count.index]
  cidr_block        = cidrsubnet(var.vpc_cidr, 4, count.index + length(local.availability_zones))

  tags = {
    Name                                            = "lynq-private-${local.availability_zones[count.index]}"
    "kubernetes.io/role/internal-elb"               = "1"
    "kubernetes.io/cluster/${var.eks_cluster_name}" = "shared"
  }
}

resource "aws_eip" "nat" {
  domain = "vpc"

  tags = {
    Name = "lynq-nat-eip"
  }

  depends_on = [aws_internet_gateway.lynq]
}

resource "aws_nat_gateway" "lynq" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id

  tags = {
    Name = "lynq-nat"
  }

  depends_on = [aws_internet_gateway.lynq]
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.lynq.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.lynq.id
  }

  tags = {
    Name = "lynq-public-rt"
  }
}

resource "aws_route_table_association" "public" {
  count = length(aws_subnet.public)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.lynq.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.lynq.id
  }

  tags = {
    Name = "lynq-private-rt"
  }
}

resource "aws_route_table_association" "private" {
  count = length(aws_subnet.private)

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}
