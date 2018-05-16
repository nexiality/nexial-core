/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.aws;

import java.io.File;
import java.io.IOException;
import javax.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.aws.AwsS3Helper.PutOption;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;

import static com.amazonaws.regions.Regions.DEFAULT_REGION;
import static org.nexial.core.NexialConst.S3_PATH_SEPARATOR;
import static org.nexial.core.NexialConst.S3_PUBLIC_URL;

public abstract class S3Support extends AwsSupport {
    protected String encoding;
    protected boolean s3PathStyleAccessEnabled;

    public void setEncoding(String encoding) { this.encoding = encoding; }

    public void setS3PathStyleAccessEnabled(boolean s3PathStyleAccessEnabled) {
        this.s3PathStyleAccessEnabled = s3PathStyleAccessEnabled;
    }

    public String importToS3(File source, String targetPath, boolean removeLocal) throws IOException {
        PutObjectResult result = copyToS3(source, targetPath);

        // no exception thrown means upload is successful
        String publicUrl = result.getMetadata().getUserMetaDataOf(S3_PUBLIC_URL);

        if (StringUtils.isBlank(publicUrl)) {
            throw new IOException(String.format("Probably the file %s is not imported to S3 as returned blank url",
                                                source));
        }

        if (removeLocal && !FileUtils.deleteQuietly(source)) {
            // ready to delete
            throw new IOException("Unable to delete file " + source + " after being copied to S3");
        }

        return publicUrl;
    }

    public String importToS3(File source, String targetPath) throws IOException {
        return importToS3(source, targetPath, true);
    }

    /**
     * Download an object from S3.
     *
     * @param bucket           the S3 bucket path.
     * @param removeFromBucket flag which decides whether to remove the file from bucket or not.
     * @return content of the file in terms of bytes.
     * @throws IOException when file failed to download or delete in case of move.
     */
    public byte[] downloadFromS3(final @NotNull String bucket, @NotNull final String key,
                                 final @NotNull boolean removeFromBucket) throws IOException {
        return newAWSS3Helper(bucket + "/" + key).copyFromS3(bucket, key, removeFromBucket);
    }

    public void delete(final @NotNull String bucket, @NotNull final String key) {
        newAWSS3Helper(bucket + S3_PATH_SEPARATOR + key).deleteS3Object(bucket, key);
    }

    /**
     * @param to S3 bucket + folder
     */
    protected PutObjectResult copyToS3(File from, String to) {
        PutOption option = new PutOption();
        option.setPublicableReadable(true);
        option.setReducedRedundancy(true);

        // conform to URL convention for path separator
        to = StringUtils.replace(to, "\\", "/");

        PutObjectResult result = newAWSS3Helper(to).copyToS3(from, option);

        if (logger.isDebugEnabled()) {
            ObjectMetadata metadata = result.getMetadata();
            logger.debug("copy-to-s3 complete. Details below...");
            logger.debug("\tLocation:      s3://" + StringUtils.appendIfMissing(to, "/") + from.getName());
            logger.debug("\tURL:           " + metadata.getUserMetaDataOf(S3_PUBLIC_URL));
            logger.debug("\tFile length:   " + from.length());
            logger.debug("\tStorage Class: " + metadata.getStorageClass());
        }

        return result;
    }

    protected byte[] copyFromS3(String fromPath, String targetFile) throws IOException {
        return newAWSS3Helper(fromPath).copyFromS3(targetFile);
    }

    protected AwsS3Helper newAWSS3Helper(String s3path) {
        AwsS3Helper s3 = new AwsS3Helper();
        if (StringUtils.isNotBlank(accessKey)) { s3.setAccessKey(accessKey); }
        if (StringUtils.isNotBlank(secretKey)) { s3.setSecretKey(secretKey); }

        s3.setRegion((region == null) ? DEFAULT_REGION : region);

        if (StringUtils.isNotBlank(s3path)) {
            s3.setBucketName(StringUtils.substringBefore(s3path, "/"));
            String subdir = StringUtils.substringAfter(s3path, "/");
            if (StringUtils.isNotEmpty(subdir)) { s3.setSubDir(subdir); }
        }

        // added "PathStyleAccessEnabled() to avoid SSL certificate issue since the adding bucket as subdomain to
        // Amazon's SSL cert would result in cert to domain name mismatch
        s3.setS3PathStyleAccessEnabled(s3PathStyleAccessEnabled);

        return s3;
    }
}
