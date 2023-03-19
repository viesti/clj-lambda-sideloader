resource "random_pet" "demo" {
}

resource "aws_lambda_function" "demo" {
  function_name = "${random_pet.demo.id}-demo"
  role          = aws_iam_role.demo.arn

  handler          = "demo.handler"
  filename         = "../target/demo.jar"
  source_code_hash = filebase64sha256("../target/demo.jar")

  runtime = "java11"

  memory_size = 3008

  environment {
    variables = {
      SIDELOAD_BUCKET = aws_s3_bucket.sideload.id
      SIDELOAD_SRC = var.src_file
      SIDELOAD_ENABLED = "true"
    }
  }
}

resource "aws_iam_role" "demo" {
  name               = "${random_pet.demo.id}-demo"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  inline_policy {
    name = "sideload"

    policy = jsonencode({
      Version = "2012-10-17"
      Statement = [
        {
          Action = [
            "s3:GetObject*"
          ]
          Effect = "Allow"
          Resource = [
            "${aws_s3_bucket.sideload.arn}/*"
          ]
        },
      ]
    })
  }
}

resource "aws_iam_role_policy_attachment" "demo" {
  role       = aws_iam_role.demo.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_s3_bucket" "sideload" {
  bucket = "${random_pet.demo.id}-demo"
}

resource "aws_s3_bucket_public_access_block" "sideload" {
  bucket = aws_s3_bucket.sideload.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
