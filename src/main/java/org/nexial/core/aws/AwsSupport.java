/*
 * Copyright 2012-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.aws;

import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.core.plugins.aws.AwsSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;

public abstract class AwsSupport {
    protected Logger logger = LoggerFactory.getLogger(getClass());
    protected boolean verbose;
    protected String accessKey;
    protected String secretKey;
    protected String url;
    protected Regions region;
    protected String assumeRoleArn;
    protected String assumeRoleSession;
    protected int assumeRoleDuration;

    public void setVerbose(boolean verbose) { this.verbose = verbose;}

    public void setAccessKey(String accessKey) { this.accessKey = accessKey;}

    public void setSecretKey(String secretKey) { this.secretKey = secretKey;}

    public void setUrl(String url) { this.url = url; }

    public void setRegion(Regions region) { this.region = region;}

    public void setAssumeRoleArn(String assumeRoleArn) { this.assumeRoleArn = assumeRoleArn;}

    public void setAssumeRoleSession(String assumeRoleSession) { this.assumeRoleSession = assumeRoleSession;}

    public void setAssumeRoleDuration(int assumeRoleDuration) { this.assumeRoleDuration = assumeRoleDuration;}

    public void setCredentials(AwsSettings settings) {
        setAccessKey(settings.getAccessKey());
        setSecretKey(settings.getSecretKey());
        setRegion(settings.getRegion());
        setUrl(settings.getAwsUrl());
        setAssumeRoleArn(settings.getAssumeRoleArn());
        setAssumeRoleSession(settings.getAssumeRoleSession());
        setAssumeRoleDuration(settings.getAssumeRoleDuration());
    }

    public boolean isReadyForUse() {
        return StringUtils.isNotBlank(accessKey) && StringUtils.isNotBlank(secretKey) && region != null;
    }

    @NotNull
    protected AWSCredentialsProvider resolveCredentials(Regions region) {
        AWSCredentialsProvider credProvider = resolveBasicCredentials(accessKey, secretKey);
        if (StringUtils.isBlank(assumeRoleArn)) { return credProvider; }

        return resolveAssumeRoleCredentials(credProvider, region,
                                            assumeRoleArn,
                                            assumeRoleSession,
                                            assumeRoleDuration);
    }

    @NotNull
    protected static AWSCredentialsProvider resolveAssumeRoleCredentials(AWSCredentialsProvider credProvider,
                                                                         Regions region,
                                                                         String assumeRoleArn,
                                                                         String assumeRoleSession,
                                                                         int assumeRoleDuration) {

        if (StringUtils.isBlank(assumeRoleArn)) { return credProvider; }

        String roleSession = StringUtils.defaultIfBlank(assumeRoleSession, RandomStringUtils.randomAlphabetic(5));

        AssumeRoleRequest assumeRequest = new AssumeRoleRequest().withRoleArn(assumeRoleArn)
                                                                 .withDurationSeconds(assumeRoleDuration)
                                                                 .withRoleSessionName(roleSession);

        AssumeRoleResult assumeResult = AWSSecurityTokenServiceClient.builder()
                                                                     .withCredentials(credProvider)
                                                                     .withRegion(region)
                                                                     .build()
                                                                     .assumeRole(assumeRequest);

        if (assumeResult == null || assumeResult.getCredentials() == null) {
            throw new RuntimeException("Unable to assume role with specified credential and role details");
        }

        Credentials assumed = assumeResult.getCredentials();
        BasicSessionCredentials assumedCredential = new BasicSessionCredentials(assumed.getAccessKeyId(),
                                                                                assumed.getSecretAccessKey(),
                                                                                assumed.getSessionToken());
        return new AWSStaticCredentialsProvider(assumedCredential);
    }

    @NotNull
    protected static AWSCredentialsProvider resolveBasicCredentials(String accessKey, String secretKey) {
        return new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey));
    }
}
