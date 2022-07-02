//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.kie.server.client.impl;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.kie.server.api.commands.CommandScript;
import org.kie.server.api.exception.KieServicesException;
import org.kie.server.api.exception.KieServicesHttpException;
import org.kie.server.api.marshalling.Marshaller;
import org.kie.server.api.marshalling.MarshallerFactory;
import org.kie.server.api.marshalling.MarshallingException;
import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.api.model.ServiceResponsesList;
import org.kie.server.api.model.KieServiceResponse.ResponseType;
import org.kie.server.api.rest.RestURI;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesConfiguration.Transport;
import org.kie.server.client.balancer.LoadBalancer;
import org.kie.server.client.jms.ResponseHandler;
import org.kie.server.common.rest.KieServerHttpRequest;
import org.kie.server.common.rest.KieServerHttpRequestException;
import org.kie.server.common.rest.KieServerHttpResponse;
import org.kie.server.common.rest.NoEndpointFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractKieServicesClientImpl {
    private static Logger logger = LoggerFactory.getLogger(AbstractKieServicesClientImpl.class);
    protected static final Boolean BYPASS_AUTH_USER = Boolean.parseBoolean(System.getProperty("org.kie.server.bypass.auth.user", "false"));
    protected LoadBalancer loadBalancer;
    protected final KieServicesConfiguration config;
    protected final Marshaller marshaller;
    protected ClassLoader classLoader;
    protected KieServicesClientImpl owner;
    private ResponseHandler responseHandler;

    public AbstractKieServicesClientImpl(KieServicesConfiguration config) {
        this.config = config.clone();
        this.loadBalancer = config.getLoadBalancer() == null ? LoadBalancer.getDefault(config) : config.getLoadBalancer();
        this.classLoader = Thread.currentThread().getContextClassLoader() != null ? Thread.currentThread().getContextClassLoader() : CommandScript.class.getClassLoader();
        this.marshaller = MarshallerFactory.getMarshaller(config.getExtraClasses(), config.getMarshallingFormat(), this.classLoader);
        this.responseHandler = config.getResponseHandler();
    }

    public AbstractKieServicesClientImpl(KieServicesConfiguration config, ClassLoader classLoader) {
        this.config = config.clone();
        this.loadBalancer = config.getLoadBalancer() == null ? LoadBalancer.getDefault(config) : config.getLoadBalancer();
        this.classLoader = classLoader;
        this.marshaller = MarshallerFactory.getMarshaller(config.getExtraClasses(), config.getMarshallingFormat(), classLoader);
        this.responseHandler = config.getResponseHandler();
    }

    protected String initializeURI(URL url, String servicePrefix) {
        if (url == null) {
            throw new IllegalArgumentException("The url may not be empty or null.");
        } else {
            try {
                url.toURI();
            } catch (URISyntaxException var6) {
                throw new IllegalArgumentException("URL (" + url.toExternalForm() + ") is incorrectly formatted: " + var6.getMessage(), var6);
            }

            String urlString = url.toExternalForm();
            if (!urlString.endsWith("/")) {
                urlString = urlString + "/";
            }

            urlString = urlString + "services/" + servicePrefix + "/server";

            try {
                new URL(urlString);
                return urlString;
            } catch (MalformedURLException var5) {
                throw new IllegalArgumentException("URL (" + url.toExternalForm() + ") is incorrectly formatted: " + var5.getMessage(), var5);
            }
        }
    }

    public void setOwner(KieServicesClientImpl owner) {
        this.owner = owner;
    }

    public LoadBalancer getLoadBalancer() {
        return this.loadBalancer;
    }

    public ResponseHandler getResponseHandler() {
        return this.responseHandler;
    }

    public void setResponseHandler(ResponseHandler responseHandler) {
        if (this.config.getTransport() == Transport.REST) {
            throw new UnsupportedOperationException("Response handlers can only be configured for JMS client");
        } else {
            this.responseHandler = responseHandler;
        }
    }

    protected void throwExceptionOnFailure(ServiceResponse<?> serviceResponse) {
        if (serviceResponse != null && ResponseType.FAILURE.equals(serviceResponse.getType())) {
            throw new KieServicesException(serviceResponse.getMsg());
        }
    }

    protected boolean shouldReturnWithNullResponse(ServiceResponse<?> serviceResponse) {
        if (serviceResponse != null && ResponseType.NO_RESPONSE.equals(serviceResponse.getType())) {
            logger.debug("Returning null as the response type is NO_RESPONSE");
            return true;
        } else {
            return false;
        }
    }

    protected void sendTaskOperation(String containerId, Long taskId, String operation, String queryString) {
        this.sendTaskOperation(containerId, taskId, operation, queryString, (Object)null);
    }

    protected void sendTaskOperation(String containerId, Long taskId, String operation, String queryString, Object data) {
        Map<String, Object> valuesMap = new HashMap();
        valuesMap.put("containerId", containerId);
        valuesMap.put("taskInstanceId", taskId);
        this.makeHttpPutRequestAndCreateCustomResponse(RestURI.build(this.loadBalancer.getUrl(), operation, valuesMap) + queryString, data, String.class, this.getHeaders((Object)null));
    }

    protected <T> ServiceResponse<T> makeHttpGetRequestAndCreateServiceResponse(String uri, Class<T> resultType) {
        KieServerHttpRequest request = this.invoke(uri, new RemoteHttpOperation() {
            public KieServerHttpRequest doOperation(String url) {
                AbstractKieServicesClientImpl.logger.debug("About to send GET request to '{}'", url);
                return AbstractKieServicesClientImpl.this.newRequest(url).get();
            }
        });
        KieServerHttpResponse response = request.response();
        this.owner.setConversationId(response.header("X-KIE-ConversationId"));
        if (response.code() == Status.OK.getStatusCode()) {
            ServiceResponse<T> serviceResponse = (ServiceResponse)this.deserialize(response.body(), ServiceResponse.class);
            this.checkResultType(serviceResponse, resultType);
            return serviceResponse;
        } else {
            throw this.createExceptionForUnexpectedResponseCode(request, response);
        }
    }

    protected <T> T makeHttpGetRequestAndCreateCustomResponse(String uri, Class<T> resultType) {
        KieServerHttpRequest request = this.invoke(uri, new RemoteHttpOperation() {
            public KieServerHttpRequest doOperation(String url) {
                AbstractKieServicesClientImpl.logger.debug("About to send GET request to '{}'", url);
                return AbstractKieServicesClientImpl.this.newRequest(url).get();
            }
        });
        KieServerHttpResponse response = request.response();
        this.owner.setConversationId(response.header("X-KIE-ConversationId"));
        if (response.code() == Status.OK.getStatusCode()) {
            return this.deserialize(response.body(), resultType);
        } else {
            throw this.createExceptionForUnexpectedResponseCode(request, response);
        }
    }

    protected <T> T makeHttpGetRequestAndCreateCustomResponseWithHandleNotFound(String uri, Class<T> resultType) {
        KieServerHttpRequest request = this.invoke(uri, new RemoteHttpOperation() {
            public KieServerHttpRequest doOperation(String url) {
                AbstractKieServicesClientImpl.logger.debug("About to send GET request to '{}'", url);
                return AbstractKieServicesClientImpl.this.newRequest(url).get();
            }
        });
        KieServerHttpResponse response = request.response();
        this.owner.setConversationId(response.header("X-KIE-ConversationId"));
        if (response.code() == Status.NOT_FOUND.getStatusCode()) {
            return null;
        } else if (response.code() == Status.OK.getStatusCode()) {
            return this.deserialize(response.body(), resultType);
        } else {
            throw this.createExceptionForUnexpectedResponseCode(request, response);
        }
    }

    protected String makeHttpGetRequestAndCreateRawResponse(String uri) {
        KieServerHttpRequest request = this.invoke(uri, new RemoteHttpOperation() {
            public KieServerHttpRequest doOperation(String url) {
                AbstractKieServicesClientImpl.logger.debug("About to send GET request to '{}'", url);
                return AbstractKieServicesClientImpl.this.newRequest(url).get();
            }
        });
        KieServerHttpResponse response = request.response();
        this.owner.setConversationId(response.header("X-KIE-ConversationId"));
        if (response.code() == Status.OK.getStatusCode()) {
            return response.body();
        } else {
            throw this.createExceptionForUnexpectedResponseCode(request, response);
        }
    }

    protected String makeHttpGetRequestAndCreateRawResponse(String uri, final Map<String, String> headers) {
        KieServerHttpRequest request = this.invoke(uri, new RemoteHttpOperation() {
            public KieServerHttpRequest doOperation(String url) {
                AbstractKieServicesClientImpl.logger.debug("About to send GET request to '{}'", url);
                return AbstractKieServicesClientImpl.this.newRequest(url).headers(headers).get();
            }
        });
        KieServerHttpResponse response = request.response();
        this.owner.setConversationId(response.header("X-KIE-ConversationId"));
        if (response.code() == Status.OK.getStatusCode()) {
            return response.body();
        } else {
            throw this.createExceptionForUnexpectedResponseCode(request, response);
        }
    }

    protected <T> ServiceResponse<T> makeHttpPostRequestAndCreateServiceResponse(String uri, Object bodyObject, Class<T> resultType) {
        return this.makeHttpPostRequestAndCreateServiceResponse(uri, this.serialize(bodyObject), resultType);
    }

    protected <T> ServiceResponse<T> makeHttpPostRequestAndCreateServiceResponse(String uri, Object bodyObject, Class<T> resultType, Map<String, String> headers) {
        return this.makeHttpPostRequestAndCreateServiceResponse(uri, this.serialize(bodyObject), resultType, headers);
    }

    protected <T> ServiceResponse<T> makeHttpPostRequestAndCreateServiceResponse(String uri, Object bodyObject, Class<T> resultType, Map<String, String> headers, Response.Status status) {
        return this.makeHttpPostRequestAndCreateServiceResponse(uri, this.serialize(bodyObject), resultType, headers, status);
    }

    protected <T> ServiceResponse<T> makeHttpPostRequestAndCreateServiceResponse(String uri, String body, Class<T> resultType) {
        return this.makeHttpPostRequestAndCreateServiceResponse(uri, (String)body, resultType, new HashMap());
    }

    protected <T> ServiceResponse<T> makeHttpPostRequestAndCreateServiceResponse(String uri, String body, Class<T> resultType, Map<String, String> headers) {
        return this.makeHttpPostRequestAndCreateServiceResponse(uri, body, resultType, headers, Status.OK);
    }

    protected <T> ServiceResponse<T> makeHttpPostRequestAndCreateServiceResponse(String uri, final String body, Class<T> resultType, final Map<String, String> headers, Response.Status status) {
        KieServerHttpRequest request = this.invoke(uri, new RemoteHttpOperation() {
            public KieServerHttpRequest doOperation(String url) {
                AbstractKieServicesClientImpl.logger.debug("About to send POST request to '{}' with payload '{}'", url, body);
                return AbstractKieServicesClientImpl.this.newRequest(url).headers(headers).body(body).post();
            }
        });
        KieServerHttpResponse response = request.response();
        this.owner.setConversationId(response.header("X-KIE-ConversationId"));
        if (response.code() == status.getStatusCode()) {
            ServiceResponse<T> serviceResponse = (ServiceResponse)this.deserialize(response.body(), ServiceResponse.class);
            this.checkResultType(serviceResponse, resultType);
            return serviceResponse;
        } else {
            throw this.createExceptionForUnexpectedResponseCode(request, response);
        }
    }

    protected <T> T makeHttpPostRequestAndCreateCustomResponse(String uri, Object bodyObject, Class<T> resultType, Map<String, String> headers) {
        return this.makeHttpPostRequestAndCreateCustomResponse(uri, this.serialize(bodyObject), resultType, headers);
    }

    protected <T> T makeHttpPostRequestAndCreateCustomResponse(String uri, Object bodyObject, Class<T> resultType) {
        Map map =  new HashMap();
        map.put("Authorization", "Basic YWRtaW46YWRtaW4=");
        return (T) this.makeHttpPostRequestAndCreateCustomResponse(uri, (String)this.serialize(bodyObject), resultType, map);
    }

    protected <T> T makeHttpPostRequestAndCreateCustomResponse(String uri, final String body, Class<T> resultType, final Map<String, String> headers) {
        KieServerHttpRequest request = this.invoke(uri, new RemoteHttpOperation() {
            public KieServerHttpRequest doOperation(String url) {
                AbstractKieServicesClientImpl.logger.debug("About to send POST request to '{}' with payload '{}'", url, body);
                return AbstractKieServicesClientImpl.this.newRequest(url).headers(headers).body(body).post();
            }
        });
        KieServerHttpResponse response = request.response();
        this.owner.setConversationId(response.header("X-KIE-ConversationId"));
        if (response.code() != Status.OK.getStatusCode() && response.code() != Status.CREATED.getStatusCode()) {
            throw this.createExceptionForUnexpectedResponseCode(request, response);
        } else {
            return this.deserialize(response.body(), resultType);
        }
    }

    protected <T> ServiceResponse<T> makeHttpPutRequestAndCreateServiceResponse(String uri, Object bodyObject, Class<T> resultType) {
        return this.makeHttpPutRequestAndCreateServiceResponse(uri, this.serialize(bodyObject), resultType);
    }

    protected <T> ServiceResponse<T> makeHttpPutRequestAndCreateServiceResponse(String uri, final String body, Class<T> resultType) {
        KieServerHttpRequest request = this.invoke(uri, new RemoteHttpOperation() {
            public KieServerHttpRequest doOperation(String url) {
                AbstractKieServicesClientImpl.logger.debug("About to send PUT request to '{}' with payload '{}'", url, body);
                return AbstractKieServicesClientImpl.this.newRequest(url).body(body).put();
            }
        });
        KieServerHttpResponse response = request.response();
        this.owner.setConversationId(response.header("X-KIE-ConversationId"));
        if (response.code() != Status.CREATED.getStatusCode() && response.code() != Status.BAD_REQUEST.getStatusCode()) {
            throw this.createExceptionForUnexpectedResponseCode(request, response);
        } else {
            ServiceResponse<T> serviceResponse = (ServiceResponse)this.deserialize(response.body(), ServiceResponse.class);
            this.checkResultType(serviceResponse, resultType);
            return serviceResponse;
        }
    }

    protected <T> T makeHttpPutRequestAndCreateCustomResponse(String uri, Object bodyObject, Class<T> resultType, Map<String, String> headers) {
        return this.makeHttpPutRequestAndCreateCustomResponse(uri, this.serialize(bodyObject), resultType, headers);
    }

    protected <T> T makeHttpPutRequestAndCreateCustomResponse(String uri, final String body, Class<T> resultType, final Map<String, String> headers) {
        KieServerHttpRequest request = this.invoke(uri, new RemoteHttpOperation() {
            public KieServerHttpRequest doOperation(String url) {
                AbstractKieServicesClientImpl.logger.debug("About to send PUT request to '{}' with payload '{}'", url, body);
                return AbstractKieServicesClientImpl.this.newRequest(url).headers(headers).body(body).put();
            }
        });
        KieServerHttpResponse response = request.response();
        this.owner.setConversationId(response.header("X-KIE-ConversationId"));
        if (response.code() == Status.CREATED.getStatusCode()) {
            T serviceResponse = this.deserialize(response.body(), resultType);
            return serviceResponse;
        } else {
            throw this.createExceptionForUnexpectedResponseCode(request, response);
        }
    }

    protected <T> ServiceResponse<T> makeHttpDeleteRequestAndCreateServiceResponse(String uri, Class<T> resultType) {
        KieServerHttpRequest request = this.invoke(uri, new RemoteHttpOperation() {
            public KieServerHttpRequest doOperation(String url) {
                AbstractKieServicesClientImpl.logger.debug("About to send DELETE request to '{}' ", url);
                return AbstractKieServicesClientImpl.this.newRequest(url).delete();
            }
        });
        KieServerHttpResponse response = request.response();
        this.owner.setConversationId(response.header("X-KIE-ConversationId"));
        if (response.code() == Status.OK.getStatusCode()) {
            ServiceResponse<T> serviceResponse = (ServiceResponse)this.deserialize(response.body(), ServiceResponse.class);
            this.checkResultType(serviceResponse, resultType);
            return serviceResponse;
        } else {
            throw this.createExceptionForUnexpectedResponseCode(request, response);
        }
    }

    protected <T> T makeHttpDeleteRequestAndCreateCustomResponse(String uri, Class<T> resultType) {
        KieServerHttpRequest request = this.invoke(uri, new RemoteHttpOperation() {
            public KieServerHttpRequest doOperation(String url) {
                AbstractKieServicesClientImpl.logger.debug("About to send DELETE request to '{}' ", url);
                return AbstractKieServicesClientImpl.this.newRequest(url).delete();
            }
        });
        KieServerHttpResponse response = request.response();
        this.owner.setConversationId(response.header("X-KIE-ConversationId"));
        if (response.code() != Status.OK.getStatusCode() && response.code() != Status.NO_CONTENT.getStatusCode()) {
            throw this.createExceptionForUnexpectedResponseCode(request, response);
        } else {
            return resultType == null ? null : this.deserialize(response.body(), resultType);
        }
    }

    protected KieServerHttpRequest newRequest(String uri) {
        KieServerHttpRequest httpRequest = KieServerHttpRequest.newRequest(uri).followRedirects(true).timeout(this.config.getTimeout());
        httpRequest.accept(this.getMediaType(this.config.getMarshallingFormat()));
        httpRequest.header("X-KIE-ContentType", this.config.getMarshallingFormat().toString());
        if (this.config.getHeaders() != null) {
            Iterator var3 = this.config.getHeaders().entrySet().iterator();

            while(var3.hasNext()) {
                Map.Entry<String, String> header = (Map.Entry)var3.next();
                httpRequest.header((String)header.getKey(), header.getValue());
                logger.debug("Adding additional header {} value {}", header.getKey(), header.getValue());
            }
        }

        if (this.config.getCredentialsProvider() != null) {
            String authorization = this.config.getCredentialsProvider().getAuthorization();
            if (authorization != null && !authorization.isEmpty()) {
                httpRequest.header(this.config.getCredentialsProvider().getHeaderName(), authorization);
            }
        }

        httpRequest.clientCertificate(this.config.getClientCertificate());
        if (this.owner.getConversationId() != null) {
            httpRequest.header("X-KIE-ConversationId", this.owner.getConversationId());
        }

        return httpRequest;
    }

    protected ServiceResponsesList executeJmsCommand(CommandScript command) {
        return this.executeJmsCommand(command, (String)null);
    }

    protected ServiceResponsesList executeJmsCommand(CommandScript command, String classType) {
        return this.executeJmsCommand(command, classType, (String)null, (String)null);
    }

    protected ServiceResponsesList executeJmsCommand(CommandScript command, String classType, String targetCapability) {
        return this.executeJmsCommand(command, classType, targetCapability, (String)null);
    }

    protected ServiceResponsesList executeJmsCommand(CommandScript command, String classType, String targetCapability, String containerId) {
        ConnectionFactory factory = this.config.getConnectionFactory();
        Queue sendQueue = this.config.getRequestQueue();
        Queue responseQueue = this.config.getResponseQueue();
        Connection connection = null;
        Session session = null;
        ServiceResponsesList cmdResponse = null;
        String corrId = UUID.randomUUID().toString();
        String selector = "JMSCorrelationID = '" + corrId + "'";

        ServiceResponsesList var39;
        try {
            MessageProducer producer;
            try {
                if (this.config.getPassword() != null) {
                    connection = factory.createConnection(this.config.getUserName(), this.config.getPassword());
                } else {
                    connection = factory.createConnection();
                }

                session = connection.createSession(this.config.isJmsTransactional(), 1);
                producer = session.createProducer(sendQueue);
                connection.start();
            } catch (JMSException var34) {
                throw new KieServicesException("Unable to setup a JMS connection.", var34);
            }

            try {
                String xmlStr = this.marshaller.marshall(command);
                logger.debug("Message content to be sent '{}'", xmlStr);
                TextMessage textMsg = session.createTextMessage(xmlStr);
                textMsg.setJMSCorrelationID(corrId);
                textMsg.setIntProperty("serialization_format", this.config.getMarshallingFormat().getId());
                textMsg.setIntProperty("kie_interaction_pattern", this.responseHandler.getInteractionPattern());
                if (classType != null) {
                    textMsg.setStringProperty("kie_class_type", classType);
                }

                if (targetCapability != null) {
                    textMsg.setStringProperty("kie_target_capability", targetCapability);
                }

                textMsg.setStringProperty("kie_user", this.config.getUserName());
                textMsg.setStringProperty("kie_password", this.config.getPassword());
                if (containerId != null) {
                    textMsg.setStringProperty("container_id", containerId);
                }

                if (this.owner.getConversationId() != null) {
                    textMsg.setStringProperty("kie_conversation_id", this.owner.getConversationId());
                }

                if (this.config.getHeaders() != null) {
                    Iterator var16 = this.config.getHeaders().entrySet().iterator();

                    while(var16.hasNext()) {
                        Map.Entry<String, String> header = (Map.Entry)var16.next();
                        logger.debug("Adding additional property {} value {}", header.getKey(), header.getValue());
                        textMsg.setStringProperty((String)header.getKey(), (String)header.getValue());
                    }
                }

                producer.send(textMsg);
            } catch (JMSException var35) {
                JMSException jmse = var35;
                throw new KieServicesException("Unable to send a JMS message.", var35);
            } finally {
                if (producer != null) {
                    try {
                        producer.close();
                    } catch (JMSException var33) {
                        logger.warn("Unable to close producer!", var33);
                    }
                }

            }

            cmdResponse = this.responseHandler.handleResponse(selector, connection, session, responseQueue, this.config, this.marshaller, this.owner);
            var39 = cmdResponse;
        } finally {
            this.responseHandler.dispose(connection, session);
        }

        return var39;
    }

    protected String getMediaType(MarshallingFormat format) {
        switch (format) {
            case JAXB:
                return "application/xml";
            case JSON:
                return "application/json";
            default:
                return "application/xml";
        }
    }

    protected String serialize(Object object) {
        if (object == null) {
            return "";
        } else {
            try {
                return this.marshaller.marshall(object);
            } catch (MarshallingException var3) {
                throw new KieServicesException("Error while serializing request data!", var3);
            }
        }
    }

    protected <T> T deserialize(String content, Class<T> type) {
        logger.debug("About to deserialize content: \n '{}' \n into type: '{}'", content, type);
        if (content != null && !content.isEmpty()) {
            try {
                return this.marshaller.unmarshall(content, type);
            } catch (MarshallingException var4) {
                throw new KieServicesException("Error while deserializing data received from server!", var4);
            }
        } else {
            return null;
        }
    }

    protected void checkResultType(ServiceResponse<?> serviceResponse, Class<?> expectedResultType) {
        Object actualResult = serviceResponse.getResult();
        if (actualResult != null && !expectedResultType.isInstance(actualResult)) {
            throw new KieServicesException("Error while creating service response! The actual result type " + serviceResponse.getResult().getClass() + " does not match the expected type " + expectedResultType + "!");
        }
    }

    protected RuntimeException createExceptionForUnexpectedResponseCode(KieServerHttpRequest request, KieServerHttpResponse response) {
        String summaryMessage = "Unexpected HTTP response code when requesting URI '" + request.getUri() + "'! Error code: " + response.code() + ", message: " + response.body();
        logger.debug(summaryMessage + ", response body: " + this.getMessage(response));
        return new KieServicesHttpException(summaryMessage, response.code(), request.getUri().toString(), response.body());
    }

    protected String getMessage(KieServerHttpResponse response) {
        try {
            String body = response.body();
            if (body != null && !body.isEmpty()) {
                return body;
            }
        } catch (Exception var3) {
            logger.debug("Error when getting both of the response {}", var3.getMessage());
        }

        return response.message();
    }

    protected String buildQueryString(String paramName, List<?> items) {
        StringBuilder builder = new StringBuilder("?");
        Iterator var4 = items.iterator();

        while(var4.hasNext()) {
            Object o = var4.next();
            builder.append(paramName).append("=").append(o).append("&");
        }

        builder.deleteCharAt(builder.length() - 1);
        return builder.toString();
    }

    protected Map<String, String> getHeaders(Object object) {
        Map<String, String> headers = new HashMap();
        if (object != null) {
            headers.put("X-KIE-ClassType", object.getClass().getName());
        }

        return headers;
    }

    protected String getUserQueryStr(String userId, char prefix) {
        return BYPASS_AUTH_USER && userId != null ? prefix + "user=" + userId : "";
    }

    protected String getUserQueryStr(String userId) {
        return this.getUserQueryStr(userId, '?');
    }

    protected String getUserAndPagingQueryString(String userId, Integer page, Integer pageSize) {
        StringBuilder queryString = new StringBuilder(this.getUserQueryStr(userId));
        if (queryString.length() == 0) {
            queryString.append("?");
        } else {
            queryString.append("&");
        }

        queryString.append("page=" + page).append("&pageSize=" + pageSize);
        return queryString.toString();
    }

    protected String getUserAndAdditionalParam(String userId, String name, String value) {
        StringBuilder queryString = new StringBuilder(this.getUserQueryStr(userId));
        if (queryString.length() == 0) {
            queryString.append("?");
        } else {
            queryString.append("&");
        }

        queryString.append(name).append("=").append(value);
        return queryString.toString();
    }

    protected String getUserAndAdditionalParams(String userId, String name, List<?> values) {
        StringBuilder queryString = new StringBuilder(this.getUserQueryStr(userId));
        if (values != null) {
            if (queryString.length() == 0) {
                queryString.append("?");
            } else {
                queryString.append("&");
            }

            Iterator var5 = values.iterator();

            while(var5.hasNext()) {
                Object value = var5.next();
                queryString.append(name).append("=").append(value).append("&");
            }

            queryString.deleteCharAt(queryString.length() - 1);
        }

        return queryString.toString();
    }

    protected String getPagingQueryString(String inQueryString, Integer page, Integer pageSize) {
        StringBuilder queryString = new StringBuilder(inQueryString);
        if (queryString.length() == 0) {
            queryString.append("?");
        } else {
            queryString.append("&");
        }

        queryString.append("page=" + page).append("&pageSize=" + pageSize);
        return queryString.toString();
    }

    protected String getSortingQueryString(String inQueryString, String sort, boolean sortOrder) {
        StringBuilder queryString = new StringBuilder(inQueryString);
        if (queryString.length() == 0) {
            queryString.append("?");
        } else {
            queryString.append("&");
        }

        queryString.append("sort=" + sort).append("&sortOrder=" + sortOrder);
        return queryString.toString();
    }

    protected String getAdditionalParams(String inQueryString, String name, List<?> values) {
        StringBuilder queryString = new StringBuilder(inQueryString);
        if (values != null) {
            if (queryString.length() == 0) {
                queryString.append("?");
            } else {
                queryString.append("&");
            }

            Iterator var5 = values.iterator();

            while(var5.hasNext()) {
                Object value = var5.next();
                queryString.append(name).append("=").append(value).append("&");
            }

            queryString.deleteCharAt(queryString.length() - 1);
        }

        return queryString.toString();
    }

    protected Map<?, ?> safeMap(Map<?, ?> map) {
        return map == null ? new HashMap() : new HashMap(map);
    }

    protected List<?> safeList(List<?> list) {
        return list == null ? new ArrayList() : new ArrayList(list);
    }

    protected <T> ServiceResponse<T> makeBackwardCompatibleHttpPostRequestAndCreateServiceResponse(String uri, Object body, Class<T> resultType, Map<String, String> headers) {
        return this.makeBackwardCompatibleHttpPostRequestAndCreateServiceResponse(uri, body, resultType, headers, Status.OK);
    }

    protected <T> ServiceResponse<T> makeBackwardCompatibleHttpPostRequestAndCreateServiceResponse(String uri, Object body, Class<T> resultType, Map<String, String> headers, Response.Status expectedStatus) {
        logger.debug("About to send POST request to '{}' with payload '{}'", uri, body);
        KieServerHttpRequest request = this.newRequest(uri).headers(headers).body(this.serialize(body)).post();
        KieServerHttpResponse response = request.response();
        this.owner.setConversationId(response.header("X-KIE-ConversationId"));
        if (response.code() == expectedStatus.getStatusCode()) {
            ServiceResponse<T> serviceResponse = (ServiceResponse)this.deserialize(response.body(), ServiceResponse.class);
            serviceResponse.setResult((T) this.serialize(serviceResponse.getResult()));
            this.checkResultType(serviceResponse, resultType);
            return serviceResponse;
        } else {
            throw this.createExceptionForUnexpectedResponseCode(request, response);
        }
    }

    protected <T> ServiceResponse<T> makeBackwardCompatibleHttpPostRequestAndCreateServiceResponse(String uri, String body, Class<T> resultType) {
        logger.debug("About to send POST request to '{}' with payload '{}'", uri, body);
        KieServerHttpRequest request = this.newRequest(uri).body(body).post();
        KieServerHttpResponse response = request.response();
        this.owner.setConversationId(response.header("X-KIE-ConversationId"));
        if (response.code() == Status.OK.getStatusCode()) {
            ServiceResponse<T> serviceResponse = (ServiceResponse)this.deserialize(response.body(), ServiceResponse.class);
            serviceResponse.setResult((T) this.serialize(serviceResponse.getResult()));
            this.checkResultType(serviceResponse, resultType);
            return serviceResponse;
        } else {
            throw this.createExceptionForUnexpectedResponseCode(request, response);
        }
    }

    public String getConversationId() {
        return this.owner.getConversationId();
    }

    protected KieServerHttpRequest invoke(String url, RemoteHttpOperation operation) {
        String currentUrl = url;
        String nextHost = null;

        while(true) {
            try {
                return operation.doOperation(currentUrl);
            } catch (KieServerHttpRequestException var9) {
                if (!(var9.getCause() instanceof IOException)) {
                    throw var9;
                }

                String failedBaseUrl = this.loadBalancer.markAsFailed(currentUrl);
                logger.warn("Marking endpoint '{}' as failed due to {}", failedBaseUrl, var9.getCause().getMessage());

                try {
                    nextHost = this.loadBalancer.getUrl();
                    currentUrl = nextHost + url.substring(failedBaseUrl.length());
                    logger.debug("Selecting next endpoint from load balancer - '{}'", currentUrl);
                } catch (NoEndpointFoundException var8) {
                    logger.warn("Cannot invoke request - '{}'", var8.getMessage());
                    throw var8;
                }

                if (nextHost == null) {
                    throw new KieServerHttpRequestException("Unable to invoke operation " + operation);
                }
            }
        }
    }

    protected String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException var3) {
            throw new IllegalStateException("Unexpected error while encoding string '" + value + "'", var3);
        }
    }

    protected void close() {
        this.loadBalancer.close();
    }

    private abstract class RemoteHttpOperation {
        private RemoteHttpOperation() {
        }

        public abstract KieServerHttpRequest doOperation(String url);
    }
}
