package io.dataease.dataset.manage;

import io.dataease.api.chart.dto.ChartViewDTO;
import io.dataease.api.chart.dto.ColumnPermissionItem;
import io.dataease.api.chart.dto.DeSortField;
import io.dataease.api.chart.request.ChartExtRequest;
import io.dataease.api.dataset.dto.DatasetTableDTO;
import io.dataease.api.dataset.dto.EnumValueRequest;
import io.dataease.api.dataset.dto.PreviewSqlDTO;
import io.dataease.api.dataset.dto.SqlLogDTO;
import io.dataease.api.dataset.union.DatasetGroupInfoDTO;
import io.dataease.api.dataset.union.DatasetTableInfoDTO;
import io.dataease.api.dataset.union.model.SQLMeta;
import io.dataease.api.ds.vo.TableField;
import io.dataease.api.permissions.dataset.dto.DataSetRowPermissionsTreeDTO;
import io.dataease.auth.bo.TokenUserBO;
import io.dataease.chart.manage.ChartViewManege;
import io.dataease.chart.utils.ChartDataBuild;
import io.dataease.commons.utils.SqlparserUtils;
import io.dataease.dataset.constant.DatasetTableType;
import io.dataease.dataset.dto.DatasourceSchemaDTO;
import io.dataease.dataset.utils.FieldUtils;
import io.dataease.dataset.utils.SqlUtils;
import io.dataease.dataset.utils.TableUtils;
import io.dataease.datasource.dao.auto.entity.CoreDatasource;
import io.dataease.datasource.dao.auto.mapper.CoreDatasourceMapper;
import io.dataease.datasource.manage.EngineManage;
import io.dataease.datasource.provider.CalciteProvider;
import io.dataease.datasource.request.DatasourceRequest;
import io.dataease.dto.dataset.DatasetTableFieldDTO;
import io.dataease.engine.constant.ExtFieldConstant;
import io.dataease.engine.constant.SQLConstants;
import io.dataease.engine.constant.SqlPlaceholderConstants;
import io.dataease.engine.sql.SQLProvider;
import io.dataease.engine.trans.Field2SQLObj;
import io.dataease.engine.trans.Order2SQLObj;
import io.dataease.engine.trans.Table2SQLObj;
import io.dataease.engine.trans.WhereTree2Str;
import io.dataease.engine.utils.SQLUtils;
import io.dataease.engine.utils.Utils;
import io.dataease.exception.DEException;
import io.dataease.i18n.Translator;
import io.dataease.utils.AuthUtils;
import io.dataease.utils.BeanUtils;
import io.dataease.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author Junjun
 */
@Component
public class DatasetDataManage {
    @Resource
    private DatasetSQLManage datasetSQLManage;
    @Resource
    private CalciteProvider calciteProvider;
    @Resource
    private CoreDatasourceMapper coreDatasourceMapper;
    @Resource
    private DatasetTableFieldManage datasetTableFieldManage;
    @Resource
    private EngineManage engineManage;
    @Resource
    private DatasetGroupManage datasetGroupManage;
    @Resource
    private PermissionManage permissionManage;
    @Resource
    private DatasetTableSqlLogManage datasetTableSqlLogManage;
    @Resource
    private ChartViewManege chartViewManege;

    private static Logger logger = LoggerFactory.getLogger(DatasetDataManage.class);

    public List<DatasetTableFieldDTO> getTableFields(DatasetTableDTO datasetTableDTO) throws Exception {
        List<DatasetTableFieldDTO> list = null;
        List<TableField> tableFields = null;
        String type = datasetTableDTO.getType();
        DatasetTableInfoDTO tableInfoDTO = JsonUtil.parseObject(datasetTableDTO.getInfo(), DatasetTableInfoDTO.class);
        if (StringUtils.equalsIgnoreCase(type, DatasetTableType.DB) || StringUtils.equalsIgnoreCase(type, DatasetTableType.SQL)) {
            CoreDatasource coreDatasource = coreDatasourceMapper.selectById(datasetTableDTO.getDatasourceId());
            DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
            if (StringUtils.equalsIgnoreCase("excel", coreDatasource.getType()) || StringUtils.equalsIgnoreCase("api", coreDatasource.getType())) {
                coreDatasource = engineManage.getDeEngine();
            }
            BeanUtils.copyBean(datasourceSchemaDTO, coreDatasource);
            datasourceSchemaDTO.setSchemaAlias(String.format(SQLConstants.SCHEMA, datasourceSchemaDTO.getId()));

            DatasourceRequest datasourceRequest = new DatasourceRequest();
            datasourceRequest.setDsList(Map.of(datasourceSchemaDTO.getId(), datasourceSchemaDTO));
            String sql;
            if (StringUtils.equalsIgnoreCase(type, DatasetTableType.DB)) {
                // add table schema
                sql = TableUtils.tableName2Sql(datasourceSchemaDTO, tableInfoDTO.getTable()) + " LIMIT 0 OFFSET 0";
                // replace schema alias, trans dialect
                sql = Utils.replaceSchemaAlias(sql, datasourceRequest.getDsList());
                sql = SqlUtils.transSqlDialect(sql, datasourceRequest.getDsList());
            } else {
                // parser sql params and replace default value
                String originSql = SqlparserUtils.handleVariableDefaultValue(new String(Base64.getDecoder().decode(tableInfoDTO.getSql())), datasetTableDTO.getSqlVariableDetails(), false, false, null, false, datasourceRequest.getDsList());
                // add sql table schema

                sql = SQLUtils.buildOriginPreviewSql(SqlPlaceholderConstants.TABLE_PLACEHOLDER, 0, 0);
                sql = SqlUtils.transSqlDialect(sql, datasourceRequest.getDsList());
                // replace placeholder
                sql = SqlUtils.replaceTablePlaceHolder(sql, originSql);
            }
            datasourceRequest.setQuery(sql.replaceAll("\r\n", " ")
                    .replaceAll("\n", " "));
            logger.info("calcite data table field sql: " + datasourceRequest.getQuery());
            // 获取数据源表的原始字段
            if (StringUtils.equalsIgnoreCase(type, DatasetTableType.DB)) {
                datasourceRequest.setTable(tableInfoDTO.getTable());
                tableFields = calciteProvider.fetchTableField(datasourceRequest);
            } else {
                tableFields = calciteProvider.fetchTableField(datasourceRequest);
            }
        } else {
            // excel,api
            CoreDatasource coreDatasource = engineManage.getDeEngine();
            DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
            BeanUtils.copyBean(datasourceSchemaDTO, coreDatasource);
            datasourceSchemaDTO.setSchemaAlias(String.format(SQLConstants.SCHEMA, datasourceSchemaDTO.getId()));

            DatasourceRequest datasourceRequest = new DatasourceRequest();
            datasourceRequest.setDsList(Map.of(datasourceSchemaDTO.getId(), datasourceSchemaDTO));
            String sql = TableUtils.tableName2Sql(datasourceSchemaDTO, tableInfoDTO.getTable()) + " LIMIT 0 OFFSET 0";
            // replace schema alias, trans dialect
            sql = Utils.replaceSchemaAlias(sql, datasourceRequest.getDsList());
            sql = SqlUtils.transSqlDialect(sql, datasourceRequest.getDsList());
            datasourceRequest.setQuery(sql);
            logger.info("calcite data table field sql: " + datasourceRequest.getQuery());
            tableFields = calciteProvider.fetchTableField(datasourceRequest);
        }
        return transFields(tableFields, true);
    }

    public List<DatasetTableFieldDTO> transFields(List<TableField> tableFields, boolean defaultStatus) {
        return tableFields.stream().map(ele -> {
            DatasetTableFieldDTO dto = new DatasetTableFieldDTO();
            dto.setName(StringUtils.isNotEmpty(ele.getName()) ? ele.getName() : ele.getOriginName());
            dto.setOriginName(ele.getOriginName());
            dto.setChecked(defaultStatus);
            dto.setType(ele.getType());
            int deType = FieldUtils.transType2DeType(ele.getType());
            dto.setDeExtractType(deType);
            dto.setDeType(deType);
            dto.setGroupType(FieldUtils.transDeType2DQ(deType));
            dto.setExtField(0);
            dto.setDescription(StringUtils.isNotEmpty(ele.getName()) ? ele.getName() : null);
            return dto;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> previewDataWithLimit(DatasetGroupInfoDTO datasetGroupInfoDTO, Integer start, Integer count, boolean checkPermission) throws Exception {
        Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, null);
        String sql = (String) sqlMap.get("sql");

        // 获取allFields
        List<DatasetTableFieldDTO> fields = datasetGroupInfoDTO.getAllFields();
        if (ObjectUtils.isEmpty(fields)) {
            DEException.throwException(Translator.get("i18n_no_fields"));
        }

        Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
        if (checkPermission) {
            fields = permissionManage.filterColumnPermissions(fields, desensitizationList, datasetGroupInfoDTO.getId(), null);
            if (ObjectUtils.isEmpty(fields)) {
                DEException.throwException(Translator.get("i18n_no_column_permission"));
            }
        }

        buildFieldName(sqlMap, fields);

        Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        List<String> dsList = new ArrayList<>();
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            dsList.add(next.getValue().getType());
        }
        boolean needOrder = Utils.isNeedOrder(dsList);
        boolean crossDs = Utils.isCrossDs(dsMap);
        if (!crossDs) {
            sql = Utils.replaceSchemaAlias(sql, dsMap);
        }

        List<DataSetRowPermissionsTreeDTO> rowPermissionsTree = new ArrayList<>();
        TokenUserBO user = AuthUtils.getUser();
        if (user != null && checkPermission) {
            rowPermissionsTree = permissionManage.getRowPermissionsTree(datasetGroupInfoDTO.getId(), user.getUserId());
        }

        // build query sql
        SQLMeta sqlMeta = new SQLMeta();
        Table2SQLObj.table2sqlobj(sqlMeta, null, "(" + sql + ")", crossDs);
        Field2SQLObj.field2sqlObj(sqlMeta, fields, crossDs, dsMap);
        WhereTree2Str.transFilterTrees(sqlMeta, rowPermissionsTree, fields, crossDs, dsMap);
        Order2SQLObj.getOrders(sqlMeta, fields, datasetGroupInfoDTO.getSortFields(), crossDs, dsMap);
        String querySQL;
        if (start == null || count == null) {
            querySQL = SQLProvider.createQuerySQL(sqlMeta, false, false, needOrder);
        } else {
            querySQL = SQLProvider.createQuerySQLWithLimit(sqlMeta, false, needOrder, false, start, count);
        }
        querySQL = SqlUtils.rebuildSQL(querySQL, sqlMeta, crossDs, dsMap);
        logger.info("calcite data preview sql: " + querySQL);

        // 通过数据源请求数据
        // 调用数据源的calcite获得data
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setQuery(querySQL);
        datasourceRequest.setDsList(dsMap);
        Map<String, Object> data = calciteProvider.fetchResultField(datasourceRequest);
        Map<String, Object> map = new LinkedHashMap<>();
        // 重新构造data
        Map<String, Object> previewData = buildPreviewData(data, fields, desensitizationList);
        map.put("data", previewData);
        if (ObjectUtils.isEmpty(datasetGroupInfoDTO.getId())) {
            map.put("allFields", fields);
        } else {
            List<DatasetTableFieldDTO> fieldList = datasetTableFieldManage.selectByDatasetGroupId(datasetGroupInfoDTO.getId());
            map.put("allFields", fieldList);
        }
        map.put("sql", Base64.getEncoder().encodeToString(querySQL.getBytes()));
        map.put("total", getDatasetTotal(datasetGroupInfoDTO));
        return map;
    }

    public Long getDatasetTotal(Long datasetGroupId) throws Exception {
        DatasetGroupInfoDTO dto = datasetGroupManage.getForCount(datasetGroupId);
        if (StringUtils.equalsIgnoreCase(dto.getNodeType(), "dataset")) {
            return getDatasetTotal(dto);
        }
        return 0L;
    }

    public Long getDatasetTotal(DatasetGroupInfoDTO datasetGroupInfoDTO) throws Exception {
        Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, null);
        String sql = (String) sqlMap.get("sql");

        Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        boolean crossDs = Utils.isCrossDs(dsMap);
        if (!crossDs) {
            sql = Utils.replaceSchemaAlias(sql, dsMap);
        }

        String querySQL = "SELECT COUNT(*) FROM (" + sql + ") t_a_0";
        logger.info("calcite data count sql: " + querySQL);

        // 通过数据源请求数据
        // 调用数据源的calcite获得data
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setQuery(querySQL);
        datasourceRequest.setDsList(dsMap);
        Map<String, Object> data = calciteProvider.fetchResultField(datasourceRequest);
        List<String[]> dataList = (List<String[]>) data.get("data");
        if (ObjectUtils.isNotEmpty(dataList) && ObjectUtils.isNotEmpty(dataList.get(0)) && ObjectUtils.isNotEmpty(dataList.get(0)[0])) {
            return Long.valueOf(dataList.get(0)[0]);
        }
        return 0L;
    }

    public Map<String, Object> previewSqlWithLog(PreviewSqlDTO dto) {
        if (dto == null) {
            return null;
        }
        SqlLogDTO sqlLogDTO = new SqlLogDTO();
        String sql = new String(Base64.getDecoder().decode(dto.getSql()));
        sqlLogDTO.setSql(sql);
        Map<String, Object> map = null;
        try {
            sqlLogDTO.setStartTime(System.currentTimeMillis());
            map = previewSql(dto);
            sqlLogDTO.setEndTime(System.currentTimeMillis());
            sqlLogDTO.setSpend(sqlLogDTO.getEndTime() - sqlLogDTO.getStartTime());
            sqlLogDTO.setStatus("Completed");
        } catch (Exception e) {
            sqlLogDTO.setStatus("Error");
            DEException.throwException(e.getMessage());
        } finally {
            if (ObjectUtils.isNotEmpty(dto.getTableId())) {
                sqlLogDTO.setTableId(dto.getTableId());
                datasetTableSqlLogManage.save(sqlLogDTO);
            }
        }
        return map;
    }

    public Map<String, Object> previewSql(PreviewSqlDTO dto) throws DEException {
        CoreDatasource coreDatasource = coreDatasourceMapper.selectById(dto.getDatasourceId());
        DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
        if (coreDatasource.getType().equalsIgnoreCase("API") || coreDatasource.getType().equalsIgnoreCase("Excel")) {
            BeanUtils.copyBean(datasourceSchemaDTO, engineManage.getDeEngine());
        } else {
            BeanUtils.copyBean(datasourceSchemaDTO, coreDatasource);
        }
        String alias = String.format(SQLConstants.SCHEMA, datasourceSchemaDTO.getId());
        datasourceSchemaDTO.setSchemaAlias(alias);

        Map<Long, DatasourceSchemaDTO> dsMap = new LinkedHashMap<>();
        dsMap.put(datasourceSchemaDTO.getId(), datasourceSchemaDTO);
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDsList(dsMap);

        // parser sql params and replace default value
        String originSql = SqlparserUtils.handleVariableDefaultValue(datasetSQLManage.subPrefixSuffixChar(new String(Base64.getDecoder().decode(dto.getSql()))), dto.getSqlVariableDetails(), true, true, null, false, dsMap);

        // sql 作为临时表，外层加上limit
        String sql;
        if (Utils.isNeedOrder(List.of(datasourceSchemaDTO.getType()))) {
            // 先根据sql获取表字段
            String sqlField = SQLUtils.buildOriginPreviewSql(SqlPlaceholderConstants.TABLE_PLACEHOLDER, 0, 0);

            sqlField = SqlUtils.transSqlDialect(sqlField, datasourceRequest.getDsList());
            // replace placeholder
            sqlField = SqlUtils.replaceTablePlaceHolder(sqlField, originSql);
            datasourceRequest.setQuery(sqlField);
            // 获取数据源表的原始字段
            List<TableField> list = calciteProvider.fetchTableField(datasourceRequest);
            if (ObjectUtils.isEmpty(list)) {
                return null;
            }
            sql = SQLUtils.buildOriginPreviewSqlWithOrderBy(SqlPlaceholderConstants.TABLE_PLACEHOLDER, 100, 0, String.format(SQLConstants.FIELD_DOT, list.get(0).getOriginName()) + " ASC ");
        } else {
            sql = SQLUtils.buildOriginPreviewSql(SqlPlaceholderConstants.TABLE_PLACEHOLDER, 100, 0);
        }
        sql = SqlUtils.transSqlDialect(sql, datasourceRequest.getDsList());
        // replace placeholder
        sql = SqlUtils.replaceTablePlaceHolder(sql, originSql);

        logger.info("calcite data preview sql: " + sql);
        datasourceRequest.setQuery(sql);
        Map<String, Object> data = calciteProvider.fetchResultField(datasourceRequest);
        // 重新构造data
        List<TableField> fList = (List<TableField>) data.get("fields");
        List<DatasetTableFieldDTO> fields = transFields(fList, false);
        Map<String, Object> previewData = buildPreviewData(data, fields, new HashMap<>());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("data", previewData);
        map.put("sql", Base64.getEncoder().encodeToString(sql.getBytes()));
        return map;
    }

    public Map<String, Object> buildPreviewData(Map<String, Object> data, List<DatasetTableFieldDTO> fields, Map<String, ColumnPermissionItem> desensitizationList) {
        Map<String, Object> map = new LinkedHashMap<>();
        List<String[]> dataList = (List<String[]>) data.get("data");
        List<LinkedHashMap<String, Object>> dataObjectList = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(dataList)) {
            for (int i = 0; i < dataList.size(); i++) {
                String[] row = dataList.get(i);
                LinkedHashMap<String, Object> obj = new LinkedHashMap<>();
                if (row.length > 0) {
                    for (int j = 0; j < fields.size(); j++) {
                        if (desensitizationList.keySet().contains(fields.get(j).getDataeaseName())) {
                            obj.put(fields.get(j).getDataeaseName(), ChartDataBuild.desensitizationValue(desensitizationList.get(fields.get(j).getDataeaseName()), String.valueOf(row[j])));
                        } else {
                            obj.put(ObjectUtils.isNotEmpty(fields.get(j).getDataeaseName()) ?
                                    fields.get(j).getDataeaseName() : fields.get(j).getOriginName(), row[j]);
                        }
                    }
                }
                dataObjectList.add(obj);
            }
        }

        map.put("fields", fields);
        map.put("data", dataObjectList);
        return map;
    }

    public void buildFieldName(Map<String, Object> sqlMap, List<DatasetTableFieldDTO> fields) {
        // 获取内层union sql和字段
        List<DatasetTableFieldDTO> unionFields = (List<DatasetTableFieldDTO>) sqlMap.get("field");
        for (DatasetTableFieldDTO datasetTableFieldDTO : fields) {
            DatasetTableFieldDTO dto = datasetTableFieldManage.selectById(datasetTableFieldDTO.getId());
            if (ObjectUtils.isEmpty(dto)) {
                if (Objects.equals(datasetTableFieldDTO.getExtField(), ExtFieldConstant.EXT_NORMAL)) {
                    for (DatasetTableFieldDTO fieldDTO : unionFields) {
                        if (Objects.equals(datasetTableFieldDTO.getDatasetTableId(), fieldDTO.getDatasetTableId())
                                && Objects.equals(datasetTableFieldDTO.getOriginName(), fieldDTO.getOriginName())) {
                            datasetTableFieldDTO.setDataeaseName(fieldDTO.getDataeaseName());
                            datasetTableFieldDTO.setFieldShortName(fieldDTO.getFieldShortName());
                        }
                    }
                }
                if (Objects.equals(datasetTableFieldDTO.getExtField(), ExtFieldConstant.EXT_CALC)) {
                    String dataeaseName = TableUtils.fieldNameShort(datasetTableFieldDTO.getId() + "_" + datasetTableFieldDTO.getOriginName());
                    datasetTableFieldDTO.setDataeaseName(dataeaseName);
                    datasetTableFieldDTO.setFieldShortName(dataeaseName);
                    datasetTableFieldDTO.setDeExtractType(datasetTableFieldDTO.getDeType());
                }
            } else {
                datasetTableFieldDTO.setDataeaseName(dto.getDataeaseName());
                datasetTableFieldDTO.setFieldShortName(dto.getFieldShortName());
            }
        }
    }

    public List<String> getFieldEnum(List<Long> ids) throws Exception {
        // 根据前端传的查询组件field ids，获取所有字段枚举值并去重合并
        List<List<String>> list = new ArrayList<>();
        for (Long id : ids) {
            DatasetTableFieldDTO field = datasetTableFieldManage.selectById(id);
            if (field == null) {
                DEException.throwException(Translator.get("i18n_no_field"));
            }
            List<DatasetTableFieldDTO> allFields = new ArrayList<>();
            // 根据图表计算字段，获取数据集
            Long datasetGroupId;
            if (field.getDatasetGroupId() == null && field.getChartId() != null) {
                ChartViewDTO chart = chartViewManege.getChart(field.getChartId());
                datasetGroupId = chart.getTableId();
                allFields.addAll(datasetTableFieldManage.getChartCalcFields(field.getChartId()));
            } else {
                datasetGroupId = field.getDatasetGroupId();
            }
            DatasetGroupInfoDTO datasetGroupInfoDTO = datasetGroupManage.get(datasetGroupId, null);

            Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, new ChartExtRequest());
            String sql = (String) sqlMap.get("sql");

            allFields.addAll(datasetGroupInfoDTO.getAllFields());

            Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
            boolean crossDs = Utils.isCrossDs(dsMap);
            if (!crossDs) {
                sql = Utils.replaceSchemaAlias(sql, dsMap);
            }

            // build query sql
            SQLMeta sqlMeta = new SQLMeta();
            Table2SQLObj.table2sqlobj(sqlMeta, null, "(" + sql + ")", crossDs);

            // 获取allFields
            List<DatasetTableFieldDTO> fields = Collections.singletonList(field);
            Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
            fields = permissionManage.filterColumnPermissions(fields, desensitizationList, datasetGroupInfoDTO.getId(), null);
            if (ObjectUtils.isEmpty(fields)) {
                DEException.throwException(Translator.get("i18n_no_column_permission"));
            }
            buildFieldName(sqlMap, fields);

            List<String> dsList = new ArrayList<>();
            for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
                dsList.add(next.getValue().getType());
            }
            boolean needOrder = Utils.isNeedOrder(dsList);

            List<DataSetRowPermissionsTreeDTO> rowPermissionsTree = new ArrayList<>();
            TokenUserBO user = AuthUtils.getUser();
            if (user != null) {
                rowPermissionsTree = permissionManage.getRowPermissionsTree(datasetGroupInfoDTO.getId(), user.getUserId());
            }

            Field2SQLObj.field2sqlObj(sqlMeta, fields, crossDs, dsMap);
            WhereTree2Str.transFilterTrees(sqlMeta, rowPermissionsTree, fields, crossDs, dsMap);
            Order2SQLObj.getOrders(sqlMeta, fields, datasetGroupInfoDTO.getSortFields(), crossDs, dsMap);
            String querySQL = SQLProvider.createQuerySQLWithLimit(sqlMeta, false, needOrder, true, 0, 1000);
            querySQL = SqlUtils.rebuildSQL(querySQL, sqlMeta, crossDs, dsMap);
            logger.info("calcite data enum sql: " + querySQL);

            // 通过数据源请求数据
            // 调用数据源的calcite获得data
            DatasourceRequest datasourceRequest = new DatasourceRequest();
            datasourceRequest.setQuery(querySQL);
            datasourceRequest.setDsList(dsMap);
            Map<String, Object> data = calciteProvider.fetchResultField(datasourceRequest);
            List<String[]> dataList = (List<String[]>) data.get("data");
            List<String> previewData = new ArrayList<>();
            if (ObjectUtils.isNotEmpty(dataList)) {
                List<String> tmpData = dataList.stream().map(ele -> (ObjectUtils.isNotEmpty(ele) && ele.length > 0) ? ele[0] : null).collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(tmpData)) {
                    if (desensitizationList.keySet().contains(field.getDataeaseName())) {
                        for (int i = 0; i < tmpData.size(); i++) {
                            previewData.add(ChartDataBuild.desensitizationValue(desensitizationList.get(field.getDataeaseName()), tmpData.get(i)));
                        }
                    } else {
                        previewData = tmpData;
                    }
                }
                list.add(previewData);
            }
        }

        // 重新构造data
        Set<String> result = new LinkedHashSet<>();
        for (List<String> l : list) {
            result.addAll(l);
        }
        return result.stream().toList();
    }

    public List<Map<String, Object>> getFieldEnumObj(EnumValueRequest request) throws Exception {
        List<Long> ids = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(request.getQueryId())) {
            ids.add(request.getQueryId());
        }
        if (ObjectUtils.isNotEmpty(request.getDisplayId())) {
            ids.add(request.getDisplayId());
        }

        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }
        if (ids.size() == 2 && Objects.equals(ids.get(0), ids.get(1))) {
            ids.remove(1);
        }

        SQLMeta sqlMeta = new SQLMeta();
        DatasetGroupInfoDTO datasetGroupInfoDTO = null;
        List<DatasetTableFieldDTO> fields = new ArrayList<>();
        Map<String, Object> sqlMap = null;
        boolean crossDs = false;
        Map<Long, DatasourceSchemaDTO> dsMap = null;

        for (Long id : ids) {
            DatasetTableFieldDTO field = datasetTableFieldManage.selectById(id);
            if (field == null) {
                DEException.throwException(Translator.get("i18n_no_field"));
            }
            List<DatasetTableFieldDTO> allFields = new ArrayList<>();
            // 根据图表计算字段，获取数据集
            Long datasetGroupId;
            if (field.getDatasetGroupId() == null && field.getChartId() != null) {
                ChartViewDTO chart = chartViewManege.getChart(field.getChartId());
                datasetGroupId = chart.getTableId();
                allFields.addAll(datasetTableFieldManage.getChartCalcFields(field.getChartId()));
            } else {
                datasetGroupId = field.getDatasetGroupId();
            }
            datasetGroupInfoDTO = datasetGroupManage.get(datasetGroupId, null);

            sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, new ChartExtRequest());
            String sql = (String) sqlMap.get("sql");

            allFields.addAll(datasetGroupInfoDTO.getAllFields());

            dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
            crossDs = Utils.isCrossDs(dsMap);
            if (!crossDs) {
                sql = Utils.replaceSchemaAlias(sql, dsMap);
            }

            // build query sql
            Table2SQLObj.table2sqlobj(sqlMeta, null, "(" + sql + ")", crossDs);
            fields.add(field);
        }

        // 获取allFields
        Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
        fields = permissionManage.filterColumnPermissions(fields, desensitizationList, datasetGroupInfoDTO.getId(), null);
        if (ObjectUtils.isEmpty(fields)) {
            DEException.throwException(Translator.get("i18n_no_column_permission"));
        }
        buildFieldName(sqlMap, fields);

        List<String> dsList = new ArrayList<>();
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            dsList.add(next.getValue().getType());
        }
        boolean needOrder = Utils.isNeedOrder(dsList);

        List<DataSetRowPermissionsTreeDTO> rowPermissionsTree = new ArrayList<>();
        TokenUserBO user = AuthUtils.getUser();
        if (user != null) {
            rowPermissionsTree = permissionManage.getRowPermissionsTree(datasetGroupInfoDTO.getId(), user.getUserId());
        }

        // 排序
        if (ObjectUtils.isNotEmpty(request.getSortId())) {
            DatasetTableFieldDTO field = datasetTableFieldManage.selectById(request.getSortId());
            if (field == null) {
                DEException.throwException(Translator.get("i18n_no_field"));
            }
            DeSortField deSortField = new DeSortField();
            BeanUtils.copyBean(deSortField, field);
            deSortField.setOrderDirection(request.getSort());
            datasetGroupInfoDTO.setSortFields(Collections.singletonList(deSortField));
        }

        Field2SQLObj.field2sqlObj(sqlMeta, fields, crossDs, dsMap);
        WhereTree2Str.transFilterTrees(sqlMeta, rowPermissionsTree, fields, crossDs, dsMap);
        Order2SQLObj.getOrders(sqlMeta, fields, datasetGroupInfoDTO.getSortFields(), crossDs, dsMap);
        String querySQL = SQLProvider.createQuerySQLWithLimit(sqlMeta, false, needOrder, false, 0, 1000);
        querySQL = SqlUtils.rebuildSQL(querySQL, sqlMeta, crossDs, dsMap);
        logger.info("calcite data enum sql: " + querySQL);

        // 通过数据源请求数据
        // 调用数据源的calcite获得data
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setQuery(querySQL);
        datasourceRequest.setDsList(dsMap);
        Map<String, Object> data = calciteProvider.fetchResultField(datasourceRequest);
        List<String[]> dataList = (List<String[]>) data.get("data");
        Map<String, String[]> distinctData = new LinkedHashMap<>();
        for (String[] arr : dataList) {
            String key = Arrays.toString(arr);
            if (!distinctData.containsKey(key)) {
                distinctData.put(key, arr);
            }
        }

        List<String[]> distinctDataList = new ArrayList<>();
        for (Map.Entry<String, String[]> ele : distinctData.entrySet()) {
            distinctDataList.add(ele.getValue());
        }

        List<Map<String, Object>> previewData = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(distinctDataList)) {
            for (String[] ele : distinctDataList) {
                Map<String, Object> map = new LinkedHashMap<>();
                for (int i = 0; i < ele.length; i++) {
                    String val = ele[i];
                    DatasetTableFieldDTO field = fields.get(i);
                    if (desensitizationList.containsKey(field.getDataeaseName())) {
                        String str = ChartDataBuild.desensitizationValue(desensitizationList.get(field.getDataeaseName()), val);
                        map.put(field.getId() + "", str);
                    } else {
                        map.put(field.getId() + "", val);
                    }
                }
                previewData.add(map);
            }
        }
        return previewData;
    }
}
