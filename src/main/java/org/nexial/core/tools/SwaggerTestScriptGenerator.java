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

package org.nexial.core.tools;

import static java.io.File.separator;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.NexialConst.ExitStatus.RC_BAD_CLI_ARGS;
import static org.nexial.core.NexialConst.ExitStatus.RC_FAILURE_FOUND;
import static org.nexial.core.NexialConst.Project.BATCH_EXT;
import static org.nexial.core.NexialConst.Ws.*;
import static org.nexial.core.excel.ExcelConfig.HEADER_TEST_STEP_DESCRIPTION;
import static org.nexial.core.tools.CliUtils.newArgOption;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nexial.core.tools.swagger.*;
import org.nexial.core.utils.JsonUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * <p>
 * This class takes a Swagger file which contains the REST API operation definitions and generates the Nexial script
 * Template and related artifacts(files like properties, data file and others). Later, Nexial script developers can
 * set the values against the configuration variables declared in the properties file and run the scripts.
 * </p>
 *
 * <p>
 * This class takes the location of the Swagger file, the location of the Nexial project and a prefix which defines the
 * version of the Swagger file.
 * </p>
 *
 * @author Dhanapathi Marepalli
 */
public class SwaggerTestScriptGenerator {
    private static final int PREFIX_MAX_LENGTH = 30;
    private static final int MAX_TAB_NAME_LENGTH_EXCEL = 31;
    private static final String STRING_SEPARATOR = "-";
    private static final String QUERY_STRING_PARAMS_APPENDER = "&";

    private static final String PARAM_TYPE_HEADER = "header";
    private static final String PARAM_TYPE_PATH = "path";
    private static final String PARAM_TYPE_COOKIE = "cookie";
    private static final String PARAM_TYPE_QUERY = "query";

    private static final String AUTH_SCHEME_BASIC = "basic";
    private static final String AUTH_SCHEME_BEARER = "bearer";
    private static final String AUTH_SCHEME_API_KEY = "apiKey";

    private static final String CMD_TYPE_WS = "ws";
    private static final String CMD_TYPE_BASE = "base";
    private static final String CMD_TYPE_JSON = "json";
    private static final String CMD_TYPE_STEP = "step";

    private static final String COMMAND_HEADER = "header(name,value)";
    private static final String COMMAND_CLEAR_HEADER = "clearHeaders(headers)";
    private static final String COMMAND_ASSERT_EQUAL = "assertEqual(expected,actual)";
    private static final String COMMAND_ASSERT_RETURN_CODE = "assertReturnCode(var,returnCode)";
    private static final String COMMAND_ASSERT_NOT_EMPTY = "assertNotEmpty(text)";
    private static final String COMMAND_ASSERT_CORRECTNESS = "assertCorrectness(json,schema)";
    private static final String COMMAND_CLEAR = "clear(vars)";
    private static final String COMMAND_OBSERVE = "observe(prompt)";

    private static final String RESPONSE_FILE_POSTFIX = "Schema.json";

    private static final String CONTENT_TYPE_OCTET_STREAM = "application/octet-stream";

    private static final List<String> SWAGGER_FILE_EXTENSIONS = Arrays.asList(".json", ".yaml", ".yml");
    private static final List<String> RESERVED_PREFIXES = Arrays.asList("Swagger", "Schema");
    private static final String NON_TEXT_MARKER = "###";

    private static final Options cmdOptions = initOptions();
    private static final String JSON_SCHEMA_URL = "https://json-schema.org/draft/2019-09/schema";
    private static final String BATCH_NAME = "nexial-swagger." + BATCH_EXT;
    private static final String DEF_SERVER_URL = "http://<DEFINE YOUR BASE URL>";
    private static final List<String> PARAMS =
            Arrays.asList("headerParams", "queryParams", "pathParams", "cookieParams");

    private static String projectDirPath;
    private static String swaggerPrefix;
    private static String swaggerFile;

    /**
     * The main method where the execution starts.
     *
     * @param args the arguments for the command to generate script out of Swagger file.
     */
    public static void main(String... args) {
        parseCLIOptions(args);

        Map<String, Object> dataMap = null;
        try {
            if (swaggerFile.endsWith("yaml") || swaggerFile.endsWith("yml")) {
                InputStream inputStream = new FileInputStream(swaggerFile);
                dataMap = new Yaml().load(inputStream);
            } else if (swaggerFile.endsWith("json")) {
                String content = new String(Files.readAllBytes(Paths.get(swaggerFile)));
                dataMap = new ObjectMapper().readValue(content, LinkedHashMap.class);
            }
        } catch (IOException ioe) {
            System.err.println("Unable to parse file '" + swaggerFile + "': " + ioe.getMessage());
            System.exit(RC_FAILURE_FOUND);
        }

        if (dataMap != null) {
            if (dataMap.containsKey("swagger")) {
                System.err.println("Older Swagger versions are not supported. Open API 3 or higher only supported.");
                System.exit(RC_FAILURE_FOUND);
            }

            if (dataMap.containsKey("openapi")) {
                JSONObject json = new JSONObject(dataMap);
                try {
                    generateFiles(json, projectDirPath);
                } catch (IOException e) {
                    System.err.println("Unable to generate project: " + swaggerPrefix);
                    System.exit(RC_FAILURE_FOUND);
                }
            }
        }
    }

    /**
     * Initialize the Commandline arguments passed in.
     */
    private static Options initOptions() {
        Options cmdOptions = new Options();
        cmdOptions.addOption(newArgOption("f", "file", "[REQUIRED] Location of a swagger file.", true));
        cmdOptions.addOption(newArgOption("d", "dir", "[REQUIRED] Location of a project directory.", true));
        cmdOptions.addOption(newArgOption("p", "prefix", "[REQUIRED] A prefix for the generated script.", true));
        return cmdOptions;
    }

    /**
     * Validates if the arguments passed to the class are valid or not and parses
     * them accordingly. Validate and parse the command line inputs.
     *
     * @param args the command line arguments received from the main method.
     */
    private static void parseCLIOptions(String[] args) {
        CommandLine cmd = CliUtils.getCommandLine(BATCH_NAME, args, cmdOptions);
        if (cmd == null) {System.exit(RC_BAD_CLI_ARGS);}

        swaggerPrefix = cmd.getOptionValue("p");
        if (swaggerPrefix.length() > PREFIX_MAX_LENGTH) {
            throw new RuntimeException("[prefix] length should not be greater than " + PREFIX_MAX_LENGTH + ".");
        }
        if (!swaggerPrefix.matches("\\S+")) {throw new RuntimeException("[prefix] should not contain spaces");}

        if (RESERVED_PREFIXES.stream().anyMatch(x -> x.equalsIgnoreCase(swaggerPrefix))) {
            throw new RuntimeException(
                    "[prefix] '" + swaggerPrefix +
                    "' is not a valid prefix. You cannot use following words as prefix " +
                    RESERVED_PREFIXES + " in uppercase or lowercase.");
        }

        projectDirPath = StringUtils.removeEnd(cmd.getOptionValue("d"), separator);

        swaggerFile = cmd.getOptionValue("f");
        if (SWAGGER_FILE_EXTENSIONS.stream().noneMatch(swaggerFile::endsWith)) {
            throw new RuntimeException("[file] is of invalid type.Only json and yaml files are supported.");
        }
        if (!new File(swaggerFile).exists()) {throw new RuntimeException("[file] does not exist.");}
    }

    /**
     * Reads the swagger contents and generates the necessary files/folders like property, data, script, request body,
     * response json, batch files etc. as part of the operation.
     *
     * @param json           swagger file content in json format.
     * @param projectDirPath the Nexial project directory path.
     * @throws IOException in case of File operation failures.
     */
    private static void generateFiles(JSONObject json, String projectDirPath) throws IOException {
        SwaggerScriptFilesCreator filesCreator = new SwaggerScriptFilesCreator();
        boolean filesValid = filesCreator.validateAndGenerateFiles(projectDirPath, swaggerPrefix);
        if (!filesValid) {System.exit(RC_FAILURE_FOUND);}

        NexialContents contents = generateNexialContent(json, projectDirPath);
        filesCreator.generateFiles(projectDirPath, contents, swaggerPrefix, swaggerFile);
    }

    /**
     * Generates various Nexial script related content like parameters, script content, data file content etc.
     * out of the Swagger file content, inside the project directory path specified.
     *
     * @param json           the swagger file content
     * @param projectDirPath the Nexial project directory path.
     * @return {@link NexialContents} related to the Swagger file.
     * @throws IOException in case of File operation failures.
     */
    private static NexialContents generateNexialContent(JSONObject json, String projectDirPath) throws IOException {
        JSONArray servers = json.optJSONArray("servers");
        String baseUrl = (Objects.isNull(servers) || servers.length() == 0) ? DEF_SERVER_URL : getBaseUrl(servers);
        JSONObject components = json.optJSONObject("components");
        JSONObject securitySchemes = components != null ? components.optJSONObject("securitySchemes") : null;

        JSONObject paths = json.optJSONObject("paths");
        JSONObject baseSecurity = getBaseSecurity(json.optJSONArray("security"), securitySchemes);

        NexialContents nexialContents = new NexialContents();
        List<SwaggerScenario> scenarios = new ArrayList<>();
        nexialContents.setScenarios(scenarios);
        nexialContents.setSchemaFiles(new HashMap<>());

        SwaggerDataVariables dataVariables = SwaggerDataVariables.getInstance();
        nexialContents.setDataVariables(dataVariables);
        dataVariables.setBaseUrl(baseUrl);

        JSONObject comp = json.optJSONObject("components");
        if (comp != null) {
            JSONObject schemas = comp.optJSONObject("schemas");
            if (schemas != null) {
                File dir = new File(
                        StringUtils.joinWith(separator, projectDirPath, "artifact", "data", swaggerPrefix, "Schema"));
                dir.mkdirs();
                for (String schema : schemas.keySet()) {
                    JSONObject schemaDef = schemas.getJSONObject(schema);
                    schemaDef.put("$schema", JSON_SCHEMA_URL);
                    String responseSchemaFile = StringUtils.joinWith(".", schema, RESPONSE_FILE_POSTFIX);

                    File file = new File(StringUtils.joinWith(separator, dir.getPath(), responseSchemaFile));
                    Files.write(file.toPath(), JsonUtils.beautify(schemaDef.toString()).getBytes());
                    nexialContents.getSchemaFiles().put(schema, file.toString());
                }
            }
        }

        // Iterate over the various paths in the Swagger json.
        Map<String, List<SwaggerScenarioVars>> scenarioVarMap = new LinkedHashMap<>();
        for (String path : paths.keySet()) {
            JSONArray parentParams = new JSONArray();
            JSONObject methods = paths.optJSONObject(path);

            JSONArray pathLevelParameters = methods.optJSONArray("parameters");
            if (pathLevelParameters != null) {
                pathLevelParameters.forEach(parentParams::put);
                methods.remove("parameters");
            }

            // Iterate over various methods like get, put, post etc.
            for (String method : methods.keySet()) {
                String title = json.optJSONObject("info").optString("title");
                String pathString = path.equals("/") ? title.replaceAll("\\s", "") : path;

                String scenarioName = getScenarioName(pathString, method, true);
                String scenarioFullName = getScenarioName(pathString, method, false);
                scenarioVarMap.put(scenarioName, new ArrayList<>());

                SwaggerScenario scenario = new SwaggerScenario();
                List<SwaggerActivity> activities = new ArrayList<>();
                boolean authenticationAdded = false;

                JSONObject methodAttributes = methods.optJSONObject(method);
                String methodDescription = methodAttributes.optString("description");
                JSONObject requestBody = methodAttributes.optJSONObject("requestBody");

                JSONObject requestBodySchema = new JSONObject();
                String contentType = "";
                String warningMessage = "";

                if (requestBody != null) {
                    JSONObject requestBodyContent = requestBody.optJSONObject("content");
                    if (requestBodyContent.optJSONObject(WS_JSON_CONTENT_TYPE) != null) {
                        requestBodySchema = getJSONSchema(requestBodyContent, components);
                        contentType       = WS_JSON_CONTENT_TYPE;
                    } else if (requestBodyContent.optJSONObject(CONTENT_TYPE_OCTET_STREAM) != null) {
                        contentType = CONTENT_TYPE_OCTET_STREAM;
                    } else {
                        warningMessage = "For the method " + method + ", the Content-Type is neither " +
                                         WS_JSON_CONTENT_TYPE + " or " + CONTENT_TYPE_OCTET_STREAM + ", which are the" +
                                         " only 2 supported content types at this point in time. Edit the script accordingly.";
                    }
                }

                createScenario(scenarioName, scenario, activities, methodDescription);
                JSONObject responses = methodAttributes.optJSONObject("responses");

                // Iterate over the responses of various methods.
                String headerName = "";
                String headerValue = "";
                for (String response : responses.keySet()) {
                    SwaggerScenarioVars swaggerScenarioVars = new SwaggerScenarioVars();
                    String scenarioPrefix = StringUtils.joinWith(".", method.toUpperCase(), response);
                    swaggerScenarioVars.setActivityName(scenarioPrefix);

                    String scenarioUrl =
                            "${baseUrl}".concat(replacePathVars(path, StringUtils.joinWith(".", scenarioPrefix, "path",
                                                                                           "")));
                    swaggerScenarioVars.setUrl(scenarioUrl);

                    SwaggerActivity activity = new SwaggerActivity();
                    String activityName = StringUtils.joinWith(".", method, response);
                    activity.setName(activityName);
                    String varName = StringUtils.joinWith(".", scenarioFullName, response);
                    String jsonFile = "";

                    if (requestBodySchema != null && components != null) {
                        String prefix = StringUtils.joinWith(".", scenarioFullName, response);
                        JSONObject requestBodyJSON
                                = getRequestBody(components, requestBodySchema, new JSONObject(), prefix);

                        if (requestBodyJSON.keySet().size() > 0) {
                            jsonFile = StringUtils.joinWith(".", scenarioFullName, response, "json");
                            createRequestJSONFile(projectDirPath, requestBodyJSON, jsonFile);
                            swaggerScenarioVars.setJson("${payloadBase}/".concat(jsonFile));
                        }
                    }

                    JSONArray parameters = methodAttributes.optJSONArray("parameters");
                    JSONObject parameterSchemas = Objects.requireNonNull(components).optJSONObject("parameters");
                    ParameterGenerationAttributes parameterGenerationAttributes =
                            new ParameterGenerationAttributes().withPath(path)
                                                               .withMethod(method)
                                                               .withResponse(response)
                                                               .withParameters(parameters)
                                                               .withParentParams(parentParams)
                                                               .withVarName(varName)
                                                               .withScenarioName(scenarioFullName)
                                                               .withParameterSchemas(parameterSchemas);
                    JSONObject params = extractParameters(parameterGenerationAttributes, swaggerScenarioVars);

                    JSONObject responseParameters = getResponseParameters(components, responses, response);

                    boolean sameAuthentication = baseSecurity != null;
                    JSONObject methodSecurity = sameAuthentication ?
                                                baseSecurity : getSecurity(securitySchemes, methodAttributes);

                    if (!authenticationAdded && methodSecurity != null) {
                        Map<String, String> securityHeaders =
                                generateSecurityVariable(methodSecurity, sameAuthentication, scenarioFullName);
                        if (securityHeaders != null) {
                            // todo: could there be multiple security headers? if so, how's the code handling such situation?
                            for (String s : securityHeaders.keySet()) {
                                headerValue = securityHeaders.get(s);
                                headerName  = s;

                                Map<String, List<String>> securityVars;
                                if (sameAuthentication) {
                                    securityVars = new LinkedHashMap<>();
                                    securityVars.put("authType", new ArrayList<>());
                                    securityVars.get("authType").add(headerValue);
                                } else {
                                    securityVars = dataVariables.getSecurityVars();
                                    securityVars.put(varName, new ArrayList<>());
                                    securityVars.get(varName).add(headerValue);
                                }
                                dataVariables.setSecurityVars(securityVars);
                            }

                            // todo: looks like we are only considering the last security header/value?!
                            activities.add(newSetupActivity(headerName, headerValue));
                        } else {
                            String authScheme = methodSecurity.optString("scheme");
                            String scheme =
                                    StringUtils.isNotEmpty(authScheme) ? authScheme : methodSecurity.optString("type");
                            activities.add(newPromptStep(
                                    "Authentication mechanism " + scheme + " is not supported. " +
                                    "Edit the script accordingly."
                                                        ));
                        }
                        authenticationAdded = true;
                    }

                    StepAttributes attributes = new StepAttributes().withPath(path)
                                                                    .withMethod(method)
                                                                    .withResponseParameters(responseParameters)
                                                                    .withRequestJSONBodyFile(jsonFile)
                                                                    .withVarName(varName)
                                                                    .withParams(params)
                                                                    .withScenarioName(scenarioFullName)
                                                                    .withWarningMessage(warningMessage)
                                                                    .withContentType(contentType);
                    activity.setSteps(generateSteps(attributes, swaggerScenarioVars));
                    activities.add(activity);
                    scenarioVarMap.get(scenarioName).add(swaggerScenarioVars);
                }

                if (StringUtils.isNotEmpty(headerName)) {activities.add(newCleanupActivity());}
                scenarios.add(scenario);
                nexialContents.setSwaggerScenarioVarsMap(scenarioVarMap);
            }
        }
        return nexialContents;
    }

    private static String replacePathVars(String path, String replacement) {
        Matcher matcher = Pattern.compile("\\{.*?}").matcher(path);
        while (matcher.find()) {
            String group = matcher.group();
            String match = StringUtils.join("${", replacement, group.substring(1));
            path = StringUtils.replace(path, group, match);
        }
        return path;
    }

    /**
     * Creates a Prompt Step with the message passed in. A new {@link SwaggerActivity} is created to which the
     * Prompt step created earlier is added. This newly created {@link SwaggerActivity} is added to the activities
     * passed in.
     *
     * @param message the caution message corresponding to the Prompt Step generated.
     */
    private static SwaggerActivity newPromptStep(String message) {
        SwaggerActivity activity = new SwaggerActivity();
        activity.setName("Warning");
        activity.setSteps(new ArrayList<SwaggerStep>() {{
            add(createStep("Warning", "Unsupported Authentication mechanism.", CMD_TYPE_STEP, COMMAND_OBSERVE,
                           message));
        }});
        return activity;
    }

    /**
     * Adds the Step where the headers needed for the Authentication is added.
     *
     * @param headerName  name of the header to be added.
     * @param headerValue the value corresponding to the header.
     * @return true/false based on whether the header initialization added or not.
     */
    private static SwaggerActivity newSetupActivity(String headerName, String headerValue) {
        SwaggerStep securityStep = createStep("set up", "Set up HTTP headers.",
                                              CMD_TYPE_WS, COMMAND_HEADER, headerName,
                                              generateNexialVariablePlaceHolderString(headerValue));

        SwaggerActivity activity = new SwaggerActivity();
        activity.setName("set up");
        activity.setSteps(new ArrayList<SwaggerStep>() {{add(securityStep);}});
        return activity;
    }

    /**
     * Resets the security header(s) as part of cleanup activity in the scenario.
     *
     * @return {@link SwaggerActivity} which does the cleanup activity.
     */
    private static SwaggerActivity newCleanupActivity() {
        SwaggerStep cleanAuthHeader = createStep("tear down", "clear HTTP headers.", CMD_TYPE_WS, COMMAND_CLEAR_HEADER,
                                                 WS_ALL_HEADERS);
        SwaggerActivity activity = new SwaggerActivity();
        activity.setName("tear down");
        activity.setSteps(new ArrayList<SwaggerStep>() {{add(cleanAuthHeader);}});
        return activity;
    }

    /**
     * Retrieves the authentication mechanism common to all the rest api's. Retrieves null if there isn't one.
     *
     * @param security        the security types.
     * @param securitySchemas the security schemas from Swagger file.
     * @return the {@link JSONObject} containing the base security details.
     */
    private static JSONObject getBaseSecurity(JSONArray security, JSONObject securitySchemas) {
        if (security == null) {return null;}
        String securitySchemaName = new ArrayList<>(((JSONObject) security.get(0)).keySet()).get(0);
        return securitySchemas.getJSONObject(securitySchemaName);
    }

    /**
     * Generates the Nexial response parameters.
     *
     * @param components retrieved from Swagger file.
     * @param responses  the response metadata.
     * @param response   the response status code.
     * @return the response parameters generated.
     */
    private static JSONObject getResponseParameters(JSONObject components, JSONObject responses, String response) {
        JSONObject responseParameters = new JSONObject();
        responseParameters.put("response", response);

        JSONObject responseAttributes = responses.optJSONObject(response);
        String responseDescription = responseAttributes.optString(HEADER_TEST_STEP_DESCRIPTION);
        responseParameters.put("responseDescription", responseDescription);

        JSONObject responseHeaders = responseAttributes.optJSONObject("headers");
        responseParameters.put("responseHeaders", responseHeaders);

        JSONObject responseBodyContent = responseAttributes.optJSONObject("content");
        if (responseBodyContent == null) {return responseParameters;}

        JSONObject responseBodySchema = getJSONSchema(responseBodyContent, components);
        if (responseBodySchema == null) {return responseParameters;}

        responseParameters.put("responseSchema",
                               getResponseSchemaJSON(responseBodySchema, components.getJSONObject("schemas")));

        String schemaRef = responses.optJSONObject(response).optJSONObject("content").optJSONObject("application/json")
                                    .optJSONObject("schema").optString("$ref");
        if (StringUtils.isNotEmpty(schemaRef)) {
            responseParameters.put("$ref", schemaRef);
        }
        return responseParameters;
    }

    /**
     * Creates the request body json file with the path specified inside the project directory and adds the json content
     * to it.
     *
     * @param projectDirPath      the path of the Nexial project.
     * @param requestBodyJSON     the request body json to be written to the file.
     * @param requestBodyJsonFile the path of the request body json file.
     * @throws IOException if the file operations fail.
     */
    private static void createRequestJSONFile(String projectDirPath, JSONObject requestBodyJSON,
                                              String requestBodyJsonFile) throws IOException {
        String requestJSONDir =
                StringUtils.joinWith(separator, projectDirPath, "artifact", "data", swaggerPrefix, "payload");
        File dir = new File(requestJSONDir);
        if (!dir.exists()) {
            if (!dir.mkdir()) {
                System.err.println("Directory " + requestJSONDir + " failed to create.");
                System.exit(RC_FAILURE_FOUND);
            }
        }

        String fileContent = JsonUtils.beautify(requestBodyJSON.toString());
        fileContent = StringUtils.replace(fileContent, "\"".concat(NON_TEXT_MARKER), "");
        fileContent = StringUtils.replace(fileContent, NON_TEXT_MARKER.concat("\""), "");
        File jsonFile = new File(StringUtils.joinWith(separator, requestJSONDir, requestBodyJsonFile));
        Files.write(jsonFile.toPath(), fileContent.getBytes());
    }

    /**
     * Retrieves the json schema out of the schema object directly or from the reference object provided as part of the
     * Swagger file.
     *
     * @param schemaContent specified in the Swagger file.
     * @param components    specified in the Swagger file.
     * @return the {@link JSONObject} containing the schema details.
     */
    private static JSONObject getJSONSchema(JSONObject schemaContent, JSONObject components) {
        JSONObject jsonObject = schemaContent.optJSONObject(WS_JSON_CONTENT_TYPE);
        JSONObject requestBody = null;
        if (jsonObject != null) {requestBody = jsonObject.optJSONObject("schema");}
        if (requestBody != null) {
            String requestBodyRefStr = requestBody.optString("$ref");
            if (StringUtils.isNotEmpty(requestBodyRefStr)) {
                JSONObject schemas = components.optJSONObject("schemas");
                return schemas.optJSONObject(StringUtils.substringAfterLast(requestBodyRefStr, "/"));
            }
        }
        return requestBody;
    }

    /**
     * Creates a scenario based on the details passed in.
     *
     * @param scenarioName      the name of the scenario.
     * @param scenario          the scenario object passed in.
     * @param activities        the activities to be added to scenario.
     * @param methodDescription the description of the method corresponding to the scenario.
     */
    private static void createScenario(String scenarioName, SwaggerScenario scenario, List<SwaggerActivity> activities,
                                       String methodDescription) {
        scenario.setDescription(methodDescription);
        scenario.setName(scenarioName);
        scenario.setActivities(activities);
    }

    /**
     * Generates a Nexial request body JSON matching the schema specified in the Swagger file. The property values
     * will be replaced with the Nexial placeholders.
     *
     * @param components    contains schemas, authentication details and the parameters details.
     * @param schemaDetails the schema details
     * @param result        the end result object to be retrieved.
     * @param prefix        the prefix is the variable name string generated so far as part of the recursive call.
     * @return the request body content which will be added to the request json body file.
     */
    private static JSONObject getRequestBody(JSONObject components, JSONObject schemaDetails, JSONObject result,
                                             String prefix) {
        JSONObject schemas = components.optJSONObject("schemas");
        if (schemas == null || schemas.keySet().size() <= 0) {return result;}

        JSONObject schemaProperties = schemaDetails != null ? schemaDetails.optJSONObject("properties") : null;
        if (schemaProperties == null) {return result;}

        for (String property : schemaProperties.keySet()) {
            String propertyRef = schemaProperties.getJSONObject(property).optString("$ref");
            String type = schemaProperties.optJSONObject(property).optString("type");

            if (StringUtils.isNotEmpty(propertyRef)) {
                JSONObject current = new JSONObject();
                result.put(property, current);
                String innerSchema = StringUtils.substringAfterLast(propertyRef, "/");
                getRequestBody(components, schemas.optJSONObject(innerSchema), current,
                               StringUtils.joinWith(".", prefix, innerSchema));
            } else {
                String value = "";
                switch (type) {
                    case "integer":
                        value = StringUtils.join(NON_TEXT_MARKER, 0, NON_TEXT_MARKER);
                        break;
                    case "number":
                        value = StringUtils.join(NON_TEXT_MARKER, 0.0, NON_TEXT_MARKER);
                        break;
                    case "boolean":
                        value = StringUtils.join(NON_TEXT_MARKER, false, NON_TEXT_MARKER);
                        break;
                    case "array":
                        value = StringUtils.join(NON_TEXT_MARKER, "[]", NON_TEXT_MARKER);
                        break;
                }
                result.put(property, value);
            }
        }
        return result;
    }

    /**
     * Generates the authentication corresponding to the current activity.
     *
     * @param securitySchemes  various json schemas related to security.
     * @param methodAttributes various rest api attributes.
     * @return {@link JSONObject} representing the authentication mechanism corresponding to the activity.
     */
    private static JSONObject getSecurity(JSONObject securitySchemes, JSONObject methodAttributes) {
        JSONArray security = methodAttributes.optJSONArray("security");
        JSONArray authenticationSchemes = new JSONArray();
        if (security != null) {
            for (int i = 0; i < security.length(); i++) {
                Set<String> keys = security.optJSONObject(i).keySet();
                keys.forEach(x -> authenticationSchemes.put(securitySchemes.optJSONObject(x)));
            }
            return (JSONObject) authenticationSchemes.get(0);
        }
        return null;
    }

    /**
     * Generate the parameters like "headerParams", "queryParams", "pathParams", "cookieParams" from the Swagger file
     * corresponding to the current activity in the script.
     *
     * @param attributes {@link ParameterGenerationAttributes} used to generate the parameters.
     */
    private static JSONObject extractParameters(ParameterGenerationAttributes attributes,
                                                SwaggerScenarioVars details) {
        JSONObject params = new JSONObject();
        PARAMS.forEach(s -> params.put(s, new JSONObject()));

        JSONArray paramUnion = new JSONArray();
        JSONArray parameters = attributes.getParameters();
        if (parameters != null && parameters.length() > 0) {parameters.forEach(paramUnion::put);}

        JSONArray parentParams = attributes.getParentParams();
        if (parentParams != null && parentParams.length() > 0) {parentParams.forEach(paramUnion::put);}

        for (int counter = 0; counter < paramUnion.length(); counter++) {
            JSONObject paramDetails = paramUnion.getJSONObject(counter);

            String parameterSchemaRef = paramDetails.optString("$ref");
            JSONObject parameterSchemas = attributes.getParameterSchemas();
            if (parameterSchemas != null && StringUtils.isNotEmpty(parameterSchemaRef)) {
                paramDetails = parameterSchemas.getJSONObject(StringUtils.substringAfterLast(parameterSchemaRef, "/"));
            }

            String paramType = paramDetails.optString("in");
            String type = paramDetails.optString("type");
            String name = paramDetails.getString("name");

            if (paramType != null) {
                switch (paramType) {
                    case PARAM_TYPE_HEADER: {
                        params.getJSONObject("headerParams").put(name, name);
                        details.getHeaderParams()
                               .add(StringUtils.joinWith(".", details.getActivityName(), "header", name));
                        break;
                    }
                    case PARAM_TYPE_PATH: {
                        params.getJSONObject("pathParams").put(name, name);
                        details.getPathParams().add(StringUtils.joinWith(".", details.getActivityName(), "path", name));
                        break;
                    }
                    case PARAM_TYPE_COOKIE: {
                        params.getJSONObject("cookieParams").put(name, name);
                        if (StringUtils.isNotEmpty(type) && type.equals(AUTH_SCHEME_API_KEY)) {
                            break;
                        }
                        details.getCookieParams()
                               .add(StringUtils.joinWith(".", details.getActivityName(), "cookie", name));
                        break;
                    }
                    case PARAM_TYPE_QUERY: {
                        params.getJSONObject("queryParams").put(name, name);
                        details.getQueryParams()
                               .add(StringUtils.joinWith(".", details.getActivityName(), "query", name));
                        break;
                    }
                    default:
                        System.err.println("Invalid parameter type " + paramType);
                }
            }
        }
        return params;
    }

    /**
     * Retrieves the scenario name out of the path and method. The path is appended with method.
     * The / will be replaced with {@link SwaggerTestScriptGenerator#STRING_SEPARATOR}.
     * Similarly, the "{" and "}" will be removed. If the obtained text is greater than
     * characters then the last {@link SwaggerTestScriptGenerator#MAX_TAB_NAME_LENGTH_EXCEL} characters from the end
     * becomes the scenario name.
     *
     * @param path   the url path.
     * @param method the http method.
     * @return the scenario name.
     */
    private static String getScenarioName(String path, String method, boolean shorten) {
        String scenarioName = StringUtils.remove(StringUtils.remove(path.replace("/", STRING_SEPARATOR), "{"), "}");
        if (scenarioName.startsWith(STRING_SEPARATOR)) {scenarioName = scenarioName.substring(1);}
        scenarioName = StringUtils.joinWith(STRING_SEPARATOR, scenarioName, method.toUpperCase());
        return shorten ? StringUtils.substring(scenarioName, MAX_TAB_NAME_LENGTH_EXCEL * -1) : scenarioName;
    }

    /**
     * Generate the Nexial steps for the script file.
     *
     * @param attributes the {@link StepAttributes} passed in.
     * @return {@link List} of {@link SwaggerStep}.
     * @throws IOException in case there are issues with File operations while creating RequestBody JSON file.
     */
    private static List<SwaggerStep> generateSteps(StepAttributes attributes, SwaggerScenarioVars swaggerScenarioVars)
            throws IOException {
        List<SwaggerStep> steps = new ArrayList<>();
        String responseVariable = "response";
        Set<String> bodyLessMethods = new HashSet<String>() {{
            add("get");
            add("delete");
        }};
        boolean sameActivity = false;

        String responseDescription = attributes.getResponseParameters().getString("responseDescription");
        String response = attributes.getResponseParameters().getString("response");
        JSONObject headerParams = attributes.getParams().getJSONObject("headerParams");
        String activityName = StringUtils.join(attributes.getMethod(), ".", response).toUpperCase();
        String contentType = attributes.getContentType();

        String warningMessage = attributes.getWarningMessage();
        if (StringUtils.isNotEmpty(warningMessage)) {
            steps.add(createStep(activityName,
                                 "Warning. Unsupported Content-Type",
                                 CMD_TYPE_STEP,
                                 COMMAND_OBSERVE,
                                 warningMessage));
            sameActivity = true;
        }
        sameActivity = generateHeaderParamsStep(steps, responseDescription, activityName, headerParams, sameActivity);

        if (!bodyLessMethods.contains(attributes.getMethod())) {
            steps.add(createStep(sameActivity ? "" : activityName, sameActivity ? "" : responseDescription,
                                 CMD_TYPE_WS, COMMAND_HEADER, WS_CONTENT_TYPE, contentType));
            sameActivity = true;
            headerParams.put(WS_CONTENT_TYPE, contentType);
        }

        sameActivity = generateCookieStep(attributes.getParams(), sameActivity, steps, responseDescription,
                                          activityName);
        String pathString = getPathString(attributes.getPath(), response,
                                          attributes.getParams().getJSONObject("pathParams"),
                                          attributes.getScenarioName());

        MethodInvocationStepAttributes methodInvocationStepAttributes =
                new MethodInvocationStepAttributes().withMethod(attributes.getMethod())
                                                    .withNewActivity(sameActivity)
                                                    .withResponseVariable(responseVariable)
                                                    .withResponseDescription(responseDescription)
                                                    .withActivityName(activityName)
                                                    .withResponse(response)
                                                    .withPathString(
                                                            StringUtils.isNotEmpty(pathString) ? pathString :
                                                            attributes.getPath())
                                                    .withQueryParamString(generateQueryParams(attributes.getParams(),
                                                                                              swaggerScenarioVars.getActivityName()));


        if (contentType.equals(WS_JSON_CONTENT_TYPE)) {
            String requestJSONBodyFile = StringUtils.joinWith("/", "${payloadBase}",
                                                              attributes.getRequestJSONBodyFile());
            methodInvocationStepAttributes.withRequestBody(requestJSONBodyFile);
        } else if (contentType.equals(CONTENT_TYPE_OCTET_STREAM)) {
            String fileVar = StringUtils.joinWith(".", attributes.getScenarioName(), response, "file");
            methodInvocationStepAttributes.withRequestBody(fileVar);
        } else {
            methodInvocationStepAttributes.withRequestBody(Data.EMPTY);
        }

        generateMethodInvocationStep(methodInvocationStepAttributes, steps);
        generateResponseSteps(new ResponseStepAttributes()
                                      .withPath(attributes.getPath())
                                      .withResponseParameters(attributes.getResponseParameters())
                                      .withResponse(response)
                                      .withActivityName(activityName)
                                      .withResponseVariable(responseVariable)
                                      .withStatusTextVar(attributes.getVarName())
                                      .withScenarioName(attributes.getScenarioName()),
                              steps, swaggerScenarioVars);

        clearHeadersAndResponseVariable(steps, headerParams, responseVariable);
        return steps;
    }

    /**
     * Generates the pathString to be appended to the baseUrl path by replacing the path variables with the Nexial
     * variables placeholders.
     *
     * @param path       the path from the swagger file.
     * @param response   the response status code.
     * @param pathParams the path parameters to be replaced in the actual path.
     * @return the path String to be appended to the baseUrl.
     */
    private static String getPathString(String path, String response, JSONObject pathParams, String scenarioName) {
        String pathString = "";
        for (String name : pathParams.keySet()) {
            String parameterNexialDataVariable = generateNexialVariablePlaceHolderString(
                    StringUtils.joinWith(".", PARAM_TYPE_PATH, scenarioName, response, name)
                               .replace("{", "")
                               .replace("}", ""));
            pathString = path.replace(StringUtils.join("{", name, "}"), parameterNexialDataVariable);
        }
        return pathString;
    }

    /**
     * Generates the Nexial step for adding the Header.
     *
     * @param steps               the Nexial steps generated so far.
     * @param responseDescription the description corresponding to the response from the Swagger file.
     * @param activityName        the name of the activity against which the step needs to be added.
     * @param headerParams        the {@link JSONObject} related to the headers.
     * @return the Nexial Cookie Step generated.
     */
    private static boolean generateHeaderParamsStep(List<SwaggerStep> steps, String responseDescription,
                                                    String activityName, JSONObject headerParams,
                                                    boolean sameActivity) {
        for (String name : headerParams.keySet()) {
            steps.add(createStep(sameActivity ? "" : activityName,
                                 sameActivity ? "" : responseDescription,
                                 CMD_TYPE_WS,
                                 COMMAND_HEADER,
                                 name,
                                 generateNexialVariablePlaceHolderString(headerParams.getString(name))));
            sameActivity = true;
        }
        return sameActivity;
    }

    /**
     * Generates the Nexial step for adding the Cookie.
     *
     * @param params              the {@link JSONObject} containing various param details.
     * @param sameActivity        if the step is part of the same activity or not.
     * @param steps               the Nexial steps generated so far.
     * @param responseDescription the description corresponding to the response from the Swagger file.
     * @param activityName        the name of the activity against which the step needs to be added.
     * @return the Nexial Cookie Step generated.
     */
    private static boolean generateCookieStep(JSONObject params, boolean sameActivity, List<SwaggerStep> steps,
                                              String responseDescription, String activityName) {
        JSONObject cookieParams = params.getJSONObject("cookieParams");
        Set<String> cookies = cookieParams.keySet();
        if (cookies.size() > 0) {
            StringBuilder cookieString = new StringBuilder();
            for (String it : cookies) {
                cookieString.append(cookieParams.getString(it))
                            .append("=")
                            .append(cookieParams.getString(it))
                            .append(";");
            }

            steps.add(createStep(sameActivity ? "" : activityName, sameActivity ? "" : responseDescription,
                                 CMD_TYPE_WS, COMMAND_HEADER, "Cookie", cookieString.toString()));
            sameActivity = true;
        }
        return sameActivity;
    }

    /**
     * Creates the Nexial step which performs the operation related to the invocation of the method. This step
     * will be added to the generated steps so far.
     *
     * @param attributes {@link MethodInvocationStepAttributes} used to generate the Method Invocation step.
     * @param steps      the generated steps.
     */
    private static void generateMethodInvocationStep(MethodInvocationStepAttributes attributes,
                                                     List<SwaggerStep> steps) {
        boolean newActivity = attributes.isNewActivity();
        String queryParamString = attributes.getQueryParamString();
        String requestBody = attributes.getRequestBody();

        String urlParamValue =
                StringUtils.join("${", attributes.getMethod().toUpperCase(), ".", attributes.getResponse(), ".", "url",
                                 "}");
        String url = StringUtils.join(urlParamValue,
                                      StringUtils.isNotEmpty(queryParamString) ? "?" : "",
                                      queryParamString).trim();

        steps.add(createStep(newActivity ? "" : attributes.getActivityName().toUpperCase(),
                             newActivity ? "" : attributes.getResponseDescription(),
                             CMD_TYPE_WS,
                             StringUtils.join(attributes.getMethod(), "(url,body,var)"),
                             url,
                             StringUtils.isNotEmpty(requestBody) ? requestBody : Data.EMPTY,
                             attributes.getResponseVariable()));
    }

    /**
     * Generates the Query parameter string as part of the rest api url.
     *
     * @param params the {@link JSONObject} containing the various params details.
     * @return the Query parameter string to be appended to the url.
     */
    private static String generateQueryParams(JSONObject params, String prefix) {
        JSONObject queryParams = params.getJSONObject("queryParams");
        StringBuilder queryStringBuilder = new StringBuilder();
        for (String queryParam : queryParams.keySet()) {
            queryStringBuilder.append(queryParam).append("=")
                              .append(TOKEN_START)
                              .append(prefix)
                              .append(".query.")
                              .append(queryParams.get(queryParam))
                              .append(TOKEN_END)
                              .append(QUERY_STRING_PARAMS_APPENDER);
        }
        return StringUtils.removeEnd(queryStringBuilder.toString(), QUERY_STRING_PARAMS_APPENDER);
    }

    /**
     * Generates the Nexial steps which includes the validation of the response status, status Text returned,
     * correctness of the JSON response post the invocation of the API call.
     *
     * @param steps      Nexial steps generated so far.
     * @param attributes {@link ResponseStepAttributes} for creating the Response
     *                   validation steps as part of the script.
     * @throws IOException if the generation of response JSON file fails.
     */
    private static void generateResponseSteps(ResponseStepAttributes attributes, List<SwaggerStep> steps,
                                              SwaggerScenarioVars swaggerScenarioVars)
            throws IOException {
        String responseVariable = attributes.getResponseVariable();
        String response = attributes.getResponse();
        String scenarioName = attributes.getScenarioName();

        steps.add(createStep("", "", CMD_TYPE_WS, COMMAND_ASSERT_RETURN_CODE, responseVariable, response));

        Object[] params = {"statusText", scenarioName, response};
        attributes.withStatusTextVar(StringUtils.joinWith(".", params));

        steps.add(createStep("", "", CMD_TYPE_BASE, COMMAND_ASSERT_EQUAL,
                             StringUtils.join(TOKEN_START, swaggerScenarioVars.getActivityName(), ".statusText",
                                              TOKEN_END),
                             "${response}.statusText"));

        JSONObject responseHeaders = attributes.getResponseParameters().optJSONObject("responseHeaders");
        if (responseHeaders != null) {
            for (String responseHeader : responseHeaders.keySet()) {
                createStep("", "", CMD_TYPE_BASE, COMMAND_ASSERT_NOT_EMPTY,
                           StringUtils.join(TOKEN_START, responseVariable, TOKEN_END, ".headers[", responseHeader,
                                            "]"));
            }
        }

        String $ref = StringUtils.substringAfterLast(attributes.getResponseParameters().optString("$ref"), "/");
        if (StringUtils.isNotEmpty($ref)) {
            String responseSchemaFile =
                    StringUtils.joinWith(".", $ref, RESPONSE_FILE_POSTFIX);
            swaggerScenarioVars.setSchema("${schemaBase}/".concat(responseSchemaFile));
            steps.add(createStep("", "", CMD_TYPE_JSON, COMMAND_ASSERT_CORRECTNESS,
                                 StringUtils.join(TOKEN_START, responseVariable, TOKEN_END, ".body"),
                                 swaggerScenarioVars.getSchema(), "", "", "",
                                 StringUtils.join("ProceedIf(", TOKEN_START, responseVariable,
                                                  TOKEN_END, ".headers[Content-Type] contain application/json)")));
        } else {
            JSONObject responseSchema = attributes.getResponseParameters().optJSONObject("responseSchema");
            if (responseSchema != null) {
                responseSchema.put("$schema", JSON_SCHEMA_URL);
                String responseSchemaFile =
                        StringUtils.joinWith(".", scenarioName, response, RESPONSE_FILE_POSTFIX);

                swaggerScenarioVars.setSchema("${schemaBase}/".concat(responseSchemaFile));

                String dirPath =
                        StringUtils.joinWith(separator, projectDirPath, "artifact", "data", swaggerPrefix, "Schema");

                File file = new File(StringUtils.joinWith(separator, dirPath, responseSchemaFile));
                Files.write(file.toPath(), JsonUtils.beautify(responseSchema.toString()).getBytes());
                steps.add(createStep("", "", CMD_TYPE_JSON, COMMAND_ASSERT_CORRECTNESS,
                                     StringUtils.join(TOKEN_START, responseVariable, TOKEN_END, ".body"),
                                     swaggerScenarioVars.getSchema(), "", "", "",
                                     StringUtils.join("ProceedIf(", TOKEN_START, responseVariable,
                                                      TOKEN_END, ".headers[Content-Type] contain application/json)")));
            }
        }
    }

    /**
     * Retrieves the Schema for the JSON response replacing the $ref.
     *
     * @param schema  the response schema
     * @param schemas the list of all the available schema definitions in the Swagger definition file.
     * @return eventual response schema.
     */
    private static JSONObject getResponseSchemaJSON(JSONObject schema, JSONObject schemas) {
        JSONObject properties = schema.optJSONObject("properties");

        if (properties != null) {
            Map<String, JSONObject> defMap = new LinkedHashMap<>();

            for (String key : properties.keySet()) {
                JSONObject value = properties.optJSONObject(key);
                if (value != null) {
                    for (String x : value.keySet()) {
                        if (x.equals("$ref")) {
                            String inlineSchema = StringUtils.substringAfterLast(value.getString(x), "/");
                            JSONObject innerSchema = schemas.getJSONObject(inlineSchema).getJSONObject("properties");
                            properties.put(key, innerSchema);
                            getResponseSchemaJSON(innerSchema, schemas);
                        }

                        if (x.equals("items")) {
                            String ref = value.optJSONObject("items").optString("$ref");
                            if (StringUtils.isNotEmpty(ref)) {
                                String inlineSchema = StringUtils.substringAfterLast(ref, "/");
                                value.getJSONObject(x).put("$ref", StringUtils.join("#/$defs/", inlineSchema));

                                JSONObject inSchema = schemas.getJSONObject(inlineSchema).getJSONObject("properties");
                                JSONObject defs = new JSONObject().put(inlineSchema, inSchema);
                                defMap.put(key, defs);
                            }
                        }
                    }
                }
            }

            for (String key : defMap.keySet()) {
                JSONObject value = defMap.get(key);
                properties.getJSONObject(key).put("$defs", value);

                for (String x : value.keySet()) {getResponseSchemaJSON(value.getJSONObject(x), schemas);}
            }

        } else {
            JSONObject items = schema.optJSONObject("items");
            if (items != null) {
                String ref = items.optString("$ref");
                if (StringUtils.isNotEmpty(ref)) {
                    String inlineSchema = StringUtils.substringAfterLast(ref, "/");
                    items.put("$ref", StringUtils.join("#/$defs/", inlineSchema));
                    JSONObject innerSchema = schemas.getJSONObject(inlineSchema).getJSONObject("properties");
                    schema.put("$defs", new JSONObject().put(inlineSchema, innerSchema));
                    getResponseSchemaJSON(innerSchema, schemas);
                }
            }
        }

        return schema;
    }

    /**
     * Generate the Security variables which are added as part of the properties file generated. These variables will be
     * used as part of the Nexial script as part of Authentication corresponding to all the methods inside a scenario.
     *
     * @param security           security object corresponding to the authentication details.
     * @param sameAuthentication tells if the authentication mechanism is same for all the Rest API calls
     *                           in the Swagger or not.
     * @param scenarioName       the scenario name.
     * @return a {@link Map} of security variable scenarioName-value pairs. For example "Authorization" is the scenarioName and value
     *         is in the format ${<AUTH_TYPE>.<scenarioName>.<method>}.
     */
    private static Map<String, String> generateSecurityVariable(JSONObject security, boolean sameAuthentication,
                                                                String scenarioName) {
        // TODO OAuth2.0 and OpenId connectivity are not considered for now. Will support in later releases.
        String in = security.optString("in");
        String name = security.optString("name");
        String authScheme = security.optString("scheme");
        String scheme = StringUtils.isNotEmpty(authScheme) ? authScheme : security.optString("type");

        String headerName = "Cookie";
        String headerValue = sameAuthentication ? scheme.toUpperCase() :
                             StringUtils.joinWith(".", scheme.toUpperCase(), scenarioName);

        boolean schemeSupported = true;
        switch (scheme) {
            case AUTH_SCHEME_BASIC:
            case AUTH_SCHEME_BEARER:
                headerName = "Authorization";
                break;

            case AUTH_SCHEME_API_KEY:
                if (StringUtils.isNotEmpty(in) && in.equals("header") && StringUtils.isNotEmpty(name)) {
                    headerName = name;
                }
                break;

            default:
                schemeSupported = false;
                break;
        }

        if (schemeSupported) {
            Map<String, String> securityMap = new HashMap<>();
            securityMap.put(headerName, headerValue);
            return securityMap;
        }
        return null;
    }

    /**
     * Generates the Nexial steps to clear the headers which are set earlier prior to invocation of Restful API call.
     * Also, the Nexial step related to clearing the response variable is created. These steps are added to the steps
     * passed in.
     *
     * @param steps            the Nexial steps generated prior to this call.
     * @param headers          the headers to be cleared.
     * @param responseVariable the name of the response variable set.
     */
    private static void clearHeadersAndResponseVariable(List<SwaggerStep> steps, JSONObject headers,
                                                        String responseVariable) {
        Optional<String> headerOpt = headers.keySet().stream().reduce((x, y) -> StringUtils.join(x, ",", y));
        headerOpt.ifPresent(s -> steps.add(createStep("", "", CMD_TYPE_WS, COMMAND_CLEAR_HEADER, s)));
        steps.add(createStep("", "", CMD_TYPE_BASE, COMMAND_CLEAR, responseVariable));
    }

    /**
     * Creates a Nexial step which is equivalent to the Excel row specified as a Nexial Step, in the Nexial script.
     *
     * @param activityName name of the activityName.
     * @param description  description of the step.
     * @param cmdType      command type like {@link SwaggerTestScriptGenerator#CMD_TYPE_BASE}, {@link SwaggerTestScriptGenerator#CMD_TYPE_WS} etc.
     * @param command      the type of command corresponding to the Command Type.
     * @param params       various commands passed to the Nexial step.
     * @return returns a {@link SwaggerStep} which represents the Nexial Step as part of the Nexial script file.
     */
    private static SwaggerStep createStep(String activityName, String description, String cmdType, String command,
                                          String... params) {
        SwaggerStep step = new SwaggerStep();
        step.setActivityName(activityName);
        step.setDescription(description);
        step.setCmdType(cmdType);
        step.setCmd(command);
        if (params.length == 6) {step.setFlowControl(params[5]);}

        int counter = 0;
        if (counter < params.length) {step.setParam1(params[counter++]);}
        if (counter < params.length) {step.setParam2(params[counter++]);}
        if (counter < params.length) {step.setParam3(params[counter++]);}
        if (counter < params.length) {step.setParam4(params[counter++]);}
        if (counter < params.length) {step.setParam5(params[counter]);}

        return step;
    }

    /**
     * Generates a Nexial variable placeholder string which looks like ${a.b.c}. For example if the params provided are
     * {w, x, y, z} then the generated then the String generated will be ${w.x.y.z}.
     *
     * @param params the params with which the placeholder string needs to be created.
     * @return the Nexial placeholder string.
     */
    private static String generateNexialVariablePlaceHolderString(Object... params) {
        return StringUtils.join(TOKEN_START, StringUtils.joinWith(".", params), TOKEN_END);
    }

    /**
     * Retrieves the <b>baseUrl</b> corresponding to all the Restful API calls. In case there are multiple Servers,
     * mentioned in the Swagger document, the first i.e. the 0th index will be considered.
     * <p>
     * The baseUrl will be constructed based on all the variable values which are mentioned in the Server. The url path
     * placeholders will be replaced with the variable values which are provided(default if not).
     *
     * @param servers the various server(s) available in the swagger dropdown.
     * @return the baseUrl for all the API operations.
     */
    private static String getBaseUrl(JSONArray servers) {
        JSONObject server = servers.optJSONObject(0);
        String baseUrl = server.optString("url");

        JSONObject variables = server.optJSONObject("variables");
        if (variables != null && variables.length() != 0) {
            for (String key : variables.keySet()) {
                JSONObject variable = variables.optJSONObject(key);
                String defaultValue = variable.optString("default");

                if (StringUtils.isNotEmpty(defaultValue)) {
                    baseUrl = baseUrl.replace(StringUtils.join("{", key, "}"), defaultValue);
                }
            }
        }
        return baseUrl;
    }
}
