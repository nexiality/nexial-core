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

package org.nexial.core.plugins.aws;

import java.io.File;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.IOFilePathFilter;
import org.nexial.commons.utils.TextUtils;
import org.nexial.core.IntegrationConfigException;
import org.nexial.core.aws.AwsS3Helper;
import org.nexial.core.aws.NexialS3Helper;
import org.nexial.core.model.ExecutionContext;
import org.nexial.core.model.StepResult;
import org.nexial.core.plugins.RemoteFileActionOutcome;
import org.nexial.core.plugins.base.BaseCommand;
import org.nexial.core.utils.ConsoleUtils;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.S3_PATH_SEPARATOR;
import static org.nexial.core.plugins.RemoteFileActionOutcome.TransferAction.*;
import static org.nexial.core.plugins.RemoteFileActionOutcome.TransferProtocol.AWS;
import static org.nexial.core.utils.CheckUtils.*;

/**
 * This is a class which provides various AWS S3 commands to perform various operations over S3 bucket through Nexial.
 */
public class S3Command extends BaseCommand {

    @Override
    public void init(ExecutionContext context) { super.init(context); }

    @Override
    public String getTarget() { return "aws.s3"; }

    /**
     * This is a nexial command to write file(s) to S3 bucket. The file path can contain the directory name so that all
     * the files can be added to it. It can also contain '*' wild card representing any. For example:-
     * C:/xyz/abc/nexial*.pdf. So in this case all the pdf files starting with the name nexial will be uploaded.
     *
     * @param local  the local is the source from where files are to be copied.
     * @param remote target folder in the S3 bucket to copyTo.
     * @param var    the nexial variable to which the output url is assigned to.
     * @return {@link StepResult} which returns success or fail based on file uploaded or not.
     * @throws IntegrationConfigException thrown when configuration object is not available.
     */
    public StepResult copyTo(@NotNull final String var,
                             @NotNull final String profile,
                             @NotNull final String local,
                             @NotNull final String remote) throws IntegrationConfigException {
        return moveOrCopyToS3(var, profile, local, remote, false, COPY_TO);
    }

    /**
     * Same as {@link S3Command#copyTo(String, String, String, String)}. Except that it deletes the files from the
     * local system
     * once they are uploaded.
     */
    public StepResult moveTo(@NotNull final String var,
                             @NotNull final String profile,
                             @NotNull final String local,
                             @NotNull final String remote) throws IntegrationConfigException {
        return moveOrCopyToS3(var, profile, local, remote, true, MOVE_TO);
    }

    /**
     * This is a nexial command to download file(s) from S3 bucket. The file path can contain the directory name so
     * that all the files can be added to it. It can also contain '*' wild card representing any. For example:-
     * ep/abc/nexial*.pdf. So in this case all the pdf files starting with the name nexial will be downloaded.
     *
     * @param local  the local is the target where files are to be downloaded.
     * @param remote source folder in the S3 bucket to copyFrom.
     * @param var    the nexial variable to which the output url is assigned to.
     * @return {@link StepResult} which returns success or fail based on whether the file downloaded or not.
     */
    public StepResult copyFrom(@NotNull final String var,
                               @NotNull final String profile,
                               @NotNull final String remote,
                               @NotNull final String local) throws IntegrationConfigException {
        return moveOrCopyFromS3(var, profile, remote, local, false, COPY_FROM);
    }

    /**
     * Same as {@link S3Command#copyFrom(String, String, String, String)}. Except that it deletes the files from the
     * remote location(bucket) once they are downloaded.
     */
    public StepResult moveFrom(@NotNull final String var,
                               @NotNull final String profile,
                               @NotNull final String remote,
                               @NotNull final String local) throws IntegrationConfigException {
        return moveOrCopyFromS3(var, profile, remote, local, true, MOVE_FROM);
    }

    /**
     * This is a nexial command used to delete the files from the s3 bucket based on the criteria provided
     * by the remotePath.
     *
     * @param var        nexial variable name assigned.
     * @param profile    profile name assigned to the data.
     * @param remotePath file pattern which describes what files to delete.
     * @return {@link StepResult} which specifies success or failure.
     */
    public StepResult delete(@NotNull final String var,
                             @NotNull final String profile,
                             @NotNull final String remotePath) throws IntegrationConfigException {
        requiresValidVariableName(var);

        final RemoteFileActionOutcome outcome = new RemoteFileActionOutcome().setProtocol(AWS)
                                                                             .setAction(DELETE)
                                                                             .setRemotePath(remotePath);
        final AwsS3Helper helper = initS3helper(resolveAWSSettings(profile));

        List<String> keys;
        try {
            keys = helper.listFiles(remotePath);
        } catch (Exception e) {
            outcome.setErrors(e.getMessage());
            context.setData(var, outcome.end());
            return StepResult.fail(String.format("Operation failed: %s", e.getMessage()));
        }

        String noMatchingFiles = "There are no files matching your criteria.";

        if (CollectionUtils.isNotEmpty(keys)) {
            // reverse the list so that we can delete the most significant object first
            Collections.reverse(keys);
            for (final String key : keys) {
                final String bucketName = StringUtils.substringBefore(remotePath, S3_PATH_SEPARATOR);
                final String filePath = bucketName + "/" + key;
                try {
                    helper.deleteS3Object(bucketName, key);
                    outcome.addAffected(filePath);
                } catch (Exception e) {
                    outcome.addFailed(filePath);
                    outcome.appendError(e.getMessage() + "\n");
                }
            }
        } else {
            outcome.setErrors(noMatchingFiles);
        }

        context.setData(var, outcome.end());
        ConsoleUtils.log(String.format("Outcome for the action %s is %s.", DELETE + "", outcome.toString()));

        final List<String> failedFiles = outcome.getFailed();
        if (CollectionUtils.isNotEmpty(failedFiles)) {
            return StepResult.fail(String.format("Following files failed to get deleted from S3: %s.", failedFiles));
        }

        return StepResult.success(
            CollectionUtils.isEmpty(keys) ?
            noMatchingFiles :
            String.format("The following files are successfully deleted %s.", outcome.getAffected()));
    }

    /**
     * This is method used to list the files in the S3 bucket as specified in the remotePath criteria.
     *
     * @param var        nexial variable name to which the retrieved files details are assigned.
     * @param profile    profile name in the data file.
     * @param remotePath the file criteria which specifies the files to be listed.
     * @return {@link StepResult} which specifies success or failure.
     */
    public StepResult list(@NotNull final String var,
                           @NotNull final String profile,
                           @NotNull final String remotePath) {
        requiresValidVariableName(var);

        final RemoteFileActionOutcome outcome = new RemoteFileActionOutcome().setProtocol(AWS)
                                                                             .setAction(LIST)
                                                                             .setRemotePath(remotePath);
        List<String> keys;
        try {
            keys = filterFiles(profile, remotePath);
        } catch (Exception e) {
            outcome.setErrors(e.getMessage());
            context.setData(var, outcome.end());
            return StepResult.fail(String.format("Failed to list: %s", e.getMessage()));
        }

        if (CollectionUtils.isNotEmpty(keys)) {
            for (String key : keys) {
                final String bucketName = StringUtils.substringBefore(remotePath, "/");
                outcome.getAffected().add(StringUtils.joinWith("/", bucketName, key));
            }

            context.setData(var, outcome.end());
            return StepResult.success(String.format("Successfully retrieved files from %s", remotePath));
        }

        final String emptyListMessage = "List empty as there are no files matching the criteria.";
        outcome.setErrors(emptyListMessage);
        context.setData(var, outcome.end());
        return StepResult.success(emptyListMessage);
    }

    /**
     * Same as {@link S3Command#list(String, String, String)}. Except that it checks whether there is atleast one
     * file matching the criteria or not.
     */
    public StepResult assertPresent(@NotNull final String profile, @NotNull final String remotePath) {
        return assertFileList(profile, remotePath, true);
    }

    /**
     * Same as {@link S3Command#list(String, String, String)}. Except that it ensures that there are no files matching
     * the criteria.
     */
    public StepResult assertNotPresent(@NotNull final String profile, @NotNull final String remotePath) {
        return assertFileList(profile, remotePath, false);
    }

    /**
     * Check if there is at least one file matching the criteria or not. If yes the result is success else fail.
     *
     * @param profile    profile name in the data file.
     * @param remotePath the file criteria which specifies the files to be listed.
     * @return {@link StepResult} which specifies success or failure.
     */
    private StepResult assertFileList(@NotNull final String profile,
                                      @NotNull final String remotePath,
                                      @NotNull final boolean assertPresent) {
        List<String> keys;
        try {
            keys = filterFiles(profile, remotePath);
        } catch (Exception e) {
            return StepResult.fail(String.format("Operation failed due to the following error: %s", e.getMessage()));
        }

        boolean filesPresent = CollectionUtils.isNotEmpty(keys);

        if (assertPresent) {
            if (filesPresent) {
                return StepResult.success(String.format("Successfully retrieved files from %s.", remotePath));
            }

            return StepResult.fail("There are no files matching the criteria.");
        }

        if (!filesPresent) { return StepResult.success("No files matching the criteria"); }
        return StepResult.fail(String.format("Files found: %s", TextUtils.toString(keys, ", ")));
    }

    /**
     * Filter the files/folders based on the criteria and retrieve the keys corresponding to them. If includeFolders
     * is set to true then folders matching the criteria are also retrieved. Else only files matching will be retrieved.
     *
     * @param profile    profile name set in the data file.
     * @param remotePath criteria which specifies what files to be retrieved.
     * @return {@link StepResult} which specifies success or failure.
     */
    private List<String> filterFiles(@NotNull final String profile, @NotNull final String remotePath)
        throws IntegrationConfigException {
        requiresNotBlank(profile, "Invalid profile", profile);
        requiresNotBlank(remotePath, "Invalid remotePath", remotePath);

        try {
            return initS3helper(resolveAWSSettings(profile)).listFiles(remotePath);
        } catch (Exception e) {
            ConsoleUtils.error("Error occurred: " + e.getMessage());
            throw e;
        }
    }

    /**
     * This method is used to move or copy file(s) from S3 bucket specified in the remote path to the systemPath.
     *
     * @param var              nexial variable set.
     * @param systemPath       path of the system where to save the downloaded file.
     * @param s3BucketPath     destination path of the system.
     * @param removeFromBucket flag to decide whether to move or copy file from S3 object.
     * @param action           {@link RemoteFileActionOutcome.TransferAction} specifies the operation to perform.
     * @return {@link StepResult} which specifies success or failure.
     */
    private StepResult moveOrCopyFromS3(@NotNull final String var,
                                        @NotNull final String profile,
                                        @NotNull final String s3BucketPath,
                                        @NotNull final String systemPath,
                                        @NotNull final boolean removeFromBucket,
                                        @NotNull final RemoteFileActionOutcome.TransferAction action)
        throws IntegrationConfigException {

        s3CommandValidations(var, s3BucketPath, systemPath, true);
        final RemoteFileActionOutcome outcome = new RemoteFileActionOutcome().setLocalPath(systemPath)
                                                                             .setRemotePath(s3BucketPath)
                                                                             .setProtocol(AWS)
                                                                             .setAction(action);

        final AwsS3Helper helper = initS3helper(resolveAWSSettings(profile));

        final List<String> keys;
        try {
            keys = helper.listFiles(s3BucketPath);
        } catch (Exception e) {
            outcome.end().setErrors(e.getMessage());
            return StepResult.fail(e.getMessage());
        }

        String msgNoMatches = "There are no files matching your criteria.";

        if (CollectionUtils.isNotEmpty(keys)) {
            for (final String key : keys) {
                final String bucketName = StringUtils.substringBefore(s3BucketPath, "/");
                final String filePath = bucketName + "/" + key;
                try {
                    byte[] content = helper.copyFromS3(bucketName, key, removeFromBucket);
                    String affectedFile = StringUtils.appendIfMissing(systemPath, separator) +
                                          (key.contains("/") ? StringUtils.substringAfterLast(key, "/") : key);
                    FileUtils.writeByteArrayToFile(new File(affectedFile), content);
                    outcome.addAffected(affectedFile);
                } catch (Exception e) {
                    outcome.addFailed(filePath);
                    outcome.appendError(e.getMessage() + "\n");
                }
            }
        } else {
            outcome.setErrors(msgNoMatches);
        }

        context.setData(var, outcome.end());
        ConsoleUtils.log(String.format("Outcome for the action %s is %s.", action + "", outcome.toString()));

        final List<String> failedFiles = outcome.getFailed();
        if (CollectionUtils.isNotEmpty(failedFiles)) {
            return StepResult.fail(String.format("Following downloads from S3 failed: %s.", failedFiles.toString()));
        }

        return StepResult.success(
            CollectionUtils.isEmpty(keys) ?
            msgNoMatches :
            String.format("The file(s) are %s to the local path '%s'. The files are %s.",
                          removeFromBucket ? "moved" : "uploaded",
                          systemPath,
                          outcome.getAffected().toString()));
    }

    /**
     * This method moves or copy file(s) to the S3 bucket path from the local path. It gives success or failure based on
     * whether the operation is completed or not. Also it sets the context variable with appropriate key value pair of
     * the variable passed in along with outcome that is generated.
     *
     * @param var         nexial variable to set.
     * @param systemPath  the path on the local system where the file resides.
     * @param s3Path      the destination path where the file should be uploaded.
     * @param removeLocal flag which decides whether to move the file or copy.
     * @param action      {@link RemoteFileActionOutcome.TransferAction} action which specifies
     *                    the operation to perform.
     * @return {@link StepResult} which returns success or fail based on file(s) moved/uploaded or not.
     */
    private StepResult moveOrCopyToS3(@NotNull final String var,
                                      @NotNull final String profile,
                                      @NotNull final String systemPath,
                                      @NotNull final String s3Path,
                                      @NotNull final boolean removeLocal,
                                      @NotNull final RemoteFileActionOutcome.TransferAction action)
        throws IntegrationConfigException {
        s3CommandValidations(var, s3Path, systemPath, false);

        RemoteFileActionOutcome outcome = new RemoteFileActionOutcome().setLocalPath(systemPath)
                                                                       .setRemotePath(s3Path)
                                                                       .setProtocol(AWS)
                                                                       .setAction(action);

        final List<String> files = new IOFilePathFilter().filterFiles(systemPath);
        if (CollectionUtils.isEmpty(files)) {
            String message = String.format("No files matched to %s.", systemPath);
            context.setData(var, outcome.end().setErrors(message));
            return StepResult.success(message);
        }

        AwsS3Helper helper = initS3helper(resolveAWSSettings(profile));
        for (final String file : files) {
            try {
                outcome.addAffected(helper.importToS3(new File(file), s3Path, removeLocal));
            } catch (Exception ase) {
                outcome.addFailed(file);
                outcome.appendError(ase.getMessage() + "\n");
            }
        }

        outcome.end();
        context.setData(var, outcome);
        ConsoleUtils.log("Outcome is " + outcome.toString());

        if (StringUtils.isNotEmpty(outcome.getErrors())) {
            return StepResult.fail(String.format("Operation failed: %s", outcome.getErrors()));
        }

        return StepResult.success(String.format("The file(s) are %s to the target path '%s': %s.",
                                                removeLocal ? "moved" : "copied",
                                                s3Path,
                                                TextUtils.toString(outcome.getAffected(), "\n")));
    }

    /**
     * Retrieves the AwsSettings corresponding to the profile name passed in.
     *
     * @param profile the profile name.
     * @return AWSSetting corresponding to the profile.
     */
    private AwsSettings resolveAWSSettings(@NotNull final String profile) throws IntegrationConfigException {
        AwsSettings awsSettings = AwsSettings.resolveFrom(context, profile);
        requiresNotNull(awsSettings, "Unable to resolve AWS credentials.");
        return awsSettings;
    }

    /**
     * Initialize helper with aws credentials.
     *
     * @return {@link NexialS3Helper} with aws credentials set accordingly.
     */
    private AwsS3Helper initS3helper(AwsSettings settings) {
        AwsS3Helper helper = new AwsS3Helper();
        helper.setAccessKey(settings.getAccessKey());
        helper.setSecretKey(settings.getSecretKey());
        helper.setRegion(settings.getRegion());
        // added to avoid SSL certificate issue since the adding bucket as subdomain to Amazon's SSL cert would result
        // in cert to domain name mismatch
        helper.setS3PathStyleAccessEnabled(true);
        return helper;
    }

    /**
     * Validations that needs to be passed so as to perform the move or copy files to bucket.
     *
     * @param var        variable name of the ouput generated in the nexial.
     * @param remotePath path of the S3 buckect where to move/copy the file(s).
     */
    private void s3CommandValidations(final String var,
                                      final String remotePath,
                                      final String localPath,
                                      final boolean checkReadWritableDirectory) {
        isValidVariable(var);

        requiresNotBlank(remotePath, "Target path cannot be blank.");
        requiresNotNull(remotePath, "Target path cannot be null.");

        requiresNotBlank(localPath, "Local path cannot be blank.");
        requiresNotNull(localPath, "Local path cannot be null.");

        if (checkReadWritableDirectory) {
            requiresReadWritableDirectory(localPath, "read/write permissions required", localPath);
        }
    }
}
