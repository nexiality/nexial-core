package org.nexial.core.tools;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nexial.core.tools.swagger.*;
import org.nexial.core.utils.JsonUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.*;
import static org.nexial.core.NexialConst.ExitStatus.RC_BAD_CLI_ARGS;
import static org.nexial.core.NexialConst.ExitStatus.RC_FAILURE_FOUND;
import static org.nexial.core.NexialConst.*;
import static org.nexial.core.excel.ExcelConfig.HEADER_TEST_STEP_DESCRIPTION;
import static org.nexial.core.tools.CliUtils.newArgOption;

/**
 * This class takes a Swagger file which contains the REST API operation definitions and generates the Nexial script
 * Template and related artifacts(files like properties, data file and others). Later, Nexial script developers can
 * set the values against the configuration variables declared in the properties file and run the scripts.
 * <p>
 * This class takes the location of the Swagger file, the location of the Nexial project and a prefix which defines the
 * version of the Swagger file.
 *
 * @author Dhanapathi Marepalli
 */
public class SwaggerTestScriptGenerator {
    private static final int PREFIX_MAX_LENGTH = 30;
    private static final int MAX_TAB_NAME_LENGTH_EXCEL = 31;
    private static final String SWAGGER_PATH_SEPARATOR = "/";
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
    private static final String HEADER_COMMAND = "header(name,value)";
    private static final String ASSERT_EQUAL_COMMAND = "assertEqual(expected,actual)";
    private static final String ASSERT_RETURN_CODE_COMMAND = "assertReturnCode(var,returnCode)";
    private static final String ASSERT_NOT_EMPTY_COMMAND = "assertNotEmpty(text)";
    private static final String ASSERT_CORRECTNESS_JSON_SCHEMA = "assertCorrectness(json,schema)";
    private static final String CLEAR_VARS_CMD = "clear(vars)";

    private static final String NEXIAL_EMPTY_STRING = "(empty)";
    private static final String RESPONSE_FILE_POSTFIX = "Schema.json";

    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";

    private static final List<String> SWAGGER_FILE_EXTENSIONS = Arrays.asList(".json", ".yaml", ".yml");
    private static final String[] NON_STRING_TYPES = new String[]{"integer", "number", "boolean"};
    private static final String NON_STRING_REPRESENTATION_STR = "###";

    private static final Options cmdOptions = new Options();

    private static String projectDirPath;
    private static String swaggerPrefix;
    private static String swaggerFile;

    /**
     * The main method where the execution starts.
     *
     * @param args the arguments for the command to generate script out of Swagger file.
     */
    public static void main(String... args) {
        initOptions();
        newInstance(args);

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
            System.err.println("Unable to parse the file due to exception. Error is " + ioe.getMessage());
            System.exit(RC_FAILURE_FOUND);
        }

        if (dataMap != null) {
            if (dataMap.containsKey("swagger")) {
                System.err.println("Older Swagger versions are not supported. Open API 3 or higher only supported.");
                System.exit(RC_FAILURE_FOUND);
            } else if (dataMap.containsKey("openapi")) {
                JSONObject json = new JSONObject(dataMap);
                try {
                    generateFiles(json, projectDirPath);
                } catch (IOException e) {
                    System.err.println("Error is " + e.getMessage());
                    System.exit(RC_FAILURE_FOUND);
                }
            }
        }
    }

    /**
     * Create a new instance of the class. Validates if the arguments passed to the class are valid or not and parses
     * them accordingly.
     *
     * @param args the command line arguments received from the main method.
     */
    protected static void newInstance(String[] args) {
        try {
            SwaggerTestScriptGenerator updater = new SwaggerTestScriptGenerator();
            updater.parseCLIOptions(new DefaultParser().parse(cmdOptions, args));
        } catch (Exception e) {
            System.err.println(NL + "ERROR: " + e.getMessage() + NL);
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(SwaggerTestScriptGenerator.class.getName(), cmdOptions, true);
            System.exit(RC_BAD_CLI_ARGS);
        }
    }

    /**
     * Initialize the Commandline arguments passed in.
     */
    private static void initOptions() {
        cmdOptions.addOption(newArgOption("f", "file", "[REQUIRED] Location of the Swagger file.", true));
        cmdOptions.addOption(newArgOption("d", "dir", "[REQUIRED] Location of the project directory.", true));
        cmdOptions.addOption(newArgOption("p", "prefix", "[REQUIRED] The swagger prefix.", true));
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
        NexialContents contents = generateNexialContent(json, projectDirPath);
        new SwaggerScriptFilesCreator().generateFiles(projectDirPath, contents, swaggerPrefix);
    }

    /**
     * Generates various Nexial script related content  like parameters, script content, data file content etc.
     * out of the Swagger file content, inside the project directory path specified.
     *
     * @param json           the swagger file content
     * @param projectDirPath the Nexial project directory path.
     * @return {@link NexialContents} related to the Swagger file.
     * @throws IOException in case of File operation failures.
     */
    private static NexialContents generateNexialContent(JSONObject json, String projectDirPath) throws IOException {
        JSONArray servers = json.optJSONArray("servers");
        String baseUrl = (Objects.isNull(servers) || servers.length() == 0) ? "http://<DEFINE YOUR BASE URL>" :
                         getBaseUrl(servers);

        JSONObject paths = json.optJSONObject("paths");
        JSONObject components = json.optJSONObject("components");
        JSONObject securitySchemes = components != null ? components.optJSONObject("securitySchemes") : null;
        JSONObject baseSecurity = getBaseSecurity(json.optJSONArray("security"), securitySchemes);

        NexialContents nexialContents = new NexialContents();
        List<SwaggerScenario> scenarios = new ArrayList<>();
        nexialContents.setScenarios(scenarios);

        SwaggerDataVariables dataVariables = SwaggerDataVariables.getInstance();
        nexialContents.setDataVariables(dataVariables);
        dataVariables.setBaseUrl(baseUrl);

        Map<String, List<String>> requestBodyVariables = dataVariables.getRequestBodyVars();
        // Iterate over the various paths in the Swagger json.
        for (String path : paths.keySet()) {
            String scenarioName = getScenarioName(path);
            JSONArray parentParams = new JSONArray();
            JSONObject methods = paths.optJSONObject(path);

            JSONArray pathLevelParameters = methods.optJSONArray("parameters");
            if (pathLevelParameters != null) {
                pathLevelParameters.forEach(parentParams::put);
                methods.remove("parameters");
            }

            // Iterate over various methods like get, put, post etc.
            for (String method : methods.keySet()) {
                SwaggerScenario scenario = new SwaggerScenario();
                List<SwaggerActivity> activities = new ArrayList<>();
                boolean authenticationAdded = false;

                JSONObject methodAttributes = methods.optJSONObject(method);
                String methodDescription = methodAttributes.optString("description");
                JSONObject requestBody = methodAttributes.optJSONObject("requestBody");

                JSONObject requestBodySchema = new JSONObject();
                boolean contentTypeNeeded = false;

                if (requestBody != null) {
                    JSONObject requestBodyContent = requestBody.optJSONObject("content");
                    if (requestBodyContent.optJSONObject(CONTENT_TYPE_APPLICATION_JSON) != null) {
                        contentTypeNeeded = true;
                    }
                    requestBodySchema = getJSONSchema(requestBodyContent, components);
                }

                createScenario(scenarioName, scenario, activities, methodDescription);
                JSONObject responses = methodAttributes.optJSONObject("responses");

                // Iterate over the responses of various methods.
                String headerName = EMPTY;
                String headerValue = EMPTY;
                for (String response : responses.keySet()) {
                    SwaggerActivity activity = new SwaggerActivity();
                    String activityName = joinWith(".", method, response);
                    activity.setName(activityName);
                    String varName = joinWith(".", scenarioName, method, response);

                    requestBodyVariables.put(varName, new ArrayList<>());
                    String jsonFile = EMPTY;

                    if (requestBodySchema != null && components != null) {
                        String prefix = joinWith(".", scenarioName, activityName);
                        JSONObject requestBodyJSON = getRequestBody(components, requestBodySchema, new JSONObject(),
                                                                    prefix, requestBodyVariables, varName);

                        if (requestBodyJSON.keySet().size() > 0) {
                            jsonFile = joinWith(".", scenarioName, method, response, "json");
                            createRequestJSONFile(projectDirPath, requestBodyJSON, jsonFile);
                        }
                    }

                    JSONArray parameters = methodAttributes.optJSONArray("parameters");
                    JSONObject params = extractParameters(path, method, response, parameters, parentParams,
                                                          Objects.requireNonNull(components).optJSONObject("parameters"),
                                                          dataVariables, varName);

                    JSONObject responseParameters = getResponseParameters(components, responses, response);
                    Map<String, String> securityHeaders;
                    SwaggerStep securityStep;
                    boolean sameAuthentication = baseSecurity != null;
                    JSONObject methodSecurity =
                            sameAuthentication ? baseSecurity : getSecurity(securitySchemes, methodAttributes);

                    if (!authenticationAdded && methodSecurity != null) {
                        securityHeaders =
                                generateSecurityVariable(methodSecurity, sameAuthentication, scenarioName, method);

                        if (securityHeaders != null) {
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
                        }

                        securityStep = createStep("Set up", "Set up http headers.",
                                                  CMD_TYPE_WS, HEADER_COMMAND, headerName,
                                                  generateNexialVariablePlaceHolderString(headerValue));

                        SwaggerActivity initActivity = new SwaggerActivity();
                        initActivity.setName("Set up");
                        initActivity.setSteps(new ArrayList<SwaggerStep>() {{add(securityStep);}});
                        activities.add(initActivity);
                        authenticationAdded = true;
                    }

                    StepAttributes attributes = new StepAttributes(path, method, responseParameters, params,
                                                                   jsonFile, contentTypeNeeded, varName);
                    List<SwaggerStep> steps = generateSteps(attributes, dataVariables);
                    activity.setSteps(steps);
                    activities.add(activity);
                }

                if (isNotEmpty(headerName)) {activities.add(generateCleanupActivity(headerName));}
                scenarios.add(scenario);
            }
        }
        return nexialContents;
    }

    /**
     * Resets the security header as part of cleanup activity in the scenario.
     *
     * @param headerName the header to be reset.
     * @return {@link SwaggerActivity} which does the cleanup activity.
     */
    private static SwaggerActivity generateCleanupActivity(String headerName) {
        SwaggerActivity cleanupActivity = new SwaggerActivity();
        cleanupActivity.setName("tear down");
        SwaggerStep cleanAuthHeader =
                createStep("tear down", "clear http headers.", CMD_TYPE_WS, HEADER_COMMAND, headerName,
                           NEXIAL_EMPTY_STRING);
        cleanupActivity.setSteps(new ArrayList<SwaggerStep>() {{add(cleanAuthHeader);}});
        return cleanupActivity;
    }

    /**
     * Retrieves the authentication mechanism common to all the rest api's. Retrieves null if there isn't one.
     *
     * @param security        the security types.
     * @param securitySchemas the security schemas from Swagger file.
     * @return the {@link JSONObject} containing the base security details.
     */
    private static JSONObject getBaseSecurity(JSONArray security, JSONObject securitySchemas) {
        if (security != null) {
            String securitySchemaName = new ArrayList<>(((JSONObject) security.get(0)).keySet()).get(0);
            return securitySchemas.getJSONObject(securitySchemaName);
        }
        return null;
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
        JSONObject responseAttributes = responses.optJSONObject(response);

        JSONObject responseParameters = new JSONObject();
        responseParameters.put("response", response);

        String responseDescription = responseAttributes.optString(HEADER_TEST_STEP_DESCRIPTION);
        responseParameters.put("responseDescription", responseDescription);

        JSONObject responseHeaders = responseAttributes.optJSONObject("headers");
        responseParameters.put("responseHeaders", responseHeaders);

        JSONObject responseBodyContent = responseAttributes.optJSONObject("content");
        if (responseBodyContent != null) {
            JSONObject responseBodySchema = getJSONSchema(responseBodyContent, components);
            responseParameters.put("responseSchema", getResponseSchemaJSON(responseBodySchema,
                                                                           components.getJSONObject("schemas")));
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
        String requestJSONDir = joinWith(File.separator, projectDirPath, "artifact", "data", swaggerPrefix);
        File dir = new File(requestJSONDir);
        if (!dir.exists()) {
            boolean dirCreated = dir.mkdir();
            if (dirCreated) {
                System.out.println("Directory " + requestJSONDir + " created.");
            }
        }

        String jsonStr = requestBodyJSON.toString();
        String fileContent = replace(replace(JsonUtils.beautify(jsonStr),
                                             "\"".concat(NON_STRING_REPRESENTATION_STR), EMPTY),
                                     NON_STRING_REPRESENTATION_STR.concat("\""), EMPTY);
        File jsonFile = new File(joinWith(File.separator, requestJSONDir, requestBodyJsonFile));
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
        JSONObject requestBody = schemaContent
                .optJSONObject(CONTENT_TYPE_APPLICATION_JSON)
                .optJSONObject("schema");

        String requestBodyRefStr = requestBody.optString("$ref");

        if (isNotEmpty(requestBodyRefStr)) {
            JSONObject schemas = components.optJSONObject("schemas");
            return schemas.optJSONObject(substringAfterLast(requestBodyRefStr, SWAGGER_PATH_SEPARATOR));
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
     * @param components           contains schemas, authentication details and the parameters details.
     * @param schemaDetails        the schema details
     * @param result               the end result object to be retrieved.
     * @param prefix               the prefix is the variable name string generated so far as part of the recursive call.
     * @param requestBodyVariables the array of the Request body variables from dataVariables.
     * @param varName              the name of the activity.
     * @return the request body content which will be added to the request json body file.
     */
    private static JSONObject getRequestBody(JSONObject components, JSONObject schemaDetails, JSONObject result,
                                             String prefix, Map<String, List<String>> requestBodyVariables,
                                             String varName) {
        JSONObject schemas = components.optJSONObject("schemas");
        if (schemas != null && schemas.keySet().size() > 0) {
            JSONObject schemaProperties = schemaDetails != null ? schemaDetails.optJSONObject("properties") : null;

            if (schemaProperties != null) {
                for (String property : schemaProperties.keySet()) {
                    String propertyRef = schemaProperties.getJSONObject(property).optString("$ref");
                    String type = schemaProperties.optJSONObject(property).optString("type");

                    if (isNotEmpty(propertyRef)) {
                        JSONObject current = new JSONObject();
                        result.put(property, current);
                        String innerSchema = substringAfterLast(propertyRef, SWAGGER_PATH_SEPARATOR);
                        getRequestBody(components, schemas.optJSONObject(innerSchema),
                                       current, joinWith(".", prefix, innerSchema),
                                       requestBodyVariables, varName);
                    } else {
                        String value = generateNexialVariablePlaceHolderString(prefix, property);
                        if (Arrays.asList(NON_STRING_TYPES).contains(type)) {
                            result.put(property, join(NON_STRING_REPRESENTATION_STR, value,
                                                      NON_STRING_REPRESENTATION_STR));
                        } else {
                            result.put(property, value);
                        }
                        requestBodyVariables.get(varName).add(value);
                    }
                }
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
     * @param path             the url path
     * @param method           the rest api method.
     * @param response         the response status code.
     * @param parameters       the parameters related to this method.
     * @param parentParams     the parameters at the base level which are common to all operations.
     * @param parameterSchemas schemas corresponding to parameters.
     * @param dataVariables    the data variables related parameters.
     * @return {@link JSONObject} representing all the parameters.
     */
    private static JSONObject extractParameters(String path, String method, String response, JSONArray parameters,
                                                JSONArray parentParams, JSONObject parameterSchemas,
                                                SwaggerDataVariables dataVariables, String varName) {
        String scenarioName = getScenarioName(path);
        JSONObject params = new JSONObject();
        Arrays.asList("headerParams", "queryParams", "pathParams", "cookieParams")
              .forEach(s -> params.put(s, new JSONObject()));

        Map<String, List<String>> headerVars = dataVariables.getHeaderParams();
        Map<String, List<String>> pathVars = dataVariables.getPathParams();
        Map<String, List<String>> cookieVars = dataVariables.getCookieParams();
        Map<String, List<String>> queryParamVars = dataVariables.getQueryParams();

        headerVars.put(varName, new ArrayList<>());
        pathVars.put(varName, new ArrayList<>());
        cookieVars.put(varName, new ArrayList<>());
        queryParamVars.put(varName, new ArrayList<>());

        JSONArray paramUnion = new JSONArray();
        if (parameters != null && parameters.length() > 0) {
            parameters.forEach(paramUnion::put);
        }

        if (parentParams != null && parentParams.length() > 0) {
            parentParams.forEach(paramUnion::put);
        }

        for (int counter = 0; counter < paramUnion.length(); counter++) {
            JSONObject paramDetails = paramUnion.getJSONObject(counter);

            String parameterSchemaRef = paramDetails.optString("$ref");
            if (parameterSchemas != null && isNotEmpty(parameterSchemaRef)) {
                paramDetails = parameterSchemas.getJSONObject(substringAfterLast(parameterSchemaRef,
                                                                                 SWAGGER_PATH_SEPARATOR));
            }

            String paramType = paramDetails.optString("in");
            String type = paramDetails.optString("type");
            String name = paramDetails.getString("name");
            String paramNexialDataVar = joinWith(".", paramType, scenarioName, method, response, name);

            if (paramType != null) {
                switch (paramType) {
                    case PARAM_TYPE_HEADER: {
                        params.getJSONObject("headerParams").put(name, paramNexialDataVar);
                        headerVars.get(varName).add(paramNexialDataVar);
                        break;
                    }
                    case PARAM_TYPE_PATH: {
                        params.getJSONObject("pathParams").put(name, paramNexialDataVar);
                        pathVars.get(varName).add(paramNexialDataVar);
                        break;
                    }
                    case PARAM_TYPE_COOKIE: {
                        params.getJSONObject("cookieParams").put(name, paramNexialDataVar);
                        if (isNotEmpty(type) && type.equals(AUTH_SCHEME_API_KEY)) {
                            break;
                        }
                        cookieVars.get(varName).add(paramNexialDataVar);
                        break;
                    }
                    case PARAM_TYPE_QUERY: {
                        params.getJSONObject("queryParams").put(name, paramNexialDataVar);
                        queryParamVars.get(varName).add(paramNexialDataVar);
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
     * Retrieves the scenario name out of the path. The {@link SwaggerTestScriptGenerator#SWAGGER_PATH_SEPARATOR}
     * will be replaced with {@link SwaggerTestScriptGenerator#STRING_SEPARATOR}. Similarly the "{" and "}" will be
     * removed. If the obtained text is greater than 31 characters then the 31 characters from the ending will be taken
     * into consideration.
     *
     * @param path the url path.
     * @return the scenario name corresponding to the path.
     */
    private static String getScenarioName(String path) {
        String scenarioName = substring(path.replace(SWAGGER_PATH_SEPARATOR, STRING_SEPARATOR)
                                            .replace("{", EMPTY).replace("}", EMPTY),
                                        MAX_TAB_NAME_LENGTH_EXCEL * -1);
        if (scenarioName.startsWith(STRING_SEPARATOR)) {scenarioName = scenarioName.substring(1);}
        return scenarioName;
    }

    /**
     * Generate the Nexial steps for the script file.
     *
     * @param attributes the {@link StepAttributes} needed for creation of Steps.
     * @return {@link List} of {@link SwaggerStep}.
     * @throws IOException in case there are issues with File operations while creating RequestBody JSON file.
     */
    private static List<SwaggerStep> generateSteps(StepAttributes attributes, SwaggerDataVariables dataVariables)
            throws IOException {
        List<SwaggerStep> steps = new ArrayList<>();
        String responseVariable = "response";
        boolean newActivity;

        String responseDescription = attributes.getResponseParameters().getString("responseDescription");
        String response = attributes.getResponseParameters().getString("response");
        JSONObject headerParams = attributes.getParams().getJSONObject("headerParams");
        String activityName = join(attributes.getMethod(), ".", response);

        newActivity = generateHeaderParamsStep(steps, responseDescription, activityName, headerParams);
        if (attributes.isContentTypeNeeded()) {
            steps.add(createStep(newActivity ? EMPTY : activityName,
                                 newActivity ? EMPTY : responseDescription, CMD_TYPE_WS,
                                 HEADER_COMMAND, HEADER_CONTENT_TYPE, CONTENT_TYPE_APPLICATION_JSON));
            newActivity = true;
            headerParams.put(HEADER_CONTENT_TYPE, CONTENT_TYPE_APPLICATION_JSON);
        }

        newActivity = generateCookieStep(attributes.getParams(), newActivity, steps, responseDescription, activityName);
        String pathString = getPathString(attributes.getPath(), attributes.getMethod(), response,
                                          attributes.getParams().getJSONObject("pathParams"));

        generateMethodInvocationStep(attributes.getMethod(), attributes.getRequestJSONBodyFile(), newActivity, steps,
                                     responseVariable,
                                     responseDescription, activityName,
                                     isNotEmpty(pathString) ? pathString : attributes.getPath(),
                                     generateQueryParams(attributes.getParams()));

        Map<String, List<String>> statusTextVars = dataVariables.getStatusTextVars();
        statusTextVars.put(attributes.getVarName(), new ArrayList<>());

        generateResponseSteps(attributes.getPath(), attributes.getResponseParameters(), steps, response, activityName,
                              responseVariable, statusTextVars.get(attributes.getVarName()));

        clearHeadersAndResponseVariable(steps, headerParams, responseVariable);
        return steps;
    }

    /**
     * Generates the pathString to be appended to the baseUrl path by replacing the path variables with the Nexial
     * variables placeholders.
     *
     * @param path       the path from the swagger file.
     * @param method     the restful api method.
     * @param response   the response status code.
     * @param pathParams the path parameters to be replaced in the actual path.
     * @return the path String to be appended to the baseUrl.
     */
    private static String getPathString(String path, String method, String response, JSONObject pathParams) {
        String pathString = EMPTY;
        for (String name : pathParams.keySet()) {
            String scenarioName = getScenarioName(path);
            String parameterNexialDataVariable = generateNexialVariablePlaceHolderString(
                    joinWith(".", PARAM_TYPE_PATH, scenarioName, method, response, name)
                            .replace("{", EMPTY).replace("}", EMPTY));
            pathString = path.replace(join("{", name, "}"), parameterNexialDataVariable);
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
                                                    String activityName, JSONObject headerParams) {
        boolean newActivity = false;
        for (String name : headerParams.keySet()) {
            SwaggerStep headerStep = createStep(newActivity ? EMPTY : activityName,
                                                newActivity ? EMPTY : responseDescription, CMD_TYPE_WS,
                                                HEADER_COMMAND, name,
                                                generateNexialVariablePlaceHolderString(headerParams.getString(name)));
            steps.add(headerStep);
            newActivity = true;
        }
        return newActivity;
    }

    /**
     * Generates the Nexial step for adding the Cookie.
     *
     * @param params              the {@link JSONObject} containing various param details.
     * @param newActivity         if the activity is part of the same activity or not.
     * @param steps               the Nexial steps generated so far.
     * @param responseDescription the description corresponding to the response from the Swagger file.
     * @param activityName        the name of the activity against which the step needs to be added.
     * @return the Nexial Cookie Step generated.
     */
    private static boolean generateCookieStep(JSONObject params, boolean newActivity, List<SwaggerStep> steps,
                                              String responseDescription, String activityName) {
        JSONObject cookieParams = params.getJSONObject("cookieParams");
        Set<String> cookies = cookieParams.keySet();
        if (cookies.size() > 0) {
            StringBuilder cookieString = new StringBuilder();
            for (String it : cookies) {
                cookieString.append(cookieParams.getString(it)).append("=").append(cookieParams.getString(it))
                            .append(";");
            }

            steps.add(createStep(newActivity ? EMPTY : activityName, newActivity ? EMPTY : responseDescription,
                                 CMD_TYPE_WS, HEADER_COMMAND, "Cookie", cookieString.toString()));
            newActivity = true;
        }
        return newActivity;
    }

    /**
     * Creates the Nexial step which performs the operation related to the invocation of the method. This step
     * will be added to the generated steps so far.
     *
     * @param method              the restful method type
     * @param requestJSONBodyFile the name of the requestJSONBody file.
     * @param newActivity         is the step created part of new activity or not.
     * @param steps               the generated steps.
     * @param responseVariable    the name of the response variable.
     * @param responseDescription the description of the response.
     * @param activityName        the name of the Activity.
     * @param pathString          the url path which is appended to the baseUrl.
     * @param queryParamString    the query parameter string to be appended to the url.
     */
    private static void generateMethodInvocationStep(String method, String requestJSONBodyFile, boolean newActivity,
                                                     List<SwaggerStep> steps, String responseVariable,
                                                     String responseDescription, String activityName,
                                                     String pathString, String queryParamString) {
        steps.add(createStep(newActivity ? EMPTY : activityName,
                             newActivity ? EMPTY : responseDescription,
                             CMD_TYPE_WS,
                             join(method, "(url,body,var)"),
                             join("${baseUrl}", pathString, isNotEmpty(queryParamString) ? "?" : EMPTY,
                                  queryParamString).trim(),
                             isNotEmpty(requestJSONBodyFile) ? joinWith(SWAGGER_PATH_SEPARATOR,
                                                                        "$(syspath|data|fullpath)",
                                                                        swaggerPrefix, requestJSONBodyFile) :
                             NEXIAL_EMPTY_STRING,
                             responseVariable));
    }

    /**
     * Generates the Query parameter string as part of the rest api url.
     *
     * @param params the {@link JSONObject} containing the various params details.
     * @return the Query parameter string to be appended to the url.
     */
    private static String generateQueryParams(JSONObject params) {
        JSONObject queryParams = params.getJSONObject("queryParams");
        StringBuilder queryStringBuilder = new StringBuilder();
        for (String queryParam : queryParams.keySet()) {
            queryStringBuilder.append(queryParam).append("=").append(TOKEN_START).append(queryParams.get(queryParam))
                              .append(TOKEN_END).append(QUERY_STRING_PARAMS_APPENDER);
        }
        return removeEnd(queryStringBuilder.toString(), QUERY_STRING_PARAMS_APPENDER);
    }

    /**
     * Generates the Nexial steps which includes the validation of the response status, status Text returned,
     * correctness of the JSON response post the invocation of the API call.
     *
     * @param path                the rest api path.
     * @param responseParameters  {@link JSONObject} containing the metadata of the response parameters.
     * @param steps               Nexial steps generated so far.
     * @param response            the status code of the rest api call invocation.
     * @param activityName        the name of the activity.
     * @param responseVariable    the name of the response variable.
     * @param statusTextVariables the variables for
     * @throws IOException if the generation of response JSON file fails.
     */
    private static void generateResponseSteps(String path, JSONObject responseParameters, List<SwaggerStep> steps,
                                              String response, String activityName,
                                              String responseVariable, List<String> statusTextVariables)
            throws IOException {
        steps.add(createStep(EMPTY, EMPTY, CMD_TYPE_WS, ASSERT_RETURN_CODE_COMMAND, responseVariable, response));

        Object[] params = {"statusText", getScenarioName(path), activityName};
        statusTextVariables.add(joinWith(".", params));

        steps.add(createStep(EMPTY, EMPTY, CMD_TYPE_BASE, ASSERT_EQUAL_COMMAND,
                             join(TOKEN_START, responseVariable, TOKEN_END, ".statusText"),
                             generateNexialVariablePlaceHolderString(params)));

        JSONObject responseHeaders = responseParameters.optJSONObject("responseHeaders");
        if (responseHeaders != null) {
            for (String responseHeader : responseHeaders.keySet()) {
                createStep(EMPTY, EMPTY, CMD_TYPE_BASE, ASSERT_NOT_EMPTY_COMMAND,
                           join(TOKEN_START, responseVariable, TOKEN_END, ".headers[", responseHeader, "]"));
            }
        }

        JSONObject responseSchema = responseParameters.optJSONObject("responseSchema");
        if (responseSchema != null) {
            responseSchema.put("$schema", "https://json-schema.org/draft/2019-09/schema");

            String responseSchemaFile =
                    joinWith(".", getScenarioName(path), activityName, RESPONSE_FILE_POSTFIX);
            String responseFileName =
                    joinWith("/", "$(syspath|data|fullpath)/Schema", swaggerPrefix, responseSchemaFile);

            String dirPath = joinWith(File.separator, projectDirPath, "artifact", "data", "Schema", swaggerPrefix);
            File dir = new File(dirPath);

            if (!dir.exists()) {
                boolean dirCreated = dir.mkdirs();
                if (!dirCreated) {
                    System.err.println("Failed to create directory " + dir.getName());
                    System.exit(RC_FAILURE_FOUND);
                }
            }

            File file = new File(joinWith(File.separator, dirPath, responseSchemaFile));
            Files.write(file.toPath(), JsonUtils.beautify(responseSchema.toString()).getBytes());
            steps.add(createStep(EMPTY, EMPTY, CMD_TYPE_JSON, ASSERT_CORRECTNESS_JSON_SCHEMA,
                                 join(TOKEN_START, responseVariable, TOKEN_END, ".body"),
                                 responseFileName, EMPTY, EMPTY, EMPTY,
                                 join("ProceedIf(", TOKEN_START, responseVariable,
                                      TOKEN_END, ".headers[Content-Type] contain application/json)")));
        }
    }

    private static JSONObject getResponseSchemaJSON(JSONObject schema, JSONObject schemas) {
        JSONObject properties = schema.optJSONObject("properties");
        if (properties != null) {
            for (String key : properties.keySet()) {
                JSONObject value = properties.optJSONObject(key);
                if (value != null) {
                    for (String x : value.keySet()) {
                        if (x.equals("$ref")) {
                            JSONObject innerSchema = schemas.getJSONObject(
                                    substringAfterLast(value.getString(x),
                                                       SWAGGER_PATH_SEPARATOR)).getJSONObject("properties");
                            properties.put(key, innerSchema);
                            getResponseSchemaJSON(innerSchema, schemas);
                        }
                    }
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
     * @param method             the http method.
     * @return a {@link Map} of security variable scenarioName-value pairs. For example "Authorization" is the scenarioName and value
     *         is in the format ${<AUTH_TYPE>.<scenarioName>.<method>}.
     */
    private static Map<String, String> generateSecurityVariable(JSONObject security, boolean sameAuthentication,
                                                                String scenarioName, String method) {
        // TODO OAuth2.0 and OpenId connectivity are not considered for now. Will support in later releases.
        String in = security.optString("in");
        String name = security.optString("name");
        String authScheme = security.optString("scheme");
        String scheme = isNotEmpty(authScheme) ? authScheme : security.optString("type");

        String headerName = "Cookie";
        String headerValue = sameAuthentication ? scheme.toUpperCase() :
                             joinWith(".", scheme.toUpperCase(), scenarioName, method);

        boolean schemeSupported = true;
        switch (scheme) {
            case AUTH_SCHEME_BASIC:

            case AUTH_SCHEME_BEARER:
                headerName = "Authorization";
                break;

            case AUTH_SCHEME_API_KEY:
                if (isNotEmpty(in) && in.equals("header") && isNotEmpty(name)) {
                    headerName = name;
                }
                break;

            default:
                System.err.println("Unsupported Security(Authentication) mechanism " + scheme + ".");
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
        for (String name : headers.keySet()) {
            steps.add(createStep(EMPTY, EMPTY, CMD_TYPE_WS, HEADER_COMMAND, name, NEXIAL_EMPTY_STRING));
        }
        steps.add(createStep(EMPTY, EMPTY, CMD_TYPE_BASE, CLEAR_VARS_CMD, responseVariable));
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

        if (params.length == 6) {
            step.setFlowControl(params[5]);
        }

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
        return join(TOKEN_START, joinWith(".", params), TOKEN_END);
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

                if (isNotEmpty(defaultValue)) {
                    baseUrl = baseUrl.replace(join("{", key, "}"), defaultValue);
                }
            }
        }
        return baseUrl;
    }

    /**
     * Validate and parse the command line inputs.
     *
     * @param cmd {@link CommandLine} argument passed in.
     */
    protected void parseCLIOptions(CommandLine cmd) {
        swaggerPrefix  = cmd.getOptionValue("p");
        projectDirPath = cmd.getOptionValue("d");
        swaggerFile    = cmd.getOptionValue("f");

        if (swaggerPrefix.length() > PREFIX_MAX_LENGTH) {
            throw new RuntimeException("[prefix] length should not be greater than " + PREFIX_MAX_LENGTH + ".");
        }
        if (!swaggerPrefix.matches("\\S+")) {throw new RuntimeException("[prefix] should not contain spaces");}

        if (SWAGGER_FILE_EXTENSIONS.stream().noneMatch(swaggerFile::endsWith)) {
            throw new RuntimeException("[file] is of invalid type.Only json and yaml files are supported.");
        }
        if (!new File(swaggerFile).exists()) {throw new RuntimeException("[file] does not exist.");}

        if (!new File(projectDirPath).exists()) {throw new RuntimeException("[dir] does not exist.");}
    }

    /**
     * The Inner class which contains various attributes to create a Step inside the Nexial script file.
     */
    static class StepAttributes {
        private String path;
        private String method;
        private JSONObject responseParameters;
        private JSONObject params;
        private String requestJSONBodyFile;
        private boolean contentTypeNeeded;
        private String varName;

        public StepAttributes(String path, String method, JSONObject responseParameters, JSONObject params,
                              String requestJSONBodyFile, boolean contentTypeNeeded, String varName) {
            this.path                = path;
            this.method              = method;
            this.responseParameters  = responseParameters;
            this.params              = params;
            this.requestJSONBodyFile = requestJSONBodyFile;
            this.contentTypeNeeded   = contentTypeNeeded;
            this.varName             = varName;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public JSONObject getResponseParameters() {
            return responseParameters;
        }

        public void setResponseParameters(JSONObject responseParameters) {
            this.responseParameters = responseParameters;
        }

        public JSONObject getParams() {
            return params;
        }

        public void setParams(JSONObject params) {
            this.params = params;
        }

        public String getRequestJSONBodyFile() {
            return requestJSONBodyFile;
        }

        public void setRequestJSONBodyFile(String requestJSONBodyFile) {
            this.requestJSONBodyFile = requestJSONBodyFile;
        }

        public boolean isContentTypeNeeded() {
            return contentTypeNeeded;
        }

        public void setContentTypeNeeded(boolean contentTypeNeeded) {
            this.contentTypeNeeded = contentTypeNeeded;
        }

        public String getVarName() {
            return varName;
        }

        public void setVarName(String varName) {
            this.varName = varName;
        }

        @Override
        public String toString() {
            return "StepAttributes{" +
                   "path='" + path + '\'' +
                   ", method='" + method + '\'' +
                   ", responseParameters=" + responseParameters +
                   ", params=" + params +
                   ", requestJSONBodyFile='" + requestJSONBodyFile + '\'' +
                   ", contentTypeNeeded=" + contentTypeNeeded +
                   ", varName='" + varName + '\'' +
                   '}';
        }
    }
}
