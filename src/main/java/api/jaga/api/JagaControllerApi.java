package api.jaga.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import api.ApiClient;
import api.jaga.dto.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JagaControllerApi {
	private ApiClient apiClient;

	public JagaControllerApi() {
		this(new ApiClient());
	}

	@Autowired
	public JagaControllerApi(ApiClient apiClient) {
		this.apiClient = apiClient;
	}

	public ApiClient getApiClient() {
		return apiClient;
	}

	public void setApiClient(ApiClient apiClient) {
		this.apiClient = apiClient;
	}

	/**
	 * <p><b>200</b> - OK
	 *
	 * @param jagaLoginRequest  (required)
	 * @return JagaLoginResponse
	 * @throws WebClientResponseException if an error occurs while attempting to invoke the API
	 */
	private ResponseSpec loginRequestCreation(JagaLoginRequest jagaLoginRequest) throws WebClientResponseException {
		Object postBody = jagaLoginRequest;

		if (jagaLoginRequest == null) {
			throw new WebClientResponseException(
					"Missing the required parameter 'jagaLoginRequest' when calling login",
					400,
					"Bad Request",
					null,
					null,
					null
			);
		}

		final Map<String, Object> pathParams = new HashMap<String, Object>();
		final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
		final HttpHeaders headerParams = new HttpHeaders();
		final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
		final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

		final String[] localVarAccepts = {
				"*/*"
		};
		final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);

		final String[] localVarContentTypes = {
				"application/json"
		};
		final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

		String[] localVarAuthNames = new String[]{};

		ParameterizedTypeReference<JagaLoginResponse> localVarReturnType =
				new ParameterizedTypeReference<JagaLoginResponse>() {
				};

		return apiClient.invokeAPI(
				"/backend/auth/login",
				HttpMethod.POST,
				pathParams,
				queryParams,
				postBody,
				headerParams,
				cookieParams,
				formParams,
				localVarAccept,
				localVarContentType,
				localVarAuthNames,
				localVarReturnType
		);
	}

	/**
	 * <p><b>200</b> - OK
	 *
	 * @param jagaLoginRequest  (required)
	 * @return JagaLoginResponse
	 * @throws WebClientResponseException if an error occurs while attempting to invoke the API
	 */
	public Mono<JagaLoginResponse> login(JagaLoginRequest jagaLoginRequest) throws WebClientResponseException {
		ParameterizedTypeReference<JagaLoginResponse> localVarReturnType =
				new ParameterizedTypeReference<JagaLoginResponse>() {
				};
		return loginRequestCreation(jagaLoginRequest).bodyToMono(localVarReturnType);
	}

	/**
	 * <p><b>200</b> - OK
	 *
	 * @param projectId  (required)
	 * @param hasWorkflow  (required)
	 * @return List<JagaTaskTypeResponse>
	 * @throws WebClientResponseException if an error occurs while attempting to invoke the API
	 */
	private ResponseSpec getProjectTaskTypesRequestCreation(Long projectId, Boolean hasWorkflow) throws WebClientResponseException {
		Object postBody = null;

		if (projectId == null) {
			throw new WebClientResponseException(
					"Missing the required parameter 'projectId' when calling getProjectTaskTypes",
					400,
					"Bad Request",
					null,
					null,
					null
			);
		}

		if (hasWorkflow == null) {
			throw new WebClientResponseException(
					"Missing the required parameter 'hasWorkflow' when calling getProjectTaskTypes",
					400,
					"Bad Request",
					null,
					null,
					null
			);
		}

		final Map<String, Object> pathParams = new HashMap<String, Object>();
		pathParams.put("projectId", projectId);

		final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
		queryParams.putAll(apiClient.parameterToMultiValueMap(null, "hasWorkflow", hasWorkflow));

		final HttpHeaders headerParams = new HttpHeaders();
		final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
		final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

		final String[] localVarAccepts = {
				"application/json",
				"*/*"
		};
		final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);

		final String[] localVarContentTypes = {};
		final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

		String[] localVarAuthNames = new String[] { "bearer-jwt" };

		ParameterizedTypeReference<List<JagaTaskTypeResponse>> localVarReturnType =
				new ParameterizedTypeReference<List<JagaTaskTypeResponse>>() {
				};

		return apiClient.invokeAPI(
				"/backend/project/{projectId}/taskType",
				HttpMethod.GET,
				pathParams,
				queryParams,
				postBody,
				headerParams,
				cookieParams,
				formParams,
				localVarAccept,
				localVarContentType,
				localVarAuthNames,
				localVarReturnType
		);
	}

	/**
	 * <p><b>200</b> - OK
	 *
	 * @param projectId  (required)
	 * @param hasWorkflow  (required)
	 * @return List<JagaTaskTypeResponse>
	 * @throws WebClientResponseException if an error occurs while attempting to invoke the API
	 */
	public Mono<List<JagaTaskTypeResponse>> getProjectTaskTypes(Long projectId, Boolean hasWorkflow) throws WebClientResponseException {
		ParameterizedTypeReference<List<JagaTaskTypeResponse>> localVarReturnType =
				new ParameterizedTypeReference<List<JagaTaskTypeResponse>>() {
				};
		return getProjectTaskTypesRequestCreation(projectId, hasWorkflow).bodyToMono(localVarReturnType);
	}

	/**
	 * <p><b>200</b> - OK
	 *
	 * @param projectId (required)
	 * @param taskTypeId (required)
	 * @return JagaTaskTypeDetailsResponse
	 * @throws WebClientResponseException if an error occurs while attempting to invoke the API
	 */
	private ResponseSpec getProjectTaskTypeRequestCreation(Long projectId, Long taskTypeId) throws WebClientResponseException {
		Object postBody = null;

		if (projectId == null) {
			throw new WebClientResponseException(
					"Missing the required parameter 'projectId' when calling getProjectTaskType",
					400,
					"Bad Request",
					null,
					null,
					null
			);
		}

		if (taskTypeId == null) {
			throw new WebClientResponseException(
					"Missing the required parameter 'taskTypeId' when calling getProjectTaskType",
					400,
					"Bad Request",
					null,
					null,
					null
			);
		}

		final Map<String, Object> pathParams = new HashMap<String, Object>();
		pathParams.put("projectId", projectId);
		pathParams.put("taskTypeId", taskTypeId);

		final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
		final HttpHeaders headerParams = new HttpHeaders();
		final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
		final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

		final String[] localVarAccepts = {
				"application/json",
				"*/*"
		};
		final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);

		final String[] localVarContentTypes = {};
		final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

		String[] localVarAuthNames = new String[] { "bearer-jwt" };

		ParameterizedTypeReference<JagaTaskTypeDetailsResponse> localVarReturnType =
				new ParameterizedTypeReference<JagaTaskTypeDetailsResponse>() {
				};

		return apiClient.invokeAPI(
				"/backend/project/{projectId}/taskType/{taskTypeId}",
				HttpMethod.GET,
				pathParams,
				queryParams,
				postBody,
				headerParams,
				cookieParams,
				formParams,
				localVarAccept,
				localVarContentType,
				localVarAuthNames,
				localVarReturnType
		);
	}

	/**
	 * <p><b>200</b> - OK
	 *
	 * @param projectId (required)
	 * @param taskTypeId (required)
	 * @return JagaTaskTypeDetailsResponse
	 * @throws WebClientResponseException if an error occurs while attempting to invoke the API
	 */
	public Mono<JagaTaskTypeDetailsResponse> getProjectTaskType(Long projectId, Long taskTypeId) throws WebClientResponseException {
		ParameterizedTypeReference<JagaTaskTypeDetailsResponse> localVarReturnType =
				new ParameterizedTypeReference<JagaTaskTypeDetailsResponse>() {
				};
		return getProjectTaskTypeRequestCreation(projectId, taskTypeId).bodyToMono(localVarReturnType);
	}

	/**
	 * <p><b>200</b> - OK
	 *
	 * @param dictionaryId (required)
	 * @return JagaListRefResponse
	 * @throws WebClientResponseException if an error occurs while attempting to invoke the API
	 */
	private ResponseSpec getListRefAnyRequestCreation(Long dictionaryId) throws WebClientResponseException {
		Object postBody = null;

		if (dictionaryId == null) {
			throw new WebClientResponseException(
					"Missing the required parameter 'dictionaryId' when calling getListRefAny",
					400,
					"Bad Request",
					null,
					null,
					null
			);
		}

		final Map<String, Object> pathParams = new HashMap<String, Object>();
		pathParams.put("dictionaryId", dictionaryId);

		final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
		final HttpHeaders headerParams = new HttpHeaders();
		final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
		final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

		final String[] localVarAccepts = {
				"application/json",
				"*/*"
		};
		final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);

		final String[] localVarContentTypes = {};
		final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

		String[] localVarAuthNames = new String[] { "bearer-jwt" };

		ParameterizedTypeReference<JagaListRefResponse> localVarReturnType =
				new ParameterizedTypeReference<JagaListRefResponse>() {
				};

		return apiClient.invokeAPI(
				"/backend/listRef/{dictionaryId}/any",
				HttpMethod.GET,
				pathParams,
				queryParams,
				postBody,
				headerParams,
				cookieParams,
				formParams,
				localVarAccept,
				localVarContentType,
				localVarAuthNames,
				localVarReturnType
		);
	}

	/**
	 * <p><b>200</b> - OK
	 *
	 * @param dictionaryId (required)
	 * @return JagaListRefResponse
	 * @throws WebClientResponseException if an error occurs while attempting to invoke the API
	 */
	public Mono<JagaListRefResponse> getListRefAny(Long dictionaryId) throws WebClientResponseException {
		ParameterizedTypeReference<JagaListRefResponse> localVarReturnType =
				new ParameterizedTypeReference<JagaListRefResponse>() {
				};
		return getListRefAnyRequestCreation(dictionaryId).bodyToMono(localVarReturnType);
	}

	/**
	 * <p><b>200</b> - OK
	 *
	 * @param projectId (required)
	 * @param taskTypeId (required)
	 * @param jagaCreateTaskRequest (required)
	 * @return JagaTaskResponse
	 * @throws WebClientResponseException if an error occurs while attempting to invoke the API
	 */
	private ResponseSpec createTaskByTaskTypeRequestCreation(
			Long projectId,
			Long taskTypeId,
			JagaCreateTaskRequest jagaCreateTaskRequest
	) throws WebClientResponseException {
		Object postBody = jagaCreateTaskRequest;

		if (projectId == null) {
			throw new WebClientResponseException(
					"Missing the required parameter 'projectId' when calling createTaskByTaskType",
					400,
					"Bad Request",
					null,
					null,
					null
			);
		}

		if (taskTypeId == null) {
			throw new WebClientResponseException(
					"Missing the required parameter 'taskTypeId' when calling createTaskByTaskType",
					400,
					"Bad Request",
					null,
					null,
					null
			);
		}

		if (jagaCreateTaskRequest == null) {
			throw new WebClientResponseException(
					"Missing the required parameter 'jagaCreateTaskRequest' when calling createTaskByTaskType",
					400,
					"Bad Request",
					null,
					null,
					null
			);
		}

		final Map<String, Object> pathParams = new HashMap<String, Object>();
		pathParams.put("projectId", projectId);
		pathParams.put("taskTypeId", taskTypeId);

		final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<String, String>();
		final HttpHeaders headerParams = new HttpHeaders();
		final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<String, String>();
		final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<String, Object>();

		final String[] localVarAccepts = {
				"application/json",
				"*/*"
		};
		final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);

		final String[] localVarContentTypes = {
				"application/json"
		};
		final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

		String[] localVarAuthNames = new String[] { "bearer-jwt" };

		ParameterizedTypeReference<JagaTaskResponse> localVarReturnType =
				new ParameterizedTypeReference<JagaTaskResponse>() {
				};

		return apiClient.invokeAPI(
				"/backend/task/createByTaskType/{projectId}/{taskTypeId}",
				HttpMethod.POST,
				pathParams,
				queryParams,
				postBody,
				headerParams,
				cookieParams,
				formParams,
				localVarAccept,
				localVarContentType,
				localVarAuthNames,
				localVarReturnType
		);
	}

	/**
	 * <p><b>200</b> - OK
	 *
	 * @param projectId (required)
	 * @param taskTypeId (required)
	 * @param jagaCreateTaskRequest (required)
	 * @return JagaTaskResponse
	 * @throws WebClientResponseException if an error occurs while attempting to invoke the API
	 */
	public Mono<JagaTaskResponse> createTaskByTaskType(
			Long projectId,
			Long taskTypeId,
			JagaCreateTaskRequest jagaCreateTaskRequest
	) throws WebClientResponseException {
		ParameterizedTypeReference<JagaTaskResponse> localVarReturnType =
				new ParameterizedTypeReference<JagaTaskResponse>() {
				};
		return createTaskByTaskTypeRequestCreation(projectId, taskTypeId, jagaCreateTaskRequest)
				.bodyToMono(localVarReturnType);
	}

	private ResponseSpec getWorkflowRequestCreation(Long workflowId) throws WebClientResponseException {
		Object postBody = null;

		if (workflowId == null) {
			throw new WebClientResponseException(
					"Missing the required parameter 'workflowId' when calling getWorkflow",
					400,
					"Bad Request",
					null,
					null,
					null
			);
		}

		final Map<String, Object> pathParams = new HashMap<>();
		pathParams.put("workflowId", workflowId);

		final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
		final HttpHeaders headerParams = new HttpHeaders();
		final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<>();
		final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<>();

		final String[] localVarAccepts = {
				"application/json",
				"*/*"
		};
		final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);

		final String[] localVarContentTypes = {};
		final MediaType localVarContentType = apiClient.selectHeaderContentType(localVarContentTypes);

		String[] localVarAuthNames = new String[] { "bearer-jwt" };

		ParameterizedTypeReference<JagaWorkflowResponse> localVarReturnType =
				new ParameterizedTypeReference<JagaWorkflowResponse>() {};

		return apiClient.invokeAPI(
				"/backend/workflow/{workflowId}",
				HttpMethod.GET,
				pathParams,
				queryParams,
				postBody,
				headerParams,
				cookieParams,
				formParams,
				localVarAccept,
				localVarContentType,
				localVarAuthNames,
				localVarReturnType
		);
	}

	public Mono<JagaWorkflowResponse> getWorkflow(Long workflowId) throws WebClientResponseException {
		ParameterizedTypeReference<JagaWorkflowResponse> localVarReturnType =
				new ParameterizedTypeReference<JagaWorkflowResponse>() {};
		return getWorkflowRequestCreation(workflowId).bodyToMono(localVarReturnType);
	}
}