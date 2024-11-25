/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.config.server.controller;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.enums.ApiVersionEnum;
import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import com.alibaba.nacos.config.server.model.ConfigCacheGray;
import com.alibaba.nacos.config.server.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.model.ConfigQueryChainResponse;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.TagGrayRule;
import com.alibaba.nacos.config.server.remote.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.LongPollingService;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.MD5Util;
import com.alibaba.nacos.config.server.utils.Protocol;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.plugin.encryption.handler.EncryptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.api.common.Constants.CONFIG_TYPE;
import static com.alibaba.nacos.api.common.Constants.VIPSERVER_TAG;
import static com.alibaba.nacos.config.server.constant.Constants.CONTENT_MD5;
import static com.alibaba.nacos.config.server.utils.LogUtil.PULL_LOG;

/**
 * ConfigServlet inner for aop.
 *
 * @author Nacos
 */
@Service
public class ConfigServletInner {
    
    private static final int TRY_GET_LOCK_TIMES = 9;
    
    private static final int START_LONG_POLLING_VERSION_NUM = 204;
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigServletInner.class);
    
    private final LongPollingService longPollingService;
    
    private final ConfigQueryChainService configQueryChainService;
    
    public ConfigServletInner(LongPollingService longPollingService, ConfigQueryChainService configQueryChainService) {
        this.longPollingService = longPollingService;
        this.configQueryChainService = configQueryChainService;
    }
    
    /**
     * long polling the config.
     */
    public String doPollingConfig(HttpServletRequest request, HttpServletResponse response,
            Map<String, String> clientMd5Map, int probeRequestSize) throws IOException {
        
        // Long polling.
        if (LongPollingService.isSupportLongPolling(request)) {
            longPollingService.addLongPollingClient(request, response, clientMd5Map, probeRequestSize);
            return HttpServletResponse.SC_OK + "";
        }
        
        // Compatible with short polling logic.
        List<String> changedGroups = MD5Util.compareMd5(request, response, clientMd5Map);
        
        // Compatible with short polling result.
        String oldResult = MD5Util.compareMd5OldResult(changedGroups);
        String newResult = MD5Util.compareMd5ResultString(changedGroups);
        
        String version = request.getHeader(Constants.CLIENT_VERSION_HEADER);
        if (version == null) {
            version = "2.0.0";
        }
        int versionNum = Protocol.getVersionNumber(version);
        
        // Before 2.0.4 version, return value is put into header.
        if (versionNum < START_LONG_POLLING_VERSION_NUM) {
            response.addHeader(Constants.PROBE_MODIFY_RESPONSE, oldResult);
            response.addHeader(Constants.PROBE_MODIFY_RESPONSE_NEW, newResult);
        } else {
            request.setAttribute("content", newResult);
        }
        
        // Disable cache.
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        response.setHeader("Cache-Control", "no-cache,no-store");
        response.setStatus(HttpServletResponse.SC_OK);
        return HttpServletResponse.SC_OK + "";
    }
    
    /**
     * Execute to get config [API V1] or [API V2].
     */
    public String doGetConfig(HttpServletRequest request, HttpServletResponse response, String dataId, String group,
            String tenant, String tag, String isNotify, String clientIp, ApiVersionEnum apiVersion) throws IOException {
        
        boolean notify = StringUtils.isNotBlank(isNotify) && Boolean.parseBoolean(isNotify);
        String groupKey = GroupKey2.getKey(dataId, group, tenant);
        String requestIp = RequestUtil.getRemoteIp(request);
        String requestIpApp = RequestUtil.getAppName(request);
        
        ConfigQueryChainRequest chainRequest = buildChainRequest(request, dataId, group, tenant, tag, clientIp);
        ConfigQueryChainResponse chainResponse = configQueryChainService.handle(chainRequest);
        
        if (ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND == chainResponse.getStatus()) {
            return handlerConfigNotFound(response, apiVersion, dataId, group, tenant, requestIpApp, requestIp, notify);
        }
        
        if (ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_QUERY_CONFLICT == chainResponse.getStatus()) {
            return handlerConfigConflict(response, apiVersion, clientIp, groupKey);
        }
        
        if (chainResponse.getContent() == null) {
            return handlerConfigNotFound(response, apiVersion, dataId, group, tenant, requestIpApp, requestIp, notify);
        }
        
        setResponseHead(response, chainResponse, apiVersion);
        
        writeContent(response, chainResponse, dataId, apiVersion);
        
        String pullEvent = resolvePullEventType(chainResponse, tag);
        
        LogUtil.PULL_CHECK_LOG.warn("{}|{}|{}|{}", groupKey, requestIp, chainResponse.getMd5(), TimeUtils.getCurrentTimeStr());
        final long delayed = notify ? -1 : System.currentTimeMillis() - chainResponse.getLastModified();
        ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, chainResponse.getLastModified(), pullEvent,
                ConfigTraceService.PULL_TYPE_OK, delayed, clientIp, notify, "http");
        
        return HttpServletResponse.SC_OK + "";
    }
    
    private ConfigQueryChainRequest buildChainRequest(HttpServletRequest request, String dataId, String group,
            String tenant, String tag, String clientIp) {
        ConfigQueryChainRequest chainRequest = new ConfigQueryChainRequest();
        
        Map<String, String> appLabels = new HashMap<>(4);
        String autoTag = request.getHeader(VIPSERVER_TAG);
        appLabels.put(BetaGrayRule.CLIENT_IP_LABEL, clientIp);
        if (StringUtils.isNotBlank(tag)) {
            appLabels.put(TagGrayRule.VIP_SERVER_TAG_LABEL, tag);
        } else if (StringUtils.isNotBlank(autoTag)) {
            appLabels.put(TagGrayRule.VIP_SERVER_TAG_LABEL, autoTag);
        }
        
        chainRequest.setDataId(dataId);
        chainRequest.setGroup(group);
        chainRequest.setTenant(tenant);
        chainRequest.setTag(tag);
        chainRequest.setAppLabels(appLabels);
        
        return chainRequest;
    }
    
    private void writeContent(HttpServletResponse response, ConfigQueryChainResponse chainResponse, String dataId, ApiVersionEnum apiVersion)
            throws IOException {
        PrintWriter out = response.getWriter();
        try {
            Pair<String, String> pair = EncryptionHandler.decryptHandler(dataId, chainResponse.getEncryptedDataKey(), chainResponse.getContent());
            String decryptContent = pair.getSecond();
            if (ApiVersionEnum.V2 == apiVersion) {
                out.print(JacksonUtils.toJson(Result.success(decryptContent)));
            } else {
                out.print(decryptContent);
            }
        } finally {
            out.flush();
            out.close();
        }
    }
    
    private String resolvePullEventType(ConfigQueryChainResponse chainResponse, String tag) {
        switch (chainResponse.getStatus()) {
            case BETA:
            case TAG:
                ConfigCacheGray matchedGray = chainResponse.getMatchedGray();
                if (matchedGray != null) {
                    return ConfigTraceService.PULL_EVENT + "-" + matchedGray.getGrayName();
                } else {
                    return ConfigTraceService.PULL_EVENT;
                }
            case TAG_NOT_FOUND:
                return ConfigTraceService.PULL_EVENT + "-" + TagGrayRule.TYPE_TAG + "-" + tag;
            default:
                return ConfigTraceService.PULL_EVENT;
        }
    }
    
    private void setResponseHead(HttpServletResponse response, ConfigQueryChainResponse chainResponse,
            ApiVersionEnum version) {
        String contentType = chainResponse.getContentType();
        FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType(contentType);
        String contentTypeHeader = fileTypeEnum.getContentType();
        
        response.setHeader(CONFIG_TYPE, contentType);
        response.setHeader(HttpHeaderConsts.CONTENT_TYPE, ApiVersionEnum.V2 == version ? MediaType.APPLICATION_JSON : contentTypeHeader);
        response.setHeader(CONTENT_MD5, chainResponse.getMd5());
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
        response.setHeader("Cache-Control", "no-cache,no-store");
        response.setDateHeader("Last-Modified", chainResponse.getLastModified());
        
        if (chainResponse.getEncryptedDataKey() != null) {
            response.setHeader("Encrypted-Data-Key", chainResponse.getEncryptedDataKey());
        }
        
        if (ConfigQueryChainResponse.ConfigQueryStatus.BETA == chainResponse.getStatus()) {
            response.setHeader("isBeta", "true");
        }
        
        if (ConfigQueryChainResponse.ConfigQueryStatus.TAG == chainResponse.getStatus()) {
            try {
                response.setHeader(VIPSERVER_TAG, URLEncoder.encode(chainResponse.getMatchedGray().getGrayRule().getRawGrayRuleExp(),
                        StandardCharsets.UTF_8.displayName()));
            } catch (Exception e) {
                LOGGER.error("Error encoding tag", e);
            }
        }
    }
    
    private String handlerConfigConflict(HttpServletResponse response, ApiVersionEnum version, String clientIp, String groupKey)
            throws IOException {
        PULL_LOG.info("[client-get] clientIp={}, {}, get data during dump", clientIp, groupKey);
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        PrintWriter writer = response.getWriter();
        if (ApiVersionEnum.V2 == version) {
            writer.println(JacksonUtils.toJson(Result.failure(ErrorCode.RESOURCE_CONFLICT,
                    "requested file is being modified, please try later.")));
        } else {
            writer.println("requested file is being modified, please try later.");
        }
        
        return HttpServletResponse.SC_CONFLICT + "";
    }
    
    private String handlerConfigNotFound(HttpServletResponse response, ApiVersionEnum version, String dataId, String group,
            String tenant, String requestIpApp, String requestIp, boolean notify) throws IOException {
        ConfigTraceService.logPullEvent(dataId, group, tenant, requestIpApp, -1, ConfigTraceService.PULL_EVENT,
                ConfigTraceService.PULL_TYPE_NOTFOUND, -1, requestIp, notify, "http");
        
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        PrintWriter writer = response.getWriter();
        if (ApiVersionEnum.V2 == version) {
            writer.println(JacksonUtils.toJson(Result.failure(ErrorCode.RESOURCE_NOT_FOUND, "config data not exist")));
        } else {
            writer.println("config data not exist");
        }
        
        return HttpServletResponse.SC_NOT_FOUND + "";
    }
}
