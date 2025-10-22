import boto3
import os

session = boto3.Session(
    aws_access_key_id='test',
    aws_secret_access_key='test',
    region_name='ap-south-1'
)

s3_client = session.client(
    "s3",
    endpoint_url=f"http://localhost:4566",
    region_name="ap-south-1"
)

s3_client.create_bucket(
Bucket="java-http",
CreateBucketConfiguration={
        'LocationConstraint': 'ap-south-1'
    }
)

s3_client.put_bucket_versioning(
    Bucket='java-http',
    VersioningConfiguration={'Status': 'Enabled'}
)

# Path to the zip file
zip_file_path = '/build/tmp/agreements.zip'

# Upload the zip file to S3
with open(zip_file_path, 'rb') as data:
    s3_client.upload_fileobj(data, 'java-http', 'agreements.zip')
