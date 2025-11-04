package org.joget.plugin.enterprise;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.PackageDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.datalist.model.DataList;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.model.DataListFilter;
import org.joget.apps.datalist.model.DataListFilterQueryObject;
import org.joget.apps.datalist.model.DataListQueryParam;
import org.joget.apps.datalist.service.DataListService;
import org.joget.apps.userview.lib.InboxMenu;
import org.joget.apps.userview.model.UserviewBuilderPalette;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.joget.commons.util.TimeZoneUtil;
import org.joget.plugin.base.PluginWebSupport;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.WorkflowProcess;
import org.joget.workflow.model.service.WorkflowManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.joget.workflow.util.WorkflowUtil;
import org.json.JSONArray;
import org.springframework.context.ApplicationContext;

public class UniversalInboxMenu extends InboxMenu implements PluginWebSupport {
    private DataList cacheDataList = null;

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getLabel() {
        return "Universal Inbox";
    }

    @Override
    public String getIcon() {
        return "<i class=\"fas fa-inbox\"></i>";
    }

    @Override
    public String getRenderPage() {
        return null;
    }

    public String getName() {
        return "Universal Inbox Menu";
    }

    public String getVersion() {
        return "5.0.0";
    }

    public String getDescription() {
        return "";
    }

    @Override
    public String getCategory() {
        return UserviewBuilderPalette.CATEGORY_GENERAL;
    }

    @Override
    public boolean isHomePageSupported() {
        return true;
    }

    @Override
    public String getDecoratedMenu() {
        String menuItem = null;
        boolean showRowCount = Boolean.valueOf(getPropertyString("rowCount")).booleanValue();
        if (showRowCount) {
            int rowCount = 0;

            if (!"true".equalsIgnoreCase(getRequestParameterString("isBuilder"))) {
                rowCount = getDataTotalRowCount();
            }

            // sanitize label
            String label = getPropertyString("label");
            if (label != null) {
                label = StringUtil.stripHtmlRelaxed(label);
            }

            // generate menu link
            menuItem = "<a href=\"" + getUrl() + "\" class=\"menu-link default\"><span>" + label
                    + "</span> <span class='pull-right badge rowCount'>" + rowCount + "</span></a>";
        }
        return menuItem;
    }

    @Override
    protected DataList getDataList() {
        if (cacheDataList == null) {
            // get datalist
            ApplicationContext ac = AppUtil.getApplicationContext();
            DataListService dataListService = (DataListService) ac.getBean("dataListService");
            String target = "_self";
            String embed = "";
            if ("true".equalsIgnoreCase(getPropertyString("showPopup"))) {
                target = "popup";
                embed = "&embed=true";
            }
            String json = AppUtil.readPluginResource(getClass().getName(),
                    "/properties/userview/universalInboxMenuListJson.json", new String[] { embed, target }, true,
                    "message/userview/universalInboxMenu");
            cacheDataList = dataListService.fromJson(json);
        }
        return cacheDataList;
    }

    private void logFilterMap(Map<String, String> filterMap, String logTag) {
        if (filterMap == null || filterMap.isEmpty()) {
            LogUtil.info(logTag, "No filters detected in request or datalist.");
            return;
        }

        LogUtil.info(logTag, "------ FILTER MAP START ------");
        for (Map.Entry<String, String> entry : filterMap.entrySet()) {
            LogUtil.info(logTag, String.format("Filter: %-25s = %s", entry.getKey(), entry.getValue()));
        }
        LogUtil.info(logTag, "------ FILTER MAP END --------");
    }

    @Override
    protected DataListCollection getRows(DataList dataList) {
        try {
            DataListCollection resultList = new DataListCollection();
            DataListQueryParam param = dataList.getQueryParam(null, null);
            DataListFilterQueryObject[] queryParams = dataList.getFilterQueryObjects();

            final String logTag = getClass().getName();

            LogUtil.info(logTag, "=== DEBUG: Start getRows ===");

            Map<String, String> filterMap = new HashMap<>();

            if (queryParams == null) {
                LogUtil.info(logTag, "No filter query params found.");
            } else {
                LogUtil.info(logTag, "Filter query params count: " + queryParams.length);
                for (DataListFilterQueryObject queryObj : queryParams) {
                    String query = queryObj.getQuery();
                    String[] values = queryObj.getValues();
                    LogUtil.info(logTag, "Filter Query: " + query);
                    LogUtil.info(logTag, "Filter Values: " + Arrays.toString(values));

                    if (query != null && values != null && values.length > 0) {
                        String normalized = query.toLowerCase();
                        String fieldName = null;
                        if (normalized.contains("processid"))
                            fieldName = "processId";
                        else if (normalized.contains("activityname"))
                            fieldName = "activityName";
                        else if (normalized.contains("processrequesterid"))
                            fieldName = "processRequesterId";
                        else
                            fieldName = "unknown_" + UUID.randomUUID();

                        String value = values[0].replace("%", "").trim();
                        filterMap.put(fieldName, value);
                    }
                }
            }

            HttpServletRequest request = WorkflowUtil.getHttpServletRequest();
            if (request != null) {
                Enumeration<String> paramNames = request.getParameterNames();
                while (paramNames.hasMoreElements()) {
                    String name = paramNames.nextElement();
                    if (name.startsWith("d-") && name.contains("fn_")) {
                        String value = request.getParameter(name);
                        if (value != null && !value.trim().isEmpty()) {
                            String cleanField = name.substring(name.indexOf("fn_") + 3);
                            filterMap.put(cleanField, value.trim());
                        }
                    }
                }

            }

            logFilterMap(filterMap, logTag);

            String processIdParam = filterMap.get("processId");
            String activityNameParam = filterMap.get("activityName");
            String requesterParam = filterMap.get("processRequesterId");
            String searchType = filterMap.get("searchType");
            if (processIdParam == null) processIdParam = "";
            if (searchType == null) searchType = "any";
            switch(searchType) {
                case "exact":
                    break;
                case "startsWith":
                    processIdParam = processIdParam + "%";
                    break;
                case "endsWith":
                    processIdParam = "%" + processIdParam;
                    break;
                default:
                    processIdParam = "%" + processIdParam + "%";
                    break;
            }

            // Get assignments with filters
            WorkflowManager workflowManager = (WorkflowManager) WorkflowUtil.getApplicationContext()
                    .getBean("workflowManager");
            Collection<WorkflowAssignment> assignmentList = workflowManager.getAssignmentListLite(null, null,
                    processIdParam, null, param.getSort(), param.getDesc(), param.getStart(), param.getSize());

            LogUtil.info(logTag, "Assignments returned (before local filtering): "
                    + (assignmentList != null ? assignmentList.size() : "null"));

            if (assignmentList != null && !assignmentList.isEmpty()) {
                Stream<WorkflowAssignment> stream = assignmentList.stream();

                if (activityNameParam != null && !activityNameParam.isEmpty()) {
                    stream = stream.filter(a -> a.getActivityName() != null &&
                            a.getActivityName().toLowerCase().contains(activityNameParam.toLowerCase()));
                }

                if (requesterParam != null && !requesterParam.isEmpty()) {
                    stream = stream.filter(a -> a.getProcessRequesterId() != null &&
                            a.getProcessRequesterId().toLowerCase().contains(requesterParam.toLowerCase()));
                }

                assignmentList = stream.collect(Collectors.toList());
            }

            LogUtil.info(logTag, "Assignments returned (after local filtering): " + assignmentList.size());

            String format = AppUtil.getAppDateFormat();
            for (WorkflowAssignment assignment : assignmentList) {
                Map data = new HashMap();
                data.put("processId", assignment.getProcessId());
                data.put("processRequesterId", assignment.getProcessRequesterId());
                data.put("activityId", assignment.getActivityId());
                data.put("processName", assignment.getProcessName());
                data.put("activityName", assignment.getActivityName());
                data.put("processVersion", assignment.getProcessVersion());
                data.put("dateCreated", TimeZoneUtil.convertToTimeZone(assignment.getDateCreated(), null, format));
                data.put("acceptedStatus", assignment.isAccepted());
                data.put("dueDate",
                        assignment.getDueDate() != null
                                ? TimeZoneUtil.convertToTimeZone(assignment.getDueDate(), null, format)
                                : "-");
                data.put("serviceLevelMonitor",
                        WorkflowUtil.getServiceLevelIndicator(assignment.getServiceLevelValue()));

                // set results
                resultList.add(data);
            }
            LogUtil.info(logTag, "=== DEBUG: End getRows ===");
            return resultList;
        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "⚠️ Exception in getRows: " + e.getMessage());
            return null;
        }
    }

    @Override
    public int getDataTotalRowCount() {
        DataList dataList = getDataList();
        DataListFilterQueryObject[] queryParams = dataList.getFilterQueryObjects();

        // get filter values for processId
        String processIdParam = null;
        if (queryParams != null && queryParams.length > 0) {
            for (DataListFilterQueryObject queryObj : queryParams) {
                // The query contains the WHERE clause, we need to check if it contains
                // processId field
                if (queryObj.getQuery() != null && queryObj.getQuery().contains("processId")) {
                    // TextFieldDataListFilterType already formats the value with wildcards
                    String[] values = queryObj.getValues();
                    if (values != null && values.length > 0 && values[0] != null && !values[0].isEmpty()) {
                        processIdParam = values[0];
                    }
                    break;
                }
            }
        }

        WorkflowManager workflowManager = (WorkflowManager) WorkflowUtil.getApplicationContext()
                .getBean("workflowManager");
        int count = 0;
        count = workflowManager.getAssignmentSize(null, null, processIdParam);
        return count;
    }

    @Override
    public String getPropertyOptions() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef.getId();
        String appVersion = appDef.getVersion().toString();
        Object[] arguments = new Object[] { PROPERTY_FILTER, PROPERTY_FILTER_ALL, PROPERTY_FILTER_PROCESS, appId,
                appVersion };
        String json = AppUtil.readPluginResource(getClass().getName(), "/properties/userview/universalInboxMenu.json",
                arguments, true, "message/userview/universalInboxMenu");
        return json;
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        boolean isAdmin = WorkflowUtil.isCurrentUserInRole(WorkflowUserManager.ROLE_ADMIN);
        if (!isAdmin) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String action = request.getParameter("action");

        if ("getProcesses".equals(action)) {
            String appId = request.getParameter("appId");
            String appVersion = request.getParameter("appVersion");
            try {
                JSONArray jsonArray = new JSONArray();

                ApplicationContext ac = AppUtil.getApplicationContext();
                AppService appService = (AppService) ac.getBean("appService");
                WorkflowManager workflowManager = (WorkflowManager) ac.getBean("workflowManager");
                AppDefinition appDef = appService.getAppDefinition(appId, appVersion);
                PackageDefinition packageDefinition = appDef.getPackageDefinition();
                Long packageVersion = (packageDefinition != null) ? packageDefinition.getVersion() : new Long(1);
                Collection<WorkflowProcess> processList = workflowManager.getProcessList(null, null);

                Map<String, String> empty = new HashMap<String, String>();
                empty.put("value", "");
                empty.put("label", "");
                jsonArray.put(empty);

                for (WorkflowProcess p : processList) {
                    Map<String, String> option = new HashMap<String, String>();
                    option.put("value", p.getIdWithoutVersion());
                    option.put("label", p.getName());
                    jsonArray.put(option);
                }

                jsonArray.write(response.getWriter());
            } catch (Exception ex) {
                LogUtil.error(this.getClass().getName(), ex, "Get Run Process's options Error!");
            }
        } else {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
    }
}
