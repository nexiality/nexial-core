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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.CollectionUtil;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.plugins.aws.AwsSettings;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;

import static com.amazonaws.SDKGlobalConfiguration.DISABLE_CERT_CHECKING_SYSTEM_PROPERTY;
import static com.amazonaws.regions.Regions.DEFAULT_REGION;
import static com.amazonaws.services.s3.model.CannedAccessControlList.PublicRead;
import static com.amazonaws.services.s3.model.StorageClass.ReducedRedundancy;
import static org.nexial.commons.utils.FilePathFilter.REGEX_FOR_ANY;
import static org.nexial.core.NexialConst.*;

public class AwsS3Helper {
    // characters that must be escaped in order NOT to be mistaken as part of regex
    private static final char[] REGEX_ESCAPE_CHARS = ".-()[]+,".toCharArray();

    private String accessKey;
    private String secretKey;
    private Regions region;
    private String assumeRoleArn;
    private String assumeRoleSession;
    private int assumeRoleDuration;
    private String bucketName;
    private String subDir;
    private boolean s3PathStyleAccessEnabled = true;

    public static class PutOption {
        private boolean publiclyReadable;
        private boolean reducedRedundancy;

        public boolean isPubliclyReadable() { return publiclyReadable;}

        public void setPubliclyReadable(boolean publiclyReadable) { this.publiclyReadable = publiclyReadable;}

        public boolean isReducedRedundancy() { return reducedRedundancy;}

        public void setReducedRedundancy(boolean reducedRedundancy) { this.reducedRedundancy = reducedRedundancy;}
    }

    public void setAccessKey(String accessKey) { this.accessKey = accessKey;}

    public void setSecretKey(String secretKey) { this.secretKey = secretKey;}

    public void setRegion(Regions region) { this.region = region;}

    public void setBucketName(String bucketName) { this.bucketName = bucketName;}

    public void setSubDir(String subDir) { this.subDir = subDir;}

    public void setS3PathStyleAccessEnabled(boolean s3PathStyleAccessEnabled) {
        this.s3PathStyleAccessEnabled = s3PathStyleAccessEnabled;
    }

    public void setAssumeRoleArn(String assumeRoleArn) { this.assumeRoleArn = assumeRoleArn;}

    public void setAssumeRoleSession(String assumeRoleSession) { this.assumeRoleSession = assumeRoleSession;}

    public void setAssumeRoleDuration(int assumeRoleDuration) { this.assumeRoleDuration = assumeRoleDuration;}

    public void setCredentials(AwsSettings settings) {
        setAccessKey(settings.getAccessKey());
        setSecretKey(settings.getSecretKey());
        setRegion(settings.getRegion());
        setAssumeRoleArn(settings.getAssumeRoleArn());
        setAssumeRoleSession(settings.getAssumeRoleSession());
        setAssumeRoleDuration(settings.getAssumeRoleDuration());
    }

    public void parseObjectPath(String path) {
        if (StringUtils.isBlank(path)) { return; }

        bucketName = StringUtils.substringBefore(path, S3_PATH_SEPARATOR);
        subDir = StringUtils.substringAfter(path, S3_PATH_SEPARATOR);
    }

    public PutObjectResult copyToS3(File file, PutOption options) {
        assert StringUtils.isNotBlank(accessKey);
        assert StringUtils.isNotBlank(secretKey);
        assert StringUtils.isNotBlank(bucketName);
        assert file != null;
        assert file.canRead();

        beforeS3Connection();
        AmazonS3 s3Client = region == null ? newS3Client() : newS3Client(region);

        String objectPath = (subDir != null ? StringUtils.appendIfMissing(subDir, "/") : "") + file.getName();
        PutObjectRequest request = new PutObjectRequest(bucketName, objectPath, file);
        if (options != null) {
            if (options.isPubliclyReadable()) { request = request.withCannedAcl(PublicRead); }
            if (options.isReducedRedundancy()) { request.setStorageClass(ReducedRedundancy); }
        }

        PutObjectResult result = s3Client.putObject(request);
        if (result != null) {
            URL url = s3Client.getUrl(bucketName, request.getKey());
            if (url != null) {
                String s3url = url.toString();
                // convert
                //      https://{bucket}.s3-{region}.amazonaws.com/{object_path}  OR
                //      https://{bucket}.s3.{region}.amazonaws.com/{object_path}
                // into
                //      https://s3.{region}.amazonaws.com/{bucket}/{object_path}
                s3url = RegexUtils.replace(s3url,
                                           "(https\\:\\/\\/)(.+)\\.(s3)[\\.\\-](.+\\.amazonaws\\.com)(.+)",
                                           "$1$3.$4/$2$5");
                result.getMetadata().addUserMetadata(S3_PUBLIC_URL, s3url);
            }
        }

        afterS3Connection();
        return result;
    }

    public String importToS3(File source, String targetPath, boolean removeLocal) throws IOException {
        assert StringUtils.isNotBlank(accessKey);
        assert StringUtils.isNotBlank(secretKey);
        assert source != null;
        assert source.canRead();
        assert StringUtils.isNotBlank(targetPath);

        parseObjectPath(targetPath);
        assert StringUtils.isNotBlank(bucketName);

        PutOption option = new PutOption();
        // option.setPubliclyReadable(true);
        option.setReducedRedundancy(true);

        PutObjectResult result = copyToS3(source, option);
        if (result == null) { throw new IOException("Unable to import to S3 successfully"); }

        // no exception thrown means upload is successful
        String publicUrl = result.getMetadata().getUserMetaDataOf(S3_PUBLIC_URL);
        if (StringUtils.isBlank(publicUrl)) {
            throw new IOException(String.format("Probably the file %s is not imported to S3 as returned blank url",
                                                source));
        }

        // ready to delete
        if (removeLocal && !FileUtils.deleteQuietly(source)) {
            throw new IOException("Unable to delete file " + source + " after being copied to S3");
        }

        return publicUrl;
    }

    public byte[] copyFromS3(String name) throws IOException {
        return copyFromS3(bucketName, StringUtils.appendIfMissing(subDir, "/") + name, false);
    }

    /**
     * Returns the content of the file for the given key name and bucket.
     *
     * @param bucket           bucket name.
     * @param key              s3 object key.
     * @param removeFromBucket flag to check whether to delete the file after sending the contents or not.
     * @return content of file as bytes.
     * @throws IOException in case of failure in sending the data as bytes or fail to delete the file.
     */
    public byte[] copyFromS3(@NotNull final String bucket,
                             @NotNull final String key,
                             @NotNull final boolean removeFromBucket)
        throws IOException {
        assert StringUtils.isNotBlank(accessKey);
        assert StringUtils.isNotBlank(secretKey);
        assert StringUtils.isNotBlank(bucket);
        assert StringUtils.isNotBlank(key);

        beforeS3Connection();
        S3Object s3Object = newS3Client().getObject(new GetObjectRequest(bucket, key));
        byte[] contents = getS3ObjectContent(s3Object);

        if (removeFromBucket) { deleteS3Object(bucket, key); }

        afterS3Connection();
        return contents;
    }

    /**
     * Delete the S3 object in the bucket name with the specified key.
     *
     * @param bucket bucket name.
     * @param key    s3 object key.
     */
    public void deleteS3Object(@NotNull final String bucket, @NotNull final String key) {
        assert StringUtils.isNotBlank(bucket);
        assert StringUtils.isNotBlank(key);
        beforeS3Connection();
        newS3Client().deleteObject(new DeleteObjectRequest(bucket, key));
        afterS3Connection();
    }

    /**
     * Retrieve all the files from the S3 file Path matching the criteria.
     *
     * @param s3Path path from where files are downloaded.
     * @return file names returned from S3.
     */
    public List<String> listFiles(@NotNull final String s3Path) {
        if (StringUtils.isEmpty(s3Path)) { return null; }
        // assumes that the path before first / is the bucket
        bucketName = StringUtils.substringBefore(s3Path, S3_PATH_SEPARATOR);
        String path = StringUtils.substringAfter(s3Path, S3_PATH_SEPARATOR);
        subDir = StringUtils.substringBefore(StringUtils.substringBefore(path, PREFIX_REGEX), "*");

        if (!StringUtils.contains(path, PREFIX_REGEX)) {
            // [bucket]
            // [bucket]/
            // [bucket]/subdir
            // [bucket]/subdir/
            // [bucket]/subdir/subdir2
            // [bucket]/subdir/subdir2/
            if (!StringUtils.contains(path, "*")) { return getFileKeys(REGEX_FOR_ANY); }

            // [bucket]/subdir/*
            // [bucket]/subdir/*/
            // [bucket]/subdir/*/subdir2
            // [bucket]/subdir/*/subdir2/
            String pattern = toPattern(path);
            return getFileKeys(StringUtils.isNotEmpty(pattern) ? pattern : REGEX_FOR_ANY);
        }

        // [bucket]/subdir/[REGEX:...]
        // [bucket]/subdir/[REGEX:...]/subdir2
        // [bucket]/subdir/[REGEX:...]/subdir2/
        // [bucket]/subdir/*/subdir2/[REGEX:...]
        return getFileKeys(toPattern(path));
    }

    public static String toPattern(String path) {
        if (StringUtils.isEmpty(path)) { return StringUtils.defaultString(path); }

        String delim = (StringUtils.startsWith(path, PREFIX_REGEX)) ? PREFIX_REGEX : (S3_PATH_SEPARATOR + PREFIX_REGEX);
        int delimLength = delim.length();

        int regexStartPos = StringUtils.indexOf(path, delim);
        if (regexStartPos != -1) {
            return (regexStartPos > 0 ? toSimplePattern(StringUtils.substring(path, 0, regexStartPos + 1)) : "") +
                   StringUtils.substring(path, regexStartPos + delimLength);
        }

        return toSimplePattern(path);
    }

    /**
     * added to circumvent MITM cert injection (possibly instrumented by corporate infosec/firewall team)
     */
    static void beforeS3Connection() {
        System.setProperty("BEFORE::" + DISABLE_CERT_CHECKING_SYSTEM_PROPERTY,
                           System.getProperty(DISABLE_CERT_CHECKING_SYSTEM_PROPERTY, "false"));
        System.setProperty(DISABLE_CERT_CHECKING_SYSTEM_PROPERTY, "true");
    }

    /**
     * added to circumvent MITM cert injection (possibly instrumented by corporate infosec/firewall team)
     */
    static void afterS3Connection() {
        System.setProperty(DISABLE_CERT_CHECKING_SYSTEM_PROPERTY,
                           System.getProperty("BEFORE::" + DISABLE_CERT_CHECKING_SYSTEM_PROPERTY, "false"));
        System.clearProperty("BEFORE::" + DISABLE_CERT_CHECKING_SYSTEM_PROPERTY);
    }

    /**
     * Retrieves the s3 object keys of the files in the sub directory matching the pattern if set.
     *
     * @param pattern files matching the pattern are filtered from the sub directory.
     * @return list of files matching.
     */
    protected List<String> getFileKeys(@NotNull final String pattern) {
        subDir = StringUtils.defaultString(subDir);

        // if subDir is empty
        Predicate<S3ObjectSummary> keyFilter = summary -> {
            String key = summary.getKey();
            return key.matches(pattern);
            // todo: what happens if pattern is null or .* and subdir is not empty?
            // String key = summary.getKey();
            // return pattern != null ? key.matches(pattern) :
            //        StringUtils.isNotEmpty(subDir) ?
            //        StringUtils.startsWith(key, subDir) : StringUtils.endsWith(key, S3_PATH_SEPARATOR);
        };

        Set<String> output = new HashSet<>();
        String nextMarker = null;

        beforeS3Connection();

        while (true) {
            ListObjectsRequest request = new ListObjectsRequest().withBucketName(bucketName);
            if (StringUtils.isNotEmpty(subDir)) { request = request.withPrefix(subDir); }
            if (StringUtils.isNotEmpty(nextMarker)) { request.setMarker(nextMarker); }

            ObjectListing objectListing = newS3Client().listObjects(request);

            Set<String> filtered = objectListing.getObjectSummaries().stream()
                                                .filter(keyFilter)
                                                .map(S3ObjectSummary::getKey)
                                                .collect(Collectors.toSet());
            filtered.remove(null);
            output.addAll(filtered);

            if (objectListing.isTruncated()) {
                nextMarker = objectListing.getNextMarker();
            } else {
                break;
            }
        }

        afterS3Connection();
        return CollectionUtil.toList(output);
    }

    protected static String toSimplePattern(String path) {
        String regex = path;
        for (char ch : REGEX_ESCAPE_CHARS) { regex = StringUtils.replace(regex, ch + "", "\\" + ch); }
        regex = StringUtils.replace(regex, "*", ".+");
        return regex;
    }

    private AmazonS3 newS3Client() { return newS3Client(DEFAULT_REGION); }

    private AmazonS3 newS3Client(@NotNull final Regions region) {
        // added "PathStyleAccessEnabled() to avoid SSL certificate issue since the adding bucket as subdomain to
        // Amazon's SSL cert would result in cert to domain name mismatch
        return AmazonS3ClientBuilder.standard()
                                    .withRegion(region)
                                    .withCredentials(resolveCredentials(region))
                                    .withPathStyleAccessEnabled(s3PathStyleAccessEnabled)
                                    .build();
    }

    private AWSCredentialsProvider resolveCredentials(Regions region) {
        AWSCredentialsProvider credProvider = AwsSupport.resolveBasicCredentials(accessKey, secretKey);
        if (StringUtils.isBlank(assumeRoleArn)) { return credProvider; }

        return AwsSupport.resolveAssumeRoleCredentials(credProvider, region,
                                                       assumeRoleArn,
                                                       assumeRoleSession,
                                                       assumeRoleDuration);
    }

    /**
     * Retrieves the content of the s3Object passed in.
     *
     * @param s3Object the s3 object whose contents are to be copied.
     * @return content of files as bytes.
     */
    private byte[] getS3ObjectContent(@NotNull final S3Object s3Object) throws IOException {
        byte[] buffer = new byte[8192];
        try (
            BufferedInputStream bis = new BufferedInputStream(s3Object.getObjectContent());
            ByteArrayOutputStream baos = new ByteArrayOutputStream()
        ) {
            int bytesRead = bis.read(buffer);
            while (bytesRead != -1) {
                baos.write(buffer, 0, bytesRead);
                buffer = new byte[8192];
                bytesRead = bis.read(buffer);
            }
            return baos.toByteArray();
        }
    }
}
