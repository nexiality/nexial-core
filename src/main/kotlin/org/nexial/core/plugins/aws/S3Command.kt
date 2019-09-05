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
 */

package org.nexial.core.plugins.aws

import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.commons.utils.FileUtil
import org.nexial.commons.utils.IOFilePathFilter
import org.nexial.commons.utils.TextUtils
import org.nexial.core.IntegrationConfigException
import org.nexial.core.NexialConst.S3_PATH_SEPARATOR
import org.nexial.core.aws.AwsS3Helper
import org.nexial.core.aws.NexialS3Helper
import org.nexial.core.model.StepResult
import org.nexial.core.plugins.RemoteFileActionOutcome
import org.nexial.core.plugins.RemoteFileActionOutcome.TransferAction.*
import org.nexial.core.plugins.RemoteFileActionOutcome.TransferProtocol.AWS
import org.nexial.core.plugins.base.BaseCommand
import org.nexial.core.utils.CheckUtils.*
import org.nexial.core.utils.ConsoleUtils
import java.io.File
import java.io.File.separator

/**
 * This is a class which provides various AWS S3 commands to perform various operations over S3 bucket through Nexial.
 */
class S3Command : BaseCommand() {

    override fun getTarget(): String = "aws.s3"

    /**
     * This is a nexial command to write file(s) to S3 bucket. The file path can contain the directory name so that all
     * the files can be added to it. It can also contain '*' wild card representing any. For example:-
     * C:/xyz/abc/nexial*.pdf. So in this case all the pdf files starting with the name nexial will be uploaded.
     *
     * @param local  the local is the source from where files are to be copied.
     * @param remote target folder in the S3 bucket to copyTo.
     * @param var    the nexial variable to which the output url is assigned to.
     * @return [StepResult] which returns success or fail based on file uploaded or not.
     * @throws IntegrationConfigException thrown when configuration object is not available.
     */
    @Throws(IntegrationConfigException::class)
    fun copyTo(`var`: String, profile: String, local: String, remote: String) =
            moveOrCopyToS3(`var`, profile, local, remote, false, COPY_TO)

    /**
     * Same as [S3Command.copyTo]. Except that it deletes the files from the
     * local system
     * once they are uploaded.
     */
    @Throws(IntegrationConfigException::class)
    fun moveTo(`var`: String, profile: String, local: String, remote: String) =
            moveOrCopyToS3(`var`, profile, local, remote, true, MOVE_TO)

    /**
     * This is a nexial command to download file(s) from S3 bucket. The file path can contain the directory name so
     * that all the files can be added to it. It can also contain '*' wild card representing any. For example:-
     * ep/abc/nexial*.pdf. So in this case all the pdf files starting with the name nexial will be downloaded.
     *
     * @param local  the local is the target where files are to be downloaded.
     * @param remote source folder in the S3 bucket to copyFrom.
     * @param var    the nexial variable to which the output url is assigned to.
     * @return [StepResult] which returns success or fail based on whether the file downloaded or not.
     */
    @Throws(IntegrationConfigException::class)
    fun copyFrom(`var`: String, profile: String, remote: String, local: String) =
            moveOrCopyFromS3(`var`, profile, remote, local, false, COPY_FROM)

    /**
     * Same as [S3Command.copyFrom]. Except that it deletes the files from the
     * remote location(bucket) once they are downloaded.
     */
    @Throws(IntegrationConfigException::class)
    fun moveFrom(`var`: String, profile: String, remote: String, local: String) =
            moveOrCopyFromS3(`var`, profile, remote, local, true, MOVE_FROM)

    /**
     * This is a nexial command used to delete the files from the s3 bucket based on the criteria provided
     * by the remotePath.
     *
     * @param var        nexial variable name assigned.
     * @param profile    profile name assigned to the data.
     * @param remotePath file pattern which describes what files to delete.
     * @return [StepResult] which specifies success or failure.
     */
    @Throws(IntegrationConfigException::class)
    fun delete(`var`: String, profile: String, remotePath: String): StepResult {
        requiresValidVariableName(`var`)

        val outcome = RemoteFileActionOutcome().setProtocol(AWS).setAction(DELETE).setRemotePath(remotePath)
        val helper = initS3helper(resolveAWSSettings(profile))

        val keys: List<String>?
        try {
            keys = helper.listFiles(remotePath)
        } catch (e: Exception) {
            outcome.errors = e.message
            context.setData(`var`, outcome.end())
            return StepResult.fail("Operation failed: ${e.message}")
        }

        val noMatchingFiles = "There are no files matching your criteria."

        if (CollectionUtils.isNotEmpty(keys)) {
            // reverse the list so that we can delete the most significant object first
            keys.reverse()
            val bucketName = StringUtils.substringBefore(remotePath, S3_PATH_SEPARATOR)
            for (key in keys) {
                val filePath = "$bucketName/$key"
                try {
                    helper.deleteS3Object(bucketName, key)
                    outcome.addAffected(filePath)
                } catch (e: Exception) {
                    outcome.addFailed(filePath)
                    outcome.appendError(e.message + "\n")
                }
            }
        } else {
            outcome.errors = noMatchingFiles
        }

        context.setData(`var`, outcome.end())
        ConsoleUtils.log("Outcome for the action $DELETE is $outcome.")

        val failedFiles = outcome.failed
        return if (CollectionUtils.isNotEmpty(failedFiles))
            StepResult.fail("Following files failed to get deleted from S3: $failedFiles.")
        else
            StepResult.success(if (CollectionUtils.isEmpty(keys))
                                   noMatchingFiles
                               else
                                   "The following files are successfully deleted ${outcome.affected}.")
    }

    /**
     * This is method used to list the files in the S3 bucket as specified in the remotePath criteria.
     *
     * @param var        nexial variable name to which the retrieved files details are assigned.
     * @param profile    profile name in the data file.
     * @param remotePath the file criteria which specifies the files to be listed.
     * @return [StepResult] which specifies success or failure.
     */
    fun list(`var`: String, profile: String, remotePath: String): StepResult {
        requiresValidVariableName(`var`)

        val outcome = RemoteFileActionOutcome().setProtocol(AWS).setAction(LIST).setRemotePath(remotePath)
        val keys: List<String>?
        try {
            keys = filterFiles(profile, remotePath)
        } catch (e: Exception) {
            outcome.errors = e.message
            context.setData(`var`, outcome.end())
            return StepResult.fail("Failed to list: ${e.message}")
        }

        if (CollectionUtils.isNotEmpty(keys)) {
            val bucketName = "${StringUtils.substringBefore(remotePath, "/")}/"
            for (key in keys!!) outcome.affected.add(bucketName + key)

            context.setData(`var`, outcome.end())
            return StepResult.success("Successfully retrieved files from $remotePath")
        }

        val emptyListMessage = "List empty as there are no files matching the criteria."
        outcome.errors = emptyListMessage
        context.setData(`var`, outcome.end())
        return StepResult.success(emptyListMessage)
    }

    /**
     * Same as [S3Command.list]. Except that it checks whether there is atleast one
     * file matching the criteria or not.
     */
    fun assertPresent(profile: String, remotePath: String) = assertFileList(profile, remotePath, true)

    /**
     * Same as [S3Command.list]. Except that it ensures that there are no files matching
     * the criteria.
     */
    fun assertNotPresent(profile: String, remotePath: String) = assertFileList(profile, remotePath, false)

    /**
     * Check if there is at least one file matching the criteria or not. If yes the result is success else fail.
     *
     * @param profile    profile name in the data file.
     * @param remotePath the file criteria which specifies the files to be listed.
     * @return [StepResult] which specifies success or failure.
     */
    private fun assertFileList(profile: String, remotePath: String, assertPresent: Boolean): StepResult {
        val keys: List<String>?
        try {
            keys = filterFiles(profile, remotePath)
        } catch (e: Exception) {
            return StepResult.fail("Operation failed due to the following error: ${e.message}")
        }

        val filesPresent = CollectionUtils.isNotEmpty(keys)

        if (assertPresent) {
            return if (filesPresent)
                StepResult.success("Successfully retrieved files from $remotePath.")
            else
                StepResult.fail("There are no files matching the criteria.")
        }

        return if (!filesPresent)
            StepResult.success("No files matching the criteria")
        else
            StepResult.fail("Files found: ${TextUtils.toString(keys, ", ")}")
    }

    /**
     * Filter the files/folders based on the criteria and retrieve the keys corresponding to them. If includeFolders
     * is set to true then folders matching the criteria are also retrieved. Else only files matching will be retrieved.
     *
     * @param profile    profile name set in the data file.
     * @param remotePath criteria which specifies what files to be retrieved.
     * @return [StepResult] which specifies success or failure.
     */
    @Throws(IntegrationConfigException::class)
    private fun filterFiles(profile: String, remotePath: String): List<String>? {
        requiresNotBlank(profile, "Invalid profile", profile)
        requiresNotBlank(remotePath, "Invalid remotePath", remotePath)

        try {
            return initS3helper(resolveAWSSettings(profile)).listFiles(remotePath)
        } catch (e: Exception) {
            ConsoleUtils.error("Error occurred: ${e.message}")
            throw e
        }
    }

    /**
     * This method is used to move or copy file(s) from S3 bucket specified in the remote path to the systemPath.
     *
     * @param var              nexial variable set.
     * @param systemPath       path of the system where to save the downloaded file.
     * @param s3BucketPath     destination path of the system.
     * @param removeFromBucket flag to decide whether to move or copy file from S3 object.
     * @param action           [RemoteFileActionOutcome.TransferAction] specifies the operation to perform.
     * @return [StepResult] which specifies success or failure.
     */
    @Throws(IntegrationConfigException::class)
    private fun moveOrCopyFromS3(`var`: String,
                                 profile: String,
                                 s3BucketPath: String,
                                 systemPath: String,
                                 removeFromBucket: Boolean,
                                 action: RemoteFileActionOutcome.TransferAction): StepResult {

        // if `systemPath` is already a valid file, then we shouldn't check if it is a read/write dir
        val isSystemPathValidFile = FileUtil.isFileReadWritable(systemPath, -1)
        s3CommandValidations(`var`, s3BucketPath, systemPath, !isSystemPathValidFile)

        val outcome = RemoteFileActionOutcome().setLocalPath(systemPath)
            .setRemotePath(s3BucketPath)
            .setProtocol(AWS)
            .setAction(action)

        val helper = initS3helper(resolveAWSSettings(profile))

        val keys: List<String>?
        try {
            keys = helper.listFiles(s3BucketPath)
        } catch (e: Exception) {
            outcome.end().errors = e.message
            return StepResult.fail(e.message)
        }

        val msgNoMatches = "There are no files matching your criteria."

        if (CollectionUtils.isNotEmpty(keys)) {
            val bucketName = StringUtils.substringBefore(s3BucketPath, "/")
            for (key in keys!!) {
                val filePath = "$bucketName/$key"
                try {
                    val content = helper.copyFromS3(bucketName, key, removeFromBucket)
                    val affectedFile = if (isSystemPathValidFile)
                        systemPath
                    else {
                        StringUtils.appendIfMissing(systemPath, separator) +
                        if (key.contains("/")) StringUtils.substringAfterLast(key, "/") else key
                    }
                    FileUtils.writeByteArrayToFile(File(affectedFile), content)
                    outcome.addAffected(affectedFile)
                } catch (e: Exception) {
                    outcome.addFailed(filePath)
                    outcome.appendError("${e.message}\n")
                }
            }
        } else {
            outcome.errors = msgNoMatches
        }

        context.setData(`var`, outcome.end())
        ConsoleUtils.log("Outcome for the action ${action.toString() + ""} is $outcome.")

        val failedFiles = outcome.failed
        return if (CollectionUtils.isNotEmpty(failedFiles)) {
            StepResult.fail("Following downloads from S3 failed: $failedFiles.")
        } else
            StepResult.success(
                    if (CollectionUtils.isEmpty(keys))
                        msgNoMatches
                    else
                        "The file(s) are ${if (removeFromBucket) "moved" else "uploaded"} " +
                        "to the local path '$systemPath'. The files are ${outcome.affected}.")
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
     * @param action      [RemoteFileActionOutcome.TransferAction] action which specifies
     * the operation to perform.
     * @return [StepResult] which returns success or fail based on file(s) moved/uploaded or not.
     */
    @Throws(IntegrationConfigException::class)
    private fun moveOrCopyToS3(`var`: String,
                               profile: String,
                               systemPath: String,
                               s3Path: String,
                               removeLocal: Boolean,
                               action: RemoteFileActionOutcome.TransferAction): StepResult {

        s3CommandValidations(`var`, s3Path, systemPath, false)
        val outcome = RemoteFileActionOutcome().setLocalPath(systemPath)
            .setRemotePath(s3Path)
            .setProtocol(AWS)
            .setAction(action)

        val files = IOFilePathFilter().filterFiles(systemPath)
        if (CollectionUtils.isEmpty(files)) {
            val message = "No files matched to $systemPath."
            context.setData(`var`, outcome.end().setErrors(message))
            return StepResult.success(message)
        }

        val helper = initS3helper(resolveAWSSettings(profile))
        for (file in files) {
            try {
                outcome.addAffected(helper.importToS3(File(file), s3Path, removeLocal))
            } catch (ase: Exception) {
                outcome.addFailed(file)
                outcome.appendError("${ase.message}\n")
            }
        }

        outcome.end()
        context.setData(`var`, outcome)
        ConsoleUtils.log("Outcome is $outcome")

        return if (StringUtils.isNotEmpty(outcome.errors))
            StepResult.fail("Operation failed: ${outcome.errors}")
        else
            StepResult.success("The file(s) are ${if (removeLocal) "moved" else "copied"} " +
                               "to the target path '$s3Path': ${TextUtils.toString(outcome.affected, "\n")}.")
    }

    /**
     * Retrieves the AwsSettings corresponding to the profile name passed in.
     *
     * @param profile the profile name.
     * @return AWSSetting corresponding to the profile.
     */
    @Throws(IntegrationConfigException::class)
    private fun resolveAWSSettings(profile: String): AwsSettings {
        val awsSettings = AwsUtils.resolveAwsSettings(context, profile)
        requiresNotNull(awsSettings, "Unable to resolve AWS credentials.")
        return awsSettings!!
    }

    /**
     * Initialize helper with aws credentials.
     *
     * @return [NexialS3Helper] with aws credentials set accordingly.
     */
    private fun initS3helper(settings: AwsSettings): AwsS3Helper {
        val helper = AwsS3Helper()
        helper.setCredentials(settings)

        // added to avoid SSL certificate issue since the adding bucket as subdomain to Amazon's SSL cert would result
        // in cert to domain name mismatch
        helper.setS3PathStyleAccessEnabled(true)
        return helper
    }

    /**
     * Validations that needs to be passed so as to perform the move or copy files to bucket.
     *
     * @param var        variable name of the output generated in the nexial.
     * @param remotePath path of the S3 bucket where to move/copy the file(s).
     */
    private fun s3CommandValidations(`var`: String, remotePath: String, localPath: String, readWriteDir: Boolean) {
        isValidVariable(`var`)

        requiresNotBlank(remotePath, "Target path cannot be blank.")
        requiresNotNull(remotePath, "Target path cannot be null.")

        requiresNotBlank(localPath, "Local path cannot be blank.")
        requiresNotNull(localPath, "Local path cannot be null.")

        if (readWriteDir) {
            val localDir = StringUtils.trim(localPath)
            requiresReadWritableDirectory(localDir, "read/write permissions required", localDir)
        }
    }
}
