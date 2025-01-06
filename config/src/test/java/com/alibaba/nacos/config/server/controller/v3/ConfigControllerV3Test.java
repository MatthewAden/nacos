/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.controller.v3;

import com.alibaba.nacos.common.http.param.MediaType;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.controller.ConfigServletInner;
import com.alibaba.nacos.config.server.controller.parameters.SameNamespaceCloneConfigBean;
import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigMetadata;
import com.alibaba.nacos.config.server.model.GroupkeyListenserStatus;
import com.alibaba.nacos.config.server.model.SampleResult;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.ConfigSubService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.utils.YamlParserUtil;
import com.alibaba.nacos.config.server.utils.ZipUtils;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MockServletContext.class)
@WebAppConfiguration
class ConfigControllerV3Test {
    
    @InjectMocks
    ConfigControllerV3 configControllerV3;
    
    private MockMvc mockmvc;
    
    @Mock
    private ServletContext servletContext;
    
    @Mock
    private ConfigServletInner inner;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    @Mock
    private ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    @Mock
    private NamespacePersistService namespacePersistService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    @Mock
    private ConfigSubService configSubService;
    
    @Mock
    private ConfigDetailService configDetailService;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        when(servletContext.getContextPath()).thenReturn("/nacos");
        ReflectionTestUtils.setField(configControllerV3, "configSubService", configSubService);
        ReflectionTestUtils.setField(configControllerV3, "configInfoPersistService", configInfoPersistService);
        ReflectionTestUtils.setField(configControllerV3, "configInfoBetaPersistService", configInfoBetaPersistService);
        ReflectionTestUtils.setField(configControllerV3, "configInfoGrayPersistService", configInfoGrayPersistService);
        ReflectionTestUtils.setField(configControllerV3, "namespacePersistService", namespacePersistService);
        ReflectionTestUtils.setField(configControllerV3, "configOperationService", configOperationService);
        ReflectionTestUtils.setField(configControllerV3, "inner", inner);
        mockmvc = MockMvcBuilders.standaloneSetup(configControllerV3).build();
    }
    
    @Test
    void testPublishConfig() throws Exception {
        when(configOperationService.publishConfig(any(), any(), anyString())).thenReturn(true);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH)
                .param("dataId", "test").param("group", "test").param("tenant", "").param("content", "test")
                .param("tag", "").param("appName", "").param("src_user", "").param("config_tags", "").param("desc", "")
                .param("use", "").param("effect", "").param("type", "").param("schema", "");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("0", code);
        assertEquals("true", data);
    }
    
    @Test
    void testGetConfig() throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH)
                .param("dataId", "test").param("group", "test").param("tenant", "").param("tag", "");
        int actualValue = mockmvc.perform(builder).andReturn().getResponse().getStatus();
        assertEquals(200, actualValue);
    }
    
    @Test
    void testDeleteConfig() throws Exception {
        when(configOperationService.deleteConfig(anyString(), anyString(), anyString(), anyString(), any(),
                any())).thenReturn(true);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete(Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH)
                .param("dataId", "test").param("group", "test").param("tenant", "").param("tag", "");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("0", code);
        assertEquals("true", data);
    }
    
    @Test
    void testDeleteConfigs() throws Exception {
        
        final List<ConfigAllInfo> resultInfos = new ArrayList<>();
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        String dataId = "dataId1123";
        String group = "group34567";
        String tenant = "tenant45678";
        configAllInfo.setDataId(dataId);
        configAllInfo.setGroup(group);
        configAllInfo.setTenant(tenant);
        resultInfos.add(configAllInfo);
        Mockito.when(configInfoPersistService.removeConfigInfoByIds(eq(Arrays.asList(1L, 2L)), anyString(), eq(null)))
                .thenReturn(resultInfos);
        AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                ConfigDataChangeEvent event1 = (ConfigDataChangeEvent) event;
                if (event1.dataId.equals(dataId)) {
                    reference.set((ConfigDataChangeEvent) event);
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete(Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH + "/batch")
                .param("ids", "1,2");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        assertEquals("0", code);
        assertEquals("true", data);
        Thread.sleep(200L);
        //expect
        assertTrue(reference.get() != null);
    }
    
    @Test
    void testDetailConfigInfo() throws Exception {
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId("test");
        configAllInfo.setGroup("test");
        configAllInfo.setCreateIp("localhost");
        configAllInfo.setCreateUser("test");
        
        when(configInfoPersistService.findConfigAllInfo("test", "test", "public")).thenReturn(configAllInfo);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH + "/detail")
                .param("dataId", "test").param("group", "test").param("tenant", "");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        ConfigAllInfo resConfigAllInfo = JacksonUtils.toObj(data, ConfigAllInfo.class);
        
        assertEquals(configAllInfo.getDataId(), resConfigAllInfo.getDataId());
        assertEquals(configAllInfo.getGroup(), resConfigAllInfo.getGroup());
        assertEquals(configAllInfo.getCreateIp(), resConfigAllInfo.getCreateIp());
        assertEquals(configAllInfo.getCreateUser(), resConfigAllInfo.getCreateUser());
    }
    
    @Test
    void testGetConfigAdvanceInfo() throws Exception {
        
        ConfigAdvanceInfo configAdvanceInfo = new ConfigAdvanceInfo();
        configAdvanceInfo.setCreateIp("localhost");
        configAdvanceInfo.setCreateUser("test");
        configAdvanceInfo.setDesc("desc");
        
        when(configInfoPersistService.findConfigAdvanceInfo("test", "test", "public")).thenReturn(configAdvanceInfo);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(
                        Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH + "/extInfo").param("dataId", "test").param("group", "test")
                .param("tenant", "");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        ConfigAdvanceInfo resConfigAdvanceInfo = JacksonUtils.toObj(data, ConfigAdvanceInfo.class);
        
        assertEquals("0", code);
        assertEquals(configAdvanceInfo.getCreateIp(), resConfigAdvanceInfo.getCreateIp());
        assertEquals(configAdvanceInfo.getCreateUser(), resConfigAdvanceInfo.getCreateUser());
        assertEquals(configAdvanceInfo.getDesc(), resConfigAdvanceInfo.getDesc());
    }
    
    @Test
    void testGetListeners() throws Exception {
        Map<String, String> listenersGroupkeyStatus = new HashMap<>();
        listenersGroupkeyStatus.put("test", "test");
        SampleResult sampleResult = new SampleResult();
        sampleResult.setLisentersGroupkeyStatus(listenersGroupkeyStatus);
        
        when(configSubService.getCollectSampleResult("test", "test", "public", 1)).thenReturn(sampleResult);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(
                        Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH + "/listener").param("dataId", "test").param("group", "test")
                .param("tenant", "").param("sampleTime", "1");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        final String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        GroupkeyListenserStatus groupkeyListenserStatus = JacksonUtils.toObj(data,
                GroupkeyListenserStatus.class);
        assertEquals(200, groupkeyListenserStatus.getCollectStatus());
        assertEquals(1, groupkeyListenserStatus.getLisentersGroupkeyStatus().size());
        assertEquals("test", groupkeyListenserStatus.getLisentersGroupkeyStatus().get("test"));
        assertEquals("0", code);
    }
    
    @Test
    void testSearchConfig() throws Exception {
        List<ConfigInfo> configInfoList = new ArrayList<>();
        ConfigInfo configInfo = new ConfigInfo("test", "test", "test");
        configInfoList.add(configInfo);
        
        Page<ConfigInfo> page = new Page<>();
        page.setTotalCount(15);
        page.setPageNumber(1);
        page.setPagesAvailable(2);
        page.setPageItems(configInfoList);
        Map<String, Object> configAdvanceInfo = new HashMap<>(8);
        
        when(configDetailService.findConfigInfoPage("accurate", 1, 10, "test", "test", "public", configAdvanceInfo)).thenReturn(
                page);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH + "/searchDetail")
                .param("search", "accurate").param("dataId", "test").param("group", "test").param("appName", "")
                .param("tenant", "").param("config_tags", "").param("pageNo", "1").param("pageSize", "10")
                .param("config_detail", "");
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        JsonNode pageItemsNode = JacksonUtils.toObj(data).get("pageItems");
        List resultList = JacksonUtils.toObj(pageItemsNode.toString(), List.class);
        ConfigInfo resConfigInfo = JacksonUtils.toObj(pageItemsNode.get(0).toString(), ConfigInfo.class);
        
        assertEquals(configInfoList.size(), resultList.size());
        assertEquals(configInfo.getDataId(), resConfigInfo.getDataId());
        assertEquals(configInfo.getGroup(), resConfigInfo.getGroup());
        assertEquals(configInfo.getContent(), resConfigInfo.getContent());
    }
    
    @Test
    void testFuzzySearchConfig() throws Exception {
        List<ConfigInfo> configInfoList = new ArrayList<>();
        ConfigInfo configInfo = new ConfigInfo("test", "test", "test");
        configInfoList.add(configInfo);
        
        Page<ConfigInfo> page = new Page<>();
        page.setTotalCount(15);
        page.setPageNumber(1);
        page.setPagesAvailable(2);
        page.setPageItems(configInfoList);
        Map<String, Object> configAdvanceInfo = new HashMap<>(8);
        
        when(configDetailService.findConfigInfoPage("blur", 1, 10, "test", "test", "public", configAdvanceInfo)).thenReturn(
                page);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH + "/searchDetail")
                .param("search", "blur").param("dataId", "test").param("group", "test").param("appName", "")
                .param("tenant", "").param("config_tags", "").param("pageNo", "1").param("pageSize", "10")
                .param("config_detail", "");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        JsonNode pageItemsNode = JacksonUtils.toObj(data).get("pageItems");
        List resultList = JacksonUtils.toObj(pageItemsNode.toString(), List.class);
        ConfigInfo resConfigInfo = JacksonUtils.toObj(pageItemsNode.get(0).toString(), ConfigInfo.class);
        
        assertEquals(configInfoList.size(), resultList.size());
        assertEquals(configInfo.getDataId(), resConfigInfo.getDataId());
        assertEquals(configInfo.getGroup(), resConfigInfo.getGroup());
        assertEquals(configInfo.getContent(), resConfigInfo.getContent());
    }
    
    @Test
    void testStopBeta() throws Exception {
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.delete(Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH + "/beta")
                .param("beta", "true").param("dataId", "test").param("group", "test").param("tenant", "");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        assertEquals("0", code);
        assertEquals("true", data);
    }
    
    @Test
    void testQueryBeta() throws Exception {
        
        ConfigInfoGrayWrapper configInfoBetaWrapper = new ConfigInfoGrayWrapper();
        configInfoBetaWrapper.setDataId("test");
        configInfoBetaWrapper.setGroup("test");
        configInfoBetaWrapper.setContent("test");
        configInfoBetaWrapper.setGrayName("beta");
        configInfoBetaWrapper.setGrayRule("{\"type\":\"beta\",\"version\":\"1.0.0\",\"expr\":\"127.0.0.1,127.0.0.2\",\"priority\":-1000}");
        when(configInfoGrayPersistService.findConfigInfo4Gray("test", "test", "public", "beta")).thenReturn(
                configInfoBetaWrapper);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH + "/beta")
                .param("beta", "true").param("dataId", "test").param("group", "test").param("tenant", "");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        String data = JacksonUtils.toObj(actualValue).get("data").toString();
        ConfigInfoBetaWrapper resConfigInfoBetaWrapper = JacksonUtils.toObj(data, ConfigInfoBetaWrapper.class);
        
        assertEquals("0", code);
        assertEquals(configInfoBetaWrapper.getDataId(), resConfigInfoBetaWrapper.getDataId());
        assertEquals(configInfoBetaWrapper.getGroup(), resConfigInfoBetaWrapper.getGroup());
        assertEquals(configInfoBetaWrapper.getContent(), resConfigInfoBetaWrapper.getContent());
        assertEquals("127.0.0.1,127.0.0.2", resConfigInfoBetaWrapper.getBetaIps());
        
    }
    
    @Test
    void testExportConfig() throws Exception {
        String dataId = "dataId2.json";
        String group = "group2";
        String tenant = "tenant234";
        String appname = "appname2";
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId(dataId);
        configAllInfo.setGroup(group);
        configAllInfo.setTenant(tenant);
        configAllInfo.setAppName(appname);
        configAllInfo.setContent("content1234");
        List<ConfigAllInfo> dataList = new ArrayList<>();
        dataList.add(configAllInfo);
        Mockito.when(configInfoPersistService.findAllConfigInfo4Export(eq(dataId), eq(group), eq(tenant), eq(appname),
                eq(Arrays.asList(1L, 2L)))).thenReturn(dataList);
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH + "/export")
                .param("dataId", dataId).param("group", group).param("tenant", tenant)
                .param("appName", appname).param("ids", "1,2");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("0", code);
    }
    
    @Test
    void testImportAndPublishConfig() throws Exception {
        MockedStatic<ZipUtils> zipUtilsMockedStatic = Mockito.mockStatic(ZipUtils.class);
        List<ZipUtils.ZipItem> zipItems = new ArrayList<>();
        ZipUtils.ZipItem zipItem = new ZipUtils.ZipItem("test/test", "test");
        zipItems.add(zipItem);
        ZipUtils.UnZipResult unziped = new ZipUtils.UnZipResult(zipItems, null);
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", "test".getBytes());
        
        zipUtilsMockedStatic.when(() -> ZipUtils.unzip(file.getBytes())).thenReturn(unziped);
        when(namespacePersistService.tenantInfoCountByTenantId("public")).thenReturn(1);
        Map<String, Object> map = new HashMap<>();
        map.put("test", "test");
        when(configInfoPersistService.batchInsertOrUpdate(anyList(), anyString(), anyString(), any(),
                any())).thenReturn(map);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH + "/import")
                .file(file).param("src_user", "test").param("namespace", "public")
                .param("policy", "ABORT");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("0", code);
        Map<String, Object> resultMap = JacksonUtils.toObj(JacksonUtils.toObj(actualValue).get("data").toString(),
                Map.class);
        assertEquals(map.get("test"), resultMap.get("test").toString());
        
        zipUtilsMockedStatic.close();
    }
    
    @Test
    void testImportAndPublishConfigV2() throws Exception {
        List<ZipUtils.ZipItem> zipItems = new ArrayList<>();
        String dataId = "dataId23456.json";
        String group = "group132";
        String content = "content1234";
        ZipUtils.ZipItem zipItem = new ZipUtils.ZipItem(group + "/" + dataId, content);
        zipItems.add(zipItem);
        ConfigMetadata configMetadata = new ConfigMetadata();
        configMetadata.setMetadata(new ArrayList<>());
        ConfigMetadata.ConfigExportItem configExportItem = new ConfigMetadata.ConfigExportItem();
        configExportItem.setDataId(dataId);
        configExportItem.setGroup(group);
        configExportItem.setType("json");
        configExportItem.setAppName("appna123");
        configMetadata.getMetadata().add(configExportItem);
        ZipUtils.UnZipResult unziped = new ZipUtils.UnZipResult(zipItems,
                new ZipUtils.ZipItem(Constants.CONFIG_EXPORT_METADATA_NEW, YamlParserUtil.dumpObject(configMetadata)));
        MockMultipartFile file = new MockMultipartFile("file", "test.zip", "application/zip", "test".getBytes());
        MockedStatic<ZipUtils> zipUtilsMockedStatic = Mockito.mockStatic(ZipUtils.class);
        zipUtilsMockedStatic.when(() -> ZipUtils.unzip(eq(file.getBytes()))).thenReturn(unziped);
        when(namespacePersistService.tenantInfoCountByTenantId("public")).thenReturn(1);
        Map<String, Object> map = new HashMap<>();
        map.put("test", "test");
        when(configInfoPersistService.batchInsertOrUpdate(anyList(), anyString(), anyString(), any(),
                any())).thenReturn(map);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.multipart(Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH + "/import")
                .file(file).param("src_user", "test").param("namespace", "public")
                .param("policy", "ABORT");
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("0", code);
        Map<String, Object> resultMap = JacksonUtils.toObj(JacksonUtils.toObj(actualValue).get("data").toString(),
                Map.class);
        assertEquals(map.get("test"), resultMap.get("test").toString());
        
        zipUtilsMockedStatic.close();
    }
    
    @Test
    void testCloneConfig() throws Exception {
        SameNamespaceCloneConfigBean sameNamespaceCloneConfigBean = new SameNamespaceCloneConfigBean();
        sameNamespaceCloneConfigBean.setCfgId(1L);
        sameNamespaceCloneConfigBean.setDataId("test");
        sameNamespaceCloneConfigBean.setGroup("test");
        List<SameNamespaceCloneConfigBean> configBeansList = new ArrayList<>();
        configBeansList.add(sameNamespaceCloneConfigBean);
        
        when(namespacePersistService.tenantInfoCountByTenantId("public")).thenReturn(1);
        
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId("test");
        configAllInfo.setGroup("test");
        List<ConfigAllInfo> queryedDataList = new ArrayList<>();
        queryedDataList.add(configAllInfo);
        
        List<Long> idList = new ArrayList<>(configBeansList.size());
        idList.add(sameNamespaceCloneConfigBean.getCfgId());
        
        when(configInfoPersistService.findAllConfigInfo4Export(null, null, null, null, idList)).thenReturn(
                queryedDataList);
        
        Map<String, Object> map = new HashMap<>();
        map.put("test", "test");
        when(configInfoPersistService.batchInsertOrUpdate(anyList(), anyString(), anyString(), any(),
                any())).thenReturn(map);
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(Constants.CONFIG_CONTROLLER_V3_ADMIN_PATH + "/clone")
                .param("clone", "true").param("src_user", "test").param("tenant", "public").param("policy", "ABORT")
                .content(JacksonUtils.toJson(configBeansList)).contentType(MediaType.APPLICATION_JSON);
        
        String actualValue = mockmvc.perform(builder).andReturn().getResponse().getContentAsString();
        
        String code = JacksonUtils.toObj(actualValue).get("code").toString();
        assertEquals("0", code);
        Map<String, Object> resultMap = JacksonUtils.toObj(JacksonUtils.toObj(actualValue).get("data").toString(),
                Map.class);
        assertEquals(map.get("test"), resultMap.get("test").toString());
    }
}