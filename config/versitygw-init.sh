#!/bin/sh
# Bootstrap cho versitygw: tạo bucket + gắn policy đọc ẩn danh. Idempotent,
# chạy lại thoải mái (`podman compose run --rm s3-init`).
set -eu

endpoint="${S3_ENDPOINT:-http://s3:3900}"
bucket="${S3_BUCKET:-fashionstore}"

# chờ S3 sẵn sàng (depends_on chỉ đảm bảo container đã start)
i=0
until aws --endpoint-url "$endpoint" s3api list-buckets > /dev/null 2>&1; do
    i=$((i + 1))
    if [ "$i" -ge 30 ]; then
        echo "s3 không reachable sau ${i}s" >&2
        exit 1
    fi
    sleep 1
done

# bucket tồn tại sẵn là chuyện thường, không phải lỗi
aws --endpoint-url "$endpoint" s3api create-bucket --bucket "$bucket" 2>/dev/null || true

cat > /tmp/policy.json <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "PublicRead",
            "Effect": "Allow",
            "Principal": "*",
            "Action": "s3:GetObject",
            "Resource": "arn:aws:s3:::${bucket}/*"
        }
    ]
}
EOF

aws --endpoint-url "$endpoint" s3api put-bucket-policy --bucket "$bucket" --policy file:///tmp/policy.json

echo "bucket ${bucket} đã sẵn sàng (đọc ẩn danh qua policy)"
