output "lambda_name" {
  value = aws_lambda_function.demo.function_name
}

output "sideload_bucket" {
  value = aws_s3_bucket.sideload.id
}
