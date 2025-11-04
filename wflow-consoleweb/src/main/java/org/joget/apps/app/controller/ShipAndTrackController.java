package org.joget.apps.app.controller;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Map;
import org.json.JSONArray;

import org.joget.apps.app.service.AppService;

import javax.servlet.http.HttpServletResponse;

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

@Controller
public class ShipAndTrackController {

    @Autowired
    AppService appService;

    @RequestMapping("/api/po/(*:id)")
    public void getPoWithItems(Writer writer, HttpServletResponse response, @RequestParam(value = "id", required = true) String id) throws IOException, JSONException {
        AppDefinition appDef = appService.getPublishedAppDefinition("ship_and_track");

        if (appDef == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String poFormId = SecurityUtil.validateStringInput("po");
        String poItemsFormId = SecurityUtil.validateStringInput("po_items_form");
        id = SecurityUtil.validateStringInput(id); 

        Map<String, Object> poData = FormUtil.loadFormData(appDef.getId(), appDef.getVersion().toString(), poFormId, id, false, false, true, null);
       
        if (poData == null || poData.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        Collection<FormRow> poItems = formDataDao.find(
                poItemsFormId,
                "po_item",
                "where c_po_id = ?",            
                new Object[]{ id },
                null, null,null,null
            );

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
}
