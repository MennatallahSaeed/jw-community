package org.joget.apps.app.controller;

import java.io.IOException;
import java.io.Writer;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;

import javax.servlet.http.HttpServletResponse;

import org.joget.apps.app.service.AppService;
import org.joget.commons.util.LogUtil;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.SecurityUtil;

import org.json.JSONException;
import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("/api")
public class ShipAndTrackController {

    @Autowired
    AppService appService;

    @RequestMapping("/po/(*:id)")
    public void getPoWithItems(Writer writer, HttpServletResponse response,
            @RequestParam(value = "id", required = true) String id) throws IOException, JSONException {
        AppDefinition appDef = appService.getPublishedAppDefinition("ship_and_track");

        if (appDef == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String poFormId = SecurityUtil.validateStringInput("po");
        String poItemsFormId = SecurityUtil.validateStringInput("po_items_form");
        id = SecurityUtil.validateStringInput(id);

        Map<String, Object> poData = FormUtil.loadFormData(appDef.getId(), appDef.getVersion().toString(), poFormId, id,
                false, false, true, null);

        if (poData == null || poData.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        Collection<FormRow> poItems = formDataDao.find(
                poItemsFormId,
                "po_item",
                "where c_po_id = ? order by c_item",
                new Object[] { id },
                null, null, null, null);

        JSONObject result = new JSONObject();
        result.put("po", new JSONObject(poData));

        JSONArray itemsArray = new JSONArray();
        for (FormRow row : poItems) {
            itemsArray.put(new JSONObject(row));
        }
        result.put("items", itemsArray);

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Content-Disposition", "inline");

        AppUtil.writeJson(writer, result, null);
    }

    @RequestMapping(value = "/shipments/summary", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public void getShipmentSummary(Writer writer, HttpServletResponse response) throws IOException, JSONException {
        AppDefinition appDef = appService.getPublishedAppDefinition("ship_and_track");
        if (appDef == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String shipmentTableName = "app_fd_shipment";
        javax.sql.DataSource ds = (javax.sql.DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");

        JSONObject result = new JSONObject();
        JSONArray summaryArray = new JSONArray();

        String sql = "SELECT c_type AS type, c_shipment_stage AS stage, COUNT(*) AS total " +
                "FROM " + shipmentTableName + " " +
                "GROUP BY c_type, c_shipment_stage";

        try {
            java.sql.Connection conn = ds.getConnection();
            java.sql.PreparedStatement ps = conn.prepareStatement(sql);
            java.sql.ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                JSONObject obj = new JSONObject();
                obj.put("type", rs.getString("type"));
                obj.put("stage", rs.getString("stage"));
                obj.put("total", rs.getString("total"));
                summaryArray.put(obj);
            }
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            return;
        }

        result.put("summary", summaryArray);

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Content-Disposition", "inline");
        AppUtil.writeJson(writer, result, null);
    }

    @RequestMapping(value = "/shipments/byDate", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public void getShipmentsByDate(
            Writer writer,
            HttpServletResponse response,

            @RequestParam("from") String fromDateStr,
            @RequestParam("to") String toDateStr,

            @RequestParam("dateType") String dateType, // CREATE | ATA | RELEASE
            @RequestParam("domain") String domain, // Technology | Commercial
            @RequestParam("freight") String freight, // Air | Sea

            @RequestParam(value = "supplier", required = false) String supplier,
            @RequestParam(value = "agent", required = false) String agent) throws IOException, JSONException {
        AppDefinition appDef = appService.getPublishedAppDefinition("ship_and_track");
        if (appDef == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        JSONObject result = new JSONObject();
        JSONArray shipmentArray = new JSONArray();

        try {
            javax.sql.DataSource ds = (javax.sql.DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");

            StringBuilder sql = new StringBuilder(
                    "SELECT * FROM vw_shipment_dashboard WHERE 1=1 ");

            List<Object> params = new ArrayList<>();

            if ("ATA".equalsIgnoreCase(dateType)) {
                sql.append(" AND ata BETWEEN ? AND ? ");
            } else if ("RELEASE".equalsIgnoreCase(dateType)) {
                sql.append(" AND release_date BETWEEN ? AND ? ");
            } else if ("CREATION".equalsIgnoreCase(dateType)) {
                sql.append(" AND date_created BETWEEN ? AND ? ");
            }

            params.add(java.sql.Date.valueOf(fromDateStr));
            params.add(java.sql.Date.valueOf(toDateStr));

            if (domain != null && !domain.trim().isEmpty()) {
                sql.append(" AND domain = ? ");
                params.add(domain);
            }

            if (freight != null && !freight.trim().isEmpty()) {
                sql.append(" AND type = ? ");
                params.add(freight);
            }

            if (supplier != null && !supplier.trim().isEmpty()) {
                sql.append(" AND supplier LIKE ? ");
                params.add("%" + supplier + "%");
            }

            if (agent != null && !agent.trim().isEmpty()) {
                sql.append(" AND agent LIKE ? ");
                params.add("%" + agent + "%");
            }

            sql.append(" ORDER BY date_created DESC ");

            try (java.sql.Connection conn = ds.getConnection();
                    PreparedStatement ps = conn.prepareStatement(sql.toString())) {

                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }

                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        JSONObject obj = new JSONObject();
                        obj.put("date_created", rs.getTimestamp("date_created"));
                        obj.put("type", rs.getString("type"));
                        obj.put("storage_egp", rs.getString("storage_egp"));
                        obj.put("storage_usd", rs.getString("storage_usd"));
                        obj.put("demurrage_egp", rs.getString("demurrage_egp"));
                        obj.put("demurrage_usd", rs.getString("demurrage_usd"));
                        obj.put("master_bl", rs.getString("master_bl"));
                        obj.put("bill_of_lading", rs.getString("bill_of_lading"));
                        obj.put("supplier", rs.getString("supplier"));
                        obj.put("ship_total_value", rs.getString("ship_total_value"));
                        obj.put("cur", rs.getString("cur"));
                        obj.put("port_of_origin", rs.getString("port_of_origin"));
                        obj.put("port_of_discharge", rs.getString("port_of_discharge"));
                        obj.put("incoterm", rs.getString("incoterm"));
                        obj.put("weight", rs.getString("weight"));
                        obj.put("packs_pallet", rs.getString("packs_pallet"));
                        obj.put("container_20", rs.getString("container_20"));
                        obj.put("container_40", rs.getString("container_40"));
                        obj.put("containers_total", rs.getString("containers_total"));
                        obj.put("form_4", rs.getString("form_4"));
                        obj.put("form_4_receive_date", rs.getString("form_4_receive_date"));
                        obj.put("shipping_date", rs.getString("shipping_date"));
                        obj.put("eta", rs.getString("eta"));
                        obj.put("ata", rs.getString("ata"));
                        obj.put("freight_of_days", rs.getString("freight_of_days"));
                        obj.put("free_days", rs.getString("free_days"));
                        obj.put("free_days_last_day_date", rs.getString("free_days_last_day_date"));
                        obj.put("shipment_stage", rs.getString("shipment_stage"));
                        obj.put("release_date", rs.getString("release_date"));
                        obj.put("clearance_days", rs.getString("clearance_days"));
                        obj.put("sla", rs.getString("sla"));
                        obj.put("delivery_to_wh_date", rs.getString("delivery_to_wh_date"));
                        obj.put("shipment_cycle_days", rs.getString("shipment_cycle_days"));
                        obj.put("delivery_location_details", rs.getString("delivery_location_details"));
                        shipmentArray.put(obj);
                    }
                }
            }

            result.put("shipments", shipmentArray);

            try {
                response.setContentType("application/json;charset=UTF-8");
                response.setHeader("Content-Disposition", "inline");
                AppUtil.writeJson(writer, result, null);
            } catch (Exception e) {
                System.err.println("Error writing JSON: " + e);
                e.printStackTrace();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.toString());
            }

        } catch (Exception e) {
            System.err.println("Error executing date filter: " + e);
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.toString());
        }
    }
}