<%@ include file="/WEB-INF/jsp/includes/taglibs.jsp" %>

<commons:header />

<div id="nav">
    <div id="nav-title">
        <p><i class="fas fa-tasks"></i> <fmt:message key="console.header.submenu.label.inbox"/></p>
    </div>
    <div id="nav-body">
        <ul id="nav-list">
            <jsp:include page="subMenu.jsp" flush="true" />
        </ul>
    </div>
</div>

<div id="main">
    <div id="main-title"></div>
    <div id="main-action">
        <ul id="main-action-buttons">
        </ul>
    </div>
    <div id="main-body">

        <script>
            function closeDialog() {
                assignmentInbox.refresh();
            }
        </script>

        <div id="main-body-content">
            <!-- Grouping Options -->
            <div style="margin-bottom: 15px;">
                <label for="groupBy" style="margin-right: 10px;"><fmt:message key="console.app.assignment.common.label.groupBy"/>:</label>
                <select id="groupBy" onchange="applyGrouping()" style="margin-right: 20px;">
                    <option value="none"><fmt:message key="console.app.assignment.common.label.noGrouping"/></option>
                    <option value="processId"><fmt:message key="console.app.process.common.label.id"/></option>
                    <option value="assigneeName"><fmt:message key="console.app.assignment.common.label.assignee"/></option>
                </select>
                <button id="expandAll" onclick="expandAllGroups()" style="margin-right: 5px; display: none;"><fmt:message key="console.app.assignment.common.label.expandAll"/></button>
                <button id="collapseAll" onclick="collapseAllGroups()" style="display: none;"><fmt:message key="console.app.assignment.common.label.collapseAll"/></button>
            </div>
            
            <ui:jsontable url="${pageContext.request.contextPath}/web/json/workflow/assignment/list"
                          var="assignmentInbox"
                          divToUpdate="assignmentInbox"
                          jsonData="data"
                          rowsPerPage="15"
                          sort="dateCreated"
                          desc="true"
                          width="100%"
                          href="${pageContext.request.contextPath}/web/client/app/assignment/"
                          hrefParam="activityId"
                          hrefQuery="false"
                          hrefDialog="true"
                          hrefDialogWidth="600px"
                          hrefDialogHeight="400px"
                          hrefDialogTitle="Process Dialog"
                          fields="['activityId','processName','activityName','processVersion', 'dateCreated', 'processId', 'acceptedStatus', 'serviceLevelMonitor', 'due', 'assigneeId', 'assigneeName']"
                          column1="{key: 'processName', label: 'console.app.process.common.label', sortable: false, width: '120'}"
                          column2="{key: 'activityName', label: 'console.app.activity.common.label.name', sortable: false, width: '160'}"
                          column3="{key: 'processVersion', label: 'console.app.process.common.label.version', sortable: false, hide:true}"
                          column4="{key: 'dateCreated', label: 'console.app.assignment.common.label.dateCreated', sortable: true, width: '130'}"
                          column5="{key: 'processId', label: 'console.app.process.common.label.id', sortable: true, hide:true}"
                          column6="{key: 'serviceLevelMonitor', label: 'console.app.assignment.common.label.serviceLevelMonitor', sortable: true, relaxed: true, width: '100'}"
                          column7="{key: 'due', label: 'console.app.assignment.common.label.dueDate', sortable: true, width: '128'}"
                          column8="{key: 'assigneeName', label: 'console.app.assignment.common.label.assignee', sortable: true, width: '120'}"
                          />
            
            <script type="text/javascript">
                function toggleEmbedCode(){
                    var embedToggleCallback = function() {
                        $('#embed-code textarea').focus().select();
                    };
                    $("#embed-code").toggle("slow", embedToggleCallback );
                }
            </script>
                
            <div style="position:relative;margin-bottom:5px;">
                <span id="embed-icon" style="cursor:pointer"><a onclick="toggleEmbedCode()"><fmt:message key="general.method.label.embedCode"/></a></span>
                <c:if test="${!userSecurity.disableHashLogin}"> | 
                <span id="rss-icon"><a target="_blank" href="${pageContext.request.contextPath}${rssLink}"><span><fmt:message key="general.method.label.rss"/></span></a></span>
                </c:if>
            </div>
                
            <div style="clear:both;"></div>
                
            <div id="embed-code" name="embed-code" style="display:none">
                <textarea style="width:100%" rows="8">
<link rel="stylesheet" type="text/css" href="${pageContext.request.scheme}://${pageContext.request.serverName}:${pageContext.request.serverPort}${pageContext.request.contextPath}/css/portlet.css">
<script type="text/javascript" src="${pageContext.request.scheme}://${pageContext.request.serverName}:${pageContext.request.serverPort}${pageContext.request.contextPath}/js/jquery/jquery-3.5.1.min.js"></script>
<script type="text/javascript" src="${pageContext.request.scheme}://${pageContext.request.serverName}:${pageContext.request.serverPort}${pageContext.request.contextPath}/js/jquery/jquery-migrate-3.0.1.min.js"></script>
<script type="text/javascript" src="${pageContext.request.scheme}://${pageContext.request.serverName}:${pageContext.request.serverPort}${pageContext.request.contextPath}/js/json/util.js"></script>
<div id="inbox1"><center><img src="${pageContext.request.scheme}://${pageContext.request.serverName}:${pageContext.request.serverPort}${pageContext.request.contextPath}/images/v3/portlet_loading.gif"/></center></div>
<script type="text/javascript" >$(document).ready(function(){ $.getScript('${pageContext.request.scheme}://${pageContext.request.serverName}:${pageContext.request.serverPort}${pageContext.request.contextPath}/web/js/client/inbox.js?id=1&rows=5&divId=inbox1',null); });</script>
                </textarea>
            </div>
        </div>

    </div>
</div>

<script>
    Template.init("#menu-run", "#nav-run-inbox");
</script>

<script type="text/javascript">
// Grouping logic for the assignment inbox
function applyGrouping() {
    var groupBy = document.getElementById('groupBy').value;
    var table = $("#assignmentInbox table");
    if (!table.length) return;

    // Remove previous grouping
    table.find('tbody tr.group-header').remove();
    table.find('tbody tr').show();

    if (groupBy === 'none') {
        $("#expandAll, #collapseAll").hide();
        return;
    }

    // Build groups
    var rows = table.find('tbody tr');
    var groups = {};
    rows.each(function() {
        var $row = $(this);
        var key = '';
        if (groupBy === 'processId') {
            key = $row.find("td[data-key='processId']").text();
        } else if (groupBy === 'assigneeName') {
            key = $row.find("td[data-key='assigneeName']").text();
        }
        if (!groups[key]) groups[key] = [];
        groups[key].push($row);
    });

    // Remove all rows and re-add with group headers
    var tbody = table.find('tbody');
    var newRows = [];
    Object.keys(groups).forEach(function(group) {
        var groupId = 'group-' + groupBy + '-' + group.replace(/\W/g, '');
        var colspan = table.find('thead th:visible').length;
        var header = $('<tr class="group-header" data-group="' + groupId + '"><td colspan="' + colspan + '" style="background:#f0f0f0;font-weight:bold;cursor:pointer;">' + (group || '(Empty)') + ' <span class="toggle-group" style="float:right;">[-]</span></td></tr>');
        newRows.push(header);
        groups[group].forEach(function($row) {
            $row.attr('data-group', groupId);
            newRows.push($row);
        });
    });
    tbody.empty();
    newRows.forEach(function($row) {
        tbody.append($row);
    });

    // Add toggle logic
    tbody.on('click', 'tr.group-header', function() {
        var groupId = $(this).data('group');
        var rows = tbody.find('tr[data-group="' + groupId + '"]');
        var isVisible = rows.filter(':visible').length > 0;
        if (isVisible) {
            rows.hide();
            $(this).find('.toggle-group').text('[+]');
        } else {
            rows.show();
            $(this).find('.toggle-group').text('[-]');
        }
    });
    $("#expandAll, #collapseAll").show();
}

function expandAllGroups() {
    $("#assignmentInbox tr.group-header").each(function() {
        var groupId = $(this).data('group');
        $(this).find('.toggle-group').text('[-]');
        $("#assignmentInbox tr[data-group='" + groupId + "']").show();
    });
}
function collapseAllGroups() {
    $("#assignmentInbox tr.group-header").each(function() {
        var groupId = $(this).data('group');
        $(this).find('.toggle-group').text('[+]');
        $("#assignmentInbox tr[data-group='" + groupId + "']").hide();
    });
}

// Re-apply grouping after table reload
$(document).on('assignmentInbox:tableReloaded', function() {
    applyGrouping();
});

// Patch the jsontable to trigger event after reload
$(function() {
    if (window.assignmentInbox && assignmentInbox.reload) {
        var origReload = assignmentInbox.reload;
        assignmentInbox.reload = function() {
            origReload.apply(this, arguments);
            setTimeout(function() { $(document).trigger('assignmentInbox:tableReloaded'); }, 200);
        };
    }
});
</script>

<commons:footer />
