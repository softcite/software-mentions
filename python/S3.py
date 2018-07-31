import os
from boto3 import client

"""
This is derived from:
https://gist.github.com/freewayz/1fbd00928058c3d682a0e25367cc8ea4
"""

class S3(object):
    
    def __init__(self, config):
        self.config = config
        if self.config['region'] is not None:
            region = self.config['region']
        else:
            region = "us-west-2"
        self.bucket_name = self.config['bucket_name']
        self.conn = client('s3', 
                            region_name=region, 
                            aws_access_key_id=self.config['aws_access_key_id'],
                            aws_secret_access_key=self.config['aws_secret_access_key'])

    def upload_file_to_s3(self, file_path, dest_path=None):
        """
        Upload given file to s3 using a managed uploader, which will split up large
        files automatically and upload parts in parallel
        """
        s3_client = self.conn
        file_name = file_path.split('/')[-1]
        if dest_path:
            if dest_path.endswith("/"):
                full_path = dest_path + file_name
            else:
                full_path = dest_path + "/" + file_name
        else:
            full_path = file_name
        s3_client.upload_file(file_path, self.bucket_name, full_path)

    def upload_object(self, body, s3_key):
        """
        Upload object to s3 key
        """
        s3_client = self.conn
        return s3_client.put_object(Body=body, Key=s3_key)

    def download_file(self, file_path, dest_path):
        """
        Download a file given a S3 path and returns the download file path.
        """
        s3_client = self.conn
        file_name = file_path.split('/')[-1]

        file_name_len = len(file_name)
        file_path_len = len(file_path)
        file_dir = dest_path + "/" + file_path[0:file_path_len - file_name_len]
        if not os.path.exists(file_dir):
            os.makedirs(file_dir)
        try:
            s3_client.download_file(
                self.bucket_name, file_path, dest_path)
            return os.path.join(file_dir, filename) 
        except:
            print('Cannot download file', file_path)
            return

    def get_s3_results(self, dir_name):
        """
        Return all contents of a given dir in s3.
        Goes through the pagination to obtain all file names.
        """
        #dir_name = dir_name.split('tmp/')[-1]
        paginator = self.conn.get_paginator('list_objects')
        s3_results = paginator.paginate(
            Bucket=self.bucket_name,
            Prefix=dir_name,
            PaginationConfig={'PageSize': 1000}
        )
        bucket_object_list = []
        for page in s3_results:
            if "Contents" in page:
                for key in page["Contents"]:
                    s3_file_name = key['Key'].split('/')[-1]
                    bucket_object_list.append(s3_file_name)
        return bucket_object_list
