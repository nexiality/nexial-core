package org.nexial.core.tms.spi;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.tms.model.TestFile;
import org.nexial.core.tms.model.TmsMeta;
import org.nexial.core.tms.model.TmsTestFile;

import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.NexialConst.GSON;
import static org.nexial.core.NexialConst.Project.DEF_LOC_ARTIFACT;
import static org.nexial.core.NexialConst.Project.DEF_REL_META;

/**
 * Deal with operations relating to project.tms.json file present inside .meta directory of the project
 */
public class TmsMetaJson {

    /**
     * Resolve the meta file and parse it based on the location of the test file passed in
     *
     * @param path the path of the test file
     * @return TmsMeta object containing the contents of json
     */
    public static TmsMeta retrieveMeta(String path) {
        File metaFile = resolveMetaFile(path);
        if (!FileUtil.isFileReadable(metaFile)) {
            System.err.println("TMS meta json file not detected in the project");
            System.exit(-1);
        }
        TmsMeta tmsMeta = null;
        try {
            tmsMeta = GSON.fromJson(FileUtils.readFileToString(metaFile, DEF_CHARSET), TmsMeta.class);
            if (tmsMeta == null) {
                System.err.println("Could not read project TMS meta json file");
                System.exit(-1);
            }
        } catch (IOException e) {
            System.err.println("Unable to parse project TMS meta json file: " + e.getMessage());
            System.exit(-1);
        }

        return tmsMeta;
    }

    /**
     * Resolve the meta file based on the location of the Nexial test file
     *
     * @param path the path of the nexial test file
     * @return return the meta file
     */
    private static File resolveMetaFile(String path) {
        return new File(StringUtils.substringBefore(path, DEF_LOC_ARTIFACT) + DEF_REL_META + File.separator + "project.tms.json");
    }

    /**
     * Update the {@link TmsMeta} instance by removing a {@link TestFile} entry from the json and adding a new entry
     * and write in the json file
     *
     * @param testpath the path of the input file
     * @param file the {@link TestFile} instance
     */
    public static void updateMeta(String testpath, TestFile file) {
        TmsMeta meta = retrieveMeta(testpath);
        meta.removeFile(file);
        meta.addFile(file);
        try {
            FileUtils.writeStringToFile(resolveMetaFile(testpath), GSON.toJson(meta), DEF_CHARSET);
        } catch (Exception e) {
            System.out.println("Unable to write date into the project TMS meta json file: " + e.getMessage());
            System.exit(-1);
        }
    }

    /**
     * Retrieve the project id specified inside the tms meta json file
     *
     * @param filepath the path of the test file
     * @return the project id for the input file
     */
    public static String retrieveProjectId(String filepath) {
        TmsMeta meta = retrieveMeta(filepath);
        String projectId = meta.getProjectId();
        if (StringUtils.isEmpty(projectId)) {
            System.err.println("Project id not detected inside project TMS meta file");
            System.exit(-1);
        }
        return meta.getProjectId();
    }

    /**
     * Return the suite id corresponding to the test file pointed to by the input file path
     *
     * @param filepath the path of the nexial test file
     * @param subplan the name of the subplan to be considered if the test file is a plan
     * @return the suite id
     */
    public static String retrieveSuiteId(String filepath, String subplan) {
        TmsMeta meta = retrieveMeta(filepath);
        TestFile testFile = null;
        String relativePath = getRelativePath(filepath);
        for(TestFile file : meta.getFiles()){
            if (StringUtils.equals(file.getPath(), relativePath)){
                if (StringUtils.isNotEmpty(subplan) && StringUtils.equals(file.getSubStep(), subplan)){
                    testFile = file;
                    break;
                }
                testFile = file;
                break;
            }
        }
        if (testFile == null){
            System.out.println("There is no suite associated with the file: " + filepath);
            System.exit(-1);
        }
        return testFile.getSuiteId();
    }

    /**
     * Get relative path of the test file based on the absolute path passed in
     *
     * @param filepath the absolute path of the test file
     * @return the relative path
     */
    @Nullable
    public static String getRelativePath(String filepath) {
        return StringUtils.replace(StringUtils.substringAfter(filepath, StringUtils.substringBefore(
                filepath, "\\" + DEF_LOC_ARTIFACT)), "\\", "/");
    }

    /**
     * Get the corresponding json entry for the test file pointed to the by the input file path from the project.tms.json file
     *
     * @param filepath the path of the nexial test file
     * @param subplan the subplan name in case the test file is a plan
     * @return TmsTestFile instance representing a json enty in the project.tms.json file
     */
    public static TmsTestFile getJsonEntryForFile(String filepath, String subplan) {
        TmsMeta meta = retrieveMeta(filepath);
        if (ObjectUtils.isEmpty(meta.getFiles())) {
            List<TestFile> files = new ArrayList<>();
            meta.setFiles(files);
        } else {
            List<TestFile> files = meta.getFiles();
            for (TestFile file : files) {
                if (StringUtils.isEmpty(subplan)) {
                    if (file.getPath().equals(getRelativePath(filepath))) {
                        return new TmsTestFile(meta.getProjectId(), file);
                    }
                } else {
                    if (StringUtils.equals(file.getPath(), getRelativePath(filepath)) &&
                        StringUtils.equals(file.getSubStep(), subplan)) {
                        return new TmsTestFile(meta.getProjectId(), file);
                    }
                }
            }
        }
        return new TmsTestFile(meta.getProjectId(), null);
    }
}
