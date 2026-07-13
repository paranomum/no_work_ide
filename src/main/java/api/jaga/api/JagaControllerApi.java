package api.jaga.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
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

	/**
	 * <p><b>200</b> - OK
	 *
	 * @param projectId        (required)
	 * @param size             (required)
	 * @param page             (required)
	 * @param searchRequestDto (required)
	 * @return SearchResultDto
	 * @throws WebClientResponseException if an error occurs while attempting to invoke the API
	 */
	private ResponseSpec searchTaskByIdOrTitleRequestCreation(
			Long projectId,
			Integer size,
			Integer page,
			SearchRequestDto searchRequestDto
	) throws WebClientResponseException {
		Object postBody = searchRequestDto;

		if (projectId == null) {
			throw new WebClientResponseException(
					"Missing the required parameter 'projectId' when calling searchTaskByIdOrTitle",
					400,
					"Bad Request",
					null,
					null,
					null
			);
		}

		if (size == null) {
			throw new WebClientResponseException(
					"Missing the required parameter 'size' when calling searchTaskByIdOrTitle",
					400,
					"Bad Request",
					null,
					null,
					null
			);
		}

		if (page == null) {
			throw new WebClientResponseException(
					"Missing the required parameter 'page' when calling searchTaskByIdOrTitle",
					400,
					"Bad Request",
					null,
					null,
					null
			);
		}

		if (searchRequestDto == null) {
			throw new WebClientResponseException(
					"Missing the required parameter 'searchRequestDto' when calling searchTaskByIdOrTitle",
					400,
					"Bad Request",
					null,
					null,
					null
			);
		}

		final Map<String, Object> pathParams = new HashMap<>();
		final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
		final HttpHeaders headerParams = new HttpHeaders();
		final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<>();
		final MultiValueMap<String, Object> formParams = new LinkedMultiValueMap<>();

		// ?size=10&page=0&projectId={projectId}
		queryParams.putAll(apiClient.parameterToMultiValueMap(null, "size", size));
		queryParams.putAll(apiClient.parameterToMultiValueMap(null, "page", page));
		queryParams.putAll(apiClient.parameterToMultiValueMap(null, "projectId", projectId));

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

		ParameterizedTypeReference<SearchResultDto> localVarReturnType =
				new ParameterizedTypeReference<SearchResultDto>() {};

		return apiClient.invokeAPI(
				"/backend/task/searchByTitleCode",
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
	 * @param projectId        (required)
	 * @param size             (required)
	 * @param page             (required)
	 * @param searchRequestDto (required)
	 * @return SearchResultDto
	 * @throws WebClientResponseException if an error occurs while attempting to invoke the API
	 */
	public Mono<SearchResultDto> searchTaskByIdOrTitle(
			Long projectId,
			Integer size,
			Integer page,
			SearchRequestDto searchRequestDto
	) throws WebClientResponseException {
		ParameterizedTypeReference<SearchResultDto> localVarReturnType =
				new ParameterizedTypeReference<SearchResultDto>() {};
		return searchTaskByIdOrTitleRequestCreation(projectId, size, page, searchRequestDto)
				.bodyToMono(localVarReturnType);
	}

	private ResponseSpec createAttachmentRequestCreation(
			Long projectId,
			byte[] fileBytes,
			String fileName,
			String fileContentType
	) throws WebClientResponseException {

		if (projectId == null) {
			throw new WebClientResponseException(
					"Missing the required parameter 'projectId' when calling createAttachment",
					400,
					"Bad Request",
					null,
					null,
					null
			);
		}

		if (fileBytes == null || fileBytes.length == 0) {
			throw new WebClientResponseException(
					"Missing the required parameter 'fileBytes' when calling createAttachment",
					400,
					"Bad Request",
					null,
					null,
					null
			);
		}

		if (fileName == null || fileName.isBlank()) {
			throw new WebClientResponseException(
					"Missing the required parameter 'fileName' when calling createAttachment",
					400,
					"Bad Request",
					null,
					null,
					null
			);
		}

		final Map<String, Object> pathParams = new HashMap<>();
		final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
		final HttpHeaders headerParams = new HttpHeaders();
		final MultiValueMap<String, String> cookieParams = new LinkedMultiValueMap<>();

		final String[] localVarAccepts = {
				"application/json",
				"*/*",
				"text/plain"
		};
		final List<MediaType> localVarAccept = apiClient.selectHeaderAccept(localVarAccepts);

		String[] localVarAuthNames = new String[] { "bearer-jwt" };
		apiClient.updateParamsForAuth(localVarAuthNames, queryParams, headerParams, cookieParams);

		ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
			@Override
			public String getFilename() {
				return fileName;
			}
		};

		MediaType resolvedFileContentType = MediaType.APPLICATION_OCTET_STREAM;
		if (fileContentType != null && !fileContentType.isBlank()) {
			try {
				resolvedFileContentType = MediaType.parseMediaType(fileContentType);
			} catch (Exception ignored) {
				resolvedFileContentType = MediaType.APPLICATION_OCTET_STREAM;
			}
		}

		MultipartBodyBuilder multipartBuilder = new MultipartBodyBuilder();
		multipartBuilder.part("projectId", String.valueOf(projectId));
		multipartBuilder.part("file", fileResource)
				.filename(fileName)
				.contentType(resolvedFileContentType);

		String finalUri = UriComponentsBuilder
				.fromHttpUrl(apiClient.getBasePath())
				.path("/backend/attacher/file/create")
				.build(false)
				.toUriString();

		WebClient.RequestBodySpec requestBuilder = apiClient.getWebClient()
				.method(HttpMethod.POST)
				.uri(finalUri, pathParams);

		if (localVarAccept != null && !localVarAccept.isEmpty()) {
			requestBuilder.accept(localVarAccept.toArray(new MediaType[0]));
		}

		requestBuilder.contentType(MediaType.MULTIPART_FORM_DATA);

		apiClient.addHeadersToRequest(headerParams, requestBuilder);
		apiClient.addHeadersToRequest(apiClient.getDefaultHeaders(), requestBuilder);
		apiClient.addCookiesToRequest(cookieParams, requestBuilder);
		apiClient.addCookiesToRequest(apiClient.getDefaultCookies(), requestBuilder);

		requestBuilder.body(BodyInserters.fromMultipartData(multipartBuilder.build()));
		return requestBuilder.retrieve();
	}

	public Mono<JagaAttachmentResponse> createAttachment(
			Long projectId,
			byte[] fileBytes,
			String fileName,
			String fileContentType
	) throws WebClientResponseException {
		ParameterizedTypeReference<JagaAttachmentResponse> localVarReturnType =
				new ParameterizedTypeReference<JagaAttachmentResponse>() {};
		return createAttachmentRequestCreation(projectId, fileBytes, fileName, fileContentType)
				.bodyToMono(localVarReturnType);
	}
}